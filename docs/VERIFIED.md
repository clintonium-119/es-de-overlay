---
---

# Verified device facts

These are the developer's dated test notes, taken on one device while the app was being built. They aren't user documentation and aren't on the site. Older entries describe behaviour that has since changed; [Known limitations](limitations.md) and the other pages describe the app as it is now.

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
| A9 | Superseded | Gamepad combo through an AccessibilityService (the original plan's Phase 4). The menu opened on Retroid's Home message from 2026-10-29, and since 2026-11-03 opens on the controller's Back through a key filter; see [Back button menu](#back-button-menu). | — |

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
- **A game that loses focus while it's still on screen can look different.** RetroArch with `pause_nonactive` on pauses when another window takes focus, and a paused RetroArch stops drawing its shader and the dark sub-frames above, so the game looks brighter. In 1.3.2 the invisible menu that waits under a reopened guide took focus: RetroArch lost it 23 ms after that window was added and presented its last frame 34 ms later, about 125 ms before the viewer's window took focus. The waiting menu is now unfocusable as well as invisible (window alpha 0), and takes focus only once the viewer is up. An earlier entry here blamed the invisible window's display layer; the brightening was still there with that layer left out, and the check that had passed was made with the shaders off.
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

## Steering ES-DE with key presses (2026-11-18)

The first check of whether ES-DE can be walked to a game from outside, on the Nova with ES-DE 3.5.0 in front on Last Played, its custom event scripts, browsing events and Debug Mode on. Presses were sent one at a time over adb, each followed by the line ES-DE added to `es_log.txt`.

- **A press shows in the log in about 45 ms.** Eight D-pad presses (four down, four up) each logged a `game-select` line 35 to 68 ms after the press; four moves took 245 and 194 ms.
- **A lap of the systems took 1.66 s.** 37 presses of D-pad right went through all 38 lists and back to Last Played in 1,661 ms, 34 to 48 ms a line, none missed.
- **Presses sent in a burst are dropped.** Four downs sent in 18 ms and four ups in 45 ms logged two `game-select` lines for the eight presses, so each press has to wait for the line of the one before.
- **`input keyevent` moves ES-DE as a keyboard.** Down and Up (20, 19) logged `Keyboard: Key Down, isMappedTo=down` with the `game-select` line. Enter (66) logged `Key Return, isMappedTo=a` and opened a folder; Backspace (67) logged `Key Backspace, isMappedTo=b` and backed out: Enter and Backspace are ES-DE's A and B.
- **A system change logs only the new list's selected game**, with no line naming the list, so the switcher knows Last Played by its games turning up in order.
- **The first picks** (a release build of the prototype): Easyland from seven systems before Last Played, 16 presses, about 0.5 s from the pick to ES-DE's cursor on the game; Shantae from a far system, 44 presses, about 1.2 s; Super Mario World from one list past Last Played, a full lap, 50 presses, about 2.0 s. The user called it fast. Later builds learn the ring and go the short way; the times are under the steering pass below.

## How ES-DE moves on each layout (2026-11-18)

Read on the Nova from ES-DE 3.5.0's log with custom event scripts, their browsing events and Debug Mode on, one `adb shell input keyevent` at a time, each followed by the lines ES-DE added to `es_log.txt`. The user switched themes in ES-DE's menu: MinUI Menu (Overlay) for a text list, Analogue OS Menu's carousel and grid variants, and TechDweeb's carousel variant. Quick System Select was *Left/Right or Shoulders* throughout, apart from one check under *Left/Right*. Where a rule comes from ES-DE's source (tag `v3.5.0`), the file is named. Every key logs `Window::logInput(Keyboard): Key <name>, isMappedTo=<button>, value=1` and `value=0`; the table gives what came with it.

| Where | Key sent | What ES-DE logged |
| --- | --- | --- |
| Text list (MinUI) | Down / Up | `game-select` of the next / previous game; from the last game Down wraps to the first, and Up from the first to the last |
| Text list | Right / Left | `game-select` of the selected game in the next / previous system's list (Last Played to Arcade's `01) CPS I` folder and back); no line names the list |
| Text list | Page Down / Page Up | `rightshoulder` / `leftshoulder`; `game-select` ten games on / back |
| Text list | End | `righttrigger`; `game-select` of the last game |
| Text list | Backspace | `b`; `system-select "recent" "last played" "recent" ""`: the system view, on the list just left |
| Vertical carousel (Analogue OS) | Down / Up | `game-select` of the next / previous game |
| Vertical carousel | Right / Left | `game-select` of the next / previous system's selected game, as on the text list |
| Vertical carousel | Page Down / Page Up | `game-select` ten games on / back, stopping at the ends (Descent to Portal, the last; then to Turok) |
| Grid, 3 columns (Analogue OS) | Right / Left | `game-select` of the next / previous game in list order; Right at a row's end gave the next row's first (Ratchet & Clank 3 to Turok); Right on the last game wrapped to the first, Left on the first to the last |
| Grid | Down / Up | `game-select` a row on / back (Okami to Tekken 6 and back) |
| Grid, last row | Down | the key line only |
| Grid | Page Down / Page Up | `game-select` of the next / previous system's selected game |
| Grid | End | `game-select` of the last game |
| Grid, Quick System Select *Left/Right* | Page Down / Page Up | the key lines only |
| Grid, Quick System Select *Left/Right* | Right / Left | one game on / back, no system change |
| Horizontal carousel (TechDweeb) | Right / Left | `game-select` of the next / previous game |
| Horizontal carousel | Down / Up | the key lines only |
| Horizontal carousel | Page Down / Page Up | `game-select` of the next / previous system's selected game |
| System view, text list (MinUI, Analogue OS) | Down / Up | `system-select "<short>" "<full name>" "<ROMs folder>" ""` one system on / back |
| System view, text list | Right / Left | the key lines only |
| System view, text list | Page Down / Page Up | `system-select` several systems on / back (Atari Lynx to Nintendo 64 and back) |
| System view, text list | End | `system-select "recent" "last played" "recent" ""`: Last Played is the last system on the Nova |
| System view, text list, on Last Played | Enter | `a`; `game-select` of the game Last Played had selected |
| System view, horizontal carousel (TechDweeb) | Right / Left | `system-select` one system on / back; Right from Last Played wrapped to Arcade |
| System view, horizontal carousel | Down / Up, Page Down / Page Up | the key lines only |
| System view, horizontal carousel, on Last Played | Enter | `a`; `game-select` of the game Last Played had selected |

