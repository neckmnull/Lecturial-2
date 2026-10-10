# Publishing Lecturial 2

## Repository package

The source tree includes Gradle Wrapper, README, architecture and design documentation, contribution guidelines, GitHub templates, Apache License 2.0, and the Manrope license.

## Before public distribution

1. Verify the right to redistribute `onboarding_sirius_logo.png`. No evidence of permission is bundled with this repository; obtain permission or replace it with original artwork. See [third-party notices](../THIRD_PARTY_NOTICES.md).
2. Confirm that Apache License 2.0 is the intended license. It permits commercial use and closed-source derivatives subject to its terms.
3. Make sure you own or have permission to distribute all submitted code. The attribution notice uses the neutral name `Lecturial 2 contributors`; update README and NOTICE if a specific person or organization should be named.
4. Review staged files before every release. `.gitignore` does not remove secrets that have already been tracked.

## Creating the repository

Recommended repository name: `Lecturial-2`.

Recommended description: `Unofficial Android schedule app for Sirius University with Jetpack Compose, offline cache, widget, and notifications.`

Recommended topics: `android`, `kotlin`, `jetpack-compose`, `material3`, `schedule`, `room`, `datastore`, `android-widget`.

Create an empty repository without generated README, `.gitignore`, or license files because they are already included. Keep it private until all third-party material has been reviewed.

```powershell
git init -b main
git add .
git status --short
git diff --cached --stat
git diff --cached --name-only
```

Before committing, verify that `keystore/`, `keystore.properties`, `local.properties`, `.gradle/`, `.kotlin/`, build outputs, and `publication/` are absent. `keystore.properties.example` should remain because it contains no real credentials.

```powershell
git commit -m "Initial public source release"
git remote add origin https://github.com/YOUR_ACCOUNT/Lecturial-2.git
git push -u origin main
```

For an existing Git repository, retain its current configuration and branches. On Linux/macOS, preserve the executable bit with `git update-index --chmod=+x gradlew`.

## Publishing an app release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts` and the version shown in README.
2. Run the relevant checks and build APK/AAB artifacts with the long-term release key. Verify an in-place upgrade over the previous release without deleting app data.
3. Create a version tag such as `v1.4.4` only after confirming the corresponding build.
4. Create a GitHub Release with English notes and attach the signed APK for direct installation. AAB files are intended for stores, not direct installation.
5. Never attach the keystore, passwords, or local configuration. Review transitive dependency licenses before binary distribution.
6. Back up the release key securely. Future updates with the same application ID must use the same key.

Build commands are listed in the [README](../README.md). Preparing the repository does not by itself verify UI behavior, notifications, widgets, or installation on a physical device.