---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0002"
category: architecture
title: "Code Map"
status: in_progress
owner: ""
created: '2026-10-28'
updated: '2026-10-28'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, architecture]
---

# Code Map

## Subsystems

Greenfield repo at init (only `PLAN.md`); small-codebase fallback applied — one subsystem.

| Slug | Path | Purpose | File count | Status |
|---|---|---|---|---|
| es-de-recorder | `.` (planned `app/`) | Whole Android app | 0 source files at init | active |

**Source:** `apo mine subsystems --json` (read 2026-10-28) — returned only `.apo`.

## Directory Structure

At init:

```
.
├── PLAN.md        implementation plan
└── .apo/          ApoVault
```

Planned layout:

```
.
├── PLAN.md
├── docs/VERIFIED.md        Phase 0 findings
├── app/                    Android module
├── fixtures/es_log/        real launch lines captured from the device
└── README.md               setup + known limitations
```

**Sources:** `ls -la` of repo root; `PLAN.md:313-322` (read 2026-10-28)

## Key Files

- `PLAN.md` — authoritative design/plan document. **Source:** `PLAN.md` (read 2026-10-28)

> **(verify)** — entry points and build files do not exist yet
>
> Expected answers when this section is filled:
> - [ ] Application / launcher Activity path
> - [ ] Foreground service path
> - [ ] `AndroidManifest.xml` path
> - [ ] Root + module Gradle files
>
> Look at: `app/src/main/` after Phase 1 scaffold.

## Module Boundaries (Vault-wide)

Planned: `core/` holds pure logic (`GameRef`, `NameResolver`, `EsLogTailer`, `AndroidAppIndex`, `ForegroundAppDetector`); `service/` owns Android runtime pieces; `ui/` is Compose settings; `settings/` is DataStore prefs. `NameResolver` and the log parser are the unit-test targets.
**Sources:** `PLAN.md:89-113`, `PLAN.md:280-283` (read 2026-10-28)

## Module Boundaries — es-de-recorder

Same as vault-wide (single subsystem). Keep naming/path logic free of Android framework types so it runs as plain JVM unit tests.
**Source:** `PLAN.md:181`, `PLAN.md:282` (read 2026-10-28)