- **ES-DE's keyboard keys.** The Nova's `es_input.xml` has no keyboard entry, so ES-DE uses its built-in map (`es-core/src/InputManager.cpp`, `loadDefaultKBConfig`): the arrows, Enter for a, Backspace for b, Page Up and Page Down for the left and right shoulders, Home and End for the left and right triggers. `input keyevent MOVE_HOME` (122), sent twice, logged nothing, so the left trigger has no key that `input keyevent` can send; End (123) logged as the right trigger every time. Page Down and Page Up logged at every press; an earlier note of Page Down logging nothing didn't recur.
- **Which keys change system.** ES-DE gives left and right to a game list only when it is a text list or a vertical carousel or wheel; a horizontal carousel or wheel and a grid keep them for moving (`es-app/src/views/GamelistView.cpp`, `mLeftRightAvailable`). Quick System Select then picks the keys (`es-app/src/views/GamelistBase.cpp`, `getQuickSystemSelectLeftButton` and `…RightButton`): *Left/Right or Shoulders* gives left/right where they're free, else the shoulders; *Left/Right or Triggers* left/right, else the triggers; *Shoulders* and *Triggers* those keys on every layout; *Left/Right* left/right where free and nothing on a grid or horizontal carousel; *Disabled* nothing. The table above checks *Left/Right or Shoulders* on all four layouts and *Left/Right* on the grid.
- **The system view reaches every list.** Backspace from a game list's top level opens the system view and logs `system-select` for the list it left, whatever Quick System Select is. There each move logs the system it lands on by short name, Last Played as `recent`, and Enter logs the selected game of the list it opens. In a folder, Backspace goes up a folder first (`GamelistBase.cpp`); not checked on the device.
- **A press that doesn't move logs no game.** ES-DE skips `game-select` when the selected game is unchanged (`GamelistView::updateView`), as for Down in a grid's last row and Up or Down on a horizontal carousel. Showing a list logs its selected game afresh (`GamelistView::onShow`), as on a system change or Enter from the system view.
- **A stray system line.** Closing ES-DE's menu after a theme change reloaded the theme and logged `system-select "fbneo" …` while a game list was showing.
- **Lists keep their place.** Each list kept its selected game when left and entered again, by a system change or through the system view.
- **A key's line comes before its selection.** In every case above ES-DE logged a keyboard key's `value=1` line before the selection it made, so the switcher counts for a press only a selection after its own key line. A D-pad press reaching ES-DE between the switcher's reading the log and ES-DE handling the switcher's next key had, before that, been taken for the key's result, and every later press was read one step late.
- **A theme change logs `Finished loading theme "<name>"`** when ES-DE's menu closes, which is when the switcher now reads the theme again (copying a downloaded one) without waiting for a game launch.
- **Steering pass (2026-11-18 and 19).** Release builds on MinUI (text list), TechDweeb (horizontal carousel), Analogue OS Menu (vertical carousel and grid, the grid also under *Left/Right*): every pick from Last Played, from Arcade, from inside a folder and from the system view launched the picked game, in 136–788 ms. Last Played is the last of 38 lists here: the first walk from Arcade went round (46 presses, 1.3 s) and learned the ring, and the next went back one list (4 presses, 226 ms); from the Game Boy list, 20 on, it went 18 back. Under *Left/Right* on the grid it went through the system view, out of a folder with two Bs. D-pad presses sent during the walk launched the picked game once the switcher matched presses to their key lines.
- The user's theme (MinUI Menu (Overlay), `gamelist-list-screenshot`, `dark-bold`) and Quick System Select (*Left/Right or Shoulders*) were set back afterwards and read back from `es_settings.xml`.

