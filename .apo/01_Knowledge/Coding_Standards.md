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
updated: '2026-11-05'
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
- **Do not** use `AccessibilityService.rootInActiveWindow` to determine which app is in front; without requested accessibility event types or `flagRetrieveInteractiveWindows`, Android doesn't track windows for the service and it can stay null indefinitely. **Do** take "which app is in front" from usage events (foreground-app/`ACTIVITY_RESUMED` tracking) instead; reserve `rootInActiveWindow` for pressing buttons in a window the service just opened itself.
- **Do not** rely on `Toast` for anything shown while a `TYPE_APPLICATION_OVERLAY` window is up — the overlay draws opaque and full-screen above it, so the toast never becomes visible even though it fires and times out normally. **Do** report outcomes while such an overlay is open as a row in the overlay's own UI instead; `Toast` is fine again once the overlay window is gone.

## File organization (observed) — es-de-recorder

None yet. Planned: `app/{ui,service,core,settings}/`. **Source:** `PLAN.md:89-113` (read 2026-10-28)

## Test conventions (observed)

None yet. Plan calls for unit tests on `NameResolver`, `EsLogParser` (against fixtures), `AndroidAppIndex` parser, plus a manual on-device checklist.
**Source:** `PLAN.md:280-297` (read 2026-10-28)

## API-Usage Patterns (Observed)

- **Do:** scan large text (page-sized HTML/atlas content, not short strings) with a single reused `Matcher` (e.g. a `scan`/`replaceEach` helper), not `Regex.findAll`/`Regex.replace(input) { }`. **Do not:** use `findAll` or `replace {}` on large input — each match allocates a new `Matcher`, and on Android every `Matcher` copies the whole input into native ICU memory that's only freed on finalization, which can balloon RSS by gigabytes in seconds and get the process low-memory-killed; JVM unit tests won't catch this since they don't exhibit the native memory blowup.
- **Do not** join a split `<pre>` block's lines without its own trailing line break when dividing one text into multiple blocks. **Do:** keep each block (except the last) an exact slice of the original text, trailing newline included — a `<pre>` doesn't render a final line break on its own, so a block ending in blank lines displays one fewer than it should, and the rendered text shifts up a line at every split point.
- **Do not** rely on `TextView.minHeight` (or `CheckBox.minHeight`) alongside `isSingleLine = true` for touch-target height: `setSingleLine()` calls `setLines(1)`, which replaces the minimum height with the height of one line, regardless of the order the two are set in. **Do** set the view-level `minimumHeight` instead to guarantee the touch height.
- **Do not** pan a WebView page with the page's own `window.scrollBy`/`scrollTo` or an unclamped `WebView.scrollBy`: WebView widens the layout viewport to fit slightly-wider content, so page-level scripted scrolling stops at `scrollWidth - innerWidth` (or doesn't move at all for a marginally wider page), and an unclamped native step past an edge leaves the next step back counting from a `scrollX` the view never actually reached. **Do** pan with the view's own `scrollTo`, aiming from `visualViewport.pageLeft` and holding the target between 0 and `(document.documentElement.scrollWidth - visualViewport.width)` times the density.
