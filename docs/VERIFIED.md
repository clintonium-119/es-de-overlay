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
| A1 | Verified | Every launch logs `Launching game "<name>" from system "<Full Name> (<system>)"...` plus same-second lines incl. `Package: <pkg>`. The ROM path appears only for some launch arguments: raw for RetroArch (`%ROM% expanded: /storage/7BEF-E760/ROMs/snes/02) Hacks/Easyland.zip`), percent-encoded for `%ROMPROVIDER%` (`content://org.es_de.frontend.files/root/...`). Android-app launches log no path. Debug mode is not needed. `%ROMSAF%` not yet seen in a real log, but ES-DE's own code (`classes.dex` strings) logs it as `%ROMSAF% expanded: content://com.android.externalstorage.documents/tree/<tree>/document/<docId>`, which the parser handles encoded or not (unit-tested). | `fixtures/es_log/*.txt` |
| A2 | Verified | The app reads `es_log.txt` through the SAF tree while ES-DE runs; a launch at ES-DE time 15:57:40 was logged by the app at 15:57:41.37 (1 s polling). | `adb logcat -s ESDEShot` |
| A3 | Verified | Power + volume-down in a game creates `Pictures/Screenshots/Screenshot_*.png`; the `ContentObserver` filed it as `screenshots/02) Hacks/Easyland.png` within 1 s. Taken inside ES-DE, it is left alone with a notification. (`KEYCODE_SYSRQ` does not take a screenshot on this build; `input keycombination KEYCODE_VOLUME_DOWN KEYCODE_POWER` does.) | `stat` on the media file; `dumpsys notification` |
| A4 | Verified | ES-DE showed the new screenshot (and played the video) for Easyland on returning to the gamelist, no reload or restart. | Device screenshot; user observation |
| A5 | Verified | Theme variant `gamelist-list-screenshot`; existing scraped media under `downloaded_media/<system>/screenshots/`, mirroring ROM subfolders (e.g. `snes/screenshots/01) All but the Best/`) | `es_settings.xml`; `ls downloaded_media/snes/screenshots` |
| A6 | Verified | One consent at service start covered several captures in a row across app switches. Reinstalling or restarting the service needs a new consent. | Two back-to-back captures, no dialog |
| A7 | Verified | ES-DE plays the app's H.264 MP4 from `videos/`. | User observation |
| A8 | Partly | Clips now carry game audio (playback capture): an Easyland clip had AAC 48 kHz stereo, 14.89 s for 14.88 s of video, mean -23.3 dB. Whether the theme plays it in the gamelist is up to ES-DE's video-audio settings. All installed games/emulators have `ALLOW_AUDIO_PLAYBACK_CAPTURE`. | `ffprobe`, `ffmpeg -af volumedetect`, `dumpsys package` |
| A9 | Superseded | Gamepad combo through an AccessibilityService (PLAN.md Phase 4). The menu now opens on Retroid's Home message instead; see [In-game menu](#in-game-menu). | — |

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
- **Transparent windows get no touches.** An overlay window at alpha 0 is skipped for input, so the old capture button was at alpha 0 only for the screenshot frame and at 1% while recording (still tappable to stop early). The button has since been replaced by the Home menu.
- **Capture display stays empty until a window layout pass.** The MediaProjection display is bound to its own layer stack and only starts mirroring the screen when the window manager runs a layout pass. RetroArch changes its windows often enough that this happened by itself; Stuffy Frights (Unity) sitting on its menu gave 0 frames in 15 s, and a RetroArch cold start stalled after 1 frame. Showing the volume panel mid-capture made frames flow (951 in 15 s), confirming the cause. The app now relayouts its own button invisibly (window title change) right after creating the display and whenever 300 ms pass without a frame: Stuffy Frights then captured 900 frames with no outside help. `adb shell screenrecord` was never affected (it binds layer stack 0 directly).
- **ES-DE log on reboot.** After a device reboot ES-DE overwrote `es_log.txt` without moving it to `es_log.txt.bak`. The app detects the new log by its first bytes and starts over.
- **Logcat flooding.** During games the device logs ~600 SELinux lines/s, so the default logcat buffer drops app lines within seconds; use `adb logcat -G 16M` when debugging.
- **ES-DE imports new apps.** Installing this app added `ROMs/androidapps/ES-DE Recorder.app` to ES-DE's Android Apps system (`GuiGameImporter` in the log).

