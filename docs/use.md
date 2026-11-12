---
---

# Use

## The menu

Launch a game from ES-DE and press **Back** or **Menu** on your controller: the Android Back button beside Home on Retroid and AYN handhelds, or Menu on controllers that have one. You can choose another button under **Controls** in settings, or pull down the notification shade, expand ES-DE Overlay's notification and tap **Open Menu**. The menu opens over the game in your ES-DE theme's look (see [Theme](theme.md)). You'll see the game's name, its picture from ES-DE, and these rows:

- **Resume** closes the menu and takes you back to the game.
- **Guides** lists the game's guides (see [Guides](guides.md)), with **Find Guides** last. A game with no guides shows **Find Guides** here instead.
- **Manual** opens the game's scraped ES-DE manual.
- **Game Info** shows the game's developer, publisher, release date, genre, players and description from ES-DE's gamelist.
- **Capture** saves a screenshot, a clip, or both, into ES-DE's media folders.
- **Quit Game** ends the game's app and takes you to ES-DE.

![The menu over Super Mario World](images/menu.png)

- Use the D-pad to move, **A** to open, and **B** to close the menu and go back to the game. If ES-DE's **Swap the A/B and X/Y buttons** setting is on, the app swaps them too. The screenshots here were taken with it on, so their hints read `B OPEN` and `A BACK`.
- **B** in any screen you opened from the menu takes you back to the row that opened it. The guide browser is the exception, because you use it by touch.
- The menu button again closes everything the menu has open, a guide or the browser included, and takes you back to the game.
- Pressing Home closes the menu and anything open over it, and takes you to your Home app. The game keeps running unless **Quit games left with Home** is on (see [Shizuku](shizuku.md)), which works only when ES-DE is your Home app.
- Opening the menu pauses RetroArch on the Nova. Other emulators and Android games may keep running behind it.

A few details:

- The picture is the game's ES-DE image of the type picked under **Menu image** in settings, which follows **Screenshot media type** unless you change it (PNG or JPG), with pixel art kept sharp. Screenshots and title screens fill the box; other images, such as covers, are scaled to fit. A game without one shows an outlined box reading `No screenshot`. The menu doesn't play the game's video.
- A row too long for the list scrolls sideways a second after you select it, as in ES-DE's gamelist.

## Capture

**Capture** offers **Screenshot**, **Video** and **Screenshot and Video**. Pick one and the menu closes so it isn't in the shot. Only what you picked is saved.

The first time you pick one after the service starts, Android asks for permission to capture the screen. Tap **Start now**, and the capture goes ahead once you're back in the game, with no second pick. The permission lasts until the service or the device restarts. If you leave the game while Android is asking, Android closes its question and nothing is captured. If the game has closed by the time you tap **Start now**, nothing is captured either, and a message asks you to pick Capture again.


- **Screenshot** takes the picture and stops.
- **Video** and **Screenshot and Video** record a clip (15 s unless you change **Clip length**). Press the menu button to stop early. Leaving the game also stops the clip and saves it.

A toast and a notification name the saved files, for example `Saved: snes/screenshots/02) Hacks/Easyland.png, snes/videos/02) Hacks/Easyland.mp4`. A file that took the place of one the game already had is listed first, under `Replaced:`. A long list is cut short in the toast; the notification has all of it. They're saved as:

```
<ES-DE>/downloaded_media/<system>/screenshots/<ROM subfolders>/<ROM name>.png
<ES-DE>/downloaded_media/<system>/videos/<ROM subfolders>/<ROM name>.mp4
```

ES-DE shows new media when you go back to the gamelist, so you don't need to reload it.

When the game already has that image or clip, **If the media file already exists** in settings decides what happens:

- **Replace** (the default) puts the new capture in its place. The new file is written beside the old one and swapped in only once it's complete, so a capture that fails leaves the old file as it was.
- **Ask** opens the menu again once the capture is done, with **Keep Existing** selected and **Replace Screenshot** or **Replace Video** below it. **B**, the menu button or leaving the game keeps the existing file.
- **Skip** keeps the existing file and drops the new capture.

Turn on **Use system screenshots as game images**, under **Capture** in settings, and the system screenshots you take during a game are saved the same way: Power + Volume down, or the Screenshot tile in quick settings. (On the Nova it was tested on, Power + Volume down doesn't take one; the tile does.) It's off by default and needs the **Read screenshots** permission. Android keeps its own copy in Screenshots either way. Under **Ask**, a system screenshot keeps the existing file, because there's no menu open to ask in.

![The Capture menu](images/capture.png)

![The toast naming the saved screenshot](images/capture-saved.png)

![ES-DE's gamelist showing a captured screenshot](images/esde-gamelist.png)

## Manual and Game Info

- **Manual** appears only when the game has one, and opens it in the [viewer](guides.md#the-viewer).
- **Game Info** appears only when ES-DE's gamelist has at least one of its fields for the game. Press Up and Down to scroll a long description a line at a time.

![A manual page in the viewer](images/manual.png)

![Game Info](images/game-info.png)

## Guides

The menu's **Guides** row lists the guides you've saved for the game, and **Find Guides** saves new ones from guide sites. They have their own page, [Guides](guides.md):

- <a id="find-guides"></a>[Find Guides](guides.md#find-guides)
- <a id="the-guide-browser"></a>[The guide browser](guides.md#the-guide-browser)
- <a id="the-viewer"></a>[The viewer](guides.md#the-viewer)
- <a id="where-guides-are-kept"></a>[Where guides are kept](guides.md#where-guides-are-kept)
- <a id="manage-guides"></a>[Manage Guides](guides.md#manage-guides)

## Quit Game

**Save first.** Quit Game force-closes the game, so anything not yet written to storage is lost and save-on-exit doesn't run. (On the Nova it was tested on, RetroArch writes in-game saves every 10 s.)

**Quit Game** (the last row) ends the game's app at once, then takes you to ES-DE: it presses Home when ES-DE is your Home app, and otherwise brings ES-DE back to the front.

- To have it ask first, turn on **Confirm before quitting** under **Quitting games** in settings. The question opens on **Keep Playing**: **A** there, or **B**, takes you back to the menu, and **Quit "*game name*"** ends the game.
- With [Shizuku](shizuku.md), nothing else shows. Without it, the game's App Info page shows for a moment.
- It works for any game ES-DE launched, emulator or Android game.
