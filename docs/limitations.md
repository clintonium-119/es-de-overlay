---
---

# Known limitations

Everything here was found on a Retroid Pocket Nova with Android 13, the only device the app has been tested on. Items that depend on the Nova's firmware or setup say so. The [test notes](https://github.com/clintonium-119/es-de-overlay/blob/master/docs/VERIFIED.md) record what was checked on the Nova and how.

[Troubleshooting](troubleshooting.md) starts from what you see, and explains [how the app finds the game](troubleshooting.md#how-the-app-finds-the-game).

## Controller and menu

- **Button presses reach the game about 1 ms later on the Nova.** Android lets an app catch one controller button only by seeing every button before the game does, holding each one until the app answers. The D-pad and sticks aren't held.
- **Games don't get the menu button while the app is running.** That's Back and Menu unless you choose another under **Controls**. The app takes it during any game ES-DE launched, so if your emulator uses Back for its own menu, choose another menu button, or use another way in: Eden's menu still opens with a swipe from the left edge, and Android's back swipe isn't affected. In about the first second of a game, before the app has read the launch from ES-DE's log, the button still reaches the game.
- **Only single buttons can be the menu button**, not combinations or long presses. A trigger that reports as an axis can't be chosen, and some devices keep buttons such as Home or Guide for themselves. It has been tried with the Nova's own controls and a GuliKit Controller XW.
- **Home closes the menu at once only on the Nova.** Its Home button sends a key the app can see. On other devices the menu closes within a second of the game leaving the screen.
- **RetroArch pauses while the menu or viewer is open.** Other emulators and Android games weren't checked and may keep running.
- **You can't save or load states from the menu.** RetroArch's Android build leaves out its network command interface, which is the only way another app could ask it to save or load. Android doesn't let an app press another app's buttons either.
- **On the Nova, the menu doesn't open in Switch games that use nearly all its memory.** In Eden, Super Mario Odyssey and Mario + Rabbids make Android end the app within seconds, so the menu button goes to the game and Quit Game isn't available. A lower CPU clock doesn't change this. Android starts the app again by itself a few seconds later, but the screen-capture permission is lost, so your next Capture asks for it again.
- **If an emulator hands the game to a second app after launch, the menu and captures won't work for it.** No emulator has been seen doing this so far.

## Screen

- **Turning the screen off when ES-DE is idle makes ES-DE's log much longer**, because it needs ES-DE's *Debug Mode*. The app reads only the new lines each second.
- **Any input ES-DE sees restarts the idle countdown**, D-pad and touch included, because ES-DE's own screensaver timer decides what counts as idle.
- **It keeps working with the service stopped**, because the accessibility service (the controller button) does it.

## Quitting games

- **Quit games left with Home acts only when ES-DE is your Home app**, because it counts on Home taking you to ES-DE. Quit Game itself brings ES-DE back either way.
- **Home leaves the game running in the background**, unless **Quit games left with Home** is on, which needs [Shizuku](shizuku.md).
  - Launching the same game again resumes it.
  - A RetroArch left running closes when ES-DE launches a different RetroArch game, so that launch shows nothing. Launch it again and it works.
  - Quit Game, or RetroArch's own quit, ends it instead.
- **Without Shizuku, the game's App Info page shows for a moment when you quit.** Android doesn't let an app end another app directly, so Quit Game presses Force stop on that page.
- **On Retroid firmware, Quit Game does nothing for an app on its Whitelist**, because the firmware doesn't force-stop those apps. Leaving with Home probably doesn't end it either.
- **Quit games left with Home also force-stops a game that crashed or quit back to ES-DE**, as it would after Home. It also ends an Android app you opened from ES-DE's lists once you go Home from it.

## Capture

- **Clips are half resolution** (640x480 on the Nova), while screenshots are full resolution. The Nova's encoder ignores bitrate settings, so the app keeps files small through resolution.
- **Clips have only the game's own sound**, when **Game sound in clips** is enabled.
  - Android calls this permission audio recording. Clips record sound from the game's app only. The microphone is never recorded. Music or video playing in another app isn't recorded either.
  - Apps can opt out of playback capture. Every game and emulator on the Nova allows it.
  - If ES-DE Overlay can't find the game's app, the clip is silent and the notice after the capture says so. Without the permission, clips are silent too.
- **Clips of RetroArch with 120 Hz shader sub-frames play at 60 fps without flicker.** The sub-frames (`video_shader_subframes`) put a dark frame between real ones. The app skips those, so clips and screenshots still show the shader.
- **Android asks for permission before the first capture after every restart** of the service or the device, and after you reinstall the app. The menu, guides and Quit Game work without it.
- **A replaced image or clip can't be recovered.** With **Replace**, the old file is deleted once the new one is complete. Choose **Ask** or **Skip** under **If the media file already exists** to keep scraped media.
- **A system screenshot is never asked about.** Under **Ask**, it keeps the existing image, because there's no menu open to ask in.
- **Captures and guides ignore a custom media folder.** They go to `<ES-DE>/downloaded_media`, even when ES-DE keeps its media in another directory. The ES-DE folder row in settings warns you when it does.

## Guides

- **Very large maps open in stages.** On the Nova, a very large PNG map shows its top quarter about 0.3 s after it opens, and the whole map within about a second. Zooming or panning moves the view at once, and the newly shown part is blurry for up to about half a second while it sharpens.
- **You need touch to type in Manage Guides' search.** The controller's A doesn't open the keyboard there. The D-pad and A work everywhere else on that screen.
- **Find Guides misses some games a site has**, because it matches the game's ES-DE name exactly, ignoring case, punctuation, region tags and a trailing ", The". It never picks a different game. GameFAQs and Neoseeker are searched in the browser, where you pick the game.
- **Save guide can stop working on a site that changes its page layout.** Saving depends on each site's page layout, which can change, and a site could also block the in-app browser outright.
  - On other sites, Readability decides what the main content is, and the guide's other pages are guessed from its links. Check the list before saving.
  - When you save from one chapter, the other chapters listed beside it are saved after it, not in the guide's order.
- **A text guide stays one section when it has no headings the app recognises:** contents-list tags such as `[INTRO]`, or lines boxed or underlined with `=`, `-`, `*`, `#`, `~` or `_`.
- **Ads show in the live browser.** Saved guides leave them out.
- **Guides with many large images make large files.** Super Metroid's GameFAQs guide, with 264 images, is 58 MB.

## Network

**The app goes online only when you pick Find Guides or a site in the browser.** It sends its own User-Agent with its own requests, and the browser is a normal WebView. Saved guides need no connection. See [Privacy and safety](privacy.md).

## Debug log

[Troubleshooting](troubleshooting.md#getting-a-log) has the adb command that shows what the app is doing.
