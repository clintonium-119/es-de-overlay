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
| A9 | Superseded | Gamepad combo through an AccessibilityService (PLAN.md Phase 4). The menu opened on Retroid's Home message from 2026-10-29, and since 2026-11-03 opens on the controller's Back through a key filter; see [Back button menu](#back-button-menu). | — |

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
- **Key filtering was dropped** (taken up again on 2026-11-03, see [Back button menu](#back-button-menu)). A key-filtering accessibility service could swallow Home. With it on, RetroArch seemed to ignore the whole controller, but the game turned out to be paused in its save state, so that result is unconfirmed. Either way, Retroid still showed its own message for the swallowed press.
- **Retroid's Home guard** (the app no longer uses it since 2026-11-03). With *Prevent pressing Home accidentally* on (`prevent_press_home_accidentally=1`), the first Home press in a game shows an `android.widget.Toast` from `com.rp.settings`: "Press the Home button again to return to launcher." A second press while it's up goes to the launcher. In ES-DE, Home shows no message. A listen-only service with only `typeNotificationStateChanged` sees the toast, and the controller keeps working in RetroArch and ES-DE (`dumpsys accessibility`: `capabilities=0`).
- **Latency** (Home message trigger, no longer used). Toast created to `HelperService` acting: 15 ms (4 ms after the toast's window appeared). The toast stays up about 2.6 s, long enough to land in a capture, so Capture waits until 3 s after the Home press.
- **Toasts while the menu has focus** (Home message trigger, no longer used). They still arrive, so Home with the menu or a guide open goes to ES-DE (70 ms to ES-DE in front). On a quick double press, Retroid goes home itself and the menu closes when ES-DE comes to the front.
- **D-pad.** The D-pad sends `ABS_HAT0X`/`ABS_HAT0Y` and reaches a focused overlay window as `KEYCODE_DPAD_*`. A, B, L1/R1 and L2/R2 arrive as gamepad buttons.
- **RetroArch pauses** while the menu has focus, and resumes when it closes.
- **System bars.** A focusable full-screen overlay brings the navigation bar back over the game. Hiding the system bars through the overlay's own `WindowInsetsController` works, with `fitInsetsTypes = 0` so the window covers their area.
- **SAF file descriptors.** `PdfRenderer` and `Typeface.Builder(FileDescriptor)` (the theme's VAG Rounded font) both work with descriptors from the SD card's external storage provider.
- **Large PNG maps.** A `BitmapRegionDecoder` region of an 8192x2048 PNG takes about a second, because PNG rows above the region are inflated each time. The viewer moves the bitmap already shown at once, and decodes the sharp region on a worker. The sample size doesn't change that cost: in a headless benchmark on 2026-11-04, Super Metroid's Brinstar map from VGMaps (13568x5440, 4.3 MB) decoded whole in 527–564 ms at 1/8 through 1/64, and a 1920x1440 region at full size took 154 ms at the top left and 529 ms at the bottom right. Since then the whole page at its fit stays under the sharp region, so the part a zoom or pan uncovers is blurry, not black.
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
- **Toasts draw under the overlay.** A toast shown while the menu is up exists for its 2.6 s but stays hidden under the opaque menu window, so the menu shows errors as rows. The viewer's own toast for a file that won't open was hidden the same way (a Toast window existed; the screen didn't change), so the viewer now hands the error to the menu.
- **Regex on Android.** Kotlin's `Regex.findAll` and `replace { }` start a new `Matcher` per match, and on Android each one copies the whole input into native memory. Parsing the 4.7 MB SuperNES atlas page that way grew the service to 3.7 GB before the low-memory killer stopped it, taking ES-DE and other apps too. With one `Matcher`, the check peaks at 208 MB RSS.
- **Idle cost after Find guides.** After several Find guides runs, the service settles at 89.5–95.9 MB PSS once its Java heap is collected, against 67.8 MB for a fresh process; the HTML viewer adds nothing after it closes. The difference is what the network and TLS code leave loaded.
- **Save times on Wi-Fi.** The VGMaps check is one atlas page, up to 4.7 MB; StrategyWiki is two API calls. A check takes about 2 s. Super Metroid's StrategyWiki guide takes about 15 s: 17 pages and 166 images, 3.1 MB. Shantae's 56 VGMaps maps take about 8 s, a 7.2 MB CBZ.
- **Image hosts.** StrategyWiki's images (`cdn.wikimg.net`) and VGMaps' maps answer plain requests with the app's User-Agent.
- **Testing Home over adb.** `input keyevent KEYCODE_HOME` and `KEYCODE_MOVE_HOME` go straight to ES-DE without Retroid's message, so the menu needs the physical button.
- **RetroArch and a second launch.** Home leaves RetroArch running in the background (`prev`, adj 700 in `dumpsys activity oom`), from the overlay's second Home as well as over adb. Relaunching the same game resumes it in the same process. A different game's launch arrives as a new intent (`flg=0x10010000`); RetroArch closes itself and exits, and launching again works. ES-DE's bundled `es_systems.xml` adds `%ACTIVITY_CLEAR_TASK%` for many standalone emulators but not for RetroArch.
- **Ending another app.** `ActivityManager.killBackgroundProcesses` for RetroArch logs `Invalid packageName: com.retroarch.aarch64`, also with RetroArch visible through `<queries>` (`dumpsys package queries`). This Android 13 firmware appears to apply Android 14's own-processes-only rule. Force-stop needs `FORCE_STOP_PACKAGES`, which only system apps get; Retroid's Game Assistant (`com.rp.gameassistant`) has it but exposes no way for other apps to use it. Settings' App Info page does force-stop: on 2026-11-01, with RetroArch and then Eden left in the background by Home, `ACTION_APPLICATION_DETAILS_SETTINGS`, **FORCE STOP** (`com.android.settings:id/button3`) and the dialog's **OK** (`android:id/button1`) ended each process at once. Eden's foreground service and its notification went with it. Once the app is stopped, the FORCE STOP button is disabled.
- **Long rows.** A `TextView` with `maxLines = 1` wraps at a space and shows only the first line, so a long label lost its end. Rows are now single-line marquees, started a second after selection (the theme's `textHorizontalScrollDelay` is 1).

## The guide browser

Checked on the Nova on 2026-10-30 (WebView 109, `com.android.webview`) in Shantae, with the browser in the `:viewer` process and the WebView's own User-Agent and cookie settings.

- **Each site lets the overlay WebView in.** GameFAQs' "Performing security verification" page cleared by itself within 16 s. Neoseeker showed Cloudflare's "Verify you are human" checkbox, which cleared after one tap. ZeldaDungeon's challenge page cleared by itself within 12 s. Once cleared, GameFAQs opened again without a check.
- **GameFAQs' first page after the check came back empty.** `search?game=Shantae` showed the empty search form straight after the check, then the results when searched again or opened later.
- **Typing works in an overlay window.** Tapping a page's field brings up Gboard. Insets that include `ime()` shrink the window above the keyboard, so the bar and the field stay visible. A resize soft-input mode is ignored once a window sets its own insets. Sticky site banners can still cover a field.
- **Closing.** Back walks the history and then closes; Close, Home and leaving the game each end the `:viewer` process.
- **Memory.** The browser's WebView lives and dies with the `:viewer` process, so only the service's own reading changes:
  - With the menu open, 89.6 MB before the browser; 104.4 MB 67 s after closing; then 92.4–93.0 MB from 73 s on.
  - From Find guides in Shantae: 90.3 MB once the checks had settled; 101.8 MB 65 s after Close; then 97.6–98.0 MB from 71 s to 136 s. The service's native heap was 19.5 MB then, against 11.4 MB in an earlier reading. That growth is in the service, which only starts the browser; the cause isn't known.
- **Inspecting sites.** Debug builds let desktop Chrome inspect the browser: `adb forward tcp:9333 localabstract:webview_devtools_remote_<pid of :viewer>`.
- **Reading guides from the page.** Checked on live guide pages on 2026-10-30:
  - GameFAQs text guides are `#faqtext`, split into `<pre>` blocks at line ends, with the newline dropped at each split.
  - GameFAQs HTML guides are `#faqwrap`. Named-page guides list their pages in `#faq_toc .toc_menu`. Numbered `?page=N` guides list only some pages there; their pager's Last Page gives the count.
  - Neoseeker text FAQs are `#faqtxt.text_only pre`. Its wiki walkthroughs are `.mw-parser-output`, with every chapter in `.wiki-toc`.
  - ZeldaDungeon chapters are `.content-wrap`. They list the chapters in a `<ul>` in which the current one is a `<strong>`.
- **Guide images.** GameFAQs (`/a/faqs/…`) and ZeldaDungeon images are on the page's own host, so they read from inside the page. ZeldaDungeon's `http://` images need https. Neoseeker's `cdn.staticneo.com` is cross-origin, so CORS blocks it in the page, but it answers a plain request with the app's User-Agent. GameFAQs' image host answered such a request from the Nova, but refused it from a desktop with a 403.
- **Saving whole guides.**
  - Shantae's 82 KB GameFAQs text guide saved in under a second as an 84 KB file.
  - Super Metroid's 8-page GameFAQs HTML guide (bover_87) loaded each page in the visible browser 3–14 s apart. It saved 264 images in about 65 s, every one read inside the page. The file is 58 MB, mostly base64 map images, and the viewer opens it in about 2 s.
  - Link's Awakening DX's ZeldaDungeon walkthrough (Game Boy Color, 9 chapters) saved in about 2.5 minutes as an 8.2 MB file. Of its 558 images, 554 were read in the page and 4 came from plain requests: they are on `zeldadungeon.net` without `www`, which is another origin. One gave a 404 and kept its alt text.
  - Its Neoseeker FAQ/Walkthrough saved at once as a 173 KB file, with its ASCII title art aligned.
  - ZeldaDungeon's walkthrough front page covers the Switch remake. Each Switch chapter links its Game Boy Color version, and a GBC chapter lists all nine GBC chapters, so saving from there gets the DX walkthrough.
  - No bot check appeared during any save. Neoseeker's check came up once, on the first visit, and cookies kept it from showing again.

## In-game menu revamp

Checked on the Nova on 2026-11-02 with Shantae (`gbc/Shantae.zip`), Easyland and Easyland 2 (`snes/02) Hacks/`), side by side with ES-DE's gamelist for Shantae.

- **The theme's 4:3 gamelist** (`aspect-ratio-4-3.xml`, variant `gamelist-list-screenshot`, font size small), as fractions of the 1280x960 screen:

  | Element | Theme value | On the Nova |
  |---|---|---|
  | List text | pos 0.071875 0.075, size 0.4 x 0.7333 | text at x 92, list 512 px wide |
  | Selected pill | `selectedBackgroundMargins` 0.021875 | pill from x 64, hugging the text, as in ES-DE |
  | Title | large `listFontSize` 0.058333 | 56 px |
  | Art box | origin 1 0.5 at 0.928125 0.44166667, `cropSize` 0.3625 x 0.7333, corner radius 0.0078125 | x 724–1187, y 72–776 in both the menu and ES-DE's gamelist |
  | Game Info text | x-small `listFontSize` 0.033333 | 32 px |

- **ES-DE's art box plays the video.** The theme's `game-art` is a video with a 3 s `delay`, so ES-DE's screenshot of Shantae showed a video frame there. The menu shows the static screenshot instead, at the same place and size.
- **Scraped screenshots are small.** `gbc/screenshots/Shantae.png` is 160x144, the console's own size, and 105 of the 7,729 screenshots on the card are `.jpg`. The menu halves only an image larger than the art box both ways (a 1280x960 capture), and tries PNG, then JPG. Android's default bitmap filtering smoothed the pixel art; ES-DE's art had hard pixel edges, so the menu draws it unfiltered. ES-DE's own static screenshot wasn't caught before its video started, so its interpolation there wasn't compared directly.
- **ES-DE writes a gamelist entry when it launches a game.** Easyland 2 had no entry in `gamelists/snes/gamelist.xml`; launching it wrote `<game>` with only `<playcount>1</playcount>` at 18:22:45.9, a second before the launch line. So every game launched from ES-DE has an entry, and Game Info is shown only when the entry has a developer, publisher, release date, genre, players or description. All 8,413 `<releasedate>` values on the card are `YYYYMMDDTHHMMSS`.
- **X on a list without an X action.** `input keyevent BUTTON_X` on the main menu, which then didn't handle X, opened the selected row as A does. That fits Android's generic key map, which falls back from an unhandled `BUTTON_X` to `DPAD_CENTER`. The menu now always takes X.
- **Screenshot alone.** Stopping the capture from its first frame ended it 5 ms in with one video sample; `ClipWriter` gave no usable MP4, so only the PNG was saved and the notification named only it.
- **No save states from RetroArch.** The installed RetroArch 1.22.2 (`com.retroarch.aarch64`) was pulled on 2026-11-02 and its `lib/arm64-v8a/libretroarch-activity.so` searched: it has the settings strings (`Network Commands`, `network_cmd_port`, `network_cmd_enable`) but none of the command words (`GET_STATUS`, `LOAD_STATE`, `STATE_SLOT_PLUS`, `READ_CORE_RAM`, `SET_SHADER`), so the network command interface is compiled out of the Android build.
- **Memory.** Same steps on the pre-phase build (`9bf8195`) and this one: install, consent, Home in Easyland, B, then `dumpsys meminfo` TOTAL PSS:

  | | Pre-phase build | This phase |
  |---|---|---|
  | Fresh, before the menu | 76.0 MB | 76.0 MB |
  | Menu open | 88.0 MB | 96.5 MB |
  | 60 s after B | 75.9 MB | 76.1 MB |

  The 8.5 MB extra while open includes the decoded screenshot; it was gone 10 s after closing. In a process that had just made three captures, opening and closing the menu moved it from 85.7 to 86.7 MB. A fresh process reads about 76 MB on both builds, above the 66–71 MB noted for a long-running one.
- **A guide saved in the browser.** The viewer that opens the saved guide tells the service when it closes, and the service lists the guides again then; nothing redraws the menu while the viewer is on top.

## Back button menu

Checked on the Nova on 2026-11-03 with a debug build of the `back-button-menu` branch (APK sha256 `9d808a76…84e3`): Easyland and Shantae in RetroArch, Prince of Persia: The Lost Crown in Eden. Retroid's *Prevent pressing Home accidentally* was off (`prevent_press_home_accidentally=0`).

- **The controller's buttons.** "Retroid Pocket Controller" (`Generic.kl`, sources `KEYBOARD | GAMEPAD | JOYSTICK`) sends Back as `KEYCODE_BACK` with source `0x501` (keyboard and gamepad). Home arrives as `KEYCODE_MOVE_HOME`, A/B/X/Y, L1/R1, L2/R2, Start, Select and `BUTTON_C` the same way. The volume rocker is `gpio-keys`, source `0x101` (keyboard only).
- **What a key filter sees.** Android hands a key-filtering accessibility service only key events, and holds each one until the service answers (AOSP's `KeyEventDispatcher` gives up after 500 ms). The D-pad is hat motion, so it never reaches the filter; it reaches a focused overlay window as `KEYCODE_DPAD_*`. Android's back swipe from the screen edge in RetroArch produced no key event at the filter.
- **Binding.** With `flagRequestFilterKeyEvents`, `canRequestFilterKeyEvents` and no event types, Android 13 binds the service: `dumpsys accessibility` shows `capabilities=9` (window content and key filtering) and `eventTypes=` empty, with Retroid's Game Assistant still bound.
- **An update keeps the old service entry.** After `adb install -r` of the build that renamed the service, `enabled_accessibility_services` still listed `.HomeGuardService` and not `.ButtonService`. The settings row now checks for the service itself, so it read *Not enabled* until the new one was turned on.
- **Back as a toggle.** In a game ES-DE launched, Back opened the menu on Resume and RetroArch's own menu didn't open. A second Back closed the menu from the main menu, the Guides list, Game Info, a guide in the viewer and the browser, and the game went on. B stepped back one level in each screen except the browser, which is used by touch and ignores B (its bar's Back steps back). Back in ES-DE and in Android's Settings went through as before.
- **Capture without the wait.** Picking Screenshot started capturing 185 ms later (Video: 150 ms); the PNG showed the game without the menu. Back during a 15 s clip stopped it after 3,967 ms and saved a 4.0 s MP4; the menu didn't open.
- **Home with the guard off.** `KEYCODE_MOVE_HOME` passed through the filter and the firmware started ES-DE's home activity 0.13–0.17 s later: no menu, no message.
- **Leaving without Close when you leave.** RetroArch's own quit hotkey (`BUTTON_C` here) returned to ES-DE with no App Info page and no force-stop. Quit Game ended RetroArch (four times) and Eden; ES-DE's home activity started 0.44–0.71 s after the pick. After Quit Game, the next RetroArch launch started a new process and opened; after Home, a different RetroArch game's launch made RetroArch exit (`System.exit called, status: 0`) and the next launch opened, as in [RetroArch and a second launch](#find-guides-and-the-html-viewer).
- **Eden's own menu** opens with a swipe from the left edge, which still works with Back taken by the overlay.
- **The overlay ended for memory in Eden.** As Prince of Persia loaded, Android ended the overlay (and ES-DE) for memory; the sticky restart brought the service and the filter back 13 s later. Back would have reached Eden in that gap.
- **Delay.** Debug builds log `key held` (time from the press to the start of the service's `onKeyEvent`) and `key delivered` (time from the press to the menu's key listener), both from `KeyEvent.eventTime`, in whole milliseconds.

  | Measurement | Presses | Median | p90 | p99 | Max |
  |---|---|---|---|---|---|
  | Menu, X (passes the filter), `key delivered` | 51 | 3 ms | 4 ms | 6 ms | 6 ms |
  | Menu, D-pad (doesn't), `key delivered` | 58 | 2 ms | 3 ms | 4 ms | 4 ms |
  | RetroArch play, about 3.5 min, `key held` | 134 | 1 ms | 1 ms | 2 ms | 2 ms |
  | Eden play, about 3 min, `key held` | 222 | 1 ms | 2 ms | 4 ms | 4 ms |
  | Eden play, a second 2.5 min, `key held` | 167 | 1 ms | 3 ms | 4 ms | 5 ms |

  In the menu the filter adds about 1 ms (difference of the medians). The limit set before measuring was more than 1% of in-game presses over 4 ms, or any over 16 ms: one of 523 in-game presses was over 4 ms (5 ms), so the filter stays in the overlay's process. `key held` covers only the way into the service, not its answer; the menu rows cover both. The six volume presses all reached the service 152 ms after they were made, before the overlay did anything with them; the cause wasn't looked into.
- **Retroid's force-stop.** Read from the firmware while planning, not reproduced here: Retroid's `forceStopPackage` skips packages in the `app_whiteList` system setting (on this Nova: Settings, Play Store, Chrome, Gallery and Baidu OTA) and packages with an enabled accessibility service. RetroArch and Eden aren't on the list.

## Back, Home and the viewer (2026-11-05)

Checked on the Nova with a debug build of the `overlay-hardening` branch (APK sha256 `d5b59c69…a24e`), Easyland in RetroArch, Retroid's *Prevent pressing Home accidentally* off. Back and the other controller buttons were sent with `sendevent` on the Retroid Pocket Controller; Home was the user's press. Window changes are timed from `input_focus` in the events log.

- **The app in front, on Back.** The 1-second poll noticed ES-DE 0.4–1.15 s after Home. A Back 321 ms after Home went to ES-DE (its Gamelist Options opened), because Back's first down now reads usage events again; the read and decision took 0–1 ms. A Back in the game opened the menu about 120 ms later.
- **Home with the overlay open.** The menu, a guide in the viewer and the guide browser were gone, and ES-DE focused, 140–170 ms after the Home down, before the poll (which took up to 1.15 s with the browser). The game kept running in the background.
- **Back edge cases.** Two Backs 83 ms apart, inside the menu's ~120 ms lookup, left it closed. Back 30 ms after picking Screenshot was taken and ignored; the capture started 172 ms after A and showed only the game. Back during Find Guides' check closed the menu, and nothing reopened it.
- **Opening a big map.** Super Metroid's Brinstar map from VGMaps (13568x5440, 4.3 MB): the `:viewer` process and its black window took about 130 ms from A, and the whole-page decode 596 ms, so the first picture came 720 ms after A. Decoding the top quarter first, on the same decoder, showed it 305 ms after A and the whole page at 873 ms. A second decoder in parallel showed the quarter only at 463 ms (its own file open came after the first) and the whole page at 842 ms. An 8192x2048 RGBA PNG: quarter about 270 ms, whole about 500 ms.
- **Pans and zooms.** With the RGBA map zoomed to fill the view, no recorded frame had black inside the view through pans to the far edge and back; each sharp region landed 237–245 ms after the step. The `:viewer` process held 108 MB PSS while zoomed and ended on close.

## Text in the list pills (2026-11-05)

ES-DE centres a capital S in a list row, not the text's line (its `Font.cpp` puts the baseline at half the line height plus half the S's height). With VAG Rounded's deep descent, a centred line left the capitals high: measured on the Nova from screenshots, the S in a guide's contents pill (Wrecked Ship) had 9 px of pill above and 18 px below, and the G in the menu's Quit Game 13 and 17. Both lists now move their text down by the font's own difference, (ascent + descent - the S's top) / 2, which is 0.092 em for VAG Rounded: 13 and 14 px in the contents, 15 and 15 in the menu, with the pills (56 and 60 px) and row pitch unchanged. The Nova's WebView 109 has no CSS `text-box` trim, so the contents pill does it with padding.

## Viewer zoom, map names, text sections and web saves (2026-11-05)

Checked on the Nova with a debug build of the `viewer-navigation` branch (APK sha256 `a46b555b…a2e5`), Shantae in RetroArch. The user gave capture consent, launched the game and opened the menu; everything else was sent with `sendevent` or tapped after a screenshot. A dry run on the build before found four of the points below, fixed before this pass.

- **Zoom and pan.** A CBZ page and a PDF page closed at 2.25 times their fit reopened pixel-identical, the sharp region decoded for the same rectangle; closed at the fit, no view was saved and they reopened at the fit. A saved HTML guide closed at 150 % text reopened at 150 %, a few pixels from where it closed: the scroll is saved as parts per 10,000 of the guide's height. A position saved by the earlier build (4948) restored.
- **Map names.** Re-saving Shantae from VGMaps gave entries such as `003 Start - Part 1.png` and `038 L1: Dribble Fountain Labyrinth.png`; the live SNES atlas names Super Metroid's maps "Norfair" and so on, and Chrono Trigger's "1000 A.D. - Crono's House". The CBZ saved before names showed its file names (`SHANTAE-SPIDERFOREST`) and browsed as before. The longest name, "Mini Games: Dance Parlor & Gecko Match", is cut with an ellipsis before the right-aligned help.
- **Text guides.** Shantae's GameFAQs guide, saved as one text section on 2026-11-02, opens in 7 sections; a `.txt` (Solaris) in 15. A `<pre>` doesn't draw a final line break, so each section ending in blank lines showed one fewer at first; with the final break kept, every line sits on the same 30 px pitch across section boundaries. The bar named the last section starting in the top quarter of the view, so with Shantae's 7-line Thanks section (105 of 115 px) R2 went from 5 to 7 and, at the bottom, where the page can't scroll Thanks to the top, L2 stayed on 7; the section a jump went to now counts while the view stays where it left it.
- **The viewer's WebView** has no devtools socket: only the browser turns WebView debugging on, and only in its own process run.
- **Web search.** In the Nova's WebView, Brave Search and Mojeek asked to prove the browser wasn't a bot and Bing opened on a cookie banner; DuckDuckGo showed results. From a desktop with no scripts, Brave answered with results and DuckDuckGo's plain page with a challenge, so the WebView decided it.
- **Saving from other sites.** IGN's Shantae guide offered Save guide and listed its pages; its contents sidebar adds Maps part 2 to 4, and its contributors link ("+66 MORE") starts unticked. On the Maps pages, maps with one-line captions, Reader View picked the copyright footer; the block holding the page's big pictures is taken instead, so the save has all 16 maps (IGN's own small versions, about 295 px wide). Its Player's Guide page, mostly lists, fails Reader View's `isProbablyReaderable`, so a picked page is read regardless. Two page loads of the save came 2.15 s apart. The page list's rows were 38 px until set to the bar's 48 dp: a single-line `TextView` resets its minimum height to one line.

## Guide pictures and zoomed text (2026-11-05)

Checked on the Nova with debug builds of the `viewer-navigation` branch, Shantae in RetroArch, after a dry run. The user gave capture consent and opened the menu after each install; everything else was sent with `sendevent` or tapped after a screenshot.

- **Pictures.** In a throwaway guide holding Twinkle Palace (7511x1913 PNG), the overworld as a GIF and Scuttle Town, A opened the map in view whole in 164 ms; zoomed in, renders were regions such as `[1251,0][6259,1913]` in about 137 ms. The GIF, which region decoding can't read, showed after a trip through PNG (49 ms). B and a tap's B each came back to a guide 0 pixels different from before. The saved StrategyWiki Shantae guide embeds 47 SVG icons, which don't decode as bitmaps, so pictures skip SVG.
- **IGN's sizes.** On the Nova's WebView, IGN's map `<img>` has `src` at the laid-out width (`?width=427`) and a srcset of that `1x` and `&dpr=2` `2x`; the 2x is the 590x404 original. A re-save from the main page (its six default pages) has every map at 590 px wide; the earlier save's were about 295.
- **Panning zoomed text.** WebView widens a page's layout viewport to fit content a little wider than the screen, so script can't scroll it: `window.scrollBy` stopped at `scrollWidth - innerWidth` in a probe page, and moved Shantae's 125 % text not at all. Only the view's own scroll reaches it, and `WebView.scrollBy` past the right edge counted on, so the first left back did nothing. Held between the page's edges (from `visualViewport`), panning at 125 % went 252 px and straight back; at 150 %, 320 and 554 px (the edge) and back in two steps, every line together.


## Quitting games through Shizuku (2026-11-05)

Checked on the Nova with debug builds of the `shizuku-quit` branch, Shantae and Super Metroid in RetroArch and Super Mario Odyssey in Eden v0.2.1, after a dry run that found two bugs. The recorded pass ran after a reboot (APK sha256 `73ca03a8…e017`); a last build only adds the guard in the last bullet but one (`b1e82940…f7a3`). Shizuku-Next 14.0.33 (`moe.shizuku.privileged.api`, from rushiranpise/Shizuku-Next) with the client library `dev.rikka.shizuku` 13.1.5. The user launched the games and pressed the buttons.

- **The persistent adb port.** On this Nova `persist.adb.tcp.port` is labelled `default_prop`, so adb's shell may set it; after `setprop persist.adb.tcp.port 5555` adbd listens on `[::]:5555` from every boot. `ro.adb.secure=1`, so a connection still needs an allowed key.
- **Shizuku-Next on boot.** With the USB start method, *Start on boot*, *Watchdog* and *TCP mode*, `shizuku_server` (uid shell) was running 6 s after `sys.boot_completed` with Wi-Fi off. After the reboot before the recorded pass, the overlay's process started first (its accessibility service), Shizuku's server 7 s later, and the overlay attached to it at once (`attachApplication from com.clintonium.es_de_overlay`). Each time Android ended the overlay for memory, Shizuku sent its binder again as the overlay came back; once it started the overlay's process itself, through the `ShizukuProvider`.
- **The library's shell call.** `Shizuku.newProcess`, private since library 13, works by reflection under Shizuku-Next 14: `am force-stop` ran as the shell user (`am_kill … stop com.retroarch.aarch64 due to from pid …`). `ShizukuRemoteProcess` doesn't override `Process.waitFor(timeout)`, so the overlay uses its `waitForTimeout`.
- **Quit Game with Shizuku.** RetroArch was killed 17–42 ms after the pick, ES-DE resumed 10–17 ms after that, and `force stop: shizuku` followed; no Settings activity ran. The first build closed the menu before the stop, so the game showed for about 100 ms; the menu now stays up until the game is gone, and the user saw no flash.
- **Quit Game without Shizuku** (the overlay revoked in Shizuku): the App Info page opened and Force stop was pressed as before (`force stop: stopped`), App Info on screen for about 0.5 s.
- **Quit games left with Home.** Switch on: RetroArch ended 0.1–0.8 s after ES-DE resumed (the overlay checks once a second), Eden 0.4–0.6 s. The next launch of a different RetroArch game started a new process and opened the first time. A RetroArch hotkey quit also counts as leaving for ES-DE; ending the already-ended app showed nothing. Switch off: RetroArch was still running 26 s after Home. Retroid's Game Assistant resumed nothing between the game and ES-DE; Recents can, and the game was still ended once ES-DE was in front.
- **Android ends ES-DE while a Switch game loads.** Loading Super Mario Odyssey made the low-memory killer end ES-DE and the overlay, and in four loads also Eden itself (at 4.2–4.8 GB, "device is not responding"). Home then starts a new ES-DE with a new log, which made the first build forget the launch and leave Eden running; the overlay now keeps the game it saw in front until a different launch. Eden's log also showed `host_memory.cpp: assert virtual_offset + length <= virtual_size` as emulation stopped. After a run started from Eden's own list exited cleanly and rewrote its shader caches, one more load from ES-DE failed and the next worked, so whether the caches matter isn't settled.
- **Apps opened from ES-DE.** ES-DE lists Android apps as games, ES-DE Overlay and Shizuku-Next among them. With the guard, each opened from ES-DE and left with Home kept running, and the Back button menu stayed enabled. Without it, the overlay would have force-stopped itself, which also turns its accessibility service off.
- **The settings row.** Allowed: *Enabled*, no button, the switch usable. Revoked: *Not allowed* with **Allow**, and the switch greyed. **Allow** opened Shizuku's dialog, and settings showed *Enabled* again when it closed.
- **Not checked:** whether Retroid's Whitelist also stops `am force-stop` through Shizuku (RetroArch and Eden aren't on it), and **Allow** after Shizuku's *Deny and don't ask again*.

## Following the ES-DE theme (2026-11-06 and 07)

Checked on the Nova with debug builds of the `public-release` branch, Shantae in RetroArch, after a dry run whose fixes are below. The user switched themes in ES-DE, launched the game, gave capture consent and toggled Shizuku; the overlay was driven with `sendevent`. Each issue found in a pass was fixed before the next.

- **Where themes are.** A theme placed by hand is in `ES-DE/themes/` (minui-menu-es-de-custom). ES-DE's theme downloader installs into `/storage/emulated/0/Android/data/org.es_de.frontend/files/themes/`, which Android 13 hides from other apps and SAF but the shell user reads; the built-in Linear is an asset of ES-DE's APK, readable through `createPackageContext("org.es_de.frontend")` once the package is in the manifest's `<queries>`.
- **Copying through Shizuku.** One `find … | tar -c -f - -T -` through Shizuku's shell copied a theme's XML, font and SVG files at launch: Canvas 813 files in 220–316 ms, Iconic 596 in 151 ms, Art Book Next 495 in 405 ms, TechDweeb 270 in 221–260 ms, Analogue OS 31 in 270–288 ms. A process per file had taken 7.9 s. With the files unchanged, the signature (`stat` of each, through `md5sum`) said `copy is current` in 27–156 ms. Revoking the overlay in Shizuku ends the overlay's process, so capture consent is needed again.
- **Without Shizuku.** With the copy deleted and Shizuku off, Art Book Next gave the default look, and settings read *Default look (Linear): theme isn't in ES-DE/themes; allow Shizuku, or copy its folder there*.
- **The built-in Linear.** Dark and PSN were read from ES-DE's app (46 and 115 ms) in Linear's Oxygen font; settings read *Follows ES-DE's theme: Linear — Dark* and *— PSN*, and PSN's help bar is black as in ES-DE.
- **Themes side by side.** Canvas, Art Book Next, TechDweeb, Iconic (its list and carousel variants), Analogue OS and Linear's two schemes were compared with ES-DE screenshots of the same gamelist; Canvas's list panel is a raster picture, so its colour is approximate (060606 for 0C0C0C). Iconic's text is small, which the Text size setting is for: at 150 % the menu's rows, title and help pill measured 1.5 times their 100 % size.
- **minui at 100 %.** The menu, guides list, CBZ, maps list, Find Guides and browser were pixel-identical to the installed build's (fuzz 2 %), apart from where a long name's marquee had scrolled to. Intended differences: guide and Game Info text spaced at the theme's description line spacing (1.5 for minui), a text guide's title at 1.5 times its text, and the contents list on the menu's row pitch below its own title row. A first pass found a long name's pill cut square at the list's edge; it now stops rounded at the row's end.
- **overlay-theme.xml.** A file with only `<background>102030</background>`, read at the next launch, changed only the background: 392 pixels differed from the installed build's menu repainted in that colour, all of them anti-aliased edges. Settings read *overlay-theme.xml over MinUI Menu (Custom) — Dark (Bold Font)*.
- **Menu cost.** Opening the menu reads no theme files; the look read at the launch is kept (`look: kept from the launch`). Back to the menu's window gaining focus, same sitting, 5 repeat opens each: the master build 260–279 ms (median 276), this branch 236–295 (median 243). Earlier the same day this branch measured 103–129 ms, and on 2026-11-06 the installed build 89–102 (median 97) with the game paused; between runs the Nova slowed about twice over with no thermal throttling (status 0) and mostly idle CPUs, the reads before the menu (manual, guides, screenshot), its building and the window all slower alike. ES-DE's VRAM limit at 128 MB instead of 512 didn't make it faster. A theme read, at a launch or when settings opened, took 70–236 ms, off the menu's path.
- **ES-DE's VRAM limit.** At 128 MB, Linear flickered between grey and black while a video loaded and showed no game art; at 512 it was fine.
- **Installing stops the service.** After `adb install -r`, the Back button service is bound again but takes Back only for a game the service saw launch, so until capture consent and a new launch, Back reaches the game's app.

## Security sweep (2026-11-08 and 09)

The code was reviewed for the ways an app with screen capture, a key-filtering accessibility service, overlay windows, Shizuku force-stop and two WebViews could be misused. Each finding was fixed or accepted with a reason. The fixes were checked on the Nova with a debug build of the `public-release` branch (APK sha256 `3356ed52…1fdb`), Shantae in RetroArch and Mario Kart 8 Deluxe in Eden, after a dry run that found one problem (the last bullet but one). The user launched the games and apps and gave capture consent; the overlay was driven with `sendevent`, the browser through WebView devtools.

- **Manifest.** App data is kept out of cloud backups and device transfers (`allowBackup="false"` and data extraction rules excluding every domain). Every permission and exported component carries a comment with its use and protection: settings (the launcher), the Quick Settings tile and the Back button service (each bound by a system permission), Shizuku's provider (`INTERACT_ACROSS_USERS_FULL`), the boot receiver (only acts on `BOOT_COMPLETED`). After the reboot the start notification appeared and Android had the Back button service bound with capabilities 9 (window content and key filtering only).
- **Guide browser.** No file or content-provider access; links go nowhere but https (on the Nova, links to intent:, file:, content:, http: and data: left the page where it was); Safe Browsing stays on. What a page hands back for a save is treated as page data: only https pages on the page's own host, at most 50, titles at most 200 characters, and a cap on how much it can ask the app to read. GameFAQs, Neoseeker and IGN guides saved as before, with Cloudflare's check passing.
- **Saved guides.** Each section is cleaned when written: no scripts, handlers, frames, objects, forms, `<base>`, `<meta>`, SVG or MathML, and links only to http(s), mailto, `#` or relative places. Re-saved StrategyWiki and IGN guides came out byte-identical to the copies saved before. The viewer serves every guide, old ones included, with a Content Security Policy: a test guide with an inline script, an `onerror` handler, an SVG `onload` and a frame's script showed none of them running (5 refusals logged), while the contents list, section jumps, picture taps and the theme's font still worked.
- **Untrusted strings.** File names from web pages and atlas names can't be `.`/`..`, hold control characters or run past 120 characters; existing guides kept their names. The package ES-DE's log names must be a package name, and Quit Game refuses the overlay, ES-DE and Shizuku: over Shizuku-Next opened from ES-DE it logged `force stop refused` and Shizuku kept running, while RetroArch and Eden still ended through Shizuku. Every XML parse (themes, gamelists, StrategyWiki) refuses a DOCTYPE.
- **Dependencies.** All 222 artifacts on the app's and the build's classpaths were checked against the GitHub Advisory Database. None in the APK was affected. The build had seven: CVE-2026-53914 in the Kotlin Gradle plugin (fixed by Kotlin 2.4.21) and AGP's own Bouncy Castle, Guava, jose4j, JDOM2 and Commons Lang. AGP 9.5.1 (with Gradle 9.6.0) still shipped them, so the build pins patched versions. Dependabot now proposes Gradle and GitHub Actions updates weekly.
- **Lint and CI.** Android Lint's 62 security checks fail the build; `lintDebug` reports 0 errors (18 warnings, none security). CI runs the unit tests and Lint on every push and pull request; CodeQL and Scorecard are written but skip while the repository is private. Every action is pinned to a commit SHA, and every workflow's token is read-only unless it needs more.
- **Found in the dry run.** Saving the VGMaps atlas renamed 21 of Shantae's 56 maps inside the `.cbz` ("L1: Dribble Fountain Labyrinth" became "L1 - …"), because entry names had gone through the SD-card file-name rules. Entry names now keep the atlas's own, with only slashes and control characters replaced; the recorded pass's atlas had the same 56 names as before.
- **Not checked:** CodeQL and Scorecard runs (they need the repository public), the release build on the device, and the overlay refusing to end itself or ES-DE on the Nova (ES-DE can't launch itself; the same check covers all three).
