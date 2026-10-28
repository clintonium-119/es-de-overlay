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
| Log rotation | Each ES-DE start moves the old log to `es_log.txt.bak` | `ls ES-DE/logs` |

## Assumptions

| ID | Status | Result | Evidence |
|---|---|---|---|
| A1 | Verified (partly) | Every launch logs `Launching game "<name>" from system "<Full Name> (<system>)"...` followed by same-timestamp lines including `Package: <pkg>`. The ROM path shows up **only in some launch arguments**, e.g. `%ROMPROVIDER% expanded: content://org.es_de.frontend.files/root/storage/7BEF-E760/ROMs/switch/Mario%20Kart%208%20Deluxe.nsp`. Android-app launches log no path. Debug mode is **not** needed (`DebugMode` false). RetroArch (`%EXTRA_ROM%=%ROM%`) and `%ROMSAF%` lines are still pending capture. | `fixtures/es_log/*.txt`; `grep 'Launching game' es_log.txt*` |
| A2 | Pending | SAF read of `es_log.txt` from the app while ES-DE runs | MVP app on device |
| A3 | Verified (partly) | System screenshots are PNGs in `Pictures/Screenshots/`. Whether the `ContentObserver` sees them is pending. | `ls Pictures/Screenshots` |
| A4 | Pending | ES-DE shows new media without a restart | MVP app on device |
| A5 | Verified | Theme variant `gamelist-list-screenshot`; existing scraped media under `downloaded_media/<system>/screenshots/`, mirroring ROM subfolders (e.g. `snes/screenshots/01) All but the Best/`) | `es_settings.xml`; `ls downloaded_media/snes/screenshots` |
| A6 | Pending | One MediaProjection consent reused for several captures | MVP app on device |
| A7 | Pending | ES-DE plays a MediaRecorder H.264 MP4 from `videos/` | MVP app on device |
| A8 | Not checked | Whether the theme plays video audio. Clips are silent for now. | — |
| A9 | Out of scope | Gamepad combo through an AccessibilityService (PLAN.md Phase 4) | — |

## Game resolution

Because the log path is optional, the app resolves the game in this order:
1. The ROM path from the launch-block lines (absolute `/…/ROMs/<system>/…`, or the SAF/document form `7BEF-E760:ROMs/<system>/…`, percent-decoded).
2. A unique `<name>` match in `<ES-DE>/gamelists/<system>/gamelist.xml` (its `<path>` is `./<relative path>`; Android-app stubs such as `./Dead Cells.app` are listed there too).
3. Otherwise the app reports the raw launch line and saves nothing.

ES-DE's bundled `assets/systems/android/es_systems.xml` (pulled from the installed APK) shows which launch arguments carry the ROM: `%ROM%` (RetroArch `%EXTRA_ROM%`), `%ROMSAF%`, `%ROMPROVIDER%`, among others.
