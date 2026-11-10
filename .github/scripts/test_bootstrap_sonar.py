import importlib.util
import io
import os
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("bootstrap", Path(__file__).with_name("bootstrap-sonar.py"))
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)

class BootstrapTests(unittest.TestCase):
    def test_missing_project_is_not_created_unbound(self):
        with patch.object(bootstrap, "sonar", return_value=(404, {})) as api:
            with self.assertRaisesRegex(RuntimeError, "Import"):
                bootstrap.ensure_project("example-python", "fake")
            self.assertEqual(api.call_count, 1)

    def test_private_repository_cannot_use_public_project(self):
        with patch.object(bootstrap, "sonar", return_value=(200, {"component": {"organization": "vtsyryuk", "visibility": "public"}})):
            with self.assertRaisesRegex(RuntimeError, "Private"):
                bootstrap.ensure_project("CppPlayground", "fake")

    def test_audit_is_read_only_and_exports_no_credentials(self):
        with patch.dict(os.environ, {"SONAR_TOKEN": "fake", "BOOTSTRAP_PHASE": "audit"}), patch.object(bootstrap, "ensure_project") as check, patch.object(bootstrap, "sonar") as api, patch.object(bootstrap, "seal_for_repositories") as seal, patch("sys.stdout", io.StringIO()):
            bootstrap.main()
            self.assertEqual(check.call_count, 12)
            api.assert_not_called()
            seal.assert_not_called()

    def test_failed_project_audit_never_changes_settings(self):
        with patch.dict(os.environ, {"SONAR_TOKEN": "fake", "BOOTSTRAP_PHASE": "activate"}), patch.object(bootstrap, "ensure_project", side_effect=RuntimeError("missing")), patch.object(bootstrap, "sonar") as api, patch("sys.stdout", io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, "Project setup required"):
                bootstrap.main()
            api.assert_not_called()

if __name__ == "__main__":
    unittest.main()
