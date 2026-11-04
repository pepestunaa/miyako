# Miyako

Miyako is a personal fork of [Yōkai](https://github.com/null2264/yokai), a free and open-source
manga reader for Android.

The installed app is still named **Yōkai**. **Miyako** is the name of this repository and fork.
The goal is to keep Yōkai's UI/UX and features intact while making the app lighter, more
memory-efficient, and smoother. The manga details page takes UX inspiration from Usagi.

## What Miyako keeps

Yōkai's core reading, library, and source features remain available:

- Configurable reader and viewers
- Trackers for MyAnimeList, AniList, Kitsu, MangaUpdates, Shikimori, and Bangumi
- Categories, library updates, and backups
- Local source support with `ComicInfo.xml`
- TachiyomiJ2K features such as the staggered library, dynamic categories, recents, statistics, and
  shortcuts

## Highlights of this fork

### Performance and maintenance

- Removed unused dependencies, including Firebase Crashlytics/Analytics, Conscrypt, QuickJS,
  `kotlin-reflect`, and `slice`
- Removed the onboarding flow and its entry points
- Uses English-only resources and compressed JNI libraries in the standard flavor
- Replaced `image-decoder` with a libavif-backed AVIF decoder
- Adds a disk image cache and reduces reader preloading in power-save mode or when free RAM is low
- Uses update-or-insert for history writes
- Speeds up local builds with the `devAbi` flag and parallel Gradle work

### Manga details

- Usagi-style page grid
- Swipe-left page preview with magnetic swipe and a one-time gesture hint
- Automatic anchoring to the last-read page
- Edge scrims to keep system-bar content readable
- Yōkai's original chapter-row swipe actions
- Back-to-top button

## Build from source

### Requirements

- Git
- JDK 17 or newer (CI uses JDK 21)
- Android SDK with compileSdk 36
- Android NDK `27.2.12479018`

If Gradle cannot find the Android SDK, set `ANDROID_HOME` or create `local.properties` with:

```properties
sdk.dir=/path/to/Android/Sdk
```

### Clone and build

```sh
git clone https://github.com/pepestunaa/miyako.git
cd miyako

# Build all ABIs and a universal APK
./gradlew :app:assembleStandardRelease

# Build one ABI for a faster local build (example: arm64)
./gradlew :app:assembleStandardRelease -PdevAbi=arm64-v8a
```

The APKs are written to `app/build/outputs/apk/standard/release/`:

- `app-standard-<abi>-release-unsigned.apk` for an individual ABI
- `app-standard-universal-release-unsigned.apk` when building all ABIs

Release APKs are unsigned. Align and sign one before installing, for example with the debug
keystore:

```sh
cd app/build/outputs/apk/standard/release

zipalign -f 4 app-standard-arm64-v8a-release-unsigned.apk \
  app-standard-arm64-v8a-release-aligned.apk
apksigner sign \
  --ks ~/.android/debug.keystore \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out app-standard-arm64-v8a-release-signed.apk \
  app-standard-arm64-v8a-release-aligned.apk
adb install -r app-standard-arm64-v8a-release-signed.apk
```

`zipalign` and `apksigner` are included in the Android SDK build-tools directory. On Windows, use
`zipalign.exe` and `apksigner.bat`.

The standard release uses Yōkai's application ID, `eu.kanade.tachiyomi.yokai`. Uninstall the
official app first and back up your library before installing this build. Android 8.0 or newer is
required.

### Development builds

Development happens on the `dev` branch. While iterating, always build the debug variant (never
`assembleStandardRelease`): it is signed automatically, packages only the target ABI, and skips lint,
so the whole loop is faster.

```sh
git checkout dev
./gradlew :app:assembleStandardDebug -PdevAbi=arm64-v8a --console=plain -x lint
adb install -r app/build/outputs/apk/standard/debug/app-standard-arm64-v8a-debug.apk
```

The debug build installs as `eu.kanade.tachiyomi.debugYokai`, alongside the release build. Reserve
`assembleStandardRelease` for verifying release-only behavior such as minification and resource
shrinking.

Always build and run the latest APK on a device after a change. A successful compile is not proof
that the UI works.

## Contributing and bug reports

Miyako is a personal fork. Fixes that affect upstream should be contributed to
[Yōkai](https://github.com/null2264/yokai).

When reporting an issue here, include:

1. The app version from **Settings → About → Version**
2. Steps to reproduce the problem
3. A screenshot or Pastebin log, if available

## Credits

Miyako is built on:

- [Yōkai](https://github.com/null2264/yokai) by null2264
- [Mihon](https://github.com/mihonapp/mihon)
- [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi)
- TachiyomiJ2K

The manga details UX is inspired by Usagi.

## Disclaimer

The developer(s) of this application are not affiliated with the available content providers. The
application hosts no content.

## License

- Copyright © 2015 Javier Tomás
- Copyright © 2024 null2264
- Copyright © 2026 pepestunaa (Miyako fork modifications)

Licensed under the [Apache License, Version 2.0](/LICENSE).
