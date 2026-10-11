#!/usr/bin/env python3
"""Validate the Kotlin architecture registry against its accepted source contracts."""
import argparse
import json
import sys
from pathlib import Path


def validate(root: Path) -> list[str]:
    """Return actionable errors for Kotlin registry drift or invalid topology."""
    root = Path(root)
    errors: list[str] = []

    def load(relative: str):
        path = root / relative
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            errors.append(f"cannot read {relative}: {exc}")
            return {}

    kotlin = load("docs/architecture/kotlin-registry.json")
    legacy = load("docs/architecture/registry.json")
    coverage = load("docs/architecture/coverage.json")

    def unique_index(items, key, label):
        if not isinstance(items, list):
            errors.append(f"{label} must be a list")
            return {}
        indexed = {}
        for item in items:
            value = item.get(key) if isinstance(item, dict) else None
            if not isinstance(value, str) or not value:
                errors.append(f"{label} has missing/invalid {key}")
            elif value in indexed:
                errors.append(f"{label} has duplicate {key} {value}")
            else:
                indexed[value] = item
        return indexed

    legacy_components = unique_index(legacy.get("components", []), "id", "legacy components")
    components = unique_index(kotlin.get("components", []), "id", "Kotlin components")
    if len(legacy_components) != 20 or len(components) != 20:
        errors.append("legacy and Kotlin registries must each contain exactly 20 components")
    if set(components) != set(legacy_components):
        errors.append("Kotlin component IDs differ from legacy registry")
    for component_id in sorted(set(components) & set(legacy_components)):
        current, old = components[component_id], legacy_components[component_id]
        if current.get("component_dependencies") != old.get("dependencies"):
            errors.append(f"{component_id} dependencies differ from legacy registry")
        if current.get("coverage_ids") != old.get("coverage_ids"):
            errors.append(f"{component_id} coverage_ids differ from legacy registry")

    source_coverage = unique_index(coverage.get("requirements", []), "requirement_id", "coverage requirements")
    kotlin_coverage = unique_index(kotlin.get("requirements", []), "id", "Kotlin requirements")
    if len(source_coverage) != 64 or len(kotlin_coverage) != 64 or set(source_coverage) != set(kotlin_coverage):
        errors.append("Kotlin requirement IDs must exactly match the 64 COVERAGE requirements")
    for requirement_id in sorted(set(source_coverage) & set(kotlin_coverage)):
        source, current = source_coverage[requirement_id], kotlin_coverage[requirement_id]
        for field in ("primary_component", "acceptance"):
            if current.get(field) != source.get(field):
                errors.append(f"requirement {requirement_id} {field} differs from COVERAGE")
        if current.get("primary_component") not in components:
            errors.append(f"requirement {requirement_id} has unknown primary_component")
        requirement_module = current.get("module")
        if not isinstance(requirement_module, str) or not requirement_module:
            errors.append(f"requirement {requirement_id} has missing/invalid module")
        elif requirement_module not in components.get(current.get("primary_component"), {}).get("modules", []):
            errors.append(f"requirement {requirement_id} module is not owned by its primary_component")

    for component_id, component in components.items():
        for field in ("contract", "behavior_reference"):
            relative = component.get(field)
            if not isinstance(relative, str) or not relative or not (root / relative).is_file():
                errors.append(f"component {component_id} {field} does not reference an existing file")

    modules = unique_index(kotlin.get("modules", []), "id", "Kotlin modules")
    if len(modules) != 22:
        errors.append("Kotlin registry must contain exactly 22 modules")
    module_ids = set(modules)
    for requirement_id, requirement in kotlin_coverage.items():
        module_id = requirement.get("module")
        if module_id not in module_ids:
            errors.append(f"requirement {requirement_id} references unknown module {module_id}")
    module_owners = {}
    for module_id, module in modules.items():
        dependencies = module.get("dependencies", [])
        if not isinstance(dependencies, list):
            errors.append(f"module {module_id} dependencies must be a list")
            continue
        if len(dependencies) != len(set(dependencies)):
            errors.append(f"module {module_id} has duplicate dependencies")
        for dependency in dependencies:
            if dependency not in module_ids:
                errors.append(f"module {module_id} has unknown dependency {dependency}")
        owner = module.get("component")
        if owner is not None and owner not in components:
            errors.append(f"module {module_id} has unknown component {owner}")
        module_owners[module_id] = owner

    # Component-level dependencies define the only legal inter-component
    # module edges. Every component first consumes core:model, then each named
    # dependency's implementation module. The app module is composition only.
    owner_modules = {}
    for module_id, owner in module_owners.items():
        if owner is not None and module_id not in (":core:model", ":core:common"):
            owner_modules.setdefault(owner, []).append(module_id)
    for component_id, component in components.items():
        owned = component.get("modules", [])
        component_dependencies = component.get("component_dependencies", [])
        if not isinstance(component_dependencies, list):
            errors.append(f"component {component_id} dependencies must be a list")
            continue
        for dependency in component_dependencies:
            if dependency not in components:
                errors.append(f"component {component_id} has unknown dependency {dependency}")
        for module_id in (owned if isinstance(owned, list) else []):
            if module_id not in modules:
                continue
            module_dependencies = modules[module_id].get("dependencies", [])
            if module_id == ":core:model":
                expected = set()
            elif module_id == ":core:common":
                expected = {":core:model"}
            else:
                expected = {":core:model"}
                for dependency in component_dependencies:
                    if dependency == "core":
                        continue
                    candidates = owner_modules.get(dependency, [])
                    if len(candidates) != 1:
                        errors.append(f"component {component_id} dependency {dependency} has no unique module")
                    else:
                        expected.add(candidates[0])
            if isinstance(module_dependencies, list) and set(module_dependencies) != expected:
                errors.append(f"module {module_id} dependencies do not match component dependency contract")

    visiting, visited = set(), set()

    def visit(module_id):
        if module_id in visiting:
            errors.append(f"module dependency cycle at {module_id}")
            return
        if module_id in visited or module_id not in modules:
            return
        visiting.add(module_id)
        for dependency in modules[module_id].get("dependencies", []):
            if dependency in modules:
                visit(dependency)
        visiting.remove(module_id)
        visited.add(module_id)

    for module_id in modules:
        visit(module_id)

    # Component roots and module roots must agree exactly and cannot overlap.
    component_roots = []
    for component_id, component in components.items():
        paths = component.get("owned_paths", [])
        owned_modules = component.get("modules", [])
        if not isinstance(paths, list) or not isinstance(owned_modules, list):
            errors.append(f"component {component_id} paths/modules must be lists")
            continue
        expected_module_ids = [mid for mid, owner in module_owners.items() if owner == component_id]
        if set(owned_modules) != set(expected_module_ids) or len(owned_modules) != len(expected_module_ids):
            errors.append(f"component {component_id} module membership differs from module owners")
        module_roots = []
        for module_id in owned_modules:
            module = modules.get(module_id)
            if module is None:
                errors.append(f"component {component_id} references unknown module {module_id}")
                continue
            if module.get("component") != component_id:
                errors.append(f"module {module_id} owner differs from component {component_id}")
            module_roots.append(module.get("owned_root"))
        if sorted(paths) != sorted(module_roots):
            errors.append(f"component {component_id} owned_paths differ from module owned_roots")
        for path in paths:
            if isinstance(path, str):
                component_roots.append((component_id, path))

    def overlaps(left, right):
        """Compare directory-glob roots (e.g. a/b/**) as path prefixes."""
        a = left.removesuffix("/**").rstrip("/")
        b = right.removesuffix("/**").rstrip("/")
        return a == b or a.startswith(b + "/") or b.startswith(a + "/")

    for index, (owner, path) in enumerate(component_roots):
        for other_owner, other_path in component_roots[index + 1:]:
            if owner != other_owner and overlaps(path, other_path):
                errors.append(f"component path overlap {owner}:{path} and {other_owner}:{other_path}")
    for path in kotlin.get("shared_paths", []):
        if not isinstance(path, str):
            errors.append("shared_paths entries must be strings")
            continue
        for owner, owned_path in component_roots:
            if overlaps(path, owned_path):
                errors.append(f"shared path overlaps component {owner}: {path} and {owned_path}")

    # The single app module is a shared composition root outside component ownership.
    app = modules.get(":app")
    app_roots = [mid for mid, module in modules.items() if module.get("component") is None]
    if app is None or app_roots != [":app"]:
        errors.append("registry must have exactly one component-free :app composition root")
    elif (app.get("owned_root") is not None or app.get("shared_paths") != ["android-app/app/**"]
          or "composition" not in app.get("role", "").lower()):
        errors.append(":app must declare its app path as shared and identify its composition role")
    for module_id, module in modules.items():
        if module_id != ":app" and ":app" in module.get("dependencies", []):
            errors.append(f"component module {module_id} cannot depend on composition root :app")

    phase_dependencies = kotlin.get("phase_dependencies", {})
    if not isinstance(phase_dependencies, dict):
        errors.append("phase_dependencies must be an object")
    else:
        for phase, graph in phase_dependencies.items():
            if phase == "note":
                continue
            if not isinstance(graph, dict):
                errors.append(f"phase {phase} dependencies must be an object")
                continue
            for module_id, dependencies in graph.items():
                if module_id not in modules:
                    errors.append(f"phase {phase} references unknown module {module_id}")
                    continue
                if not isinstance(dependencies, list):
                    errors.append(f"phase {phase} dependencies for {module_id} must be a list")
                    continue
                accepted = set(modules[module_id].get("dependencies", []))
                for dependency in dependencies:
                    if dependency not in module_ids:
                        errors.append(f"phase {phase} references unknown module {dependency}")
                    elif dependency not in accepted:
                        errors.append(f"phase {phase} edge {module_id} -> {dependency} is outside accepted module DAG")

    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[1]))
    args = parser.parse_args()
    errors = validate(Path(args.root))
    if errors:
        for error in errors:
            print("FAIL:", error)
        return 1
    print("PASS: Kotlin registry matches 20 legacy components and 64 coverage joins; 22 modules form a valid ownership DAG")
    return 0


if __name__ == "__main__":
    sys.exit(main())
