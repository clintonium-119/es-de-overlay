# ES-DE Screenshot Helper — Implementation Plan

Android app (Kotlin) that runs alongside ES-DE on a Retroid Pocket Nova. During a game the
user presses one button; the app captures a screenshot and records a 15 s video, figures out
which game ES-DE launched, and saves both into the correct ES-DE media directories with the
correct filenames so they show up in the gamelist.

Target device: Retroid Pocket Nova, **Android 13 (API 33)**. `minSdk = 33`. Pin
`targetSdk = 33` for v1: Android 14 changes MediaProjection consent rules (one consent per
capture), and the single-consent flow below depends on the 13 behaviour. Revisit if the
device is ever updated.

---

## 1. Goals / non-goals

**Goals**
- One-press capture: a single button press takes a lossless screenshot *and* records the
  next 15 s of gameplay, auto-stopping. No further interaction.
- Secondary path: a normal system screenshot (power + vol-down) is also picked up and filed.
- Works for emulated games (RetroArch, standalone emulators) *and* native Android apps
  launched from ES-DE.
- Correct ES-DE naming/placement so no manual renaming is ever needed.
- Settings UI to point at the ES-DE data directory and ROMs directory on the SD card.
- Confirmation feedback (toast/notification: "Saved: snes / Super Metroid (Redesign).png").

**Non-goals (v1)**
- Editing or cropping screenshots.
- Editing `gamelist.xml`.
- Any scraping.
- Root.

---

## 2. Facts vs assumptions

### Verified (from ES-DE docs / source)
- ES-DE writes a log at `<ES-DE data dir>/logs/es_log.txt`.
- Media lives at `<ES-DE data dir>/downloaded_media/<system>/<mediatype>/`.
  Media type folder for this app defaults to `screenshots` (others: `titlescreens`,
  `miximages`, `covers`, etc. — make it a setting).
- Media filename = ROM file basename with the extension replaced by `.png` (or `.jpg`).
  Extension **must be lower case**. Name must match the ROM filename **exactly**.
- ROMs in subfolders: the media path mirrors the subfolder structure, e.g.
  `ROMs/c64/Multidisk/Last Ninja 2/Last Ninja 2.d64` →
  `downloaded_media/c64/screenshots/Multidisk/Last Ninja 2/Last Ninja 2.png`.
- Directories interpreted as files (e.g. `.ps3dir`, `.scummvm` folders) keep the
  full directory name including its pseudo-extension: `dig.scummvm` → `dig.scummvm.png`.
- Native Android apps in ES-DE are represented as stub files in `ROMs/androidapps/` or
  `ROMs/androidgames/`. The **filename** is the display name; the **file content** is the
  package name, optionally followed by `/activity`. ES-DE launches them via
  `%ANDROIDAPP%` + `%FILEINJECT%`.

### Assumed — Phase 0 must verify these on the device before anything else is built
- A1. `es_log.txt` contains a per-launch line with the system name and the full ROM path.
  Exact format unknown; capture real lines from the Nova and write the parser against them.
  Check whether `--debug` / "Debug mode" setting is needed to get the launch line.
