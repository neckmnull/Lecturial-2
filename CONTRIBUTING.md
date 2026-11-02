# Contributing to Lecturial 2

Read the [README](README.md), [architecture overview](docs/ARCHITECTURE.md), and [interface guidelines](DESIGN.md). Discuss substantial changes in an Issue before implementation.

## Pull requests

1. Create a focused branch with a descriptive name.
2. Keep each change limited to one concern and follow the existing Kotlin style.
3. Explain the problem, the new behavior, and how the change was verified. Include screenshots without personal data for UI changes.
4. Run all relevant checks and report their results. Explicitly state anything that was not tested.
5. Update documentation when changing structure, build steps, or behavior.

Never commit signing keys, passwords, tokens, `local.properties`, real cookies or CSRF values, or user databases. Use synthetic values in networking examples.

Debug builds install separately from release builds. Do not remove the release app just to verify a debug build.

## Building and checking

Requirements: JDK 17, Android Studio compatible with Android Gradle Plugin 9.3.1, Android SDK
Platform 37 and Build Tools 36.0.0, and an Android 8.0+ device or emulator.

```powershell
git clone https://github.com/neckmnull/Lecturial-2.git
Set-Location Lecturial-2
.\gradlew.bat assembleDebug
```

On Linux or macOS run `chmod +x gradlew` once, then `./gradlew assembleDebug`. The debug APK
is written to `app/build/outputs/apk/debug/app-debug.apk`; its package name ends in `.debug`,
so debug and release builds install side by side without sharing app data.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest # requires a device or emulator
```

The live integration test is opt-in because it depends on network access and the schedule
website's current contract:

```powershell
$env:LECTURIAL_LIVE_TEST = '1'
.\gradlew.bat testDebugUnitTest --tests '*SiriusLiveIntegrationTest' --rerun-tasks
Remove-Item Env:LECTURIAL_LIVE_TEST
```

To build signed APK/AAB artifacts, copy the secret-free template and provide your own release
key values. Without signing configuration, release outputs are not ready for installation.

```powershell
Copy-Item keystore.properties.example keystore.properties
.\gradlew.bat assembleRelease bundleRelease
```

README screenshots come from made-up data, so no real schedule or grades are published.
To redraw them, install the debug and test APKs and run
`adb shell am instrument -w -e readmeScreenshots true -e class com.rassvet.lecturial.ui.ReadmeScreenshots com.rassvet.lecturial2.debug.test/androidx.test.runner.AndroidJUnitRunner`;
the PNGs land in the debug app's `cache/readme/`.

| Component | Version / choice |
|---|---|
| Language and UI | Kotlin with Jetpack Compose |
| `compileSdk` / `targetSdk` | 37 / 37 |
| Gradle Wrapper | 9.5.0 |
| Android Gradle Plugin | 9.3.1 with built-in Kotlin support |
| Compose Compiler plugin | 2.3.21 |
| Compose BOM | 2026.08.00 |
| Material 3 | 1.5.0-alpha27 |
| Persistence | Room 2.8.4 and DataStore 1.2.1 |
| Networking | OkHttp 5.1.0 and Kotlin serialization JSON |

## License

Make sure you have the right to distribute every submitted change. Contributions are accepted under Apache License 2.0 according to the project license. Third-party materials must include their source and license information.