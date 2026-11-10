---
---

# Known limitations

These were found on the Retroid Pocket Nova (Android 13), the one device the app has been tested on. Items that depend on the Nova's firmware or setup say so. [Verified device facts](VERIFIED.md) lists what was checked on the Nova and how.

## How it finds the game

- It reads `ES-DE/logs/es_log.txt`. Each launch logs the game name, the system and the app package.
- The ROM path is taken from the launch line when ES-DE logs one (RetroArch and some standalone emulators). Otherwise the name is looked up in `gamelists/<system>/gamelist.xml`.
- The menu only opens while the launched package is the app in front.
- Nothing is saved when the game can't be identified with certainty. The notification shows the raw launch line instead.

## Controller and menu

- **Every controller button passes through the overlay before the game gets it.** Android only lets an accessibility service catch Back by holding each button until the service answers. On the Nova, that adds about 1 ms a press (5 ms at most in 523 presses during play in RetroArch and Eden). The D-pad and sticks don't pass through it.
- **Back reaches the game only in about its first second.** While the overlay runs, games ES-DE launched don't get the controller's Back, except in about their first second, because the overlay reads launches from ES-DE's log once a second. Eden's own menu still opens with a swipe from the left edge, and Android's back swipe isn't affected.
- **RetroArch pauses while the menu or viewer is open.** Other emulators and Android games are untested.
- **There are no save or load states.**
  - RetroArch's Android build leaves out its network command interface. That interface is the only way another app could ask it to save or load.
  - Android doesn't let an app press another app's buttons.
- **The menu and Quit Game need the overlay running.**
  - On the Nova, Switch games that take nearly all its memory in Eden (Super Mario Odyssey, Mario + Rabbids) make Android end the overlay within seconds. The menu then doesn't open and Back reaches the game. A lower CPU clock doesn't change this.
  - When Android ends the overlay for memory, it starts it again by itself a few seconds later. The capture permission is lost with it, so the next Capture asks for it again.
- **Emulators that hand off to a different app package after launch** would block captures and the menu. None has been seen so far.

## Screen

Turning the screen off when ES-DE is idle needs ES-DE's *Debug Mode*.

- Debug Mode makes ES-DE's log much longer. The app reads only the new lines each second.
- ES-DE's own screensaver timer decides what counts as idle. Any input ES-DE sees, D-pad and touch included, restarts it.

## Quitting games

- **Home leaves the game running in the background**, unless **Quit games left with Home** is on, which needs [Shizuku](shizuku.md).
  - Relaunching the same game resumes it.
  - A RetroArch left running closes when ES-DE launches a different RetroArch game, so that launch shows nothing; launching it again works.
  - Quit Game, or RetroArch's own quit, ends it instead.
- **Android doesn't let the app end another app directly.** Without Shizuku, Quit Game goes through the app's App Info page in Settings, which shows for a moment.
- **On Retroid firmware, apps on its Whitelist aren't force-stopped.** Quit Game, and probably leaving with Home, does nothing for an app added there.
- **Quit games left with Home ends a game that crashed or quit back to ES-DE too.** That does no harm. It also ends an Android app opened from ES-DE's lists once you go Home from it.

## Capture

- **Clips are recorded at half resolution** (640x480 on the Nova); the screenshot is full resolution. The Nova's encoder ignores bitrate settings, so size is controlled by resolution.
- **Clips include the game's sound** when **Game sound in clips** is enabled.
  - Android labels this permission audio recording, but only game playback is captured, never the microphone.
  - Apps can opt out of playback capture. Every game and emulator on the Nova allows it.
  - Without the permission, clips are silent.
- **RetroArch's 120 Hz shader sub-frames** (`video_shader_subframes`) put a dark frame between real ones. The app skips those, so clips play at 60 fps without flicker, and media keep the shader look.
- **Capture consent can't be granted at boot without a person.** With **Offer to start after boot** on, a notification after boot opens the consent dialog in one tap.
- **Reinstalling or stopping the service** (including from the tile) needs a new consent.

## Guides

- **Large maps load in stages.** A very large PNG map shows its top quarter about 0.3 s after it opens, and the whole map within about a second (on the Nova). A zoom or a pan moves the view at once; the newly shown part is blurry for up to about half a second while it sharpens.
- **Typing in Manage Guides' search needs touch.** The controller's A doesn't open the keyboard there. The D-pad and A work everywhere else on the screen.
- **Find Guides matches the game's ES-DE name exactly**, ignoring case, punctuation, region tags and a trailing ", The".
  - Some games aren't found even when a site has them, but it never picks a different game.
  - GameFAQs and Neoseeker are searched in the browser, where you pick the game.
- **Saving from the browser reads each site's page layout, as seen in October 2026.**
  - A site that changes its layout can break Save guide until the app is updated. A site could also block the in-app browser outright.
  - Elsewhere, Reader View's rules decide what the main content is, and the guide's other pages are guessed from its links. Check the list before saving.
  - Chapters found beside a chapter page come after it, not in the guide's order.
- **Older VGMaps saves show file names.** A CBZ saved from VGMaps before maps were named shows each map's file name (`Shantae-DribbleFountain`). Saving it again from Find Guides names them.
- **A text guide with no contents tags or rule lines** stays one section.
- **Ads show in the live browser.** Saved guides leave them out.
- **Guides with many large images make large files.** Super Metroid's GameFAQs guide with 264 images is 58 MB.

## Network

The app goes online only when you pick Find Guides or a browser site. It sends its own User-Agent with its own requests; the browser is a normal WebView. Saved guides need no connection. See [Privacy and safety](privacy.md).

## Debug log

To see what the overlay is doing, connect a computer with adb and run:

```sh
adb logcat -G 16M && adb logcat -s ESDEShot
```
