---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0003"
category: architecture
title: "Domain Model"
status: in_progress
owner: ""
created: '2026-10-28'
updated: '2026-10-28'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, architecture]
---

# Domain Model

## Entities

- **GameRef** — `data class(system: String, romRelativePath: String)`; the "current game". **Source:** `PLAN.md:105` (read 2026-10-28)
- **ES-DE media file** — `<esde>/downloaded_media/<system>/<mediaType>/<base>.png` (screenshot) and `.../videos/<base>.mp4`. **Sources:** `PLAN.md:39-42`, `PLAN.md:130-131` (read 2026-10-28)
- **Android app stub** — file in `ROMs/androidapps/` or `ROMs/androidgames/`; filename = display name, content = package name optionally followed by `/activity`. **Source:** `PLAN.md:49-52` (read 2026-10-28)
- **Settings** — `esdeTreeUri, romsTreeUri, mediaType, overwrite, deleteOriginal, pollIntervalMs, debugLogging`. **Source:** `PLAN.md:110-112` (read 2026-10-28)

All entities are external (owned by ES-DE's on-disk layout) except `GameRef` and settings.

## Relationships

- One ROM file (relative to ROMs root) ↔ one media basename per media type; subfolders under the system ROM dir are mirrored under the media type dir. **Source:** `PLAN.md:43-46` (read 2026-10-28)
- One stub file ↔ one Android package (`AndroidAppIndex: Map<packageName, GameRef>`). **Source:** `PLAN.md:108` (read 2026-10-28)

## Invariants

- Media filename = ROM basename with extension replaced; extension lower-case; basename case/characters never altered. **Sources:** `PLAN.md:42-43`, `PLAN.md:177` (read 2026-10-28)
- Directory-as-file ROMs (e.g. `dig.scummvm`) keep their full name: `dig.scummvm.png`. **Source:** `PLAN.md:47-48` (read 2026-10-28)
- Never guess the game: if `currentGame` is null, do nothing on press. **Source:** `PLAN.md:123-124` (read 2026-10-28)
- `es_log.txt` launch-line format is unverified (assumption A1). **Source:** `PLAN.md:55-57` (read 2026-10-28)

> **(verify)** — the exact launch-line format in `es_log.txt` and the list of ES-DE directory-as-file extensions
>
> Expected answers when this section is filled:
> - [ ] Literal launch line(s) captured from the Nova (fixtures)
> - [ ] Whether the line contains system name and absolute ROM path
> - [ ] ES-DE's list of folder-as-file pseudo-extensions
>
> Look at: `fixtures/es_log/`, `docs/VERIFIED.md` after Phase 0.
