# Contributing

Thanks for helping. You can help by reporting how the app works on your device, by reporting a bug, or by sending a change.

## Report how it works on your device

The app is developed and tested on a Retroid Pocket Nova with Android 13, so reports from other devices help most, even when everything worked. Fill in the [device report](https://github.com/clintonium-119/es-de-overlay/issues/new?template=device-report.yml) form: it asks for the device, its Android, ES-DE and app versions, the controller, how you installed the app, and which features worked.

If something didn't work, also open a [bug report](https://github.com/clintonium-119/es-de-overlay/issues/new?template=bug.yml) with what you expected instead, and a log if you can get one. [Getting a log](https://clintonium-119.github.io/es-de-overlay/troubleshooting.html#getting-a-log) shows how.

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

### Fuzz tests

The parsers that read files from elsewhere have fuzz tests, `*FuzzTest.kt` in `app/src/test`, written with [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer): ES-DE's log and settings, gamelists, theme files, saved and downloaded guide pages, and text guides. `./gradlew testDebugUnitTest` runs them as regression tests, once for each input saved under `app/src/test/resources/com/clintonium/es_de_overlay/core/<Test>Inputs/<method>/`.

To fuzz one, set `JAZZER_FUZZ` and name a single test class:

```sh
JAZZER_FUZZ=1 ./gradlew testDebugUnitTest --tests 'com.clintonium.es_de_overlay.core.XmlFuzzTest'
```

A run fuzzes only one test, so for a class with several, name the method too: `--tests '*EsThemeFuzzTest.tar'`. Each test runs for 10 minutes; set `JAZZER_MAX_DURATION=30s` for a shorter run. Each input may take 10 seconds. An input that fails is saved beside the others, and the test then replays it as a regression case. Fix the cause, keep the input, and commit both.

## Send a change

Changes reach `master` through pull requests. Each pull request runs the unit tests, Android Lint and CodeQL, and they have to pass before it can merge.

- Keep a pull request to one change, and say what you checked and on which device.
- If you add or change logic, add or update its unit tests in `app/src/test`.
- If the change is one a user would notice, add a line for it under **Unreleased** in [CHANGELOG.md](CHANGELOG.md).
- If it changes what the app does, update the docs under `docs/` to match.