## Save-state pictures (2026-11-19)

Read on the Nova over adb after the user made one save state in PPSSPP (Tekken 6) and one in ARMSX2 (DOA2 Hardcore), each launched from ES-DE. Nothing was sent to the device.

| Emulator | Where a state goes | Its picture |
| --- | --- | --- |
| RetroArch (`com.retroarch.aarch64`) | `<savestate_directory>/<core>/<ROM name>.state`, `.state1`…, `.state.auto`, the folder from `retroarch.cfg` (`/storage/emulated/0/RetroArch/states` here) | A PNG beside the state: `<ROM name>.state.auto.png` and so on. Named after the ROM, so two systems' games of one name meet only in different core folders |
| PPSSPP (`org.ppsspp.ppsspp`) | `<memory stick>/PSP/PPSSPP_STATE/<disc ID>_<version>_<slot>.ppst`; the memory stick is `/storage/7BEF-E760/ROMs/psp` here, and the app's own `Android/data` folder is empty | A JPEG beside the state, written with it: `ULUS10466_1.00_0.jpg`, 165 KB, at 11:11:55 between the launch (11:09:27) and ES-DE's `game-end` (11:12:03). Named by the disc, so the overlay ties it to the game by time |
| ARMSX2 (`com.armsx2`) | `<ROMs>/ps2/sstates/<serial> (<CRC>).<slot>.p2s`, e.g. `SLUS-20071 (23AF6876).00.p2s`; the state it replaced is kept as `.p2s.backup` | No file beside it. The `.p2s` is a zip whose `Screenshot.png` sits among the memory dumps; `unzip -p <state> Screenshot.png` through the shell gives it (640×480, 545 KB) in about 40 ms. The folder is `Savestates = sstates` in `PCSX2-Android.ini` at ARMSX2's data root (`<ROMs>/ps2` here), which also has `SaveStateOnShutdown = false` |

