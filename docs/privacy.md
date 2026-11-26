---
---

# Privacy and safety

ES-DE Overlay is written with an AI coding assistant and reviewed by its author. It comes with no warranty, and you use it at your own risk.

## What the app can access

You grant each of these yourself, in Android's settings or permission dialogs, and the app works without the optional ones.

- **The screen and sound**, for screenshots and clips.
  - The app holds Android's capture permission while its service runs, but records only when you pick Capture.
  - A capture copies the whole screen, so a notification or system dialog showing at that moment is in it.
  - Clips record sound from the game's app only. The microphone is never recorded.
- **The ES-DE and ROMs folders you pick.** It reads ES-DE's settings, log, gamelists and themes, and saves screenshots, clips and guides into `downloaded_media`.
- **System screenshots** (Power + Volume down, or the Screenshot tile in quick settings), to save them under the game in front, only if you turn on **Use system screenshots as game images** and grant **Read screenshots**. Both are off until you turn them on.
- **Which app is in front** (Usage access), to tell a game ES-DE launched from anything else.
- **One controller button**, through an accessibility service: Back or Menu, or the one you choose under **Controls**.
  - Android shows the service every button press. It intercepts the menu button only during a game ES-DE launched, a capture or the open menu, and passes every other button on to the app in front.
  - With **Keep a picture of where you left a game** on, it also intercepts RetroArch's *Quit* hotkey, during a RetroArch game ES-DE launched that saves its auto state as it closes, to keep a picture before pressing the hotkey itself.
  - The same service turns the screen off when ES-DE's screensaver has run for the time you set.
  - It hears which app's window comes to the front, only to see at once that ES-DE is back after a game, so the game switcher can open again.
  - For Quit Game, it brings ES-DE back afterwards, and without Shizuku it presses Force stop on the game's App Info page.
- **Drawing over other apps**, for the menu, the guide viewer, the guide browser and the game switcher.
- **With [Shizuku](shizuku.md)** (optional):
  - ending a game's app for Quit Game and Quit games left with Home, but never ES-DE Overlay, ES-DE or Shizuku themselves;
  - copying your current theme from ES-DE's own app storage, where its theme downloader puts themes;
  - pressing RetroArch's *Quit* and *Shaders (Toggle)* hotkeys as your controller, during a RetroArch game ES-DE launched;
  - for the game switcher, sending ES-DE the key presses that move it to the game you pick;
  - reading emulators' files for the switcher's pictures: RetroArch's settings, overrides, core options and the pictures beside its save states; PPSSPP's `ppsspp.ini` and the pictures beside its save states; ARMSX2's `PCSX2-Android.ini` and the screenshot inside each of its save states. To find PPSSPP's and ARMSX2's folders, it lists folder names up to four levels deep in internal storage and on the SD card.
- **A picture of the game's screen when you quit it**, with **Keep a picture of where you left a game** on (the default) and Shizuku ready.
  - Quit Game, Home when it ends the game, and RetroArch's *Quit* hotkey when the app takes it, read the screen, three times at once where RetroArch's shader draws sub-frames; two of the three are held for a moment in files under `/data/local/tmp`, which only the shell user can reach, and deleted. The picture is kept in the app's own private storage, one per game, and replaced the next time you quit that game. Copies of PPSSPP's and ARMSX2's save-state pictures are kept there too.
  - Only the game switcher shows them. Nothing sends them anywhere, and uninstalling the app or clearing its storage deletes them.

## When it goes online

- **Find Guides**, when you open it: StrategyWiki and VGMaps, with the app's own User-Agent.
- **The guide browser**, when you open it: GameFAQs, Neoseeker, ZeldaDungeon, a DuckDuckGo search and the sites you go to from there.
  - The browser is a normal Android WebView.
  - Like any WebView, it uses Google Safe Browsing, which checks the pages it opens against Google's lists of dangerous sites.
- **Saved guides open offline.** The viewer blocks all network access and runs none of a saved page's scripts.
  - The one exception: if you open a YouTube link in a saved guide, YouTube's page for that video opens in the guide browser.

ES-DE Overlay adds no analytics, ads or accounts of its own. Guide sites you open in the browser can load their own ads, trackers and embedded content, and keep cookies. Find Guides sends the game's title to StrategyWiki, and a guide search in the browser sends it to the site you pick.

## How it's checked

Before the app went public, its code was checked for the ways an app like this could be misused:

- its manifest and exported components;
- the accessibility service;
- both WebViews and saved guide pages;
- text from web pages and from ES-DE's log that ends up in file names or Shizuku commands;
- its XML parsing;
- its dependencies and secrets.

Each finding was fixed or accepted with a reason. Every change now runs the unit tests and Android Lint in CI, and the code is scanned with CodeQL and OpenSSF Scorecard. None of that is a guarantee.

## Check a download

Releases after 1.1.0 are built and published by the repository's release workflow from a version tag, with a signed record of how they were built. To check that an APK you downloaded came from that workflow, install the [GitHub CLI](https://cli.github.com) and run:

```sh
gh attestation verify es-de-overlay-<version>.apk --repo clintonium-119/es-de-overlay
```

The release's notes also give the APK's SHA-256, which `sha256sum es-de-overlay-<version>.apk` should match. The record itself is attached to the release as `es-de-overlay-<version>.apk.sigstore.json`.

Every release, 1.0.0 and 1.1.0 included, is signed with the same certificate. Its SHA-256 is:

```
c88e6bb16214286ce6c31f786c7c3d9441b3cf926cda43af0b85e6f4bd973a75
```

To compare, run `apksigner verify --print-certs es-de-overlay-<version>.apk` from the Android SDK's build tools and read the `Signer #1 certificate SHA-256 digest` line. Android refuses an update signed with a different certificate, so once the app is installed, updates can only come from the same key.

## Reporting a vulnerability

To report a security problem privately, use [GitHub's reporting form](https://github.com/clintonium-119/es-de-overlay/security/advisories/new). The [security policy](https://github.com/clintonium-119/es-de-overlay/security/policy) says what to include.
