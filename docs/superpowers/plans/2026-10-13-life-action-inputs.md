# Remaining life action inputs implementation plan

**Goal:** Complete typed inputs/outcomes and lossless codec1-to2 support before checked storage/service binding.

**Architecture:** Append compatible model fields and request variants; explicit binary tags preserve earlier cases. Per-frame version selects legacy result shape. Root owns contracts/codecs; independent review precedes checkpoint.

**Spec:** ../specs/2026-10-13-life-action-input-design.md

### Task1: Typed model inputs

- [x] Add LifeActionContractsTest cases for reasons/step/outcome validation and compatible empty result defaults.
- [x] Run API-absence/behavior RED; add exact GameAction shapes and result checks.
- [x] Run core/model and feature/life tests; existing reducers must reject unimplemented actions.

### Task2: Versioned payloads

- [x] Preserve actual frozen codec1 fixture; test old metadata, all new action tags, explicit outcomes and canonical creation input RED.
- [x] Emit codec2, explicitly decode1/2, add result outcomes conditional on containing frame and creation root6 only for2.
- [x] Run local model/life/storage checks; independent source review; publish truthful checkpoint. Keep the single frozen schema6 Android verification run separate.
