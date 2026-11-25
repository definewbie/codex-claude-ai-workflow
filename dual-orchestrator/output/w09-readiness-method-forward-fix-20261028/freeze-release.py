"""Offline packaging verification. Never selects a client or opens a case ledger."""
from pathlib import Path
import hashlib, json, shutil, subprocess, zipfile

root = Path(__file__).resolve().parents[2]
out = Path(__file__).resolve().parent
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
source = json.loads((out / "reactor-before-source-sha256.json").read_text())
assert {n: digest(root / n) for n in source} == source
assert json.loads((out / "test-results.json").read_text())["exit"] == 0
release = out / "release"
release.mkdir(exist_ok=True)
apps = {}
for app in ["dual-cli", "dual-server"]:
    jar = root / app / "target" / (app + "-0.1.0-SNAPSHOT.jar")
    frozen = release / jar.name
    assert not frozen.exists(), "Do not replace an existing frozen candidate"
    shutil.copy2(jar, frozen)
    modules = []
    own = []
    with zipfile.ZipFile(frozen) as archive:
        for entry in archive.namelist():
            if entry.startswith("BOOT-INF/lib/dual-") and entry.endswith(".jar"):
                name = entry.split("/")[-1].removesuffix("-0.1.0-SNAPSHOT.jar")
                local = root / name / "target" / (name + "-0.1.0-SNAPSHOT.jar")
                assert archive.read(entry) == local.read_bytes(), (app, name)
                modules.append({"module": name, "sha256": digest(local), "embedded_bytes_equal": True})
        for file in (root / app / "target/classes").rglob("*"):
            if not file.is_file():
                continue
            entry = "BOOT-INF/classes/" + file.relative_to(root / app / "target/classes").as_posix()
            assert archive.read(entry) == file.read_bytes(), (app, entry)
            own.append({"entry": entry, "sha256": digest(file)})
    assert jar.read_bytes() == frozen.read_bytes()
    apps[app] = {"frozen_path": str(frozen), "sha256": digest(frozen), "project_modules": modules,
                 "own_classes_resources": own, "copy_and_embedded_bytes_equal": True}

resources = []
for module in ["dual-runtime", "dual-adapter-acpx"]:
    with zipfile.ZipFile(root / module / "target" / (module + "-0.1.0-SNAPSHOT.jar")) as archive:
        for file in (root / module / "src/main/resources").rglob("*"):
            if file.is_file():
                entry = file.relative_to(root / module / "src/main/resources").as_posix()
                assert archive.read(entry) == file.read_bytes()
                resources.append({"module": module, "entry": entry, "sha256": digest(file)})

commands = []
checks = [(["--help"], "cli-help"), (["start", "--help"], "cli-start-help"),
          (["review", "record-readiness", "--help"], "cli-readiness-help")]
for variant in ["contract", "full", "terminal-delivery"]:
    checks.append((["workflow", "validate", str(root / "dual-runtime/src/main/resources/dev/dual/runtime/lifecycle" /
                                               ("agent-lifecycle." + variant + ".json"))], variant + "-validate"))
for args, name in checks:
    command = ["rtk", "proxy", "java", "-jar", apps["dual-cli"]["frozen_path"], *args]
    result = subprocess.run(command, capture_output=True, timeout=60)
    (out / (name + ".stdout")).write_bytes(result.stdout)
    (out / (name + ".stderr")).write_bytes(result.stderr)
    commands.append({"argv": command, "exit": result.returncode,
                     "stdout_sha256": hashlib.sha256(result.stdout).hexdigest(),
                     "stderr_sha256": hashlib.sha256(result.stderr).hexdigest()})
    assert result.returncode == 0, (name, result.returncode)
assert {n: digest(root / n) for n in source} == source
identity = {"status": "FROZEN_CANDIDATE_AWAITING_CC_NOT_RUN_AUTHORIZATION", "source_inputs": source,
            "source_input_count": len(source), "source_before_after_package_equal": True,
            "applications": apps, "module_resources": resources, "readonly_cli_checks": commands,
            "single_reactor_evidence": str(out / "test-results.json"),
            "parent_release_identity": str(root / "output/w09-sixth-forward-fix-20261015/release-identity.json"),
            "current_case_runtime_identity": "UNCHANGED; new binary not selected for stopped OKX case",
            "new_native_configuration_or_compatibility": "NOT_RUN; a future authorized Gate must check",
            "real_native_or_provider_calls": 0, "current_case_resumed": False}
(out / "release-identity.json").write_text(json.dumps(identity, indent=2) + "\n")
(out / "package-after-source-sha256.json").write_text(json.dumps(source, indent=2) + "\n")
print(json.dumps({"applications": {k: v["sha256"] for k, v in apps.items()},
                  "resources": len(resources), "readonly_checks": len(commands)}))
