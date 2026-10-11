# Lecturial 2

<img src="artwork/play-store-icon-512.png" alt="Lecturial 2 icon" width="112">

An unofficial Android app for viewing the Sirius University schedule. It presents classes, instructors, and classrooms in a dark interface with offline caching, a home-screen widget, and notifications.

**Android 8.0+ · Kotlin · Jetpack Compose · Material 3 · Apache 2.0**

## Features

- Daily schedules, week navigation, and a quick return to today.
- Class times, lesson types, instructors, and classrooms, including current-class highlighting and remaining time.
- Pull-to-refresh and cache-first loading backed by Room. Previously loaded weeks remain available offline.
- Group and foreign-language instructor selection, plus custom subject names.
- Configurable Today widget with stripe colors, visible fields, text sizes, and spacing.
- Reminders, class start/end notifications, and a live notification for the current class.
- Light and dark appearance modes, editable built-in palettes, custom themes, and configurable schedule typography.

Available groups and search results are loaded from the Sirius University schedule website.

## Technology and version

| Component | Value |
| --- | --- |
| App version | 1.4.5 (`versionCode` 24) |
| Minimum Android version | API 26 / Android 8.0 |
| `compileSdk` / `targetSdk` | 37 / 37 |
| JDK | 17 |
| Gradle Wrapper | 9.5.0 |
| Android Gradle Plugin | 9.3.1 with built-in Kotlin support |
| Compose Compiler plugin | 2.3.21 |
| Compose BOM | 2026.08.00 |
| Material 3 | 1.5.0-alpha27 |
| Storage | Room 2.8.4, DataStore 1.2.1 |
| Networking | OkHttp 5.1.0, JSON |

## Quick start

1. Download the source or clone the repository and open its root directory in an Android Studio version compatible with AGP 9.3.1.
2. Select JDK 17 for Gradle. Install Android SDK Platform 37.0 (API 37) and Build Tools 36.0.0 through SDK Manager.
3. Wait for Gradle Sync. Android Studio creates a local `local.properties` file containing the SDK path. For command-line builds, `ANDROID_HOME` can be used instead.
4. Run the `app` configuration on an Android 8.0+ device or emulator.

PowerShell from the project root:

```powershell
.\gradlew.bat assembleDebug
```

Linux/macOS with JDK and Android SDK installed:

```bash
chmod +x gradlew
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Install it with Android Platform Tools:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.rassvet.lecturial.debug/com.rassvet.lecturial.MainActivity
```

The debug build uses `com.rassvet.lecturial.debug`; the release build uses `com.rassvet.lecturial`. They can be installed side by side and keep separate data. Updating an installed release requires the same signing key.

## Verification

The project includes unit and instrumented tests:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

The last command requires a device or emulator. The live-site integration test is disabled by default and can be run separately:

```powershell
$env:LECTURIAL_LIVE_TEST = '1'
.\gradlew.bat testDebugUnitTest --tests '*SiriusLiveIntegrationTest' --rerun-tasks
Remove-Item Env:LECTURIAL_LIVE_TEST
```

That test depends on network access and the website’s current contract. A successful build alone does not verify the UI, widget, or notifications on a physical device.

## Release build

Copy the template and fill it with your local signing-key values:

```powershell
Copy-Item keystore.properties.example keystore.properties
```

Then build the release artifacts:

```powershell
.\gradlew.bat assembleRelease bundleRelease
```

Outputs are written to `app/build/outputs/apk/release/` and `app/build/outputs/bundle/release/`. Without signing configuration, release artifacts are not ready for installation or store submission. Signing keys and passwords are excluded from Git; debug builds do not require them.

## Project structure

```text
Lecturial/
├── README.md                       # Overview, setup, build, and structure
├── LICENSE                         # Apache License 2.0
├── NOTICE                          # Attribution and third-party notice
├── THIRD_PARTY_NOTICES.md          # Fonts, logo, data, and dependencies
├── CONTRIBUTING.md                 # Contribution guidelines
├── DESIGN.md                       # Interface guidelines
├── docs/
│   ├── ARCHITECTURE.md             # Layers and data flow
│   └── PUBLISHING.md               # GitHub and release process
├── licenses/Manrope-OFL.txt        # Embedded font license
├── .github/                        # Issue and pull-request templates
├── build.gradle.kts                # Gradle plugin versions
├── settings.gradle.kts             # Repositories and app module
├── gradle/                         # Gradle Wrapper files
├── gradlew / gradlew.bat           # Pinned Gradle launchers
├── keystore.properties.example     # Secret-free signing template
├── artwork/                        # App icon source artwork
└── app/
    ├── build.gradle.kts            # SDK, variants, versions, dependencies
    ├── proguard-rules.pro          # Release optimization rules
    └── src/
        ├── main/                   # Production app, resources, widget
        ├── debug/                  # Compose test activity and manifest
        ├── test/                   # JVM tests
        └── androidTest/            # Android and Compose UI tests
```

Local `local.properties`, `keystore.properties`, `keystore/`, `.gradle/`, `.kotlin/`, `build/`, and `publication/` paths are excluded from published source.

## Architecture and data source

`SiriusScheduleDataSource → ScheduleRepository → ScheduleViewModel → Compose UI`.

The adapter manages cookies, CSRF, and structured Livewire responses from the [schedule website](https://schedule.siriusuniversity.ru/). The repository stores weeks in Room, while preferences are stored in DataStore. Cache updates synchronize the widget and notifications.

See [Architecture](docs/ARCHITECTURE.md), [Design](DESIGN.md), and [Publishing](docs/PUBLISHING.md) for details.

## Limitations

- This app is not an official university project.
- The website’s Livewire contract is not a public API and may change.
- Only previously cached weeks are available offline, and cached data may become stale.
- Notifications depend on permissions and Android background-execution restrictions.
- The project currently uses an alpha version of Material 3.

## Contributing and license

Suggestions and bug reports are welcome through GitHub Issues. See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

Original code and documentation are licensed under the [Apache License 2.0](LICENSE). Copyright 2026 Lecturial 2 contributors. Apache 2.0 permits commercial use and closed-source derivatives subject to its terms.

Manrope is licensed under SIL OFL 1.1. Third-party names, logos, and data are not relicensed; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Verify permission to distribute the Sirius logo before making the repository or binaries public.