- **ES-DE sets a game's `lastplayed` as the game ends.** Dr. Mario: `game-start` at 10:35:18, then `setPlayMetadata(): Play time was 23 seconds`, `game-end` and `<lastplayed>20261119T103541</lastplayed>` at 10:35:41. A state saved in a session is written before the game's last-played time.
- **Finding PPSSPP's memory stick and ARMSX2's settings.** PPSSPP keeps its memory stick's location in its private files, so the overlay looks for a `PPSSPP_STATE` folder, and for ARMSX2's `PCSX2-Android.ini`, four folders deep under internal storage and the SD card, plus both apps' own `Android/data` folders. That walk took about 7 s on the Nova (an earlier 1.5 s reading was wrong), 3.8 s of it in `RetroArch` (28,214 entries, mostly thumbnails) and 2.7 s in `ROMs`. The first build looked as ES-DE came back and held up the overlay's polling for 10.9 s; it now skips `RetroArch` (3.1 s), runs on its own thread when PPSSPP or ARMSX2 starts a game, and the folders are kept in the app's settings, looked for again only when one is gone.
- **`screencap` through the shell.** 1280×960; raw output (a 16-byte header of width, height, format 1 = RGBA_8888 and colour space, then the rows) took about 165 ms, `-p` (PNG) about 210 ms, of a still screen.
- **Resuming from a state.** RetroArch here has `savestate_auto_load`, `savestate_auto_save` and `savestate_thumbnail_enable` all `"true"`: quitting inside RetroArch wrote `Dr. Mario.state.auto` and its PNG (11:23:38, again 11:38:57), and the next launch starts from that state. Quit Game's force-stop writes none, so the game starts from the older auto state, not where it was left. PPSSPP's `PSP/SYSTEM/ppsspp.ini` has `AutoLoadSaveState = 2` and `StateSlot = 0`; what 2 selects wasn't checked in PPSSPP. So the switcher shows the picture of the state the emulator will start from over a newer picture kept at quit (the user's choice, 2026-11-19).
- **Keeping a picture at Quit Game** (first build of the pass). Four Quit Games went from `quit: ending` to `force-stopping` in 192–200 ms: a 100 ms wait for the game to draw with the keys back, then 87–99 ms reading the screen raw through Shizuku. The pictures' means were 160, 25, 25 and 44 of 255, none near black, so no second shot was taken: no dark shader frame turned up in a capture of RetroArch with the keys back. With the setting off, `force-stopping` came in the same millisecond.
- **Home.** With Home set to end games, a picture read as Home went down (`KEYCODE_MOVE_HOME`) took 87 ms and showed Shantae as played, with no ES-DE, toast or menu in it; the game was ended about 1 s later. Whether Retroid's *Prevent pressing Home accidentally* was on wasn't checked.
- **Brightness.** The user judged Shantae's kept picture, shader on, to look like the game. Super Mario World's Valley of Bowser flashes in the background, so a picture of it is a poor check.
- **Every emulator in ES-DE's Emulators list** (2026-11-19, the user saving one state in each that has them, read over adb). RetroArch and PPSSPP write a plain picture beside each state, and the switcher uses both. ARMSX2 packs it as `Screenshot.png` inside the `.p2s`, and the user asked for it to be used too. Dolphin wrote only `StateSaves/GFZE01.s00` (F-Zero GX), PrimeHack only `StateSaves/R3ME01.s00`, Azahar only `<ROMs>/n3ds/states/0004000000054000.00.cst`, melonDS Nightly only `Sonic Rush.ml1` beside the ROM: nothing beside any of them, and nothing new in their `Android/data` folders. hakuX (xemu-based) wrote nothing to shared storage while Doom 3 ran and a state was saved, so its states are in its private storage. Eden, Cemu, PICO-8 and GameNative have no save states.
- **A dark Quit Game picture.** The user's RetroArch shader is `subframe-bfi/crt-beam-simulator.slang` then `crt-gdv-mini-ultra`, at 120 Hz with `video_shader_subframes = "2"` and `pause_nonactive = "true"`. Pictures read 100 ms after the menu gave RetroArch back the keys came out dark (mean 25 of 255, twice, at Valley of Bowser) and, to the user, with some colours inverted; the one read at Home's press, mid-play, looked right. So the screen is now read as the menu opens, before RetroArch pauses, and kept if Quit Game is picked.
- **Pictures the overlay can't reach.** The user sees state pictures in the menus of Dolphin, PrimeHack, melonDS and hakuX; Azahar shows none, and Cemu, Eden, GameNative and PICO-8 have no save states. No new picture turned up in any of their `Android/data`, `Android/media` or `Android/obb` folders either. None is in shared storage or inside the state: Dolphin's `GFZE01.s00` (header `Dolphin 2609b`) has no PNG or JPEG signature, and upstream Dolphin's `Source/Core/Core/State.cpp` writes only a header and an LZ4 payload (and a `.dtm` for movies); melonDS-android's `SaveStateScreenshotProvider` writes `context.filesDir/ss_screenshots/<ROM URI hash>/<slot>.png`; hakuX wrote nothing to shared storage. `ls /data/data/<package>` as the shell user, which is what Shizuku runs as, gave "Permission denied" for all three, so their pictures need root or the emulator's help.
- **Wide state pictures.** RetroArch saves a state's picture at the frame's own size, with no aspect: Nestopia with `nestopia_blargg_ntsc_filter = "composite"` gives 602×224, which the switcher showed at about 2.7:1. RetroArch here shows games at the core's aspect (`aspect_ratio_index = "22"`), 4:3 for NES. A picture over twice as wide as tall is now shown at 4:3; Gambatte's 160×144 and Snes9x's 256×224 are shown as they are.

## RetroArch hotkeys (2026-11-19)

Checked on the Nova with two release builds of the `game-switcher` branch (APK sha256 `04c1e3c2…5f02`, then `c4cd8173…422f` with a 50 ms tap and a 100 ms wait for clean frames), Easyland (SNES, Snes9x) and Aladdin (Genesis Plus GX) in RetroArch, and Sonic Rush in melonDS Nightly. The user gave capture consent, launched the first games and judged the pictures; the overlay was driven with `sendevent`. Every check below ran on both builds; times are the second build's unless both are given.

