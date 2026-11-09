---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0004"
category: architecture
title: "Integration Map"
status: in_progress
owner: ""
created: '2026-10-28'
updated: '2026-11-06'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, architecture]
---

# Integration Map

## External Services

No network services. External integrations are on-device:

- **ES-DE** — reads its log `<ES-DE data dir>/logs/es_log.txt`; writes into `<ES-DE data dir>/downloaded_media/`. **Source:** `PLAN.md:38-41` (read 2026-10-28)
- **Android MediaProjection** — screen capture/recording; one consent per service start on Android 13 (assumption A6, unverified). **Source:** `PLAN.md:67-72` (read 2026-10-28)
- **MediaStore** — `ContentObserver` on `MediaStore.Images` for system screenshots (assumption A3). **Source:** `PLAN.md:61-62` (read 2026-10-28)
- **UsageStatsManager** — foreground package detection (Phase 2). **Source:** `PLAN.md:149-152` (read 2026-10-28)
- **Storage Access Framework** — tree URIs for ES-DE data dir and ROMs dir. **Source:** `PLAN.md:189-190` (read 2026-10-28)

## Internal APIs

None — single-module app, no IPC surface planned beyond the foreground service.
**Source:** `PLAN.md:84-87` (read 2026-10-28)

## Data Flow

See `System_Overview.md` § High-Level Architecture. Full flow: `PLAN.md:115-134` (read 2026-10-28).

> **(verify)** — device-specific integration facts (assumptions A1–A9)
>
> Expected answers when this section is filled:
> - [ ] ES-DE package name and data-dir path on the Nova
> - [ ] Whether SAF can read `es_log.txt` while ES-DE runs
> - [ ] Whether MediaProjection consent is reusable on the Nova build
>
> Look at: `docs/VERIFIED.md` after Phase 0.
- **ES-DE theme source files:** ES-DE stores the active theme in `ES-DE/settings/es_settings.xml` as string keys `Theme` (default `linear-es-de`), `ThemeVariant`, `ThemeColorScheme`, `ThemeFontSize`, `ThemeAspectRatio` (default `automatic`); on Android only the Linear theme ships inside ES-DE's own APK (unreadable to other apps), while every other theme (Modern, Slate, and all downloader themes) is installed under `ES-DE/themes/<reponame>/` as a root `theme.xml` plus mandatory `capabilities.xml`, readable through the app's SAF tree — treat anything there as third-party content requiring hardened XML parsing. **Source:** ES-DE `es-core/src/Settings.cpp` `setDefaults()`, `THEMES.md`, `USERGUIDE.md` (read 2026-11-06).
