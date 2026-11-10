---
---

# Privacy and safety

ES-DE Overlay is written with an AI coding assistant and reviewed by its author. It comes with no warranty, and you use it at your own risk.

## What the app can access

Each of these needs your consent in Android's settings or permission dialogs. The app works without the optional ones.

- **The screen and game sound** while the overlay service runs, for screenshots and clips. Only the game's playback is captured, never the microphone.
- **The ES-DE and ROMs folders you pick.** It reads ES-DE's settings, log, gamelists and themes, and saves screenshots, clips and guides into `downloaded_media`.
- **System screenshots** (Power + Volume down), to file them under the game in front.
- **Which app is in front** (Usage access), to tell a game ES-DE launched from anything else.
- **The controller's Back button**, through an accessibility service.
  - It holds Back only during a game ES-DE launched, a capture or the open menu. Every other key goes straight on.
  - The same service turns the screen off when ES-DE's screensaver has run for the time you set.
  - Without Shizuku, it presses Force stop on a game's App Info page for Quit Game.
- **Drawing over other apps**, for the menu, the guide viewer and the guide browser.
- **With [Shizuku](shizuku.md)** (optional):
  - ending a game's app for Quit Game and Quit games left with Home, but never ES-DE Overlay, ES-DE or Shizuku themselves;
  - copying the theme in use from ES-DE's own app storage, where its theme downloader puts themes.

## When it goes online

- **Find Guides**, when you open it: StrategyWiki and VGMaps, with the app's own User-Agent.
- **The guide browser**, when you open it: GameFAQs, Neoseeker, ZeldaDungeon, a DuckDuckGo search and the sites you go to from there.
  - The browser is a normal Android WebView.
  - Like any WebView, it uses Google Safe Browsing, which checks the pages it opens against Google's lists of dangerous sites.
- **Saved guides open offline.** The viewer blocks all network access and runs none of a saved page's scripts.

There are no analytics, no accounts and no ads, and nothing is sent anywhere else.

## How it's checked

Before it went public, the code was swept for the ways an app like this could be misused:

- its manifest and exported components;
- the accessibility service;
- both WebViews and saved guide pages;
- text from web pages and from ES-DE's log that ends up in file names or Shizuku commands;
- its XML parsing;
- its dependencies and secrets.

What the sweep found was fixed. Every change now runs the unit tests and Android Lint in CI, and the code is scanned with CodeQL and OpenSSF Scorecard. None of that is a guarantee.

## Reporting a vulnerability

See the [security policy](https://github.com/clintonium-119/es-de-overlay/security/policy). Please report privately through GitHub's **Report a vulnerability** button on the repository's **Security** tab.
