<div align="center">

<a href="https://github.com/pepestunaa/miyako">
    <img src="./.github/readme-images/app-icon.webp" alt="Yōkai app icon" height="200px" width="200px" />
</a>

# Miyako

**A personal fork of [Yōkai](https://github.com/null2264/yokai): lighter, more memory-efficient, and smoother, while keeping Yōkai's UI/UX language and feature set.**

[![License: Apache-2.0](https://img.shields.io/github/license/pepestunaa/miyako?labelColor=27303D&color=0877d2)](/LICENSE)

<img src="./.github/readme-images/screens.gif" alt="Yōkai screenshots" />

</div>

## About This Fork

Miyako is the name of this repository, not a new app. The installed app is still **Yōkai**, and this
fork keeps its name, look, navigation, gestures, and full feature set. Yōkai itself is a fork of
[Mihon](https://github.com/mihonapp/mihon), which descends from the original
[Tachiyomi](https://github.com/tachiyomiorg/tachiyomi).

The fork focuses on three things:

- **Lighter**: trim dependencies and build extras the app does not need.
- **Lower memory**: cache images on disk and back off reader preloading when memory is tight.
- **Smoother**: make the manga details page feel fluid, using Usagi's page grid and pages preview as
  the UX reference.

No Yōkai reading or library feature is removed. UI additions stay inside the manga details screen,
reuse Yōkai's existing visual language, and do not introduce new screens or flows.

## Download

There are no prebuilt releases for this fork yet. Build the standard flavor from source:

```sh
./gradlew :app:assembleStandardRelease
```

Requires Android 8.0 or higher. Release APKs are unsigned, so sign them with your own key before
installing. The standard release shares the same application ID as Yōkai
(`eu.kanade.tachiyomi.yokai`), so uninstall the official app before installing this one; back up
your library first.

## Changes in This Fork

### Lighter and faster

- Removed Firebase Crashlytics/analytics, the Conscrypt TLS provider, QuickJS, `kotlin-reflect`,
  `slice`, and other unused dependencies.
- Removed the onboarding flow and its entry points.
- Standard flavor ships English-only resources and compressed JNI libraries.
- Replaced `image-decoder` with a libavif-backed AVIF decoder and a platform decoder shim.
- Added disk image caching; reader preloading backs off in power-save mode or when free RAM is low.
- Fixed history writes to use update-or-insert instead of upserts.
- Added a `devAbi` flag and parallel Gradle for quicker local builds.

### Manga details

- Usagi-style page grid: every chapter expands into its page thumbnails on the details screen.
- Swipe left on the details screen to slide in a right-docked pages preview, with a one-time swipe
  hint on first use.
- Magnetic swipe: a short swipe or light flick completes opening and closing the preview.
- The preview opens anchored to the last-read page.
- The preview page marker follows the most recently read chapter, even when reading ahead in a manga
  with many chapters.
- Top and bottom scrims keep the status bar and navigation area readable over the preview.
- Chapter rows keep their original swipe actions.
- A back-to-top button appears once the details screen is scrolled down.

## Features

Miyako keeps Yōkai's reading, library, and source features; none are removed. The feature set comes
from Yōkai's lineage:

### From Yōkai

- NSFW/SFW library filter (taken from [TachiyomiSY](https://github.com/jobobby04/TachiyomiSY)).
- Fix backup incompatibility with upstream.
- New theme.
- Local Source chapters now reads ComicInfo.xml for chapter title, number, and scanlator.

### From upstream (Tachiyomi/Mihon)

- Local reading of downloaded content.
- A configurable reader with multiple viewers, reading directions and other settings.
- Tracker support: [MyAnimeList](https://myanimelist.net/), [AniList](https://anilist.co/),
  [Kitsu](https://kitsu.app/explore/anime), [Manga Updates](https://www.mangaupdates.com/),
  [Shikimori](https://shikimori.one), and [Bangumi](https://bgm.tv/) support.
- Categories to organize your library.
- Light and dark themes.
- Schedule updating your library for new chapters.
- Create backups locally to read offline or to your desired cloud service.

### From J2K

- UI redesign.
- New Manga details screens, themed by their manga covers.
- Combine 2 pages while reading into a single one for a better tablet experience.
- An expanded toolbar for easier one handed use (with the option to reduce the size back down).
- Floating searchbar to easily start a search in your library or while browsing.
- Library redesigned as a single list view: see categories listed in a vertical view that can be
  collapsed or expanded with a tap.
- Staggered Library grid.
- Drag & Drop sorting in Library.
- Dynamic Categories: group your library automatically by the tags, tracking status, source, and more.
- New Recents page: providing quick access to newly added manga, new chapters, and to continue where
  you left off in a series.
- Stats page.
- New themes.
- Dynamic Shortcuts: open the latest chapter of what you were last reading right from your homescreen.
- [New material snackbar](.github/readme-images/material%20snackbar.png): removing manga now auto
  deletes chapters and has an undo button in case you change your mind.
- Batch Auto-Source Migration (taken from [TachiyomiEH](https://github.com/NerdNumber9/TachiyomiEH)).
- [Share sheets upgrade for Android 10](.github/readme-images/share%20menu.png).
- View all chapters right in the reader.
- A lot more Material Design You additions.
- Android 12 features such as automatic extension and app updates.

## Contributing

This is a personal fork; pull requests are welcome, but upstream contributions belong to
[Yōkai](https://github.com/null2264/yokai).

When reporting an issue on this fork:

- Include the version (**Settings → About → Version**) and clear steps to reproduce.
- Include a screenshot when relevant, and use [Pastebin](https://pastebin.com/) for long logs.
- Report issues that also affect upstream to the [Yōkai issue tracker](https://github.com/null2264/yokai/issues).

## Credits

This fork exists thanks to the [Yōkai](https://github.com/null2264/yokai) project by null2264 and
everyone who contributed to it. It also builds on the work of
[Mihon](https://github.com/mihonapp/mihon), [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi),
and TachiyomiJ2K. The manga details UX takes inspiration from Usagi.

<a href="https://github.com/null2264/yokai/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=null2264/yokai" alt="Yōkai app contributors" title="Yōkai app contributors" width="600"/>
</a>

## Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

## License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 null2264
Copyright © 2026 pepestunaa (Miyako fork modifications)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>
