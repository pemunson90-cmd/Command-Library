# Execution queue

Live pointer: coordination/state.json and CURRENT.md. Latest D17 enables bounded sequential model handoffs.

| Task | Stage | Status | Prerequisites | Next |
|---|---|---|---|---|
| orch-architecture-plan-01 | Gate3 contracts/plan | active | reviewed Gate2 working baseline | step1 architecture/coverage audit |
| orch-architecture-review-01 | independent Gate3 audit | queued | architecture checkpoint | verify contracts/path exclusivity/DAG/coverage |
| orch-toolchain-01 | pre-M0 capability and version lock | queued | reviewed architecture | verify Git checkout/hosted CI/compiler dependencies and pin real tuple |
| orch-m0-01 | signed walking skeleton | queued, not eligible yet | accepted architecture + toolchain + exclusive locks | concrete skeleton task cards prepared during plan |
| orch-m1-01 | English catalog/rules/search | queued, not eligible yet | accepted M0 foundation | resumable real corpus contracts/fixtures/data pipeline |

No task becomes done because listed here. If blocked use named independent eligible work and update pointer; record exact blocker and proof. Full stage/task cards finalized in architecture plan.