`fixtures/es_log/retroarch_rom.txt` was saved during testing as `grep -n -A10` output from `es_log.txt` with only grep's line-number prefix removed; the reboot then overwrote that log.

## Emulators on this Nova (expected, not each tested)

Capture mirrors whatever is on screen, so it doesn't depend on the emulator. What varies is how ES-DE passes the ROM (bundled `es_systems.xml` / `es_find_rules.xml`), which decides how the game is identified. Every emulator below keeps the game in the package ES-DE launched, which the foreground check needs.

| Emulator | Package | ROM argument | Status |
|---|---|---|---|
| RetroArch (all cores) | `com.retroarch.aarch64` | `%ROM%` (raw path) | Tested on device |
| Android games/apps | the app itself | none, resolved via gamelist | Tested on device (Stuffy Frights) |
| Eden (Switch) | `dev.eden.eden_emulator` | `%ROMPROVIDER%` | Real launch line parsed in tests |
| PPSSPP, Azahar, melonDS, Cemu, ARMSX2, Dolphin, NetherSX2 | own packages | `%ROMSAF%` | Format confirmed from ES-DE code; parser unit-tested |

If the path ever can't be read, a unique `<name>` in the system's `gamelist.xml` still identifies the game; failing both, nothing is saved and the notification shows the raw launch line. Not measured: any frame-rate cost of recording during the heaviest games (Switch, Wii U, PS2).

## In-game menu

Checked on the Nova on 2026-10-29 with Easyland (`snes/02) Hacks/Easyland.zip`) in RetroArch.

