# Verified device facts

Device: Retroid Pocket Nova (`kalama`), checked over adb on 2026-10-28.

## Device

| Fact | Value | Evidence |
|---|---|---|
| Android | 13 (SDK 33) | `adb shell getprop ro.build.version.release` / `ro.build.version.sdk` |
| Screen | 960x1280 physical, 320 dpi (used in landscape, 1280x960) | `adb shell wm size`, `wm density` |
| ES-DE | `org.es_de.frontend`, ES-DE 3.5.0-65 (r52), running as the home app | `pm list packages`; first lines of `es_log.txt` |
| ES-DE data dir | `/storage/7BEF-E760/ES-DE` (SD card) | `ls /storage/7BEF-E760/ES-DE` |
| ROMs dir | `/storage/7BEF-E760/ROMs` (SD card) | `ls /storage/7BEF-E760/ROMs`; paths in `es_log.txt` |
| Media dir | `<ES-DE>/downloaded_media` (`MediaDirectory` empty in `es_settings.xml`) | `grep MediaDirectory settings/es_settings.xml` |
| Theme | `minui-menu-es-de-custom`, variant `gamelist-list-screenshot` | `grep Theme settings/es_settings.xml` |
| System screenshots | `/storage/emulated/0/Pictures/Screenshots/Screenshot_YYYYMMDD-HHMMSS.png` | `ls Pictures/Screenshots` |
| Log rotation | ES-DE keeps one `es_log.txt.bak`, but after a device reboot it overwrote `es_log.txt` without rotating (see below) | `ls -la ES-DE/logs` |

## Assumptions

| ID | Status | Result | Evidence |
|---|---|---|---|
| A1 | Verified | Every launch logs `Launching game "<name>" from system "<Full Name> (<system>)"...` plus same-second lines incl. `Package: <pkg>`. The ROM path appears only for some launch arguments: raw for RetroArch (`%ROM% expanded: /storage/7BEF-E760/ROMs/snes/02) Hacks/Easyland.zip`), percent-encoded for `%ROMPROVIDER%` (`content://org.es_de.frontend.files/root/...`). Android-app launches log no path. Debug mode is not needed. `%ROMSAF%` not yet seen in a real log. | `fixtures/es_log/*.txt` |
| A2 | Verified | The app reads `es_log.txt` through the SAF tree while ES-DE runs; a launch at ES-DE time 15:57:40 was logged by the app at 15:57:41.37 (1 s polling). | `adb logcat -s ESDEShot` |
| A3 | Verified | Power + volume-down in a game creates `Pictures/Screenshots/Screenshot_*.png`; the `ContentObserver` filed it as `screenshots/02) Hacks/Easyland.png` within 1 s. Taken inside ES-DE, it is left alone with a notification. (`KEYCODE_SYSRQ` does not take a screenshot on this build; `input keycombination KEYCODE_VOLUME_DOWN KEYCODE_POWER` does.) | `stat` on the media file; `dumpsys notification` |
| A4 | Verified | ES-DE showed the new screenshot (and played the video) for Easyland on returning to the gamelist, no reload or restart. | Device screenshot; user observation |
| A5 | Verified | Theme variant `gamelist-list-screenshot`; existing scraped media under `downloaded_media/<system>/screenshots/`, mirroring ROM subfolders (e.g. `snes/screenshots/01) All but the Best/`) | `es_settings.xml`; `ls downloaded_media/snes/screenshots` |
| A6 | Verified | One consent at service start covered several captures in a row across app switches. Reinstalling or restarting the service needs a new consent. | Two back-to-back captures, no dialog |
| A7 | Verified | ES-DE plays the app's H.264 MP4 from `videos/`. | User observation |
| A8 | Not checked | Whether the theme plays video audio. Clips are silent for now. | — |
| A9 | Out of scope | Gamepad combo through an AccessibilityService (PLAN.md Phase 4) | — |

## Game resolution

Because the log path is optional, the app resolves the game in this order:
1. The ROM path from the launch-block lines (absolute `/…/ROMs/<system>/…`, or the SAF/document form `7BEF-E760:ROMs/<system>/…`, percent-decoded).
2. A unique `<name>` match in `<ES-DE>/gamelists/<system>/gamelist.xml` (its `<path>` is `./<relative path>`; Android-app stubs such as `./Dead Cells.app` are listed there too).
3. Otherwise the app reports the raw launch line and saves nothing.

ES-DE's bundled `assets/systems/android/es_systems.xml` (pulled from the installed APK) shows which launch arguments carry the ROM: `%ROM%` (RetroArch `%EXTRA_ROM%`), `%ROMSAF%`, `%ROMPROVIDER%`, among others.

## Found while testing the app on the Nova

- **RetroArch shader sub-frames.** `video_shader_subframes = "2"` in `retroarch.cfg` makes RetroArch draw a near-black frame between real frames at 120 Hz. A raw mirror recording flickers (mean luma alternating ~40 / ~118 each frame) and a screenshot can land on the dark frame. The app takes the brighter of the first two frames for the PNG and drops frames much darker than the previous one from the clip; a 15 s clip came out at 60 fps with luma steady at 117 ±0.6.
- **MediaRecorder max duration.** With a surface source the recorder stops writing at `setMaxDuration` but never calls `OnInfoListener`, so the app stops clips on its own timer.
- **Bitrate hint ignored.** A 1280x960 clip of the mirror (~120 fps) came out at ~15 Mbit/s with 8 Mbit/s and with 4 Mbit/s requested. Clips are recorded at 640x480 instead (~2.7 MB per 15 s at 60 fps).
- **Transparent windows get no touches.** An overlay window at alpha 0 is skipped for input, so the button is at alpha 0 only for the screenshot frame and at 1% while recording (still tappable to stop early).
- **ES-DE log on reboot.** After a device reboot ES-DE overwrote `es_log.txt` without moving it to `es_log.txt.bak`. The app detects the new log by its first bytes and starts over.
- **Logcat flooding.** During games the device logs ~600 SELinux lines/s, so the default logcat buffer drops app lines within seconds; use `adb logcat -G 16M` when debugging.
- **ES-DE imports new apps.** Installing this app added `ROMs/androidapps/ES-DE Recorder.app` to ES-DE's Android Apps system (`GuiGameImporter` in the log).

`fixtures/es_log/retroarch_rom.txt` was saved during testing as `grep -n -A10` output from `es_log.txt` with only grep's line-number prefix removed; the reboot then overwrote that log.
