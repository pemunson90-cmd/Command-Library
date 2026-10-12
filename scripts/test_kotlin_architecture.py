"""Regression checks for Kotlin architecture registry contracts."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from check_kotlin_architecture import validate

ROOT = Path(__file__).resolve().parents[1]


class KotlinArchitectureTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        shutil.copytree(ROOT / "docs/architecture", self.root / "docs/architecture")

    def tearDown(self):
        self.tmp.cleanup()

    def change(self, path, fn):
        file = self.root / path
        value = json.loads(file.read_text(encoding="utf-8"))
        fn(value)
        file.write_text(json.dumps(value), encoding="utf-8")

    def test_valid_registry(self):
        self.assertEqual(validate(self.root), [])

    def test_legacy_dependency_drift(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(c for c in value["components"] if c["id"] == "network").update(component_dependencies=[]))
        self.assertTrue(any("network dependencies differ" in error for error in validate(self.root)))

    def test_coverage_primary_drift(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(r for r in value["requirements"] if r["id"] == "A01").update(primary_component="rules"))
        self.assertTrue(any("requirement A01 primary_component differs" in error for error in validate(self.root)))

    def test_module_cycle(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(m for m in value["modules"] if m["id"] == ":core:model").update(dependencies=[":core:common"]))
        self.assertTrue(any("module dependency cycle" in error for error in validate(self.root)))

    def test_unknown_module_reference(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(m for m in value["modules"] if m["id"] == ":feature:catalog").update(dependencies=[":missing:module"]))
        self.assertTrue(any("unknown dependency :missing:module" in error for error in validate(self.root)))

    def test_unlisted_real_module_edge(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(m for m in value["modules"] if m["id"] == ":data:network")["dependencies"].append(":feature:catalog"))
        self.assertTrue(any("module :data:network dependencies do not match" in error for error in validate(self.root)))

    def test_core_common_is_part_of_storage_dependency_envelope(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(m for m in value["modules"] if m["id"] == ":data:storage")["dependencies"].remove(":core:common"))
        self.assertTrue(any("module :data:storage dependencies do not match" in error for error in validate(self.root)))

    def test_requirement_module_must_belong_to_primary_component(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(r for r in value["requirements"] if r["id"] == "A01").update(module=":feature:rules"))
        self.assertTrue(any("requirement A01 module is not owned" in error for error in validate(self.root)))

    def test_component_path_collision(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(c for c in value["components"] if c["id"] == "network").update(owned_paths=["android-app/feature/catalog/sub/**"]))
        errors = validate(self.root)
        self.assertTrue(any("path overlap" in error for error in errors))

    def test_shared_path_collision(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    value["shared_paths"].append("android-app/feature/catalog/**"))
        self.assertTrue(any("shared path overlaps component catalog" in error for error in validate(self.root)))

    def test_unaccepted_phase_edge(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    value["phase_dependencies"]["K2"][":feature:catalog"].append(":data:vault"))
        self.assertTrue(any("outside accepted module DAG" in error for error in validate(self.root)))

    def test_app_composition_root_is_explicit(self):
        self.change("docs/architecture/kotlin-registry.json", lambda value:
                    next(m for m in value["modules"] if m["id"] == ":app").update(component="shell"))
        self.assertTrue(any("composition root" in error or "component-free" in error for error in validate(self.root)))


if __name__ == "__main__":
    unittest.main()
