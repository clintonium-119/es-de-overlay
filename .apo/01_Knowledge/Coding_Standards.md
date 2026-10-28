---
note_type: knowledge
template_version: 1
contract_version: 1
knowledge_id: "KNOW-0006"
category: standards
title: "Coding Standards"
status: in_progress
owner: ""
created: '2026-10-28'
updated: '2026-10-28'
reviewed_on: ""
related_notes: []
tags: [apovault, knowledge, standards]
---

# Coding Standards

## Lint / format

No lint or format config exists yet (no `.editorconfig`, no ktlint/detekt config).
**Source:** `ls -la` of repo root (read 2026-10-28)

## Theme Tokens (Observed)

No token system detected (`apo mine theme-sources --json` → `sources: []`, read 2026-10-28). Settings UI is a small Compose screen (`PLAN.md:86`).

## Build / run commands

- Build + unit tests: `JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew testDebugUnitTest assembleDebug` (JDK 17; the system default JDK 27 is too new for AGP 9.0).
- Install on the Nova: `adb install -r app/build/outputs/apk/debug/app-debug.apk`; reinstalling stops the service, so capture consent must be granted again.
- Device log: `adb logcat -G 16M && adb logcat -s ESDEShot` (games flood logcat with SELinux lines).
- Toolchain: Gradle 9.1.0 wrapper (checksum pinned), AGP 9.0.0 built-in Kotlin, Compose BOM 2026.01.01 (newer BOMs need compileSdk 37 / AGP 9.1), compileSdk 36, min/target SDK 33.

**Sources:** `README.md`, `app/build.gradle.kts`, `gradle/wrapper/gradle-wrapper.properties` (read 2026-10-28)

## Naming Conventions (Observed) — es-de-recorder

**Subsystem path:** `.` — scanned 2026-10-28 — 0 Kotlin/XML source files.

- **Patterns NOT present:** `*Dialog`, `*Modal`, `*Card`, `*Form`, `*Section`, `*Drawer`, `*Panel`, `*Page`, `*View`, `*Sheet`, `*Popover`, `*Tooltip` (0 each).
- **Planned class names** (from the plan, not code): `SettingsActivity`, `HelperService`, `CaptureController`, `ProjectionHolder`, `FrameGrabber`, `ClipRecorder`, `TriggerOverlay`, `ScreenshotObserver`, `LaunchTracker`, `MediaWriter`, `NameResolver`, `EsLogTailer`, `AndroidAppIndex`, `ForegroundAppDetector`, `Prefs`. **Source:** `PLAN.md:89-113` (read 2026-10-28)

**Source:** `apo mine conventions --path . --ext kt,kts,xml --json` (read 2026-10-28)

## API-Usage Patterns (Observed) — es-de-recorder

| Area | Newer | Older | Verdict |
|---|---|---|---|
| — | — | — | No source code yet; nothing to compare |

**Source:** greenfield repo, `ls -la` (read 2026-10-28)

## File organization (observed) — es-de-recorder

None yet. Planned: `app/{ui,service,core,settings}/`. **Source:** `PLAN.md:89-113` (read 2026-10-28)

## Test conventions (observed)

None yet. Plan calls for unit tests on `NameResolver`, `EsLogParser` (against fixtures), `AndroidAppIndex` parser, plus a manual on-device checklist.
**Source:** `PLAN.md:280-297` (read 2026-10-28)
