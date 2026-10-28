---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0005"
category: architecture
title: "Agent Workflow"
status: in_progress
owner: ""
created: '2026-10-28'
updated: '2026-10-28'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, architecture]
---

# Agent Workflow

## Agent Roles

No repo-level `CLAUDE.md` / `AGENTS.md` / `.cursorrules`. User-global rails apply (`~/.pi/agent/AGENTS.md`):
- `.apo/` is the source of truth for lifecycle; `/apo:plan`, `/apo:refine`, `/apo:execute`, `/apo:promote` are the entry points.
- Work on a git branch named after the workstream slug; never execute workstream steps on `main`/`master`.
- Commit after each completed step during `/apo:execute` once validation passes.
- Commit messages wrapped at 72 chars; no AI co-author / attribution trailers.

**Source:** `~/.pi/agent/AGENTS.md` (read 2026-10-28)

## Workflow Patterns

Phase 0 (device verification spike) must precede Phase 1; assumptions A1–A9 are recorded in `docs/VERIFIED.md`.
**Source:** `PLAN.md:207-215` (read 2026-10-28)

## Tooling

- Android SDK at `~/Android/Sdk` (platforms `android-34`, `android-36`; build-tools `35.0.0`, `36.0.0`). **Source:** `ls ~/Android/Sdk/platforms ~/Android/Sdk/build-tools` (read 2026-10-28)
- Device reachable via `adb` (wireless): model `Retroid_Pocket_Nova`. **Source:** `adb devices -l` (read 2026-10-28)
- Log tag for debugging: `adb logcat -s ESDEShot`. **Source:** `PLAN.md:296` (read 2026-10-28)
