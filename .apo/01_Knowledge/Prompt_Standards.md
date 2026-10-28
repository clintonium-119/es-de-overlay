---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0007"
category: standards
title: "Prompt Standards"
status: in_progress
owner: ""
created: '2026-10-28'
updated: '2026-10-28'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, standards]
---

# Prompt Standards

## Agent-facing rails

No repo-level agent rails file. User-global rails in `~/.pi/agent/AGENTS.md` (read 2026-10-28): branch-per-workstream, commit after each completed step, 72-col commit messages, no AI attribution trailers. See `Agent_Workflow.md`.

## Vault-artifact citations in generated content

- **Do** keep vault IDs (`DEC-NNNN`, `OBS-NNNN`, `PHASE-NN`, `STEP-NN-NN`, `SESSION-*`) in their structural homes: vault file names, frontmatter, and `02_Work/**` cross-references.
- **Do not** embed those IDs, or `02_Work/**` / `01_Knowledge/_pending/**` / `01_Knowledge/_archive/**` wikilinks, in code comments, test titles, runtime strings, or `01_Knowledge/*` rail bodies.
- Domain item IDs (`TASK-NNNN` / `BUG-NNNN`) referenced as kanban items are allowed.
- Enforced by `/apo:lint` (check for vault-artifact citations).

## Theme tokens

No centralized token system (greenfield; `apo mine theme-sources` found none, read 2026-10-28).
