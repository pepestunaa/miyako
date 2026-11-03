<div align="center">

<a href="https://github.com/pepestunaa/miyako">
    <img src="./.github/readme-images/app-icon.webp" alt="Miyako logo" height="200px" width="200px" />
</a>

# Miyako

**A personal fork of [Yōkai](https://github.com/null2264/yokai)**

</div>

<div align="center">

A free and open source manga reader for Android

[![License: Apache-2.0](https://img.shields.io/github/license/pepestunaa/miyako?labelColor=27303D&color=0877d2)](/LICENSE)

<img src="./.github/readme-images/screens.gif" alt="Yokai screenshots" />

## Download

Builds of this fork are published on the [Releases](https://github.com/pepestunaa/miyako/releases) page of this repository.

*Requires Android 8.0 or higher.*

## About This Fork

Miyako is a personal fork of [Yōkai](https://github.com/null2264/yokai) maintained by
[pepestunaa](https://github.com/pepestunaa). Yōkai itself is a fork of
[Mihon](https://github.com/mihonapp/mihon), which descends from the original
[Tachiyomi](https://github.com/tachiyomiorg/tachiyomi).

The app keeps Yōkai's UI/UX and feature set intact. All changes in this fork aim to make the app
feel lighter, faster, and smoother — nothing from upstream is removed, and no new UI/UX that
contradicts Yōkai is introduced.

### Changes in this fork

**Lighter app, lower memory**
* Slimmed build: onboarding flow, crash reporting, and unused dependencies removed (`kotlin-reflect`),
  and `image-decoder` replaced with `libavif` plus the platform decoder.
* Disk image caching and reader preloading that backs off under memory pressure.
* History writes use update-or-insert instead of upserts.
* Faster local builds via a `devAbi` flag and parallel Gradle.

**Manga details and pages preview**
* Usagi-style page grid: each chapter expands into its page thumbnails right on the details screen.
* Swipe the details screen left to reveal a right-docked pages preview for every chapter, with a
  one-time affordance hint on first open.
* Magnetic swipes: a short swipe or light flick completes the open/close gesture, so a missed swipe
  never has to be repeated.
* The preview anchors to the last-read page with a smooth glide, and its page marker follows the
  most recently read chapter instead of the first unread one.
* Top and bottom scrims keep the status bar and navigation area readable over the preview.
* Chapters and Pages switch with a directional, drag-following swipe, styled like the app's main tabs,
  while chapter rows keep their original swipe actions.

## Features

Features inherited from the upstream lineage, kept unchanged in this fork.

<div align="left">

<details open="">
    <summary><h3>From Yōkai</h3></summary>

* NSFW/SFW library filter (taken from [TachiyomiSY](https://github.com/jobobby04/TachiyomiSY)).
* Fix backup incompatibility with upstream.
* New theme.
* Local Source chapters now reads ComicInfo.xml for chapter title, number, and scanlator.

</details>

<details open="">
    <summary><h3>From upstream (Tachiyomi/Mihon)</h3></summary>

* Local reading of downloaded content.
* A configurable reader with multiple viewers, reading directions and other settings.
* Tracker support:
  [MyAnimeList](https://myanimelist.net/),
  [AniList](https://anilist.co/),
  [Kitsu](https://kitsu.app/explore/anime),
  [Manga Updates](https://www.mangaupdates.com/),
  [Shikimori](https://shikimori.one),
  and [Bangumi](https://bgm.tv/) support.
* Categories to organize your library.
* Light and dark themes.
* Schedule updating your library for new chapters.
* Create backups locally to read offline or to your desired cloud service.

</details>

<details>
    <summary><h3>From J2K</h3></summary>

* UI redesign.
* New Manga details screens, themed by their manga covers.
* Combine 2 pages while reading into a single one for a better tablet experience.
* An expanded toolbar for easier one handed use (with the option to reduce the size back down).
* Floating searchbar to easily start a search in your library or while browsing.
* Library redesigned as a single list view: See categories listed in a vertical view, that can be collasped or expanded with a tap.
* Staggered Library grid.
* Drag & Drop Sorting in Library.
* Dynamic Categories: Group your library automatically by the tags, tracking status, source, and more.
* New Recents page: Providing quick access to newly added manga, new chapters, and to continue where you left on in a series.
* Stats Page.
* New Themes.
* Dynamic Shortcuts: open the latest chapter of what you were last reading right from your homescreen.
* [New material snackbar](.github/readme-images/material%20snackbar.png): Removing manga now auto deletes chapters and has an undo button in case you change your mind.
* Batch Auto-Source Migration (taken from [TachiyomiEH](https://github.com/NerdNumber9/TachiyomiEH)).
* [Share sheets upgrade for Android 10](.github/readme-images/share%20menu.png)
* View all chapters right in the reader.
* A lot more Material Design You additions.
* Android 12 features such as automatic extension and app updates.

</details>

</div>

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

<div align="left">

<details><summary>Issues</summary>

**Before reporting a new issue on this fork, take a look at this repository's [issues](https://github.com/pepestunaa/miyako/issues). Issues that also affect upstream should be reported to [Yōkai](https://github.com/null2264/yokai/issues) instead.**

</details>

<details><summary>Bugs</summary>

* Include version (**Settings → About → Version**).
  * If not latest, try updating, it may have already been solved.
  * Dev version is equal to the number of commits as seen in the main page.
* Include steps to reproduce (if not obvious from description).
* Include screenshot (if needed).
* If it could be device-dependent, try reproducing on another device (if possible).
* For large logs use [Pastebin](https://pastebin.com/) (or similar).
* Don't group unrelated requests into one issue.
- **DO**: [Example #1](https://git.mihon.tech/tachiyomi/tachiyomi/issues/24), [Example #2](https://git.mihon.tech/tachiyomi/tachiyomi/issues/71).
- **DON'T**: [Example #1](https://git.mihon.tech/tachiyomi/tachiyomi/issues/75).

</details>

<details><summary>Feature Requests</summary>

* Write a detailed issue, explaning what it should do or how.
  * Avoid writing just "like X app does"
* Include screenshot (if needed).

</details>

</div>

### Credits

This fork exists thanks to the [Yōkai](https://github.com/null2264/yokai) project and everyone who
contributed to it.

<a href="https://github.com/null2264/yokai/graphs/contributors">
    <img src="https://contrib.rocks/image?repo=null2264/yokai" alt="Yokai app contributors" title="Yokai app contributors" width="600"/>
</a>

### Disclaimer

The developer(s) of this application does not have any affiliation with the content providers available, and this application hosts zero content.

### License

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
</div>
