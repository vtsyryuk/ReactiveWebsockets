import base64
import importlib.util
import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import MagicMock, patch
from urllib.error import HTTPError

from nacl.public import PrivateKey, SealedBox

spec = importlib.util.spec_from_file_location(
    "bootstrap", Path(__file__).with_name("bootstrap-sonar.py"))
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)
TEST_TOKEN = "test-value-not-a-real-credential"


class BootstrapTests(unittest.TestCase):
    def test_http_requests_keep_credential_in_header(self):
        response = MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.read.return_value = b'{"valid":true}'
        with patch.object(bootstrap, "urlopen", return_value=response) as request:
            self.assertEqual(bootstrap.sonar("authentication/validate", TEST_TOKEN, {})[0], 200)
            sent = request.call_args.args[0]
            self.assertNotIn(TEST_TOKEN, sent.full_url)
            self.assertEqual(sent.get_header("Authorization"), "Bearer " + TEST_TOKEN)
            response.read.return_value = b""
            self.assertEqual(bootstrap.sonar("settings/set", TEST_TOKEN, {"value": "false"}, post=True), (200, {}))
            self.assertEqual(request.call_args.args[0].data, b"value=false")

    def test_http_errors_do_not_export_response_body(self):
        error = HTTPError("https://sonarcloud.io/api/example", 403, "Forbidden", {}, None)
        with patch.object(bootstrap, "urlopen", side_effect=error):
            self.assertEqual(bootstrap.sonar("example", TEST_TOKEN, {}), (403, {}))
        with self.assertRaisesRegex(RuntimeError, "HTTP 403"):
            bootstrap.require_success(403, "example")
        bootstrap.require_success(204, "example")

    def test_missing_project_is_not_created_unbound(self):
        with patch.object(bootstrap, "sonar", return_value=(404, {})) as api, self.assertRaisesRegex(RuntimeError, "Import"):
            bootstrap.ensure_project("example-python", TEST_TOKEN)
        self.assertEqual(api.call_count, 1)

    def test_project_organization_and_visibility_are_checked(self):
        public = {"organization": "vtsyryuk", "visibility": "public"}
        with patch.object(bootstrap, "sonar", return_value=(200, {"component": public})), self.assertRaisesRegex(RuntimeError, "Private"):
            bootstrap.ensure_project("CppPlayground", TEST_TOKEN)
        with patch.object(bootstrap, "sonar", return_value=(200, {"component": {"organization": "other"}})), self.assertRaisesRegex(RuntimeError, "organization mismatch"):
            bootstrap.ensure_project("example-python", TEST_TOKEN)
        private = {"organization": "vtsyryuk", "visibility": "private"}
        with patch.object(bootstrap, "sonar", return_value=(200, {"component": private})):
            self.assertEqual(bootstrap.ensure_project("CppPlayground", TEST_TOKEN), "vtsyryuk_CppPlayground")

    def test_only_approved_recipients_can_decrypt(self):
        recipients = {"vtsyryuk/" + n for n in bootstrap.REPOS if n not in {"ReactiveWebsockets", "UserAccessControl"}}
        keys = {name: PrivateKey.generate() for name in recipients}
        public = {name: {"key_id": "test-key", "key": base64.b64encode(bytes(key.public_key)).decode()} for name, key in keys.items()}
        values = bootstrap.seal_for_repositories(TEST_TOKEN, public)
        self.assertEqual(set(values), recipients)
        self.assertNotIn(TEST_TOKEN, json.dumps(values))
        for name, key in keys.items():
            self.assertEqual(SealedBox(key).decrypt(base64.b64decode(values[name]["encrypted_value"])), TEST_TOKEN.encode())
        with self.assertRaisesRegex(RuntimeError, "recipients"):
            bootstrap.seal_for_repositories(TEST_TOKEN, {})
        public[next(iter(public))]["key_id"] = ""
        with self.assertRaisesRegex(RuntimeError, "identifier"):
            bootstrap.seal_for_repositories(TEST_TOKEN, public)

    def test_subset_activation_cannot_target_other_repositories(self):
        with patch.dict(os.environ, {"TARGET_PROJECTS": "rust-playground,CppPlayground"}):
            self.assertEqual(bootstrap.selected_projects(), ["rust-playground", "CppPlayground"])
        for targets in ["other", "CppPlayground,CppPlayground", ","]:
            with patch.dict(os.environ, {"TARGET_PROJECTS": targets}), self.assertRaisesRegex(RuntimeError, "approved"):
                bootstrap.selected_projects()

    def test_audit_is_read_only_and_exports_no_credentials(self):
        with patch.dict(os.environ, {"SONAR_TOKEN": TEST_TOKEN, "BOOTSTRAP_PHASE": "audit"}), patch.object(bootstrap, "ensure_project") as check, patch.object(bootstrap, "sonar") as api, patch.object(bootstrap, "seal_for_repositories") as seal, patch("sys.stdout", io.StringIO()):
            bootstrap.main()
        self.assertEqual(check.call_count, 12)
        api.assert_not_called()
        seal.assert_not_called()

    def test_failed_project_audit_never_changes_settings(self):
        with patch.dict(os.environ, {"SONAR_TOKEN": TEST_TOKEN, "BOOTSTRAP_PHASE": "activate"}), patch.object(bootstrap, "ensure_project", side_effect=RuntimeError("missing")), patch.object(bootstrap, "sonar") as api, patch("sys.stdout", io.StringIO()), self.assertRaisesRegex(RuntimeError, "Project setup required"):
            bootstrap.main()
        api.assert_not_called()

    def test_invalid_inputs_never_audit_or_change_settings(self):
        for environment, message in [({"SONAR_TOKEN": TEST_TOKEN, "BOOTSTRAP_PHASE": "invalid"}, "Invalid"), ({"SONAR_TOKEN": "", "BOOTSTRAP_PHASE": "audit"}, "missing")]:
            with patch.dict(os.environ, environment), patch.object(bootstrap, "audit_projects") as audit, self.assertRaisesRegex(RuntimeError, message):
                bootstrap.main()
            audit.assert_not_called()

    def test_prepare_exports_only_ciphertext_after_audit(self):
        values = {"recipient": {"encrypted_value": "test-ciphertext"}}
        with patch.dict(os.environ, {"SONAR_TOKEN": TEST_TOKEN, "BOOTSTRAP_PHASE": "prepare", "REPOSITORY_PUBLIC_KEYS": "{}"}), patch.object(bootstrap, "audit_projects") as audit, patch.object(bootstrap, "seal_for_repositories", return_value=values), patch.object(bootstrap.Path, "write_text") as write, patch("sys.stdout", io.StringIO()):
            bootstrap.main()
        audit.assert_called_once_with(TEST_TOKEN, list(bootstrap.REPOS))
        self.assertEqual(json.loads(write.call_args.args[0]), values)
        self.assertNotIn(TEST_TOKEN, write.call_args.args[0])

    def test_activate_dispatches_all_checked_projects(self):
        gate = {"id": 9, "name": "Sonar way"}
        with patch.dict(os.environ, {"SONAR_TOKEN": TEST_TOKEN, "BOOTSTRAP_PHASE": "activate"}), patch.object(bootstrap, "audit_projects"), patch.object(bootstrap, "sonar", return_value=(200, {"qualitygates": [gate]})), patch.object(bootstrap, "activate_project") as activate:
            bootstrap.main()
        self.assertEqual(activate.call_count, 12)

    def test_main_branch_alignment_preserves_existing_history(self):
        existing = (200, {"branches": [{"name": "master", "isMain": True}]})
        aligned = (200, {"branches": [{"name": "main", "isMain": True}]})
        with patch.object(bootstrap, "sonar", side_effect=[existing, (204, {}), aligned]) as api, patch("sys.stdout", io.StringIO()):
            bootstrap.align_main_branch("rust-playground", TEST_TOKEN)
        self.assertEqual(api.call_args_list[1].args[0], "project_branches/rename")
        with patch.object(bootstrap, "sonar", return_value=(200, {"branches": []})), self.assertRaisesRegex(RuntimeError, "No SonarCloud"):
            bootstrap.align_main_branch("rust-playground", TEST_TOKEN)
        conflicting = (200, {"branches": [{"name": "master", "isMain": True}, {"name": "main", "isMain": False}]})
        with patch.object(bootstrap, "sonar", return_value=conflicting) as api, self.assertRaisesRegex(RuntimeError, "conflicts"):
            bootstrap.align_main_branch("rust-playground", TEST_TOKEN)
        self.assertEqual(api.call_count, 1)
        with patch.object(bootstrap, "sonar", side_effect=[existing, (204, {}), existing]), self.assertRaisesRegex(RuntimeError, "verification failed"):
            bootstrap.align_main_branch("rust-playground", TEST_TOKEN)

    def test_authorized_rust_repair_only_removes_the_conflicting_non_primary_branch(self):
        conflicting = (200, {"branches": [{"name": "master", "isMain": True}, {"name": "main", "isMain": False}]})
        aligned = (200, {"branches": [{"name": "main", "isMain": True}]})
        with patch.dict(os.environ, {"REMOVE_RUST_CONFLICT": "true"}), patch.object(bootstrap, "sonar", side_effect=[conflicting, (204, {}), (204, {}), aligned]) as api, patch("sys.stdout", io.StringIO()):
            bootstrap.align_main_branch("rust-playground", TEST_TOKEN)
        deletion = api.call_args_list[1]
        self.assertEqual(deletion.args[0], "project_branches/delete")
        self.assertEqual(deletion.args[2], {"project": "vtsyryuk_rust-playground", "branch": "main"})
        self.assertEqual(api.call_args_list[2].args[0], "project_branches/rename")
        with patch.dict(os.environ, {"REMOVE_RUST_CONFLICT": "true"}), patch.object(bootstrap, "sonar", return_value=aligned) as api:
            bootstrap.align_main_branch("rust-playground", TEST_TOKEN)
        self.assertEqual(api.call_count, 1)
        for name, current in [("CppPlayground", "master"), ("rust-playground", "develop")]:
            with patch.dict(os.environ, {"REMOVE_RUST_CONFLICT": "true"}), patch.object(bootstrap, "sonar") as api, self.assertRaisesRegex(RuntimeError, "conflicts"):
                bootstrap.remove_authorized_rust_conflict(name, TEST_TOKEN, current)
            api.assert_not_called()

    def test_activation_uses_dedicated_api_and_preserves_matching_gate(self):
        branch = (200, {"branches": [{"name": "master", "isMain": True}]})
        replies = [branch, (204, {}), (200, {"qualityGate": {"id": 9}})]
        with patch.object(bootstrap, "sonar", side_effect=replies) as api, patch("sys.stdout", io.StringIO()):
            bootstrap.activate_project("example-python", TEST_TOKEN, {"id": 9})
        self.assertEqual(api.call_count, 3)
        self.assertEqual(api.call_args_list[1].args[0], "autoscan/activation")
        self.assertEqual(api.call_args_list[1].args[2], {"projectKey": "vtsyryuk_example-python", "enable": "false"})
        replies[-1] = (200, {"qualityGate": {"id": 2}})
        with patch.object(bootstrap, "sonar", side_effect=replies + [(204, {})]) as api, patch("sys.stdout", io.StringIO()):
            bootstrap.activate_project("example-python", TEST_TOKEN, {"id": 9})
        self.assertEqual(api.call_args.args[0], "qualitygates/select")
        with patch.object(bootstrap, "sonar", return_value=(403, {})) as api, self.assertRaisesRegex(RuntimeError, "HTTP 403"):
            bootstrap.activate_project("example-python", TEST_TOKEN, {"id": 9})


if __name__ == "__main__":
    unittest.main()
