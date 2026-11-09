# Security

ES-DE Overlay is written with an AI coding assistant and reviewed by its author. It has been
checked for the ways an app like it could be misused, and CI runs its tests, Android Lint, CodeQL
and OpenSSF Scorecard, but none of that is a guarantee: it comes with no warranty, and you use it
at your own risk.

## Reporting a vulnerability

Please report it privately through GitHub's **Report a vulnerability** button on the repository's
**Security** tab. If you can't use that, open an issue that says you found a security problem,
without the details or a way to exploit it, and you'll be asked for them privately.

## Supported versions

Fixes go into the latest release only.

## What the app can access

Each of these needs your consent in Android's settings or permission dialogs, and the app works
without the optional ones:

- **The screen and game sound** while the overlay service runs, for screenshots and clips. Only
  the game's playback is captured, never the microphone.
- **The ES-DE and ROMs folders you pick**, to read ES-DE's settings, log, gamelists and themes and
  to save screenshots, clips and guides into `downloaded_media`.
- **System screenshots** (Power + Volume down), to file them under the game in front.
- **Which app is in front** (Usage access), to tell a game ES-DE launched from anything else.
- **The controller's Back button** (an accessibility service), only during a game ES-DE launched,
  a capture or the open menu; every other key goes straight on. The same service turns the screen
  off when ES-DE's screensaver has run for the time you set, and without Shizuku it presses Force
  stop on a game's App Info page for Quit Game.
- **Drawing over other apps**, for the menu, the guide viewer and the guide browser.
- **With Shizuku** (optional): ending a game's app for Quit Game and Quit games left with Home, never
  ES-DE Overlay, ES-DE or Shizuku themselves; and copying the theme in use from ES-DE's own app
  storage, where its theme downloader puts themes.

## When it goes online

- **Find Guides**, when you open it: StrategyWiki and VGMaps, with the app's own User-Agent.
- **The guide browser**, when you open it: GameFAQs, Neoseeker, ZeldaDungeon, a DuckDuckGo search
  and the sites you go to from there. Like any Android WebView it uses Google Safe Browsing, which
  checks the pages it opens against Google's lists of dangerous sites.
- **Saved guides open offline**: the viewer blocks all network access and runs none of a saved
  page's scripts.

There are no analytics, no accounts and no ads, and nothing is sent anywhere else.
