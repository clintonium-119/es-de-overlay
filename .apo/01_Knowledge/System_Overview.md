---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0001"
category: architecture
title: "System Overview"
status: in_progress
owner: ""
created: '2026-10-28'
updated: '2026-10-28'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, architecture]
---

# System Overview

## Purpose

Android app (Kotlin) that runs alongside the ES-DE frontend on a Retroid Pocket Nova. One button press during a game captures a lossless screenshot and records a 15 s video, works out which game ES-DE launched, and files both into the ES-DE `downloaded_media` directories with the exact filenames ES-DE expects.
**Source:** `PLAN.md:1-6` (read 2026-10-28)

Target: Android 13 (API 33); `minSdk = 33`, `targetSdk = 33` pinned because Android 14 changes MediaProjection consent rules.
**Source:** `PLAN.md:8-11` (read 2026-10-28)

## High-Level Architecture

_Planned, not yet implemented (greenfield repo at init)._ Single-module Kotlin app, Jetpack Compose settings UI, no third-party libraries beyond AndroidX; everything runs in one foreground service (`foregroundServiceType="mediaProjection"`).
**Sources:** `PLAN.md:84-87`, `PLAN.md:193` (read 2026-10-28)

Data flow: foreground service tails `es_log.txt` to learn the current game → overlay button press → frame grab (ImageReader VirtualDisplay) + clip record (MediaRecorder VirtualDisplay) → `NameResolver` → SAF write into `downloaded_media/<system>/<mediaType>/`. Secondary path: `ContentObserver` on MediaStore picks up system screenshots.
**Source:** `PLAN.md:115-134` (read 2026-10-28)

## Key Components

At init the repo contains only `PLAN.md`. Planned module layout (`app/ui`, `app/service`, `app/core`, `app/settings`) is listed in `PLAN.md:89-113`.
**Sources:** `ls -la` of repo root; `PLAN.md:89-113` (read 2026-10-28)

## Technology Stack

| Area | Choice | Source |
|---|---|---|
| Language | Kotlin | `PLAN.md:3` |
| UI | Jetpack Compose (settings only) | `PLAN.md:86` |
| Dependencies | AndroidX only | `PLAN.md:86-87` |
| Storage access | SAF (`ACTION_OPEN_DOCUMENT_TREE`), no `MANAGE_EXTERNAL_STORAGE` | `PLAN.md:189,199-201` |
| Settings persistence | DataStore | `PLAN.md:111` |
| Capture | MediaProjection + ImageReader + MediaRecorder | `PLAN.md:97-99` |
| Build tool | Gradle (Android) | (verify) — no build files exist yet |

All rows read 2026-10-28.

> **(verify)** — build toolchain versions not yet chosen
>
> Expected answers when this section is filled:
> - [ ] AGP / Kotlin / Compose BOM versions
> - [ ] Gradle wrapper version
> - [ ] JDK used for builds
>
> Look at: `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties` once Phase 1 scaffolds them.
