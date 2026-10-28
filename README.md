# ES-DE Recorder

Android app for the Retroid Pocket Nova (Android 13) that runs next to [ES-DE](https://es-de.org).
During a game launched from ES-DE, one tap on a small floating button saves a screenshot and a
15-second clip straight into ES-DE's media folders, named exactly the way ES-DE expects, so they
show up in the gamelist with no renaming:

```
<ES-DE>/downloaded_media/<system>/screenshots/<ROM subfolders>/<ROM name>.png
<ES-DE>/downloaded_media/<system>/videos/<ROM subfolders>/<ROM name>.mp4
```

System screenshots (power + volume down) taken during a game are filed the same way.

## Build and install

Needs JDK 17 and the Android SDK (platform 36).

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup (once)

Open **ES-DE Recorder** (ES-DE also lists it under Android Apps) and:

1. **ES-DE data folder → Pick**: open the SD card (or internal storage), choose the `ES-DE`
   folder, *Use this folder*, *Allow*.
2. **ROMs folder → Pick**: the same for `ROMs`. Android doesn't allow picking the card's root, so
   these are two separate picks.
3. Grant the permission rows: notifications, read screenshots, display over other apps,
   usage access, and ignore battery optimization.
4. Optional: screenshot media type folder (default `screenshots`), clip length (default 15 s),
   what to do when the file exists (Replace / Skip / Keep both), button corner, and the boot
   notification.
5. **Start service → Start now.** One consent covers every capture until the service stops.

## Use

- Launch a game from ES-DE. The ● button appears in the chosen corner (it hides inside ES-DE).
- Tap it: the button disappears, a screenshot is taken, and the clip records. Tap the invisible
  button again to stop early; leaving the game also stops and saves.
- The button shows ✓ or ✗ and a notification names the saved files, e.g.
  `Saved: snes/screenshots/02) Hacks/Easyland.png, snes/videos/02) Hacks/Easyland.mp4`.
- ES-DE shows the new media when you return to the gamelist; no reload needed.

## How it finds the game

It reads `ES-DE/logs/es_log.txt`. Each launch logs the game name, system, and the app package.
The ROM path is taken from the launch line when ES-DE logs one (RetroArch and some standalone
emulators); otherwise the name is looked up in `gamelists/<system>/gamelist.xml`. Captures only
count while the launched package is the app in front, and nothing is saved when the game can't be
identified with certainty (the notification shows the raw launch line instead).

## Known limitations

- Clips are silent and recorded at half resolution (640x480 on the Nova); the screenshot is full
  resolution. The Nova's encoder ignores bitrate settings, so size is controlled by resolution.
- RetroArch's 120 Hz shader sub-frames (`video_shader_subframes`) put a dark frame between real
  ones. The app skips those, so clips play at 60 fps without flicker, and media keep the shader look.
- Capture consent can't be granted at boot without a person. With "Offer to start after boot" on,
  a notification after boot opens the consent dialog in one tap.
- Reinstalling or stopping the service needs a new consent.
- Emulators that hand off to a different app package after launch would block captures (not seen
  so far).
- Device findings and how each was checked: [`docs/VERIFIED.md`](docs/VERIFIED.md). Plan:
  [`PLAN.md`](PLAN.md).

Debug log: `adb logcat -G 16M && adb logcat -s ESDEShot`.
