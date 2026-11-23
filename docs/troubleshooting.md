---
---

# Troubleshooting

Find what you're seeing, then work down its checks in order. The first ones are the likeliest.

## The menu doesn't open

1. **Something on the checklist isn't ready.** Open ES-DE Overlay: the checklist at the top marks what's missing with ✗, and tapping that line goes where it's fixed. Without usage access, the app can't tell that the game is in front; without Display over other apps, it can't draw the menu; without the **Controller button (accessibility service)**, it doesn't see the button. If Android won't turn the accessibility service on and shows a *Restricted setting* message, [Set up](setup.md#get-the-menu-working) has the way through.
2. **The service isn't running.** Under **Service**, if it says *Stopped*, tap **Start Service**. With **Start after boot** on (the default), it starts by itself after a restart; if Android refused that, a *Tap to start ES-DE Overlay* notification starts it.
3. **You chose another menu button.** Under **Controls**, **Menu button** names the one that opens the menu; **Reset** goes back to Back or Menu.
4. **The game wasn't launched from ES-DE.** The menu opens only for a game ES-DE launched, and only while that game is the app in front.
5. **You pressed the button in the game's first second.** The app reads launches from ES-DE's log once a second, so a press right after the game starts goes to the game. Press again.
6. **Android ended the app for memory.** On the Nova, a heavy Switch game in Eden can make Android end the app while the game loads. Android starts it again a few seconds later, but while it's gone, the button goes to the game. Clearing that game's cache in Eden fixed it for Super Mario Odyssey. See [Known limitations](limitations.md#controller-and-menu).
7. **The controller has no Back or Menu button, or Android doesn't pass it on.** Choose another under **Controls**. Either way, **Open Menu** in the service's notification opens the menu by touch; expand the notification to see it.

## Captures don't show in ES-DE

1. **The screenshot media type isn't the one your theme shows.** Under **Capture**, set **Screenshot media type** to the image type your theme shows in the gamelist.
2. **The game already had that image, and Skip is on or you kept it.** With **If the media file already exists** set to **Skip**, a capture of a game that already has the image is dropped, and so is one you answered **Keep Existing** under **Ask**. **Replace** puts the capture in its place.
3. **A system screenshot wasn't filed.** That needs **Use system screenshots as game images** on, under **Capture** (it's off by default), and the **Read screenshots** permission.
4. **ES-DE uses a custom media folder.** Captures go to `<ES-DE>/downloaded_media`, even when ES-DE keeps its media somewhere else. The ES-DE folder row in settings says *ES-DE shows media from …* when that's the case.
5. **The game couldn't be identified.** Nothing is saved then, and the notification shows ES-DE's launch line instead of file names. See [How the app finds the game](#how-the-app-finds-the-game).

## The menu doesn't look like my theme

1. **The theme came from ES-DE's theme downloader, and Shizuku isn't set up.** Those themes live in ES-DE's own app storage, which other apps can't read. Set up [Shizuku](shizuku.md), or copy the theme's folder into `ES-DE/themes/` as [Theme](theme.md) describes.
2. **You changed the theme during a game.** The app picks up a new theme when the next game starts.

Under **Look**, the Theme row says which look the app is using: the theme and colour scheme it follows, or the default look and why.

## Quit Game does nothing, or lands on the wrong screen

1. **The accessibility service is off.** Quit Game ends the game and brings ES-DE back through it, so the **Controller button (accessibility service)** row has to say *Enabled*.
2. **The game is on Retroid's Whitelist.** On Retroid firmware, apps on its Whitelist aren't force-stopped, so Quit Game does nothing for them.
3. **A Settings page flashes by.** Without [Shizuku](shizuku.md), Quit Game ends the game from its App Info page, which shows for a moment. That's expected.
4. **RetroArch takes a few seconds to close.** Quit Game pressed RetroArch's *Quit* hotkey and RetroArch didn't act on it, so the app force-closed it 5 s later. Check the hotkey under [RetroArch hotkeys](setup.md#retroarch-hotkeys).

## The game switcher doesn't open

The menu button in ES-DE does what ES-DE has it do, and no list of games shows.

1. **A line of its checklist lost its tick.** Open ES-DE Overlay and look under **Game Switcher**: **Switch games from ES-DE** has to be on, and every line below it ticked. Shizuku stops at a restart unless it's set to start on boot, and an ES-DE setting may have been turned off. Tap a line marked ✗ to see where it's fixed. [Set up](setup.md#game-switcher) lists them.
2. **The service isn't running**, or the accessibility service is off. The switcher needs both, as the menu does: see [The menu doesn't open](#the-menu-doesnt-open).
3. **ES-DE's screensaver is up.** A press then only ends the screensaver. Press the menu button again.

## The switcher says it couldn't reach the game

`Couldn't reach <game> in ES-DE` means the app lost its place while moving ES-DE to the game, so it launched nothing. A D-pad press during the wait can cause it. Pick the game again. If it happens every time, your theme's layout or ES-DE's *Quick System Select* setting may be one the app wasn't checked with: [Known limitations](limitations.md#game-switcher) names the ones that were, and a [log](#getting-a-log) of a failed pick shows where it stopped.

## A game's prompt or menu opens as a capture starts

Over RetroArch, the app holds your *Hotkey Enable* button (Select on the Nova) to press *Shaders (Toggle)*, and RetroArch lets that button through to the game for a few frames. A game that uses it reacts, and what it opens can show in the capture. Bind *Hotkey Enable* to a button your games don't use. See [Known limitations](limitations.md#retroarch-hotkeys).

## A switcher picture still shows the shader

It was kept by an earlier version, which took kept pictures with RetroArch's shader on. Quit that game once more and the picture is replaced. If new pictures show the shader too, the *Shaders (Toggle)* hotkey isn't on a controller button, or Shizuku isn't ready: see [RetroArch hotkeys](setup.md#retroarch-hotkeys).

## Capture asks for permission again

That's expected. Android's permission to capture the screen lasts only while the service runs, so you're asked on the first capture after the service starts: after a restart of the device, after you stop and start the service (including from the tile), after the app is updated or reinstalled, and when Android has ended the app for memory and started it again. After you tap **Start now**, pick **Capture** again: the pick that asked doesn't capture anything.

## How the app finds the game

- It reads `ES-DE/logs/es_log.txt`. Each launch logs the game name, the system and the app package.
- It takes the ROM path from the launch line when ES-DE logs one (RetroArch and some standalone emulators). Otherwise it looks the name up in `gamelists/<system>/gamelist.xml`.
- The menu only opens while the launched package is the app in front.
- Nothing is saved when the game can't be identified with certainty. The notification shows the raw launch line instead.

## Getting a log

To see what the app is doing, connect a computer with adb and run:

```sh
adb logcat -G 16M && adb logcat -s ESDEShot
```

Attach what it prints around the problem when you [open an issue](https://github.com/clintonium-119/es-de-overlay/issues).
