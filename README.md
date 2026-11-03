# Miyako

A personal fork of [Yōkai](https://github.com/null2264/yokai), a free and open source manga reader
for Android. The installed app is still **Yōkai**; Miyako is only the repository name.

The fork keeps Yōkai's UI/UX language and feature set while making the app lighter, more
memory-efficient, and smoother. The manga details page uses Usagi as the UX reference.

## Build from source

Requirements: Git, JDK 17 or newer (CI uses 21), Android SDK with compileSdk 36, and NDK
27.2.12479018. If Gradle cannot find the SDK, set `ANDROID_HOME` or create `local.properties` with
`sdk.dir=/path/to/Android/Sdk`.

```sh
git clone https://github.com/pepestunaa/miyako.git
cd miyako

# All ABIs plus a universal APK
./gradlew :app:assembleStandardRelease

# Faster single-ABI build (example: arm64)
./gradlew :app:assembleStandardRelease -PdevAbi=arm64-v8a
```

Output: `app/build/outputs/apk/standard/release/app-standard-<abi>-release-unsigned.apk`, plus
`app-standard-universal-release-unsigned.apk` when building all ABIs.

The release APK is unsigned. Sign it before installing, for example with the debug keystore:

```sh
cd app/build/outputs/apk/standard/release

zipalign -f 4 app-standard-arm64-v8a-release-unsigned.apk \
  app-standard-arm64-v8a-release-aligned.apk
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out app-standard-arm64-v8a-release-signed.apk app-standard-arm64-v8a-release-aligned.apk
adb install -r app-standard-arm64-v8a-release-signed.apk
```

`zipalign` and `apksigner` live in the Android SDK build-tools directory. On Windows, use
`zipalign.exe` and `apksigner.bat`.

The standard release shares Yōkai's application ID (`eu.kanade.tachiyomi.yokai`), so uninstall the
official app first and back up your library. Requires Android 8.0 or higher.

## Changes in this fork

- Removed Firebase Crashlytics/analytics, Conscrypt, QuickJS, `kotlin-reflect`, `slice`, and other
  unused dependencies.
- Removed the onboarding flow and its entry points.
- English-only resources and compressed JNI libraries in the standard flavor.
- Replaced `image-decoder` with a libavif-backed AVIF decoder.
- Disk image cache; reader preloading backs off in power-save mode or when free RAM is low.
- History writes use update-or-insert instead of upserts.
- Faster local builds with a `devAbi` flag and parallel Gradle.
- Manga details: Usagi-style page grid, swipe-left pages preview with magnetic swipe and a one-time
  hint, last-read page anchoring, edge scrims, original chapter row swipe actions, and a back-to-top
  button.

## Features

Yōkai's reading, library, and source features are all kept: configurable reader and viewers, trackers
(MyAnimeList, AniList, Kitsu, MangaUpdates, Shikimori, Bangumi), categories, library updates,
backups, local source with ComicInfo.xml, NSFW filter, and J2K UI features such as the staggered
library, dynamic categories, recents, stats, and shortcuts.

## Contributing

This is a personal fork. Fixes that affect upstream belong in
[Yōkai](https://github.com/null2264/yokai). When reporting an issue here, include the app version
(Settings → About → Version), reproduction steps, and a screenshot or Pastebin log.

## Credits

Built on [Yōkai](https://github.com/null2264/yokai) by null2264,
[Mihon](https://github.com/mihonapp/mihon), [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi),
and TachiyomiJ2K. Manga details UX inspired by Usagi.

## Disclaimer

The developer(s) of this application does not have any affiliation with the content providers
available, and this application hosts zero content.

## License

Copyright © 2015 Javier Tomás
Copyright © 2024 null2264
Copyright © 2026 pepestunaa (Miyako fork modifications)

Licensed under the [Apache License, Version 2.0](/LICENSE).
