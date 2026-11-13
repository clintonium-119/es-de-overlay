# Contributing

Thanks for helping. You can help by reporting how the app works on your device, by reporting a bug, or by sending a change.

## Report how it works on your device

The app is developed and tested on a Retroid Pocket Nova with Android 13, so reports from other devices help most. [Open an issue](https://github.com/clintonium-119/es-de-overlay/issues/new/choose) and include:

- the device, and its Android version;
- the ES-DE version, and your theme and its variant;
- the emulator and game;
- the ES-DE Overlay version, and whether you use Shizuku;
- what worked and what didn't, and what you expected instead;
- a log if you can get one. [Getting a log](https://clintonium-119.github.io/es-de-overlay/troubleshooting.html#getting-a-log) shows how.

Check [Troubleshooting](https://clintonium-119.github.io/es-de-overlay/troubleshooting.html) and [Known limitations](https://clintonium-119.github.io/es-de-overlay/limitations.html) first; your problem may be described there.

## Report a security problem

Don't open a public issue. [SECURITY.md](SECURITY.md) says how to report it privately.

## Build and test

You need JDK 17 and the Android SDK with platform 36. Point Gradle at the SDK with `ANDROID_HOME`, or with `sdk.dir` in `local.properties`. Then run the unit tests and Android Lint, and build a debug APK:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Lint's security checks fail the build (see `app/lint.xml`). To try the APK on a device connected with adb:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -G 16M && adb logcat -s ESDEShot   # the app's debug log
```

## Send a change

Changes reach `master` through pull requests. Each pull request runs the unit tests, Android Lint and CodeQL, and they have to pass before it can merge.

- Keep a pull request to one change, and say what you checked and on which device.
- If you add or change logic, add or update its unit tests in `app/src/test`.
- If the change is one a user would notice, add a line for it under **Unreleased** in [CHANGELOG.md](CHANGELOG.md).
- If it changes what the app does, update the docs under `docs/` to match.
