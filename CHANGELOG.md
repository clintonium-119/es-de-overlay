# Changelog

What changed in each version of ES-DE Overlay. A release's notes on GitHub are its section here.

## Unreleased

- You choose the menu button in settings: Back or Menu by default, or any one controller button. The service's notification has an **Open Menu** action that works with no button at all.
- The service starts without the screen-capture prompt. The first Capture asks, and carries on once Android allows it.
- **Start after boot** is on by default and starts the service itself, with a tap-to-start notification if Android refuses.
- Settings opens on a readiness checklist and groups permissions as Required, Recommended and Optional.
- **Menu image** sets which of the game's images the menu shows, separately from where captures save. Covers are scaled to fit; screenshots fill the box.
- The ES-DE folder row warns you when ES-DE keeps its media somewhere else.
- System screenshots are filed under the game only when you turn on **Use system screenshots as game images**. It's off by default.
- **If the media file already exists** gains **Ask**, which lets you keep or replace the existing screenshot or video from the menu.
- Captures and guides are written to a temporary file and swapped in once complete, so a failed write leaves the old file in place.
- Quit Game can ask first, with **Confirm before quitting** (off by default), and brings ES-DE back even when it isn't the Home app. Deleting a guide opens on **Keep**.
- The docs are corrected and rewritten, with new Set up, Troubleshooting and Guides pages and a sidebar on every page.
- Releases carry a signed record of how they were built, and their notes give the APK's SHA-256 and signing certificate. [Check a download](https://clintonium-119.github.io/es-de-overlay/privacy.html#check-a-download) says how to use them.

## 1.1.0 - 2026-11-11

- A, B, X and Y work as ES-DE maps them for your controller, including its button swap, and the menu's help names them for ES-DE's controller type.
- In lists, the bumpers move a screenful and the triggers jump to the ends, as in ES-DE.
- Back opens the menu for a game whose launch lines in ES-DE's log crossed a clock second; before, it didn't.
- Themes with a wallpaper backdrop, such as Canvas, give the menu their colour instead of grey.
- Saved guides keep their YouTube links, and the viewer plays them over the guide.
- Saving a guide leaves out site logos and pages the site doesn't have, and keeps IGN's guide pictures.
- A first GameFAQs visit loads the page instead of a blank search form.
- The app has its own icon.
- The website has its own layout with dark mode, and shows the menu under six themes.

## 1.0.0 - 2026-11-10

The first release.

- An in-game menu over the game, opened with the controller's Back or Menu button and drawn in the active ES-DE theme's look.
- Capture saves screenshots and clips with game sound into ES-DE's media, named the way ES-DE expects, so they show in the gamelist.
- The game's scraped manual opens in a viewer with ES-DE's controls (pages, zoom and pan) and reopens where you left it.
- Find Guides gets maps from VGMaps, guides from StrategyWiki, and pages from GameFAQs, Neoseeker, ZeldaDungeon or a web search in a browser over the game, and saves them to read offline.
- Saved guides open in a themed viewer with their contents, sections, pictures and maps. Manage Guides lists and deletes them.
- Game Info shows the game's ES-DE metadata.
- Quit Game leaves the game, and with Shizuku ends it.
- The screen can turn off when ES-DE sits idle.
- A Quick Settings tile turns the service on and off.
- Release APKs are signed and published from version tags.
