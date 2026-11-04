# ES-DE Overlay

Android app for the Retroid Pocket Nova (Android 13) that runs next to [ES-DE](https://es-de.org).
During a game launched from ES-DE, the controller's Back button opens a menu over the game, laid
out like the `minui-menu-es-de-custom` theme's gamelist: the game's name above the rows and its
ES-DE screenshot where the gamelist shows it. The rows, top to bottom:

- **Resume** closes the menu and returns to the game.
- **Guides** lists the game's guides (see [Guides](#guides)), with **Find Guides** as the last row.
  A game with no guides shows **Find Guides** here instead, which looks the game up on StrategyWiki
  and VGMaps, and opens GameFAQs, Neoseeker and ZeldaDungeon in a touch browser over the game. It
  saves the guide you pick next to your own and opens it.
- **Manual** opens the game's scraped ES-DE manual.
- **Game Info** shows the game's developer, publisher, release date, genre, players and description
  from ES-DE's gamelist.
- **Capture** saves a screenshot, a 15-second clip, or both, straight into ES-DE's media folders,
  named exactly the way ES-DE expects, so they show up in the gamelist with no renaming:

  ```
  <ES-DE>/downloaded_media/<system>/screenshots/<ROM subfolders>/<ROM name>.png
  <ES-DE>/downloaded_media/<system>/videos/<ROM subfolders>/<ROM name>.mp4
  ```

- **Quit Game** ends the game's app and returns to ES-DE.

Back again closes the menu and returns to the game; Home goes to ES-DE as usual. System screenshots
(power + volume down) taken during a game are filed the same way as captures.

## Build and install

Needs JDK 17 and the Android SDK (platform 36).

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup (once)

Open **ES-DE Overlay** (ES-DE also lists it under Android Apps). Its sections run Service,
Folders, Capture, Screen, Guides (once the ES-DE folder is set) and Permissions; set it up from
Folders down, then start it under Service:

1. **ES-DE data folder → Set Folder**: the ES-DE folder that holds `downloaded_media`, `gamelists`
   and `logs`. Open the SD card (or internal storage), choose `ES-DE`, *Use this folder*, *Allow*.
   The row warns *No ES-DE log here* when the folder has no `logs/es_log.txt`.
2. **ROMs folder → Set Folder**: the folder that holds your system folders (`snes`, `gbc`…).
   Android doesn't allow picking the card's root, so these are two separate picks. The row warns
   *No ES-DE system folders here* when none of its folders is a system in ES-DE's `gamelists`.
3. Optional, under Capture: **Screenshot media type** lists ES-DE's image types (default
   Screenshots); pick the one your theme shows in the gamelist, so captures show there and in the
   menu. **Clip length** runs from 5 to 60 s in 5 s steps (default 15 s). When the media file
   already exists, **Replace** saves over it and **Skip** keeps it and drops the new capture.
4. Under Screen: **Turn the screen off when ES-DE is idle**. ES-DE keeps the screen on while it's
   in front, even behind its screensaver, so a handheld left in ES-DE stays lit until the battery
   runs out. With this on, the app locks the screen like the power button once ES-DE's screensaver
   has run for **After the screensaver has run for** (At once, then 1 to 30 min; default 9). It's on
   by default when ES-DE is the Home app. ES-DE has to report its screensaver: in ES-DE's *Other
   Settings*, turn on *Custom Event Scripts* and *Debug Mode*; the row says so while they're off.
   It works through the Back button menu (step 7) and needs usage access (step 6), with or without
   the service started.
