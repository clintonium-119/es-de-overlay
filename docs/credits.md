---
---

# Credits

ES-DE Overlay is MIT-licensed. It builds on, reads from and borrows ideas from the projects below. Their licences and terms are their own, and none of them endorses this app.

## Bundled in the app

- **[Readability](https://github.com/mozilla/readability)** by Mozilla, version 0.6.0. Licence: Apache License 2.0; the notice ships in the app at `app/src/main/assets/readability/LICENSE.md`. It picks out the guide text when you save a page from a site the app has no special handling for.
- **[Shizuku API](https://github.com/RikkaApps/Shizuku-API)** by RikkaApps (`dev.rikka.shizuku:api` and `:provider` 13.1.5). Licence: MIT. Quit Game uses it to talk to Shizuku.
- **[Android Jetpack](https://developer.android.com/jetpack)** (AndroidX, Compose and Material 3) by Google. Licence: Apache License 2.0.

## Read at runtime, never bundled

- **[ES-DE](https://es-de.org)** ([source](https://gitlab.com/es-de/emulationstation-de)). Licence: MIT. The overlay reads ES-DE's log, gamelists, media folders, settings and themes, and saves captures and guides where ES-DE looks for them. Its manual viewer is the model for the overlay's viewer controls.
- **Your ES-DE theme.** The menu, guides and browser take the colours and fonts of the theme you use, read from the theme's own files. No theme files or fonts ship with the app, VAG Rounded included.
- **[Linear](https://gitlab.com/es-de/emulationstation-de/-/tree/master/themes/linear-es-de)**, ES-DE's bundled theme, by Northwestern Software AB, Leon Styhre and Weestuarty. Licence: MIT. Its dark colour values are the overlay's built-in look, used when the active theme can't be read.

## The theme in the screenshots

- **[minui-menu-es-de](https://github.com/anthonycaccese/minui-menu-es-de)** by Anthony Caccese, a port of Shaun Inman's MinUI menu. Licence: CC BY-NC-SA, as stated in its `theme.xml`. The screenshots use a fork by ES-DE Overlay's author, **[minui-menu-overlay-es-de](https://github.com/clintonium-119/minui-menu-overlay-es-de)**, under the same licence; see [Theme](theme.md).
- **[MinUI](https://github.com/shauninman/MinUI)** by Shaun Inman. Its repository states no licence. Its menu is where the theme's layout, and so the screenshots' look, comes from. No MinUI code is used.
- **[Modern](https://gitlab.com/es-de/themes/modern-es-de)** and **[Slate](https://gitlab.com/es-de/themes/slate-es-de)**, ES-DE's themes from its theme downloader. Licence: CC BY-NC-SA 4.0. They appear only in screenshots.

## Inspiration

- **[Pixel Guide](https://github.com/rexmont/Pixel-Guide-Android)** for Android, by rexmont. Its repository states no licence.
- **[Pocket Codex](https://www.pocketcodex.app/)** for iOS, by Anthony John Caccese ([App Store](https://apps.apple.com/us/app/pocket-codex-guides/id6754950382)).

Both showed how useful game guides are on a handheld. No code or services from either are used.

## Guide sources

The overlay opens these sites in its browser and saves pages for reading offline. Saved guides are for your own reading on your device.

- **[StrategyWiki](https://strategywiki.org)**: text under CC BY-SA 4.0. Each saved guide keeps the source and licence at its end.
- **[VGMaps](https://www.vgmaps.com)**: the site is © Jonathan Leung. Each map is by its mapper, and ripped game images belong to their copyright holders. Saved maps keep the mapper's credit in the images.
- **[GameFAQs](https://gamefaqs.gamespot.com)**, **[Neoseeker](https://www.neoseeker.com)** and **[ZeldaDungeon](https://www.zeldadungeon.net)**: guides belong to their authors and sites. The same goes for any other page you save, such as IGN.
- **[DuckDuckGo](https://duckduckgo.com)** runs Search the Web.

## Tools the setup uses

- **[Shizuku](https://github.com/RikkaApps/Shizuku)** by RikkaApps (Apache License 2.0), and **[Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next)** by rushiranpise (Apache License 2.0), the fork the screenshots' setup runs; see [Shizuku](shizuku.md).
- **[Obtainium](https://github.com/ImranR98/Obtainium)** by ImranR98 (GPL-3.0) keeps the app up to date from GitHub Releases.

Game names, logos and screenshots belong to their owners.