- **What the overlay reads at a launch.** From `retroarch.cfg`: `input_enable_hotkey_btn = "109"` (Select), `input_shader_toggle_btn = "97"`, `input_exit_emulator_btn = "108"`, `input_hotkey_block_delay = "5"`. From `dumpsys input`, the first device whose classes include GAMEPAD: `/dev/input/event7` ("Xbox Wireless Controller"), key layout `/system/usr/keylayout/Generic.kl`, which gives scan codes 314, 305 and 315 for Select, B and Start. So Shader toggle is 314 held with 305 tapped (the printed A), and Exit 314 with 315. The node is `crw-rw---- root input`, and the shell user is in `input`. Select is held 166 ms, twice the block delay at 60 fps. One `sendevent` takes about 5 ms.
- **Capture.** Read 1.5 and 2 s into a capture, the screen had no mask; the screenshot and the 15 s clip came out without it. The clip's brightness stayed between 113.3 and 113.7 over 446 frames, none darker than 60 % of the frame before; Easyland's earlier clip, taken with the shader on, swung between 16 and 160. A read after the capture showed the mask again (one read in two catches the beam simulator's dark or red-and-blue sub-frame). The user judged the screenshot and the clip to look like the game without its shader.
- **Quit Game.** RetroArch quit 332–434 ms after Exit on the two builds, having written `Easyland.state.auto` and its picture; it wasn't force-stopped. The switcher showed that picture with *Saved*, and the next launch from the switcher started at the same moment. `video_shader_enable` stayed `"true"` in the config RetroArch saved as it quit.
- **The read as the menu opens.** With Auto Save State on, the log had `menu: no screen read, RetroArch saves its state on exit` and the menu opened in 80–99 ms. With it off (set in `retroarch.cfg` while RetroArch was closed, and set back), the menu over Aladdin opened in 919–1035 ms on the first build and 793–806 ms on the second, against 80–120 ms otherwise; the mask was back once the menu closed. Quit Game then kept that picture: the switcher showed Aladdin with *Left*, in the game's colours, where the picture kept earlier with the shader on was dark and masked. The user judged it to look like the game.
- **The waits.** Presses 300 ms after the menu closed took effect every time, and reads 100 ms (second build) and 150 ms (first) after the toggle were clean.
- **Unchanged paths.** Home over Easyland still ended RetroArch by force-stop (`Force stopping com.retroarch.aarch64`), with no auto state written. Quit Game over Sonic Rush still force-stopped melonDS at once; the read as its menu opened took 87–89 ms and the menu 111–120 ms.
- **Select reaches the game.** RetroArch passes the hotkey-enable button to the game for its block delay before blocking game input, so each press the overlay makes gives the game a few frames of Select, as a press by hand does. On Easyland's map the first Shader toggle opened the game's *Continue and save* prompt, which then showed in the capture, and each later press moved its cursor. The user accepted this; a Hotkey Enable that games don't use avoids it.
- **Not checked:** another device's controller node, `quit_press_twice`, a game left during a capture (the shader goes back on when RetroArch is in front again, or before Exit), and the fallbacks to force-stop with Exit unbound or Shizuku off.

## The switcher after its games (2026-11-19 and 20)

Checked on the Nova with five release builds of the `game-switcher` branch, the last APK sha256 `742f3e89…1c95`, with Aladdin (Genesis Plus GX) and Easyland (SNES, Snes9x) in RetroArch. The overlay was driven with `sendevent`; the user switched Home ends games off and on. Times are the last build's unless said.

- **Back to the switcher.** After a game launched from the switcher ended with Quit Game or RetroArch's own Exit, or was left with Home (Home ends games on, RetroArch ended; or off, RetroArch left running), the switcher was open over ES-DE 0.12–0.13 s after Android resumed ES-DE, on the game just played, with Last Played under it. The overlay looks the moment ES-DE's window comes to the front, which its accessibility service now hears, and again 0.4 s later. The first builds took about 1.9 s: they waited for the once-a-second poll and the reread of Last Played.
- **When ES-DE logs the game's end.** ES-DE logged a game's end from 0.07 to 0.57 s after it resumed, having written the game to its gamelist just before. The switcher opens on whichever comes first, ES-DE in front after the game was up or that logged end; the reread waits for the end. One build opened only at the end, and took 1.3 s when it was late; the next opened only on ES-DE in front, and missed an end logged before it.
- **Updating.** The switcher opens on the list as read before the game, with the game just played first, played now. That game showed its old picture with *Updating* in the middle until the reread (640–915 ms) and the new picture's read were done, then the new picture and its detail line; over 12 screenshots 0.15–0.27 s apart it never went black. The reread's parts: the gamelist search 142–212 ms, parsing it 302–368, the file check 7–108, the theme 30–79, the state pictures 31–141.
- **Not after a hand launch.** Aladdin launched from ES-DE's own list and then quit, or left with Home, left ES-DE on its list; 6 s later there was still no switcher. A press that comes while ES-DE's screensaver is up only ends the screensaver.
- **A launch over a running RetroArch.** With RetroArch left running with one game, a launch of another from ES-DE brings RetroArch's task forward, and RetroArch finishes its activity and calls `System.exit(0)` within 60 ms, without loading the new game or saving the old one: the old game's auto state wasn't written (Aladdin's stayed at 21:27:24, Easyland's at 10:18:25 through every retry). What shows is ES-DE's own launch screen, then its list. ES-DE logs nothing more; it takes the game to be running until the next key, which logs the game's end (the idle time counted as play) and is acted on as well, so a second press launches the game.
- **The retry.** Picking Aladdin from the switcher with Easyland running: RetroArch quit at 14:43:09.165; watching each quarter second, the overlay saw the game hadn't come up and RetroArch's process was gone, and pressed launch again at 09.735 (0.57 s; 0.65 and 0.71 s on the builds before, 1.9 s when it checked once a second); a new RetroArch came up with Aladdin from its auto state. The failed launch's end, logged at that key, didn't reopen the switcher; ending the retried Aladdin did. The first build gave no retry (it waited for an end ES-DE doesn't log), reopened the switcher over ES-DE's launch screen at the end logged by the hand press, and left that switcher up over the game; later builds fix all three.
- **Not checked:** a press by hand in the moment before the retry, and a retried launch that fails again (the overlay doesn't press a third time).

## Screensaver, Home over RetroArch and picture shapes (2026-11-20)

Checked on the Nova with one release build of the `game-switcher` branch, APK sha256 `b8c0cd61…381b`, ES-DE's screensaver on dim after 1 minute, RetroArch 1.22.2 with its aspect on Core provided. The overlay was driven with `sendevent`; the user switched Home ends games on.

- **The switcher and ES-DE's screensaver.** ES-DE hears no presses while the switcher is up, so its screensaver can start behind it. Back from Easyland, ES-DE logged the game's end at 16:59:24 and its screensaver start at 17:00:24; the switcher, open and untouched since 16:59:24.08, closed at 17:00:25.25 and ES-DE's dim showed. A D-pad press ended the screensaver without moving ES-DE's cursor, and 5 s later the switcher hadn't reopened; Back opened it 0.14 s after the press. Kept in use (a press every 10 to 20 s) across ES-DE's screensaver start at 17:01:37, the switcher stayed open, and a pick at 17:02:01 steered ES-DE (its first press ended the screensaver) and launched Easyland 0.53 s after the pick.
- **Home over RetroArch.** With Home ends games on, the overlay keeps Home from Android over a RetroArch game and presses RetroArch's Exit as Home lets go. Over Easyland it was taken 0.03 s after the press; RetroArch exited 390 ms after Exit and wrote the auto state and its picture 0.67 s after the press, ES-DE came back and the switcher reopened on the new picture. With the menu open the same: the menu closed, RetroArch exited 381 ms after Exit, the state was written 0.67 s after the press. The force-stop after Exit, never needed here, is kept to the session quit by the code: it waits only while RetroArch stays in front and stops at another logged launch.
- **Home as before.** With Home ends games off, Home over Easyland went to Android, RetroArch kept running and no state was written. Over Tekken 6 in PPSSPP with it on, Home went to Android, a picture was kept and PPSSPP was ended.
- **Picture shapes.** RetroArch's state pictures are the core's frames as they are; the switcher now shows each at the shape RetroArch shows the game, from retroarch.cfg, the core's, folder's and game's overrides, and the core's own aspect option. Easyland's 256 × 224 frame (Snes9x, 4:3) showed at 4:3 with no bars at the sides; Aladdin's 320 × 224 (Genesis Plus GX, auto) at 1.306; NES Dr. Mario's 602 × 224 (Nestopia's NTSC filter, auto) at 1.316; Game Boy Dr. Mario and Shantae (Gambatte) kept 10:9. Scraped images (Super Metroid, DOA2) and PPSSPP's picture (Tekken 6) showed as before.
- **Cost.** Reading RetroArch's aspect settings with the state pictures took the reread's state step from 129–141 ms to 333–510 ms.
- **Not checked on the device:** the aspect settings other than Core provided, PAL games, and the cores without state pictures on the Nova (their sums are unit-tested against each core's source).

## The full pass, first build (2026-11-20)

Run on the Nova with a release build of the `game-switcher` branch, APK sha256 `91bfdd78…643b`, ES-DE 3.5.0 on MinUI Menu (Overlay) with Quick System Select on *Left/Right or Shoulders*, the Game Switcher section at its defaults to start. The overlay and ES-DE were driven with `sendevent` and `input keyevent`; the app's settings were tapped over adb.

- **Switch off.** The menu button over ES-DE logged `Button 4, isMappedTo=back` in ES-DE's log and opened ES-DE's own Gamelist Options; no switcher opened.
- **Switch on.** Every line of the checklist was ticked, and the switcher opened in 11–17 ms each time.
- **Three picks, each launching the picked game per ES-DE's `game-start` line:**
  - Super Mario World in One Game a Screen, from Last Played: 8 presses, launching 331 ms after the pick.
  - Aladdin in the strip, from the Wii list, 15 lists on from Last Played: 21 presses, 699 ms.
  - Sonic Rush (melonDS Nightly) in the strip, from the system view five systems up from Last Played: 10 presses, 390 ms.
- **Quit Game over RetroArch.** RetroArch exited 392 ms (Super Mario World) and 380 ms (Aladdin) after Exit, having written the auto state and its picture, and the switcher was open on the game about 0.1 s after that. ES-DE's window had the keys for about 0.12 s first, and ES-DE logged the game's end in the same second.
- **The pictures.** With the detail line on, eight of the ten games read *Saved* (RetroArch's, Tekken 6 in PPSSPP, DOA2 in ARMSX2), Sonic Rush read *Left*, and Bionic Commando showed its scraped image with no label. Quit Game over Sonic Rush kept the picture read as the menu opened (19 ms to store it) and the switcher showed it with *Left*.
- **Fault: ES-DE paused under the reopened switcher.** After Quit Game force-stopped melonDS, the switcher opened 0.13 s later, and Android's focus log shows the keys going to it without ES-DE's window ever having had them. ES-DE logged nothing more: *Updating* stayed up, and ES-DE logged the game's end only when the switcher was closed 58 s later, counting 87 s of play for about 29. This fits SDL's Android activity starting its loop again only once its window has focus (an inference; ES-DE's source wasn't read for it). Android reports ES-DE as the app in front before its window has the keys, and after a force-stop that gap was longer than the overlay's wait.
- **Fault: a setting turned off inside ES-DE wasn't noticed.** With Browsing Custom Events turned off in ES-DE's menu, the switcher still opened: the overlay read ES-DE's settings again only when ES-DE next came to the front. After that, its log had `ES-DE's settings lack [BROWSING_EVENTS]`, the menu button stayed ES-DE's, and the settings line read *Not ready: Browsing Custom Events*. Turned back on, it was noticed within a second. ES-DE logs `settings-changed` when its menu saves, and the overlay now reads the settings again at that line.
- **Not checked:** turning off Enable Custom Event Scripts or Debug Mode inside ES-DE, which the second change doesn't cover, as ES-DE then logs no `settings-changed` line.
- ES-DE's settings were read back afterwards: Browsing Custom Events on again, theme, variant, colour scheme and Quick System Select as before.

## The full pass, second build (2026-11-20)

The same pass on the next build, APK sha256 `e9c3b027…e2c0`, which read ES-DE's settings again at its `settings-changed` line and held the reopened switcher until the accessibility service heard ES-DE's window.

- **The scenario held.** The menu button stayed ES-DE's with the switch off; Super Mario World launched from Last Played (2 presses, 164 ms), Aladdin from the Wii list (18 presses, 585 ms) and Sonic Rush from the system view (8 presses, 323 ms); RetroArch exited 381 and 383 ms after Exit; the three kinds of picture showed as before.
- **A setting turned off inside ES-DE.** ES-DE logged `settings-changed` at 20:01:28 as its menu closed with Browsing Custom Events off; the overlay logged `ES-DE's settings lack [BROWSING_EVENTS]` at 20:01:29.09, and the menu button at 20:01:32 opened ES-DE's Gamelist Options, without ES-DE having left the front. Turned back on, it was noticed at the next `settings-changed`.
- **The window event comes too early to wait on.** After Quit Game force-stopped melonDS, ES-DE logged the game's end within a second, with 23 s of play, and *Updating* cleared. But the order was close: ES-DE's activity resumed at 19:58:51.767, the overlay reopened the switcher at .864, ES-DE's window took the keys at .869 and the switcher's took them at .893. The accessibility event the overlay waited for comes as ES-DE's activity resumes, about 0.1 s before its window has the keys, so the switcher got in behind ES-DE by 5 ms. The overlay now waits for ES-DE to log the game's end, reading its log each tenth of a second once ES-DE is back in front.


## The full pass, third build (2026-11-20)

The same pass on the build that waits for ES-DE's logged game end, APK sha256 `7f756240…8efe`, with a fourth pick added as a second force-stopped emulator. The user accepted the switcher on this build.

- **The scenario held.** The menu button stayed ES-DE's with the switch off; the switcher opened in 9–14 ms; Super Mario World launched from Last Played in One Game a Screen (1 press, 133 ms), Aladdin from the Wii list in the strip (18 presses, 612 ms), Sonic Rush from the system view (8 presses, 317 ms) and Tekken 6 (PPSSPP) from Last Played (4 presses, 239 ms). Eight detail lines read *Saved*, Sonic Rush *Left*, and Bionic Commando none.
- **Back after RetroArch.** RetroArch exited 386 and 385 ms after Exit; ES-DE had logged the game's end and the switcher was open 76 and 96 ms after that.
- **Back after a force-stop.** After Quit Game over Sonic Rush, ES-DE's window took the keys at 20:40:10.631, ES-DE logged the game's end with 23 s of play, and the switcher opened at 20:40:11.200, 0.59 s after the overlay pressed Home. After Tekken 6: keys to ES-DE at 20:42:37.526, 28 s of play logged, the switcher open at .616, 0.10 s after Home. No *Updating* label was left either time.
- **A setting turned off inside ES-DE.** ES-DE logged `settings-changed` at 20:40:52, the overlay `ES-DE's settings lack [BROWSING_EVENTS]` at 20:40:53.60, and the menu button at 20:40:56 opened ES-DE's Gamelist Options; the settings line read *Not ready: Browsing Custom Events*.
- **Not checked:** a cleared install as the start (the Game Switcher section was set back to its defaults by hand), a picture kept with RetroArch's Quit hotkey off the controller, and carousel or grid themes on these three builds.
- ES-DE's settings were read back afterwards and matched the start of the session.

## Upright arcade games in the switcher (2026-11-25)

Read on the Nova from the files of a game the user had played, RetroArch 1.22.2 with FinalBurn Neo.

- **RetroArch saves an upright game's state picture unturned.** `RetroArch/states/FinalBurn Neo/1941.state.auto.png` is 384×224 with the game on its side; turned 90° left it reads upright. ES-DE's scraped screenshot of the same game (`downloaded_media/fbneo/screenshots/01) CPS I/1941.png`) is 224×384. The switcher showed the state picture at FinalBurn Neo's 4:3, so it filled the screen sideways.
- **Which way to turn isn't in any file.** 1941 turns left (MAME's ROT270), Galaga right (ROT90), and RetroArch keeps no record of the core's rotation beside the state. Comparing each turn with the scraped screenshot, both shrunk to 14×24 in grey, gave a mean difference of 0.149 against 0.145, too close to choose by.

A first build, release APK sha256 `2eeebc6c9d841e1a99a05d5990d9518187e8553ac570363e15b03aac9a50751c`, which read the screen just before Exit and turned the picture by the tilt sensor, was tried on the Nova with the shaders on (crt-beam-simulator then crt-gdv-mini-ultra, 2 sub-frames), games picked in the switcher:

- **RetroArch's own *Quit* hotkey goes past the app.** 1941 quit with Select + Start showed the sideways state picture again: RetroArch closed with no screen read, and the user quits that way by habit.
- **Home and Quit Game read the screen clean.** 1941 left with Home, held normally, kept a 1280×960 read trimmed to 721×960 upright, with no shader marks. Quit Game's read, though taken just after the menu hands RetroArch back the keys, came out bright (mean 56 of 255), not dark.
- **Android turns the screen for RetroArch held in portrait, and `screencap` follows.** Played that way, the read was 960×1280 at rotation 0, and Android turned the screen back to rotation 1 as RetroArch closed. ES-DE asks for `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, so it shows at rotation 1 or 3.
- **The tilt sensor added nothing.** In landscape the readings had the device tilted back to within 3.4 to 4.7 m/s² of flat, around the 4 m/s² flat limit. Once the user asked for portrait play to show on its side in the switcher, how Android had the screen turned was all that was needed, and the build after this one reads no sensor.
- **Quitting took longer.** The read added 759, 954 and 753 ms before Exit for Home, and 1142 ms for Quit Game, after the 300 ms wait for RetroArch to get the keys back that came before. The read itself took 90 to 134 ms; the rest is the two shader toggles and the 100 ms wait for a frame without the shader.

A second build, release APK sha256 `42b5a08e4348f9839201c679beb4936395ffdf77bcf86b9906d4b3e919f34137`, took the *Quit* hotkey and turned the picture by the screen's rotation instead. It was tried the same way:

- **The *Quit* hotkey is taken.** 1941 quit with Select + Start logged `exit: taken`, a shader-free read (mean 55) and the quit screen in the switcher, upright, 721×960 after trimming.
- **Home, Quit Game and Super Mario World show the screen at quit.** 1941 left with Home and with Quit Game showed upright at the moment it was quit, with no shader marks and no menu in the picture. Super Mario World showed its quit screen at 1280×960, the right way up.
- **Portrait play shows on its side.** 1941 played with the Nova in portrait logged `960×1280 at rotation 0, ES-DE's 1, turned 270°`. The picture filled the switcher with the game's top towards the left edge, and the user confirmed it read the way they had played it.
- **The switcher first showed the state picture.** Back in ES-DE, each quit's first picture of the game was the state's (1941 on its side, 384×224). It was read against the state list from before the game, which had no state from that quit yet. The kept picture replaced it about a second later, when the list was read again. The build after this one counts a picture kept after the list was read as being from the same quit.
- **The game shows for about a second before RetroArch closes.** Over Quit Game, the user saw the menu close to the game, then the quit. The read added 751 to 859 ms before Exit, after the 300 ms wait. A window can't be on screen and also left out of `screencap`'s read, so the build after this one shows a *Quitting…* notice that is hidden for the read.

A third build, release APK sha256 `d90a1aa4865ade147431ae37e00d7bcf13085bcf822758514a2f2cc961663e35`, was tried the same way on 2026-11-26:

- **The kept picture shows at once.** After the *Quit* hotkey, Home, Super Mario World and portrait play, the switcher's first picture of the game, 1 to 13 ms after it opened, was the screen at quit, as the log shows.
- **The notice shows.** The user saw *Quitting…* during the quit, and asked for it in the middle of the screen, saying *Capturing Screenshot* until the read and *Quitting* after. The build after this one does that.

A fourth build, release APK sha256 `c6bb1fab2e046d71eea326c9155490d3c5c06f8257174fa711807f86748f7720`, went through the same five quits and a second Quit Game:

- **Every quit passed.**
  - The *Quit* hotkey was taken.
  - Every read was shader-free.
  - Portrait play was turned 270°.
  - On each return, the switcher's first picture of the game was the screen at quit, 7 to 13 ms after it opened.
  - The read added 751 to 856 ms before Exit, after the 300 ms wait.
- **The notice reads as asked.** The user saw *Capturing Screenshot*, then *Quitting*, in the middle of the screen. The switcher's picture of 1941 after Quit Game has no notice in it.