5. Optional, any time: **Saved guides → Manage Guides** lists the guides you've saved, by game,
   and deletes the ones you no longer need (see [Guides](#guides)).
6. **Enable** the permission rows: notifications, game sound in clips, read screenshots, display
   over other apps, usage access, and ignore battery optimization. Each row reads *Enabled* or
   *Disabled*, and its button opens where that changes. When Android no longer shows a
   permission's dialog, **Enable** opens the app's App Info page instead.
7. **Back button menu → Enable**, then turn on *ES-DE Overlay* in Android's accessibility
   settings. Android hands it each controller button before the app in front gets it; it keeps
   only Back, and only during a game ES-DE launched, a capture or the open menu. For Quit Game it
   presses Force stop on the game's App Info page in Settings. After updating from a version that
   opened the menu with Home, turn it on again: the row reads *Disabled* until you do. Retroid's
   *Prevent pressing Home accidentally* isn't needed.
8. **Start Service → Start now.** One consent covers every capture until the service stops.
   Optional: **Offer to start after boot** posts a *Tap to start ES-DE Overlay* notification after
   a restart.
9. Optional: **Quick settings tile → Add** puts an *ES-DE Overlay* on/off tile in the swipe-down
   shade, and the row reads *Added* afterwards. Turning the tile on shows the consent dialog and
   then returns to whatever you were doing.

## Use

- Launch a game from ES-DE and press **Back** on the controller. The menu opens on **Resume**; the
  D-pad moves, **A** opens, **B** closes it and returns to the game, as Resume does. The cursor stops
  at the first and last rows. **B** in any screen opened from the menu comes back to the row that
  opened it. **Back** again closes everything the menu has open, a guide or the browser too, and
  returns to the game. Home goes to ES-DE and leaves the game running. A row too long for the list
  scrolls sideways a second after it's selected, as in ES-DE's gamelist.
- The game's name sits above the rows in the theme's large size, and its ES-DE image (the type
  picked under **Screenshot media type**, PNG or JPG) fills the theme's art box on the right, with
  pixel art kept sharp. A game with no screenshot shows an outlined box reading `No screenshot`.
  The menu doesn't play the game's video.
- **Capture** opens **Screenshot**, **Video** and **Screenshot and Video**, with the cursor on
  Screenshot. Picking one closes the menu, then saves only what you picked: Screenshot takes the
  picture and stops; Video and Screenshot and Video record the clip. Press Back during the clip to
  stop early; leaving the game also stops and saves. A toast and a notification name the saved
  files, e.g.
  `Saved: snes/screenshots/02) Hacks/Easyland.png, snes/videos/02) Hacks/Easyland.mp4`.
- **Manual** only appears when the game has one, and **Game Info** only when ES-DE's gamelist has at
  least one of its fields for the game. Game Info is read-only; Up and Down scroll a long
  description a line at a time. A file that won't open shows `Couldn't open <name>` in the menu;
  **B** goes back.
- **Guides** lists the game's guides by name, then **Find Guides**. **X** on a guide asks
  `Delete "<name>"` or **Keep**; Delete removes the file (and the game's guides folder once it's
  empty) and lists the guides again, or returns to the main menu, now showing Find Guides, when none
  are left. Keep and **B** leave it.
- **Find Guides** checks VGMaps and StrategyWiki for the game and lists the ones that have it, e.g.
  `VGMaps: 56 Maps` and `StrategyWiki: Shantae (2 Pages)`. Picking one saves it with progress in the
  menu (**B** cancels and saves nothing) and then opens it; the main menu shows Guides from then on.
  Picking a source again replaces the saved file. Without a connection the menu says
  `Couldn't reach the guide sources`.
- Below those, Find Guides always lists **Search GameFAQs** and **Search Neoseeker**, plus
  **ZeldaDungeon** for a Zelda game it has a walkthrough for. These sites are only contacted once
  you pick one. Each opens a browser over the game, used by touch, at the site's search for the game
  (ZeldaDungeon at the game's walkthrough). Its bar has **Back**, the page title, **Save guide** on a
  guide's page, and **Close**; tapping a text field brings up the keyboard. The first visit to a site
  may show a security check: it either clears by itself or asks you to tap "Verify you are human".
- **Save guide** saves the open guide. A text guide saves at once. A guide split into pages saves
  by loading each page in turn in the same browser, at least 2 seconds apart, with
  `Saving page 3 of 8, image 12 of 40` in the bar. If a page shows a security check, the bar says
  `Clear the check to continue` and the save waits until you have. **Cancel** stops and saves
  nothing. When it's done, the browser closes and the guide opens; closing it shows the Guides list
  with the new guide. Closing the browser without saving returns to the Find Guides list.
- The viewer works like ES-DE's own manual viewer:

  | | Zoomed out | Zoomed in |
  |---|---|---|
  | D-pad left/right | previous/next page | pan |
  | D-pad up/down | — | pan |
  | L1 / R1 | zoom out / in | zoom out / in |
  | L2 / R2 | first / last page | reset the zoom |
  | B | close | close |

  Text guides scroll with the D-pad (left/right by a page) and jump with L2/R2. Saved web guides
  (`.html`) scroll the same way, L1/R1 change the text size and L2/R2 move between sections. **Y**
  shows their contents over the guide, with the section in view picked: the D-pad moves, **A** jumps
  to the section, and **B** or **Y** closes the list. Otherwise **B** first steps back through
  tapped links, then closes.
- **Quit Game** (the last row) ends the game's app at once, with no confirmation, and ES-DE comes
  back. It works for any game ES-DE launched, emulator or Android game. It closes without warning:
  your saves are kept, but progress since your last save is lost (RetroArch writes in-game saves
  every 10 s here), and an emulator's save-on-exit doesn't run.
- Every guide and manual reopens at the page or place where you left it, also after a restart.
- ES-DE shows new media when you return to the gamelist; no reload needed.

## Guides

Add guides next to ES-DE's other media, named like the ROM, or let **Find Guides** save them there:

```
<ES-DE>/downloaded_media/<system>/guides/<ROM subfolders>/<ROM name>.<ext>
<ES-DE>/downloaded_media/<system>/guides/<ROM subfolders>/<ROM name>/<any name>.<ext>
```

The folder form holds several guides. Supported: `pdf`, `txt` (UTF-8; ASCII art keeps its columns),
`png`, `jpg`/`jpeg` (large maps are decoded a region at a time), `cbz` and `html`. Not supported:
`cbr`. A ROM that is a folder (e.g. `dig.scummvm`) keeps its whole name, as for ES-DE's own media.

Find Guides saves into the folder form, e.g. for Shantae:

```
<ES-DE>/downloaded_media/gbc/guides/Shantae/VGMaps - Shantae.cbz
<ES-DE>/downloaded_media/snes/guides/Super Metroid/StrategyWiki - Super Metroid.html
<ES-DE>/downloaded_media/gbc/guides/Shantae/GameFAQs - Shantae – Guide and Walkthrough by DEngel.html
```

A VGMaps save is one CBZ of the game's maps in the atlas's order. A StrategyWiki save is one HTML
file: a contents list, one section per wiki page, its images inside the file, and the source and
license (CC BY-SA 4.0) at the end. A guide saved from the browser is the same kind of HTML file:
a text guide is kept as fixed-width text, and a guide in pages gets one section per page with its
images inside. Site scripts, ads and comments are left out, and the end names the source page and
author. These guides belong to their authors; save them for your own reading. The viewer shows
saved guides offline in the theme's colours and font; opened anywhere else they are plain web
pages.

**Saved guides → Manage Guides** in settings lists every saved guide by game, under the game's
ES-DE name with its system, guide count, size and last played date, and the total on top. Search
finds a game by name (**✕** clears it). **Size** (largest first), **Last Played** (oldest first,
never played before that) and **Name** sort the list. A game whose ROM is no longer in the ROMs
folder reads *ROM missing*. Open a game to see its guides and their sizes: **Delete** removes one
guide and **Delete All** removes all of the game's guides, each after a confirm (**Keep** leaves
them). Deleting a game's last guide also removes its emptied `<ROM name>/` folder. The screen
reads the guides from `downloaded_media` each time it opens.

## How it finds the game

It reads `ES-DE/logs/es_log.txt`. Each launch logs the game name, system, and the app package.
The ROM path is taken from the launch line when ES-DE logs one (RetroArch and some standalone
emulators); otherwise the name is looked up in `gamelists/<system>/gamelist.xml`. The menu only
opens while the launched package is the app in front, and nothing is saved when the game can't be
identified with certainty (the notification shows the raw launch line instead).

## Known limitations

- Every controller button passes through the overlay before the game gets it: Android only lets
  an accessibility service catch Back by holding each button until the service answers. On the
  Nova that adds about 1 ms a press (5 ms at most in 523 presses during play in RetroArch and Eden).
  The D-pad and sticks don't pass through it.
- Turning the screen off when ES-DE is idle needs ES-DE's *Debug Mode*, which makes ES-DE's log
  much longer; the app reads only the new lines each second. ES-DE's own screensaver timer decides
  what counts as idle, so any input ES-DE sees, D-pad and touch included, restarts it.
- Games ES-DE launched never get the controller's Back while the overlay runs. Eden's own menu
  still opens with a swipe from the left edge, and Android's back swipe isn't affected.
- Home leaves the game running in the background. Relaunching the same game resumes it. A RetroArch
  left running closes when ES-DE launches a different RetroArch game, so that launch shows nothing;
  launching it again works. Quit Game, or RetroArch's own quit, ends it instead.
- Android doesn't let the app end another app directly, so Quit Game goes through the app's App
  Info page in Settings, which shows for a moment. Retroid's firmware doesn't force-stop apps on its
  Whitelist, so Quit Game does nothing for an app added there.
- The menu and Quit Game need the overlay running. Switch games that take nearly all the Nova's
  memory in Eden (Super Mario Odyssey, Mario + Rabbids) make Android end the overlay within seconds,
  so the menu doesn't open and Back reaches the game; a lower CPU clock doesn't change this.
- When Android ends the overlay for memory, it starts it again by itself a few seconds later. The
  capture permission is lost with it, so the next Capture asks for it again.
- RetroArch pauses while the menu or viewer is open. Other emulators and Android games are untested.
- The menu has no save or load states. RetroArch's Android build leaves out its network command
  interface, which is the only way another app could ask it to save or load, and Android doesn't let
  an app press another app's buttons.
- Clips are recorded at half resolution (640x480 on the Nova); the screenshot is full resolution.
  The Nova's encoder ignores bitrate settings, so size is controlled by resolution.
- Clips include the game's sound when **Game sound in clips** is enabled (Android labels it audio
  recording; only game playback is captured, never the microphone). Apps can opt out of playback
  capture; every game and emulator on this Nova allows it. Without the permission clips are silent.
- RetroArch's 120 Hz shader sub-frames (`video_shader_subframes`) put a dark frame between real
  ones. The app skips those, so clips play at 60 fps without flicker, and media keep the shader look.
- A very large PNG map takes about a second to sharpen after opening or moving; the view moves at
  once and fills in.
- Capture consent can't be granted at boot without a person. With "Offer to start after boot" on,
  a notification after boot opens the consent dialog in one tap.
- Reinstalling or stopping the service (including from the tile) needs a new consent.
- Emulators that hand off to a different app package after launch would block captures and the
  menu (not seen so far).
- Typing in Manage Guides' search needs touch: the controller's A doesn't open the keyboard there.
  The D-pad and A work everywhere else on the screen.
- Find Guides matches the game's ES-DE name exactly (ignoring case, punctuation, region tags and a
  trailing ", The"), so some games aren't found even when a site has them; it never picks a
  different game. GameFAQs and Neoseeker are searched in the browser, where you pick the game.
- Saving from the browser reads each site's page layout, as seen in October 2026. A site that
  changes its layout can break Save guide until the app is updated, and a site could block the
  in-app browser outright. Ads show in the live browser; saved guides leave them out. Guides with
  many large images make large files (Super Metroid's GameFAQs guide with 264 images is 58 MB).
- The app goes online only when you pick Find Guides or a browser site, with its own User-Agent for
  its own requests; the browser is a normal WebView. Saved guides need no connection.
- Device findings and how each was checked: [`docs/VERIFIED.md`](docs/VERIFIED.md). Plan:
  [`PLAN.md`](PLAN.md).

Debug log: `adb logcat -G 16M && adb logcat -s ESDEShot`.
