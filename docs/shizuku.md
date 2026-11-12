---
---

# Shizuku

Shizuku is optional. Android normally doesn't let one app end another, but [Shizuku](https://shizuku.rikka.app) is a helper that runs with adb's permissions and is allowed to. When it's running and you've allowed ES-DE Overlay to use it:

- **Quit Game** ends the game with nothing else on screen. Without Shizuku, it opens the game's App Info page in Settings for a moment and presses Force stop. Either way it then takes you to ES-DE.
- **Quit games left with Home** (off by default, needs Shizuku) ends the game's app when you leave it with Home, as Quit Game would. It acts only when Home takes you to ES-DE, so ES-DE has to be your Home app.
  - You lose progress since your last save, and the game starts fresh next time.
  - The upside: launching a different RetroArch game afterwards works the first time (see [Known limitations](limitations.md#quitting-games)).
  - It covers every game ES-DE launched, emulators and Android games alike, except ES-DE Overlay and Shizuku themselves.
- **Themes from ES-DE's theme downloader** can be read, so the menu takes their look (see [Theme](theme.md)).

## Set it up

1. Install one of these:
   - **Shizuku**, from [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or its [GitHub releases](https://github.com/RikkaApps/Shizuku/releases). Without root, you have to start it again after every restart.
   - **Shizuku-Next**, from its [GitHub releases](https://github.com/rushiranpise/Shizuku-Next/releases). It's a fork that can start itself on boot and restart itself if it stops. It's signed with a different key, so uninstall Shizuku first. The screenshots on this site come from a setup that uses it.
2. Start it in one of the ways in [Shizuku's guide](https://shizuku.rikka.app/guide/setup/):
   - **Wireless debugging** (needs Wi-Fi):
     1. In Android's developer options, turn on *USB debugging* and *Wireless debugging*.
     2. In Shizuku, start pairing.
     3. Tap *Pair device with pairing code* under Wireless debugging, and type the code into Shizuku's notification.
     4. Tap **Start** in Shizuku.
   - **From a computer**: with USB debugging on and the device plugged in, run `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`.
   - On a rooted device, start it directly.
3. Shizuku-Next only: turn on *Start on boot*, and *Watchdog* to have it restart itself. If you started it through wireless debugging, it needs Wi-Fi at boot; see below for starting it without.
4. In ES-DE Overlay, look under **Quitting games**.
   - The **Shizuku** row says *Not installed*, *Not running* (**Open Shizuku** opens it), *Not allowed* or *Ready*.
   - Tap **Allow**, and allow ES-DE Overlay in Shizuku's dialog. The row changes to *Ready*.
   - Then turn on **Quit games left with Home** if you want it. You can't turn it on until Shizuku is *Ready*.

## Advanced: start Shizuku-Next without Wi-Fi

Some firmware lets adb listen on a network port from every boot. Shizuku-Next's USB start method can then use that port, with no Wi-Fi and no computer.

**This leaves adb listening on port 5555 on every network the device joins.** A connection still needs a computer key you've allowed, but undo it if that matters to you. To undo it, run `adb shell setprop persist.adb.tcp.port ""` and restart.

It works on the Retroid Pocket Nova. Many phones refuse the setting, stock Samsung firmware among them.

1. With USB debugging on and the device plugged into a computer, run `adb shell setprop persist.adb.tcp.port 5555`, then `adb shell getprop persist.adb.tcp.port`. It should print `5555`. If it prints nothing, the firmware doesn't allow it.
2. In Shizuku-Next, choose the USB start method, turn on *Start on boot*, *Watchdog* and *TCP mode*, and start it. Allow the *Allow USB debugging?* prompt for Shizuku's key.
3. Restart to check. Shizuku-Next should be running a few seconds after boot, with Wi-Fi on or off.
