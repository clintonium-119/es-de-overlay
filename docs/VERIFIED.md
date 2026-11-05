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
- **Text guides.** Shantae's GameFAQs guide, saved as one text section on 2026-11-02, opens in 7 sections; a `.txt` (Solaris) in 15. A `<pre>` doesn't draw a final line break, so each section ending in blank lines showed one fewer at first; with the final break kept, every line sits on the same 30 px pitch across section boundaries. A section shorter than a quarter of the screen makes the bar name the one after it, as on web guides.
- **The viewer's WebView** has no devtools socket: only the browser turns WebView debugging on, and only in its own process run.
- **Web search.** In the Nova's WebView, Brave Search and Mojeek asked to prove the browser wasn't a bot and Bing opened on a cookie banner; DuckDuckGo showed results. From a desktop with no scripts, Brave answered with results and DuckDuckGo's plain page with a challenge, so the WebView decided it.
- **Saving from other sites.** IGN's Shantae guide offered Save guide and listed its three pages. Its Player's Guide page, mostly lists, fails Reader View's `isProbablyReaderable`, so a picked page is read regardless. Two page loads of the save came 2.15 s apart. The page list's rows were 38 px until set to the bar's 48 dp: a single-line `TextView` resets its minimum height to one line.
