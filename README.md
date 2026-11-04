# Miyako

Miyako is a personal fork of [Yōkai](https://github.com/null2264/yokai), a free and open-source
manga reader for Android. The installed app is still named **Yōkai**; Miyako is the name of this
fork. The goal is a lighter, more memory-efficient, and smoother app while keeping Yōkai's UI/UX
and features intact.

## What's different

Yōkai's core reading, library, and source features remain available, including TachiyomiJ2K
features such as the staggered library, dynamic categories, recents, and statistics.

This fork adds:

- A disk image cache and reduced reader preloading under memory pressure
- An AVIF decoder backed by libavif
- Removal of unused dependencies (Firebase, Conscrypt, QuickJS) and the onboarding flow
- A Usagi-inspired manga details page: page grid, swipe-left preview, last-read anchoring,
  back-to-top button

## Build

Requirements: Git, JDK 17+, Android SDK (compileSdk 36), NDK `27.2.12479018`.

```sh
git clone https://github.com/pepestunaa/miyako.git
cd miyako

# Release build (all ABIs, or one ABI with -PdevAbi for faster local builds)
./gradlew :app:assembleStandardRelease

# Debug build for development, signed automatically and faster to iterate on
./gradlew :app:assembleStandardDebug -PdevAbi=arm64-v8a
```

APKs are written to `app/build/outputs/apk/standard/`. Release APKs are unsigned — align and sign
them before installing:

```sh
zipalign -f 4 app-standard-arm64-v8a-release-unsigned.apk aligned.apk
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --out signed.apk aligned.apk
adb install -r signed.apk
```

`zipalign` and `apksigner` ship with the Android SDK build-tools (on Windows, use `apksigner.bat`).
The release build uses Yōkai's application ID `eu.kanade.tachiyomi.yokai`, so uninstall the
official app first. Android 8.0+ is required.

## Issues

Miyako is a personal fork; fixes that affect upstream belong in
[Yōkai](https://github.com/null2264/yokai). When reporting an issue here, include the version from
**Settings → About** and steps to reproduce.

## Credits and license

Built on [Yōkai](https://github.com/null2264/yokai), [Mihon](https://github.com/mihonapp/mihon),
[Tachiyomi](https://github.com/tachiyomiorg/tachiyomi), and TachiyomiJ2K. The manga details UX is
inspired by [Usagi](https://github.com/UsagiApp/Usagi). The app hosts no content and is not
affiliated with any content provider.

- Copyright © 2015 Javier Tomás
- Copyright © 2024 null2264
- Copyright © 2026 pepestunaa (Miyako fork modifications)

Licensed under the [Apache License 2.0](/LICENSE).