- A2. `es_log.txt` is readable by a third-party app via a SAF tree URI granted to the
  ES-DE data dir (it's a plain file in a user-chosen folder, so this should work, but
  confirm; also confirm ES-DE doesn't hold an exclusive lock or rotate it mid-session).
- A3. The Nova's stock screenshot shortcut writes to `Pictures/Screenshots/` via MediaStore
  and the file is visible to a `ContentObserver` on `MediaStore.Images`.
- A4. ES-DE picks up a newly added media file when returning to the gamelist, without a
  full restart. If not, document the workaround (reload gamelists / restart) in-app.
- A5. The user's theme uses the `screenshots` media slot for the gamelist image. Confirm
  by checking which folder the existing scraped games populate.
- A6. **(Critical)** On this Android 13 device, the `Intent` returned by
  `createScreenCaptureIntent()` can be passed to `getMediaProjection()` repeatedly, i.e.
  one consent dialog at service start covers every recording in the session. (This is the
  pre-Android-14 behaviour; confirm it holds on the Nova's build and that the projection
  survives across games/app switches.) If it does not, fall back to one consent per press
  and say so in the README.
- A7. ES-DE on Android plays an H.264 MP4 (what `MediaRecorder` produces) from
  `downloaded_media/<system>/videos/`. Scraped videos are H.264 MP4, so this should be fine;
  confirm with one real file.
- A8. Whether the ES-DE theme in use plays video audio in the gamelist (decides how much
  internal-audio capture matters; see Phase 2.5).
- A9. Whether a controller button combo can be intercepted globally by an
  `AccessibilityService.onKeyEvent` on the Nova (gamepad events are often not delivered
  there). If not, the floating button is the only trigger.

---

## 3. Architecture

Single-module Kotlin app, Jetpack Compose for the (small) settings UI, no third-party
libraries beyond AndroidX. Everything runs in one foreground service.

```
app/
  ui/
    SettingsActivity        Compose screen: pick dirs, media type, options, start/stop
    PermissionsFlow         SAF pickers, notification, usage-access deep links
  service/
    HelperService           Foreground service (type mediaProjection); owns everything below
    CaptureController       One-press flow: grab frame → start recorder → auto-stop → file both
    ProjectionHolder        Holds the MediaProjection obtained once at service start (A6)
    FrameGrabber            VirtualDisplay → ImageReader, one lossless frame → PNG
    ClipRecorder            VirtualDisplay → MediaRecorder surface, setMaxDuration(clip)
    TriggerOverlay          Floating button (SYSTEM_ALERT_WINDOW), shows state + confirmation
    ScreenshotObserver      Secondary: ContentObserver on MediaStore.Images (system screenshots)
    LaunchTracker           Determines "current game" (see §4)
    MediaWriter             Copies/renames into ES-DE media dir via SAF (DocumentFile)
  core/
    GameRef                 data class(system: String, romRelativePath: String)
    NameResolver            GameRef → target media path (applies all naming rules in §2)
    EsLogTailer             Tails es_log.txt, emits GameRef on launch lines
    AndroidAppIndex         Reads androidapps/androidgames stubs → Map<packageName, GameRef>
    ForegroundAppDetector   UsageStatsManager polling for current foreground package
  settings/
    Prefs                   DataStore: esdeTreeUri, romsTreeUri, mediaType, overwrite,
                            deleteOriginal, pollIntervalMs, debugLogging
```

### Data flow
1. `HelperService` starts, registers `ScreenshotObserver`, starts `EsLogTailer`,
   builds `AndroidAppIndex` once (and on a manual "rescan" button).
2. `LaunchTracker` keeps `currentGame: GameRef?`:
   - On every `EsLogTailer` launch event → set `currentGame`.
   - If `currentGame` is null or stale, or the foreground package is in
     `AndroidAppIndex`, fall back to foreground-package mapping.
3. **Primary — one press on the overlay button** → `CaptureController`:
   a. Resolve `currentGame` first. If null, flash "No ES-DE game detected" on the button
      and do nothing. Never guess.
   b. `FrameGrabber` captures one frame via a short-lived `ImageReader` VirtualDisplay
      (crop out the overlay button region, or hide the button for the capture frame).
   c. `ClipRecorder` starts `MediaRecorder` on a second VirtualDisplay with
      `setMaxDuration(clipSeconds * 1000)`; `MEDIA_RECORDER_INFO_MAX_DURATION_REACHED`
      stops it. A second press during recording stops early (keep what was recorded).
   d. Both files go through `NameResolver` → `MediaWriter` into `screenshots/` and
      `videos/`. Write to app cache first, then copy via SAF; delete temp on success.
   e. Button shows: idle → recording (with countdown) → saved ✓ / error ✗ for ~2 s.
4. **Secondary — system screenshot** → `ScreenshotObserver` fires → same resolve/name/write
   path → notification. If `currentGame` is null: notify and leave the file in place.

---

## 4. Determining the current game

Two sources, combined:

**Source A — ES-DE log tail (emulated games and native apps alike).**
Read `logs/es_log.txt` through the SAF tree URI. On service start, seek to the end;
poll every ~1s for new bytes (SAF has no inotify; polling is fine at this scale). Parse
launch lines into `GameRef(system, romPath)`. Derive `romRelativePath` by stripping the
configured ROMs root from `romPath`. Handle log rotation / truncation (file shorter than
last offset → reset offset to 0).

**Source B — foreground package (native apps only).**
`UsageStatsManager.queryEvents` over the last few seconds, take the latest
`ACTIVITY_RESUMED`. If the package is a key in `AndroidAppIndex`, that's the game.
Requires the user to grant "Usage access" (deep-link to the settings page).

**Precedence:** if a launch line arrived within the last N seconds, trust it. Otherwise, if
the foreground package maps to a native-app stub, use that. Otherwise null. Make N
configurable; default 10 min, but also clear `currentGame` when the foreground package
becomes ES-DE itself (user returned to the frontend).

Source A alone covers native apps too (the launch line will reference the stub file), so
Source B is a fallback for when the log line is missing or ambiguous. Build A first.

---

## 5. Naming rules (NameResolver)

Given `GameRef(system, romRelativePath)` and settings:

1. `base = romRelativePath` with the final extension removed — **except** when the ROM
   is a directory-as-file (path points at a directory, or has a known pseudo-extension such
   as `.ps3dir`, `.scummvm`, `.m3u`? — check ES-DE's list; when in doubt, keep the full
   name). Keep the subfolder portion intact.
2. Target = `<esde>/downloaded_media/<system>/<mediaType>/<base>.png`.
3. Create intermediate directories as needed.
4. Overwrite policy from settings: `replace` (default) | `skip` | `suffix` (`-1`, `-2`…).
   Note: ES-DE only reads the exact-name file, so `suffix` is really "keep extras for later
   manual choice"; document that in the UI.
5. Always lower-case the extension. Never alter the basename's case or characters.
6. Save as PNG (re-encode from the MediaStore bitmap only if the source isn't already PNG;
   otherwise byte-copy).

Unit-test this class heavily; it's the part most likely to be subtly wrong.

---

## 6. Permissions

| Need | Mechanism |
|---|---|
| Read `es_log.txt`, write `downloaded_media` | SAF: `ACTION_OPEN_DOCUMENT_TREE` on the ES-DE data dir, persist URI permission |
| Read `ROMs/androidapps/*` stubs, strip ROM root from paths | SAF tree on ROMs dir (may be the same tree or a different one; support both) |
| Detect new screenshots | `READ_MEDIA_IMAGES` (API 33+) / `READ_EXTERNAL_STORAGE` (older) + ContentObserver |
| Delete original screenshot | `MediaStore.createDeleteRequest` (user confirms once per batch) — or just leave originals; make deletion opt-in |
| Screen capture / recording | `MediaProjection` via `createScreenCaptureIntent()` once at service start (A6); service declares `foregroundServiceType="mediaProjection"` |
| Floating trigger button | `SYSTEM_ALERT_WINDOW` (user grants in settings; deep-link) |
| Foreground service | `FOREGROUND_SERVICE` + `POST_NOTIFICATIONS` |
| Optional controller-combo trigger | `BIND_ACCESSIBILITY_SERVICE` (only if A9 passes; otherwise omit entirely) |
| Foreground app detection | `PACKAGE_USAGE_STATS` (special access, user grants in settings) |

Avoid `MANAGE_EXTERNAL_STORAGE`; SAF is sufficient and less fragile. If Phase 0 shows SAF
can't read the log, `MANAGE_EXTERNAL_STORAGE` is the fallback — flag it, don't silently
switch.

---

## 7. Phases

### Phase 0 — Spike / verification (do first, keep it throwaway)
- Minimal app: pick ES-DE tree via SAF, tail `es_log.txt`, dump new lines to logcat.
- Launch 3 games on the Nova: a RetroArch core game, a standalone emulator game, a native
  Android app. Record the exact launch lines. Commit them as test fixtures.
- Second throwaway: request MediaProjection once, then start/stop `MediaRecorder` three
  times in a row across two different games without re-prompting. This is A6; if it fails
  the UX changes, so find out now.
- Confirm A2–A9. Write results into `docs/VERIFIED.md`.
- Output: a parser that passes tests against the real lines.

### Phase 1 — Core pipeline (MVP)
- Settings screen: ES-DE dir, ROMs dir, media type, overwrite policy, clip length,
  start/stop service, permission onboarding (SAF ×2, overlay, notifications, projection).
- `HelperService` + `ProjectionHolder` + `EsLogTailer` + `NameResolver` + `MediaWriter`.
- `TriggerOverlay` + `CaptureController` + `FrameGrabber` + `ClipRecorder` (video only, no
  audio). Overlay button: draggable, remembers position, small, semi-transparent, hidden
  automatically while ES-DE itself is in the foreground.
- `ScreenshotObserver` secondary path.
- Boot-start optional (toggle). Note: MediaProjection consent cannot be granted at boot
  without a UI, so boot-start brings up a one-tap "grant capture" notification.
- Ship and use it for a week before Phase 2.

### Phase 2 — Native app fallback
- `AndroidAppIndex` + `ForegroundAppDetector`, usage-access onboarding.
- "Rescan apps" button and automatic rescan when the androidapps dir changes.

### Phase 2.5 — Internal game audio in the clip (optional)
`MediaRecorder` cannot capture app playback audio. To get game sound in the video:
- `AudioRecord` with `AudioPlaybackCaptureConfiguration(mediaProjection)` (API 29+) →
  `MediaCodec` AAC encoder; video via `MediaCodec` H.264 encoder with a surface input from
  the VirtualDisplay; both muxed with `MediaMuxer`. This replaces `MediaRecorder` for the
  clip path, so keep `ClipRecorder` behind an interface and add `ClipRecorderWithAudio`.
- Apps can opt out of playback capture (`allowAudioPlaybackCapture=false`); most emulators
  don't, but handle the "no audio track" case gracefully (mux video only).
- Decide whether to build this at all based on A8.

### Phase 3 — Quality of life
- "Undo last save" action on the overlay button's confirmation state and in the notification
  (deletes the just-written screenshot/video, restores the previous file if one was overwritten).
- "Last 10 saves" list in the settings screen with the ability to delete a save.
- In-app hint if A4 turns out to need a gamelist reload.

### Phase 4 (maybe) — Controller-combo trigger
Only if A9 passes: an `AccessibilityService` that watches for a configurable button combo
(e.g. Select + R3) and calls `CaptureController.trigger()`, so the overlay button can be
hidden entirely. Out of scope until Phases 1–3 are solid.

---

## 8. Edge cases to handle

- Press while in ES-DE itself (foreground package == ES-DE) → ignore; button is hidden there anyway.
- Press while a recording is in progress → stop early and save what exists.
- Game exits / returns to ES-DE mid-recording → stop and save what exists (>= 1 s) else discard.
- MediaProjection lost (user revoked, system killed it) → button shows "re-grant" state, tapping
  opens the consent dialog; nothing is silently dropped.
- Overlay button region must not appear in the screenshot: hide the overlay view for the
  frame grab (one frame later) rather than cropping.
- Multiple screenshots in quick succession → process sequentially; apply overwrite policy
  to each.
- Multi-disc / `.m3u` games → media name comes from whatever file ES-DE shows (the `.m3u`),
  not the disc image. The log line should give the right path; verify in Phase 0.
- Games in subfolders and folders-as-files → path mirroring per §5.
- ROM root on SD vs ES-DE dir on internal (or vice versa) → two separate SAF trees.
- Log line contains a path from a *different* ROMs root than configured (user moved
  things) → fail loudly with the raw path in the notification.
- ES-DE updated and log format changed → parser returns null → "couldn't parse launch line"
  notification with the raw line, and a debug toggle that writes raw lines to app storage.
- Service killed by OEM battery management → request battery optimization exemption in
  onboarding; document the Retroid-specific setting if there is one.

---

## 9. Testing

- Unit: `NameResolver`, `EsLogParser` (against Phase 0 fixtures), `AndroidAppIndex` parser.
- Instrumented/manual checklist on the Nova:
  1. Emulated game, flat ROM → correct file appears, ES-DE shows it.
  2. Emulated game in a subfolder.
  3. Directory-as-file game.
  4. Native app from `androidapps`.
  5. Screenshot inside ES-DE → nothing happens.
  6. Overwrite each policy.
  7. Kill and restart service mid-session → still resolves the current game (persist
     `currentGame` to DataStore).
  8. One press → screenshot has no overlay button in it; video is ≈15 s, H.264 MP4,
     plays in ES-DE's gamelist.
  9. Three consecutive captures across two games with no consent prompt (A6).
  10. Early-stop with a second press; game exit mid-recording.
- Useful adb: `adb logcat -s ESDEShot`, `adb shell dumpsys usagestats`,
  `adb shell content query --uri content://media/external/images/media --projection _data,date_added --where "_data LIKE '%Screenshots%'"`.

---

## 10. Open questions for the user (answer before Phase 1)

1. ~~Android version~~ — answered: Android 13.
2. Is the ES-DE data directory on internal storage or the SD card? ROMs?
3. Should the original screenshot be deleted from `Pictures/Screenshots` after a
   successful save? (default proposed: no, with an opt-in toggle)
4. Default media type: `screenshots`, or does your theme want `miximages`/`titlescreens`?
5. Any games where the gamelist entry is a folder (not a file) that you care about?
6. Is silent video acceptable for v1, or is internal audio (Phase 2.5) a must-have?

---

## 11. Repo layout

```
.
├── PLAN.md                 (this file)
├── docs/VERIFIED.md        (Phase 0 findings; fill in before Phase 1)
├── app/                    (Android module)
├── fixtures/es_log/        (real launch lines captured from the device)
└── README.md               (setup: permissions walkthrough, known limitations)
```