- **Home key.** The Nova's Home button reaches accessibility services as `KEYCODE_MOVE_HOME` from the "Retroid Pocket Controller", not `KEYCODE_HOME`. It doesn't appear in `getevent` on any input device.
- **Key filtering was dropped.** A key-filtering accessibility service could swallow Home. With it on, RetroArch seemed to ignore the whole controller, but the game turned out to be paused in its save state, so that result is unconfirmed. Either way, Retroid still showed its own message for the swallowed press. The app doesn't filter keys.
- **Retroid's Home guard.** With *Prevent pressing Home accidentally* on (`prevent_press_home_accidentally=1`), the first Home press in a game shows an `android.widget.Toast` from `com.rp.settings`: "Press the Home button again to return to launcher." A second press while it's up goes to the launcher. In ES-DE, Home shows no message. A listen-only service with only `typeNotificationStateChanged` sees the toast, and the controller keeps working in RetroArch and ES-DE (`dumpsys accessibility`: `capabilities=0`).
- **Latency.** Toast created to `HelperService` acting: 15 ms (4 ms after the toast's window appeared). The toast stays up about 2.6 s, long enough to land in a capture, so Capture waits until 3 s after the Home press.
- **Toasts while the menu has focus.** They still arrive, so Home with the menu or a guide open goes to ES-DE (70 ms to ES-DE in front). On a quick double press, Retroid goes home itself and the menu closes when ES-DE comes to the front.
- **D-pad.** The D-pad sends `ABS_HAT0X`/`ABS_HAT0Y` and reaches a focused overlay window as `KEYCODE_DPAD_*`. A, B, L1/R1 and L2/R2 arrive as gamepad buttons.
- **RetroArch pauses** while the menu has focus, and resumes when it closes.
- **System bars.** A focusable full-screen overlay brings the navigation bar back over the game. Hiding the system bars through the overlay's own `WindowInsetsController` works, with `fitInsetsTypes = 0` so the window covers their area.
- **SAF file descriptors.** `PdfRenderer` and `Typeface.Builder(FileDescriptor)` (the theme's VAG Rounded font) both work with descriptors from the SD card's external storage provider.
- **Large PNG maps.** A `BitmapRegionDecoder` region of an 8192x2048 PNG takes about a second, because PNG rows above the region are inflated each time. The viewer moves the bitmap already shown at once, and decodes the sharp region on a worker.
- **GPU cache after closing.** Drawing pages as hardware textures left about 70 MB of "Graphics" memory in the renderer's cache after the viewer closed, and nothing trims it in an overlay-only process. The page view now draws through a software layer.

### Idle cost

`dumpsys meminfo com.clintonium.es_de_overlay` (TOTAL PSS) and `top -b -n 3 -d 15` for the app process, with the menu closed and Easyland in front:

| State | PSS | Graphics | CPU (one core) |
|---|---|---|---|
| Baseline before the menu (earlier build, capture button) | about 55 MB | — | about 0.2% |
| Fresh process, menu never opened | 66–71 MB | 2.6 MB | 0.0–0.3% |
| After opening, zooming and closing the 8192x2048 map (software layer) | 77–78 MB | 8.0 MB | 0.0–0.2% |
| The same, before the software layer | 139–141 MB | 70.5 MB | 0.0–0.3% |

The fresh process had also opened the settings screen for capture consent.


### Acceptance (2026-10-29)

With test files for Easyland (a 3-page manual PDF; guides: a text file with ASCII art, an 8192x2048 PNG map, a PDF and a 3-page CBZ), removed afterwards:

- Home in Easyland opens the menu; Home again goes to ES-DE, with no overlay window left (`dumpsys window`).
- Capture saved `snes/screenshots/02) Hacks/Easyland.png` and `snes/videos/02) Hacks/Easyland.mp4` (15 s, 1787 frames, with audio). The PNG shows no menu and, with the 3 s wait, no Retroid message.
- Home 4 s into a clip stopped it and saved both files.
- The manual and all four guide formats open. Pages turn, zoom and pan with ES-DE's controls; the text keeps its 80 columns; CBZ pages follow file-name order.
- With no manual or guide on the card, the menu shows Capture only.
- No app crash in the logs across the runs.

## Find guides and the HTML viewer

Checked on the Nova on 2026-10-30 with Shantae (`gbc/Shantae.zip`), Super Metroid (`snes/Super Metroid.zip`) and Easyland.

- **WebView memory stays after `destroy()`.** A WebView in the service process left it at 137.6 MB PSS 60 s after closing (126.2 MB after a trim), against 69.2 MB before: Chromium's library and code (Code: 47.7 MB) stay loaded for the life of the process. The viewer now runs in its own `:viewer` process, which ends when it closes. The service then returned to 74.0–75.3 MB, with Code unchanged at 27.7 MB. The viewer process starts about 0.3 s before the page is requested.
- **The offline WebView.** Responses from `shouldInterceptRequest` load with `blockNetworkLoads` on, and a WebView created from the service's window context draws and takes keys and touch in an overlay window.
- **Toasts draw under the overlay.** A toast shown while the menu is up exists for its 2.6 s but stays hidden under the opaque menu window, so the menu shows errors as rows.
- **Regex on Android.** Kotlin's `Regex.findAll` and `replace { }` start a new `Matcher` per match, and on Android each one copies the whole input into native memory. Parsing the 4.7 MB SuperNES atlas page that way grew the service to 3.7 GB before the low-memory killer stopped it, taking ES-DE and other apps too. With one `Matcher`, the check peaks at 208 MB RSS.
- **Idle cost after Find guides.** After several Find guides runs, the service settles at 89.5–95.9 MB PSS once its Java heap is collected, against 67.8 MB for a fresh process; the HTML viewer adds nothing after it closes. The difference is what the network and TLS code leave loaded.
- **Save times on Wi-Fi.** The VGMaps check is one atlas page, up to 4.7 MB; StrategyWiki is two API calls. A check takes about 2 s. Super Metroid's StrategyWiki guide takes about 15 s: 17 pages and 166 images, 3.1 MB. Shantae's 56 VGMaps maps take about 8 s, a 7.2 MB CBZ.
- **Image hosts.** StrategyWiki's images (`cdn.wikimg.net`) and VGMaps' maps answer plain requests with the app's User-Agent.
- **Testing Home over adb.** `input keyevent KEYCODE_HOME` and `KEYCODE_MOVE_HOME` go straight to ES-DE without Retroid's message, so the menu needs the physical button.
- **RetroArch and a second launch.** A RetroArch left running in the background after Home receives ES-DE's next launch as a new intent, closes itself and exits; launching again works.
