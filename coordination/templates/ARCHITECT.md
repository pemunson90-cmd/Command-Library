# ARCHITECT: <component>

Path: `docs/architecture/<component>/ARCHITECT.md`. Required before any code in the component (GAUNTLET.md 5.1). Every section below is mandatory.

## Purpose

## COVERAGE IDs served

## Public interface
Signatures only, in the chosen stack's language.

## Data model

## Allowed dependencies
Components this one may import. Must keep the DAG acyclic.

## Owned paths
Globs. Exclusive to the lock holder for this component.

## Shared-file change requests
Build config, version catalog, module registry, DI root, navigation graph, CI, DB schema registry entries this component needs. The orchestrator applies them.

## Error and offline behavior

## Performance budget

## Tests and acceptance criteria

## Step plan
Numbered. Each step small enough for one subagent to finish, test, and commit in one run.

1.
