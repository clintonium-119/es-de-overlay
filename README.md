# ES-DE Overlay

Android app for the Retroid Pocket Nova (Android 13) that runs next to [ES-DE](https://es-de.org).
During a game launched from ES-DE, the first press of Home opens a menu over the game, styled like
the `minui-menu-es-de-custom` theme:

- **Capture** saves a screenshot and a 15-second clip straight into ES-DE's media folders, named
  exactly the way ES-DE expects, so they show up in the gamelist with no renaming:

  ```
  <ES-DE>/downloaded_media/<system>/screenshots/<ROM subfolders>/<ROM name>.png
  <ES-DE>/downloaded_media/<system>/videos/<ROM subfolders>/<ROM name>.mp4
  ```

- **Manual** opens the game's scraped ES-DE manual.
- **Guide** opens the game's guides (see [Guides](#guides)).
- **Find guides** looks the game up on StrategyWiki and VGMaps, saves the guide you pick next to
  your own, and opens it.

A second press of Home goes to ES-DE as usual. System screenshots (power + volume down) taken
during a game are filed the same way as captures.

## Build and install

Needs JDK 17 and the Android SDK (platform 36).

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup (once)

Open **ES-DE Overlay** (ES-DE also lists it under Android Apps) and:

1. **ES-DE data folder → Pick**: open the SD card (or internal storage), choose the `ES-DE`
   folder, *Use this folder*, *Allow*.
2. **ROMs folder → Pick**: the same for `ROMs`. Android doesn't allow picking the card's root, so
   these are two separate picks.
3. Grant the permission rows: notifications, game sound, read screenshots, display over other
   apps, usage access, and ignore battery optimization.
4. **Home menu (accessibility service) → Open**, then turn on *ES-DE Overlay* in Android's
   accessibility settings. It only listens for Retroid's Home message; it doesn't read or block
   any buttons.
5. Keep Retroid's **Prevent pressing Home accidentally** on. The menu opens on the message it shows
   for the first Home press in a game.
6. Optional: screenshot media type folder (default `screenshots`), clip length (default 15 s),
   what to do when the file exists (Replace / Skip / Keep both), and the boot notification.
7. **Start service → Start now.** One consent covers every capture until the service stops.
8. Optional: **Quick settings tile → Add** puts an *ES-DE Overlay* on/off tile in the swipe-down
   shade. Turning it on shows the consent dialog and then returns to whatever you were doing.

## Use

- Launch a game from ES-DE and press **Home** once. The menu opens; the D-pad moves, **A** opens,
  **B** closes it and returns to the game. With the menu or a guide open, Home goes to ES-DE.
- **Capture** closes the menu, waits for Retroid's Home message to clear, then takes the screenshot
  and records the clip. Press Home during the clip to stop early; leaving the game also stops and
  saves. A toast and a notification name the saved files, e.g.
  `Saved: snes/screenshots/02) Hacks/Easyland.png, snes/videos/02) Hacks/Easyland.mp4`.
- **Manual** and **Guide** only appear when the game has one. With several guides, Guide shows a
  list first.
- **Find guides** checks VGMaps and StrategyWiki for the game and lists the ones that have it, e.g.
  `VGMaps: 56 maps` and `StrategyWiki: Shantae (2 pages)`, or `No guides found`. Picking one saves
  it with progress in the menu (**B** cancels and saves nothing) and then opens it; Guide lists it
  from then on. Picking a source again replaces the saved file. Without a connection the menu says
  `Couldn't reach the guide sources`.
- The viewer works like ES-DE's own manual viewer:

  | | Zoomed out | Zoomed in |
  |---|---|---|
  | D-pad left/right | previous/next page | pan |
  | D-pad up/down | — | pan |
  | L1 / R1 | zoom out / in | zoom out / in |
  | L2 / R2 | first / last page | reset the zoom |
  | B | close | close |

  Text guides scroll with the D-pad (left/right by a page) and jump with L2/R2. Saved web guides
  (`.html`) scroll the same way, L1/R1 change the text size, L2/R2 move between sections, entries in
  the contents list can be tapped, and **B** first steps back through tapped links, then closes.
- Every guide and manual reopens at the page or place where you left it, also after a restart.
- ES-DE shows new media when you return to the gamelist; no reload needed.

## Guides

Add guides next to ES-DE's other media, named like the ROM, or let **Find guides** save them there:

```
<ES-DE>/downloaded_media/<system>/guides/<ROM subfolders>/<ROM name>.<ext>
<ES-DE>/downloaded_media/<system>/guides/<ROM subfolders>/<ROM name>/<any name>.<ext>
```

The folder form holds several guides. Supported: `pdf`, `txt` (UTF-8; ASCII art keeps its columns),
`png`, `jpg`/`jpeg` (large maps are decoded a region at a time), `cbz` and `html`. Not supported:
`cbr`. A ROM that is a folder (e.g. `dig.scummvm`) keeps its whole name, as for ES-DE's own media.

Find guides saves into the folder form, e.g. for Shantae:

```
<ES-DE>/downloaded_media/gbc/guides/Shantae/VGMaps - Shantae.cbz
<ES-DE>/downloaded_media/snes/guides/Super Metroid/StrategyWiki - Super Metroid.html
```

A VGMaps save is one CBZ of the game's maps in the atlas's order. A StrategyWiki save is one HTML
file: a contents list, one section per wiki page, its images inside the file, and the source and
license (CC BY-SA 4.0) at the end. The viewer shows it offline in the theme's colours and font;
opened anywhere else it is a plain web page.

## How it finds the game

It reads `ES-DE/logs/es_log.txt`. Each launch logs the game name, system, and the app package.
The ROM path is taken from the launch line when ES-DE logs one (RetroArch and some standalone
emulators); otherwise the name is looked up in `gamelists/<system>/gamelist.xml`. The menu only
opens while the launched package is the app in front, and nothing is saved when the game can't be
identified with certainty (the notification shows the raw launch line instead).

## Known limitations

- The menu depends on Retroid's firmware: "Prevent pressing Home accidentally" must be on, and a
  firmware update could change its message.
- RetroArch pauses while the menu or viewer is open. Other emulators and Android games are untested.
- Clips are recorded at half resolution (640x480 on the Nova); the screenshot is full resolution.
  The Nova's encoder ignores bitrate settings, so size is controlled by resolution.
- Clips include the game's sound when **Game sound in clips** is granted (Android labels it audio
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
- Find guides matches the game's ES-DE name exactly (ignoring case, punctuation, region tags and a
  trailing ", The"), so some games aren't found even when a site has them; it never picks a
  different game. It covers StrategyWiki (through its API) and VGMaps only.
- The app goes online only when you pick Find guides, one request at a time, with its own
  User-Agent. Saved guides need no connection.
- Device findings and how each was checked: [`docs/VERIFIED.md`](docs/VERIFIED.md). Plan:
  [`PLAN.md`](PLAN.md).

Debug log: `adb logcat -G 16M && adb logcat -s ESDEShot`.
