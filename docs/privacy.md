---
---

# Privacy and safety

ES-DE Overlay is written with an AI coding assistant and reviewed by its author. It comes with no warranty, and you use it at your own risk.

## What the app can access

You grant each of these yourself, in Android's settings or permission dialogs, and the app works without the optional ones.

- **The screen and sound**, for screenshots and clips.
  - The app holds Android's capture permission while its service runs, but records only when you pick Capture.
  - A capture copies the whole screen, so a notification or system dialog showing at that moment is in it.
  - Clips can include sound from any app that allows playback capture, so other audio playing at the same time may be recorded. The microphone is never recorded.
- **The ES-DE and ROMs folders you pick.** It reads ES-DE's settings, log, gamelists and themes, and saves screenshots, clips and guides into `downloaded_media`.
- **System screenshots** (Power + Volume down, or the Screenshot tile in quick settings), to save them under the game in front, only if you turn on **Use system screenshots as game images** and grant **Read screenshots**. Both are off until you turn them on.
- **Which app is in front** (Usage access), to tell a game ES-DE launched from anything else.
- **The controller's Back or Menu button**, through an accessibility service.
  - It intercepts Back or Menu only during a game ES-DE launched, a capture or the open menu. Every other button is passed on to the app in front.
  - The same service turns the screen off when ES-DE's screensaver has run for the time you set.
  - Without Shizuku, it presses Force stop on a game's App Info page for Quit Game.
- **Drawing over other apps**, for the menu, the guide viewer and the guide browser.
- **With [Shizuku](shizuku.md)** (optional):
  - ending a game's app for Quit Game and Quit games left with Home, but never ES-DE Overlay, ES-DE or Shizuku themselves;
  - copying your current theme from ES-DE's own app storage, where its theme downloader puts themes.

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

## Reporting a vulnerability

To report a security problem privately, use [GitHub's reporting form](https://github.com/clintonium-119/es-de-overlay/security/advisories/new). The [security policy](https://github.com/clintonium-119/es-de-overlay/security/policy) says what to include.
