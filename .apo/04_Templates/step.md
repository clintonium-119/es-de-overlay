---
note_type: step
template_version: 1
contract_version: 1
title: "<step-title>"
owner: ""
created: '<TODAY>'
updated: '<TODAY>'
tags: [apovault, step]
step_id: "<STEP-NN-NN>"
workstream: "[[02_Work/<branch-path>/workstream|<branch-name>]]"
phase: "[[02_Work/<branch-path>/phases/Phase_NN_<slug>/Phase|PHASE-NN]]"
depends_on: []
related_sessions: []
related_bugs: []
external_refs: []
---

# STEP-NN-NN — <step-title>

## Description

_One paragraph: what this step does in user/code terms._

## Implementation

- **Files to touch:** _path:line citations._
- **Cited decisions:** _DEC wikilinks._
- **Sibling-naming:** _nearest sibling file/pattern this step extends._
- **DO NOT:** _explicit anti-patterns to avoid for this step._

## Validation

### Acceptance

_Observable criterion IDs, required checks, allowed methods, expected observations, setup/cleanup and artifact destination. Preserve acceptance intent; enroll before implementation under `docs/verification.md`._

### Tests to add

_Test file paths + test names. Cite framework conventions from `01_Knowledge/Coding_Standards.md`._

### Manual verification

_Steps a human takes to confirm the change behaves correctly._

### Step-specific (verify)

_Open `(verify)` items scoped to this step._

## Outcome

_Populated by `/apo:execute`._
