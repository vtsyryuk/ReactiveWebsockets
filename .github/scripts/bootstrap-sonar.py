"""Run only in an explicitly dispatched GitHub job; never export a plaintext token."""
import base64
import json
import os
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

REPOS = {
    "UserAccessControl": "master", "ReactiveWebsockets": "master",
    "rust-playground": "main", "ResourceAcquisitionService": "master",
    "terrain_engine": "main", "CppPlayground": "main",
    "HackerRank-Python": "master", "HackerRank-Java": "master",
    "example-python": "master", "TDDAndDesignPatternsExample": "master",
    "rxjava-jdbc": "master", "FrameworkExtensions": "master",
}
ORGANIZATION = "vtsyryuk"


def sonar(path, token, params, *, post=False):
    encoded = urlencode(params)
    url = "https://sonarcloud.io/api/" + path
    data = encoded.encode() if post else None
    if not post:
        url += "?" + encoded
    request = Request(url, data=data, headers={
        "Authorization": "Bearer " + token,
        "Content-Type": "application/x-www-form-urlencoded",
    })
    try:
        with urlopen(request, timeout=60) as response:
            body = response.read()
            return response.status, json.loads(body) if body else {}
    except HTTPError as error:
        # Do not echo request headers or potentially sensitive response bodies.
        return error.code, {}


def require_success(code, operation):
    if code not in (200, 204):
        raise RuntimeError(f"SonarCloud {operation} failed (HTTP {code})")


def ensure_project(name, token):
    key = ORGANIZATION + "_" + name
    code, body = sonar("components/show", token, {"component": key})
    if code == 404:
        raise RuntimeError("Import " + name + " from GitHub into SonarCloud before activation")
    require_success(code, "project access for " + name)
    component = body.get("component", {})
    if component.get("organization") != ORGANIZATION:
        raise RuntimeError("Project organization mismatch for " + name)
    if name == "CppPlayground" and component.get("visibility") != "private":
        raise RuntimeError("Private CppPlayground source must not be sent to a public project")
    return key


def seal_for_repositories(token, public_keys):
    from nacl.public import PublicKey, SealedBox
    expected = {ORGANIZATION + "/" + name for name in REPOS
                if name not in {"ReactiveWebsockets", "UserAccessControl"}}
    if set(public_keys) != expected:
        raise RuntimeError("Public-key recipients must match exactly the ten approved recipient repositories")
    result = {}
    for repo, record in public_keys.items():
        key_id = record["key_id"]
        if not isinstance(key_id, str) or not key_id or len(key_id) > 256:
            raise RuntimeError("Invalid GitHub key identifier")
        key = PublicKey(base64.b64decode(record["key"], validate=True))
        value = SealedBox(key).encrypt(token.encode())
        result[repo] = {"key_id": key_id,
                        "encrypted_value": base64.b64encode(value).decode()}
    return result


def selected_projects():
    requested = os.environ.get("TARGET_PROJECTS", "")
    if not requested:
        return list(REPOS)
    names = [name.strip() for name in requested.split(",")]
    if len(set(names)) != len(names) or not set(names).issubset(REPOS):
        raise RuntimeError("Target projects must be unique approved repository names")
    return names


def audit_projects(token, names):
    failures = []
    for name in names:
        try:
            ensure_project(name, token)
            print(name + ": project accessible")
        except RuntimeError as error:
            failures.append(name)
            print(str(error))
    if failures:
        raise RuntimeError("Project setup required: " + ", ".join(failures))


def align_main_branch(name, token):
    key = ORGANIZATION + "_" + name
    code, body = sonar("project_branches/list", token, {"project": key})
    require_success(code, "main branch lookup for " + name)
    branches = body.get("branches", [])
    main = next((branch for branch in branches if branch.get("isMain")), None)
    if main is None:
        raise RuntimeError("No SonarCloud main branch found for " + name)
    expected = REPOS[name]
    if main.get("name") == expected:
        return
    if any(branch.get("name") == expected for branch in branches):
        raise RuntimeError("Main branch alignment conflicts with an existing branch for " + name)
    code, _ = sonar("project_branches/rename", token, {"project": key, "name": expected}, post=True)
    require_success(code, "main branch alignment for " + name)
    code, body = sonar("project_branches/list", token, {"project": key})
    require_success(code, "main branch verification for " + name)
    if not any(branch.get("isMain") and branch.get("name") == expected for branch in body.get("branches", [])):
        raise RuntimeError("Main branch alignment verification failed for " + name)
    print(name + ": main branch aligned to " + expected)


def activate_project(name, token, gate):
    align_main_branch(name, token)
    key = ORGANIZATION + "_" + name
    # Automatic Analysis uses its own API, not the generic project settings API.
    code, _ = sonar("autoscan/activation", token, {
        "projectKey": key, "enable": "false",
    }, post=True)
    require_success(code, "CI analysis activation for " + name)
    code, current = sonar("qualitygates/get_by_project", token, {
        "organization": ORGANIZATION, "project": key,
    })
    require_success(code, "quality gate verification for " + name)
    if str(current.get("qualityGate", {}).get("id")) != str(gate["id"]):
        code, _ = sonar("qualitygates/select", token, {
            "organization": ORGANIZATION, "projectKey": key, "gateId": gate["id"],
        }, post=True)
        require_success(code, "quality gate selection for " + name)
    print(name + ": Automatic Analysis disabled; Sonar way selected; verify the Actions scan")


def main():
    token = os.environ["SONAR_TOKEN"]
    phase = os.environ["BOOTSTRAP_PHASE"]
    if phase not in ("audit", "prepare", "activate"):
        raise RuntimeError("Invalid bootstrap phase")
    if not token:
        raise RuntimeError("SONAR_TOKEN is missing")
    values = None
    if phase == "prepare":
        values = seal_for_repositories(token, json.loads(os.environ["REPOSITORY_PUBLIC_KEYS"]))
    names = selected_projects()
    audit_projects(token, names)
    if phase == "audit":
        print("Read-only project audit complete; no configuration or credentials changed")
    elif phase == "prepare":
        Path("sonarcloud-encrypted-credentials.json").write_text(json.dumps(values))
        print("Prepared ten GitHub-encrypted secret values; no plaintext credential was exported")
    else:
        code, gates = sonar("qualitygates/list", token, {"organization": ORGANIZATION})
        require_success(code, "quality gate lookup")
        gate = next(g for g in gates["qualitygates"] if g["name"] == "Sonar way")
        for name in names:
            activate_project(name, token, gate)


if __name__ == "__main__":
    main()
