# Third-party notices

Apache License 2.0 applies to original Lecturial 2 code and documentation. Third-party materials retain their respective terms.

## Manrope

Files matching `app/src/main/res/font/manrope_*.ttf` contain the Manrope typeface.

- Copyright: Copyright 2018 The Manrope Project Authors.
- Source: https://github.com/googlefonts/manrope.
- License: SIL Open Font License 1.1.
- Full text: [licenses/Manrope-OFL.txt](licenses/Manrope-OFL.txt).

## Sirius names, logo, and schedule data

The project contains `app/src/main/res/drawable-nodpi/onboarding_sirius_logo.png` and the supporting file `artwork/render-onboarding-logo.html`. This source package does not include evidence of permission to redistribute the logo. Obtain permission from its owner or replace it with original artwork before public distribution, and update related references. Apache License 2.0 grants no rights to this logo.

Schedule data is loaded from https://schedule.siriusuniversity.ru/. The project license does not relicense website data and does not imply that the university officially supports this project.

## Build dependencies

AndroidX, Compose, Room, DataStore, Kotlin, kotlinx, OkHttp, and build tools are resolved through Gradle. Versions are listed in `app/build.gradle.kts` and `build.gradle.kts`; each dependency remains subject to its owner’s terms. Gradle Wrapper files live in `gradle/wrapper/`; Gradle uses Apache License 2.0: https://github.com/gradle/gradle/blob/master/LICENSE.

This document identifies bundled material and major dependency groups. It is not a substitute for a complete transitive-license report when distributing APK or AAB artifacts.