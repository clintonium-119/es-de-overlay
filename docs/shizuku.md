---
---

# Shizuku

Shizuku is optional. Android lets only system apps end another app. [Shizuku](https://shizuku.rikka.app) runs a small helper with adb's permissions, which is allowed to end apps. ES-DE Overlay uses it when it's running and allowed:

- **Quit Game** ends the game with nothing else on screen. Without Shizuku, it opens the game's App Info page in Settings for a moment and presses Force stop.
- **Quit games left with Home** (off by default, needs Shizuku) ends the game's app when you leave it with Home, as Quit Game would.
  - Progress since your last save is lost, and the game starts fresh next time.
  - In exchange, ES-DE's next launch of a different RetroArch game works the first time.
  - It covers every game ES-DE launched, emulators and Android games alike, except ES-DE Overlay and Shizuku themselves.
- **Themes from ES-DE's theme downloader** can be read, so the overlay takes their look (see [Theme](theme.md)).

## Set it up

1. Install one of them:
   - **Shizuku**, from [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or its [GitHub releases](https://github.com/RikkaApps/Shizuku/releases). Without root, it has to be started again after every restart.
   - **Shizuku-Next**, from its [GitHub releases](https://github.com/rushiranpise/Shizuku-Next/releases). It's a fork that can start itself on boot and restart itself if it stops. It's signed with a different key, so uninstall Shizuku first. This is the one in the setup the screenshots come from.
2. Start it, by one of the ways in [Shizuku's guide](https://shizuku.rikka.app/guide/setup/):
   - **Wireless debugging** (needs Wi-Fi):
     1. In Android's developer options, turn on *USB debugging* and *Wireless debugging*.
     2. In Shizuku, start pairing.
     3. Tap *Pair device with pairing code* under Wireless debugging, and type the code into Shizuku's notification.
     4. Tap **Start** in Shizuku.
   - **From a computer**: with USB debugging on and the device plugged in, run `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`.
   - On a rooted device, start it directly.
3. Shizuku-Next only: turn on *Start on boot*, and *Watchdog* to have it restart itself. If it was started through wireless debugging, that needs Wi-Fi at boot; see below for starting without it.
4. In ES-DE Overlay, look under **Quitting games**.
   - The **Shizuku** row reads *Not installed*, *Not running* (**Open Shizuku** opens it), *Not allowed* or *Enabled*.
   - Tap **Allow** and allow ES-DE Overlay in Shizuku's dialog; the row turns *Enabled*.
   - Then turn on **Quit games left with Home** if you want it. The switch can't be turned on while Shizuku isn't *Enabled*.

## Advanced: start Shizuku-Next without Wi-Fi

Some firmware lets adb listen on a network port from every boot. Shizuku-Next's USB start method can then use that port, with no Wi-Fi and no computer.

- This works on the Retroid Pocket Nova. Many phones refuse the setting; stock Samsung firmware does.
- It leaves adb listening on port 5555 on every network the device joins. A connection still needs a computer key you've allowed, but undo it if that matters to you.

1. With USB debugging on and the device plugged into a computer, run `adb shell setprop persist.adb.tcp.port 5555`, then `adb shell getprop persist.adb.tcp.port`. It should print `5555`; if it prints nothing, the firmware doesn't allow it.
2. In Shizuku-Next, choose the USB start method, turn on *Start on boot*, *Watchdog* and *TCP mode*, and start it. Allow the *Allow USB debugging?* prompt for Shizuku's key.
3. Restart to check. Shizuku-Next should be running a few seconds after boot, with Wi-Fi on or off.

To undo it, run `adb shell setprop persist.adb.tcp.port ""` and restart.
