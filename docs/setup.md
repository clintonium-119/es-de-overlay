---
---

# Set up

## Install

Download `es-de-overlay-<version>.apk` from the [latest release](https://github.com/clintonium-119/es-de-overlay/releases/latest) and open it on the device. Android asks once to allow installs from the app that opened it, such as your browser or file manager. Or add `https://github.com/clintonium-119/es-de-overlay` to [Obtainium](https://github.com/ImranR98/Obtainium), which installs the app and its updates for you.

## Get the menu working

Open **ES-DE Overlay** (ES-DE also lists it under Android Apps). The checklist at the top shows what the menu still needs: **Folders**, **Display over other apps**, **Usage access** and **Controller button (accessibility service)**. Tap a line marked ✗ to go where it's fixed; it shows ✓ once it's ready.

![The checklist and the Service section at the top of the settings screen](images/settings.png)

Then:

1. Pick your folders, under **Folders**.
   - **ES-DE data folder → Set Folder**: the ES-DE folder that holds `downloaded_media`, `gamelists` and `logs`. Open the SD card or internal storage, choose `ES-DE`, then *Use this folder* and *Allow*. If the row says *No ES-DE log here; set the ES-DE folder*, you picked the wrong folder: the right one contains `logs/es_log.txt`.
   - **ROMs folder → Set Folder**: the folder that holds your system folders (`snes`, `gbc`…). Android won't let an app have a whole SD card, which is why you pick the two folders separately. If the row says *No ES-DE system folders here*, none of the folders in it matches a system ES-DE has a gamelist for.
   - If the ES-DE folder row says *ES-DE shows media from …*, ES-DE is set to keep game media in another folder. Captures and guides still go to `downloaded_media` in the folder you picked, so ES-DE won't show them.
2. Grant the permissions, under **Permissions** at the bottom, where they're grouped as **Required**, **Recommended** and **Optional**. Tap **Enable** on each row you want, using the table below to choose. A row says *Enabled* once it's on. If Android has stopped showing a permission's prompt (after you deny it twice), **Enable** opens the app's App Info page instead, where you can grant it by hand.

   | Row in settings | Needed for | Kind |
   | --- | --- | --- |
   | Display over other apps | The menu, viewer and browser windows | Required |
   | Usage access | Knowing which app is in front | Required |
   | Controller button (accessibility service) | Opening the menu from the controller; also turning the screen off when idle, and Quit Game | Required |
   | Notifications | The service's notification with **Open Menu**, and capture results | Recommended |
   | Ignore battery optimization | Keeping the service alive in the background; Android can still end it when memory runs short | Recommended |
   | Game sound in clips | The game's sound in video clips, from its app only; the microphone is never recorded | Optional |
   | Read screenshots | Only **Use system screenshots as game images**, under [Capture](#capture) | Optional |

   **Controller button (accessibility service) → Enable** opens Android's accessibility settings, where you turn on *ES-DE Overlay*.
   - This is an accessibility service. Android shows it every controller button press. It passes them all through except the menu button (Back or Menu, unless you choose another under [Controls](#controls)), and takes that only during a game ES-DE launched, a capture or the open menu.
   - Quit Game uses it to show ES-DE afterwards. Without Shizuku, it also presses Force stop on the game's App Info page in Settings.
   - Android 13 can refuse to turn on an accessibility service for an app installed from a browser or file manager, with a *Restricted setting* message. On the Nova it was tested on, it didn't: installed from a file manager, the service turned on after Android's usual question about full control. If you do see the message, open App Info for ES-DE Overlay, tap the menu at the top right, tap **Allow restricted settings**, then turn the service on. See [Google's help page](https://support.google.com/android/answer/12623953).
3. Under **Service**, tap **Start Service**. It stays greyed out, naming what's missing, until the checklist is all ✓. Once it's running, the button says **Stop Service**. **Start after boot** is on, so the service starts again by itself after a restart.
4. Try it: launch a game from ES-DE and press Back or Menu on the controller. The menu opens over the game, and [Use](use.md) goes through it. If your controller has neither button, choose another under [Controls](#controls), or pull down the notification shade, expand ES-DE Overlay's notification and tap **Open Menu**.

Android asks for permission to capture the screen the first time you pick Capture, not here. See [Capture](use.md#capture).

## Optional settings

Each of these is a section of the settings screen, in the order they appear.

### Start after boot and the quick settings tile

Under **Service**:

- **Start after boot** (on by default) starts the service when the device starts, once both folders are set, so the menu works without opening the app. If Android refuses the start, you get a *Tap to start ES-DE Overlay* notification instead, which starts it in one tap. Capture still asks for permission the first time.
- **Quick settings tile → Add** puts an *ES-DE Overlay* on/off tile in the swipe-down shade, and the row says *Added* afterwards. Turning the tile on starts the service; turning it off stops it.
- **Stop Service** ends the menu and captures. Turning the screen off when ES-DE is idle keeps working, because the accessibility service does it; turn that off under [Screen](#screen-off-when-idle).

### Controls

- **Menu button** is Back or Menu until you choose another. Tap **Change**, then press the controller button you want; the row names it. **Reset** goes back to Back or Menu.
  - The D-pad, **A**, **B**, **X**, **Y**, the shoulder buttons and Home can't be chosen, because the menu, the viewer or Android use them. The row says so if you press one.
  - A trigger that reports as an axis can't be chosen, and some devices keep buttons such as Home or Guide for themselves. If pressing a button shows nothing in the row, your device doesn't pass it on.
  - The setting is a button, not one controller's: the same button on any connected controller opens the menu.
  - Games don't get the chosen button while the app is running.
- **Open Menu** in the service's notification opens the menu over the game from the swipe-down shade, with any controller or none. The notification sits under *Silent*, collapsed, so expand it to see the button. It needs the Notifications permission.

### Capture

- **Screenshot media type** lists ES-DE's image types (default Screenshots). Pick the one your theme shows in the gamelist, so captures show there. The menu's picture is set separately, under **Look**.
- **Clip length** runs from 5 to 60 s in 5 s steps (default 15 s).
- **Use system screenshots as game images** (off by default) saves a system screenshot you take during a game (Power + Volume down, or the Screenshot tile in quick settings) as that game's image, as a capture would. It needs the **Read screenshots** permission. Leave it off if you take screenshots for other reasons, such as sharing them.
- **If the media file already exists**: **Replace** (the default) puts the new capture in its place, **Ask** asks in the menu whether to replace it, and **Skip** keeps it and drops the new capture. [Capture](use.md#capture) has the details.

### Screen off when idle

ES-DE keeps the screen on while it's in front, even behind its screensaver, so a handheld left in ES-DE stays lit until the battery runs out. Under **Screen**, **Turn the screen off when ES-DE is idle** locks the screen like the power button once ES-DE's screensaver has run for **After the screensaver has run for** (At once, or 1 to 30 min; default 9).

- It's on by default when ES-DE is your Home app.
- ES-DE has to report its screensaver: in ES-DE's *Other Settings*, turn on *Custom Event Scripts* and *Debug Mode*. The row tells you while they're off.
- The accessibility service behind **Controller button** does the locking, so it keeps working with the service stopped. It also needs **Usage access**.

### Look and quitting games

- Under **Look**, the Theme row shows which ES-DE theme the app follows. **Text size** scales the theme's text in the menu and guides. The menu takes it from the next game you start. See [Theme](theme.md).
- Also under **Look**, **Menu image** picks which of the game's ES-DE images the menu shows. **Same as captures** (the default) follows **Screenshot media type**. Screenshots and title screens fill the picture box, as in ES-DE's gamelist; covers and the other images are scaled to fit it.
- Under **Quitting games**, **Confirm before quitting** (off by default) makes Quit Game ask first, with **Keep Playing** selected.
- Also under **Quitting games**, set up [Shizuku](shizuku.md), then tap **Allow** on the **Shizuku** row. With Shizuku, Quit Game ends a game without showing its App Info page, and you can turn on **Quit games left with Home**.

### Manage Guides

Under **Guides**, once the ES-DE folder is set, **Saved guides → Manage Guides** lists the guides you've saved, by game, and deletes the ones you no longer need. See [Manage Guides](guides.md#manage-guides).

## Updating and uninstalling

- An update from one release to the next keeps your settings, so you don't need to uninstall. An update stops the service, so tap **Start Service** again afterwards.
- A build of your own is signed with a different key from the release APK, so Android won't install one over the other. Uninstall first, then set the app up again, because uninstalling clears its settings.
- To uninstall, turn off *ES-DE Overlay* in Android's accessibility settings first, then uninstall it like any app. Your captures and saved guides stay in `downloaded_media`.

## Build from source

Needs JDK 17 and the Android SDK, platform 36.

```sh
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
