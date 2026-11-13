---
---

# Credits

ES-DE Overlay is MIT-licensed. It builds on, reads from and borrows ideas from the projects below. Their licences and terms are their own, and none of them endorses this app.

## Bundled in the app

- **[Readability](https://github.com/mozilla/readability)** by Mozilla, version 0.6.0. Licence: Apache License 2.0; the notice ships in the app at `app/src/main/assets/readability/LICENSE.md`. It picks out the guide text when you save a page from a site the app has no special handling for.
- **[Shizuku API](https://github.com/RikkaApps/Shizuku-API)** by RikkaApps (`dev.rikka.shizuku:api` and `:provider` 13.1.5). Licence: MIT. Quit Game uses it to talk to Shizuku.
- **[Android Jetpack](https://developer.android.com/jetpack)** (AndroidX, Compose and Material 3) by Google. Licence: Apache License 2.0.

## Read at runtime, never bundled

- **[ES-DE](https://es-de.org)** ([source](https://gitlab.com/es-de/emulationstation-de)). Licence: MIT. The app reads ES-DE's log, gamelists, media folders, settings and themes, and saves captures and guides where ES-DE looks for them. ES-DE's manual viewer is the model for the app's viewer controls.
- **Your ES-DE theme.** The menu, guides and browser take the colours and fonts of your theme, read from the theme's own files. No theme files or fonts ship with the app.
- **[Linear](https://gitlab.com/es-de/emulationstation-de/-/tree/master/themes/linear-es-de)**, ES-DE's bundled theme, by Northwestern Software AB, Leon Styhre and Weestuarty. Licence: MIT. Its files are read at runtime like any theme's, but its dark colour values are also built into the app, as the default look used when your theme can't be read.

## The themes in the screenshots

- **[minui-menu-es-de](https://github.com/anthonycaccese/minui-menu-es-de)** by Anthony Caccese, a port of Shaun Inman's MinUI menu. Licence: CC BY-NC-SA, as stated in its `theme.xml`. The screenshots use a fork by ES-DE Overlay's author, **[minui-menu-overlay-es-de](https://github.com/clintonium-119/minui-menu-overlay-es-de)**, under the same licence; see [Theme](theme.md).
- **[MinUI](https://github.com/shauninman/MinUI)** by Shaun Inman. Its repository states no licence. Its menu is where the theme's layout, and so the screenshots' look, comes from. No MinUI code is used.
- **[Art Book Next](https://github.com/anthonycaccese/art-book-next-es-de)** by Anthony Caccese. Licence: CC BY-NC-SA, as stated in its `theme.xml`.
- **[Iconic](https://github.com/Siddy212/iconic-es-de)** by Siddy212. Licence: its `theme.xml` states CC BY-NC-SA 2.0; its `LICENSE` file is CC0 1.0.
- **[TechDweeb](https://github.com/anthonycaccese/techdweeb-es-de)**, designed by TechDweeb and ported to ES-DE by Anthony Caccese. Licence: CC BY-NC-SA, as stated in its `theme.xml`.
- **[Canvas](https://github.com/Siddy212/canvas-es-de)** by Siddy212. Licence: its `theme.xml` states CC BY-NC-SA 2.0; its `LICENSE` file is CC0 1.0.

These four come from ES-DE's theme downloader and appear only in screenshots, as does Linear, credited above. The ES-DE gamelist screenshots show each theme's own artwork.

## Inspiration

- **[Pixel Guide](https://github.com/rexmont/Pixel-Guide-Android)** for Android, by rexmont. Its repository states no licence.
- **[Pocket Codex](https://www.pocketcodex.app/)** for iOS, by Anthony John Caccese ([App Store](https://apps.apple.com/us/app/pocket-codex-guides/id6754950382)).

Both showed how useful game guides are on a handheld. No code or services from either are used.

## Guide sources

The app opens these sites in its browser and saves pages for you to read offline. Keep saved guides for your own reading on your device.

- **[StrategyWiki](https://strategywiki.org)**: text under CC BY-SA 4.0. Each saved guide keeps the source and licence at its end.
- **[VGMaps](https://www.vgmaps.com)**: the site is © Jonathan Leung. Each map is by its mapper, and ripped game images belong to their copyright holders. Saved maps keep the mapper's credit in the images.
- **[GameFAQs](https://gamefaqs.gamespot.com)**, **[Neoseeker](https://www.neoseeker.com)** and **[ZeldaDungeon](https://www.zeldadungeon.net)**: guides belong to their authors and sites. The same goes for any other page you save, such as IGN.
- **[DuckDuckGo](https://duckduckgo.com)** runs Search the Web.

## Optional tools

- **[Shizuku](https://github.com/RikkaApps/Shizuku)** by RikkaApps (Apache License 2.0), and **[Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next)** by rushiranpise (Apache License 2.0), the fork used for the screenshots; see [Shizuku](shizuku.md).
- **[Obtainium](https://github.com/ImranR98/Obtainium)** by ImranR98 (GPL-3.0) keeps the app up to date from GitHub Releases.

Game names, logos and screenshots belong to their owners.
