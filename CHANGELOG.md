# Changelog

All notable Deckscape changes are documented here. Versions follow semantic
versioning, and public Android `versionCode` values increase once per release.

## [1.5.0] - 2026-08-10

### Added

- Independent **Add** and **Remove** controls for slideshow membership. Removing
  a wallpaper keeps its downloaded file available on the device.
- A confirmed **Delete** action that removes a wallpaper from the device and
  safely advances the current selection when necessary.

### Changed

- The wallpaper library now shows both included and on-device wallpapers so
  excluded items can be restored without downloading them again.
- Wallpaper title overlays are smaller and more transparent, and displayed
  wallpaper names no longer include file extensions.
- The library remains accessible when Deckscape is not the active wallpaper.

### Security and reliability

- Delete operations are restricted to validated files inside Deckscape's
  private wallpaper directory.
- Existing installations migrate without changing slideshow membership: every
  previously downloaded wallpaper remains included until the user removes it.
- The local test suite now contains 34 tests, alongside strict Android lint and
  minified release-build verification.

## [1.4.0] - 2026-08-09

### Added

- A dedicated **Slideshow** library showing every downloaded wallpaper and the
  image currently displayed, with a per-item **Show now** control.
- Separate **Preview** controls that do not download or select a wallpaper.
- Animated GIF playback inside the in-app preview, capped at 10 fps and paused
  when the preview is not visible.
- A one-time landscape setup guide before Android's live-wallpaper activation
  screen, including a warning that some head units may briefly rotate it.
- Daily stable-release checks and automatic APK downloads over any available
  internet connection, including mobile data.
- An explicit **Install update** flow using Android's package installer.

### Changed

- Every downloaded wallpaper now joins the slideshow automatically. Selecting
  **Show now** changes the current image without removing the other downloads.
- Wallpaper cards clearly distinguish **Now showing**, **In slideshow**, and
  **Ready** states.
- Wallpaper titles are overlaid on thumbnails for more room and better
  readability; **Preview** and **Download/Show now** share an equal-width row.
- Download progress is displayed inside the card's download button.
- The top bar now reports **Deckscape on** or **Setup needed**, and its adjacent
  action opens either the slideshow or activation guide.
- The Info panel now includes update status, manual update checking, preview
  cache controls, and clearer network behaviour.

### Security and reliability

- Update metadata is restricted to Deckscape's fixed GitHub repository and
  stable semantic versions.
- Update downloads enforce HTTPS host allowlists, redirect and response-size
  limits, exact asset naming, and SHA-256 verification.
- APKs are checked for package identity, exact advertised version, increasing
  version code, and signing-certificate continuity before installation.
- Release builds additionally pin Deckscape's production signing certificate.
- Added parser, version, update-policy, and slideshow-library tests. The local
  suite now contains 32 tests, alongside strict Android lint and minified
  release-build verification.

## [1.3.0] - 2026-08-08

- Established the Deckscape product identity and public project metadata.
- Changed the Android application ID and Java namespace to
  `uk.darkbyte.deckscape`.
- Centralized product/version metadata for UI labels and network user agents.
- Hardened GitHub redirect validation, cache initialization, preview lifecycle
  cleanup, custom-source recovery, and installed-image decoding.
- Added package and API documentation plus identity-focused unit tests.
- Adopted the MIT License and expanded the GitHub README and community files.
- Published the first update-compatible signed APK through GitHub Releases.

## 1.2.1 - 2026-08-08

- Matched the wallpaper-status control to the 48 dp height, padding, border,
  corner radius, and spacing used by the adjacent top-bar controls.

## 1.2.0 - 2026-08-08

- Added Vyrx Wallpapers, Aesthetic Wallpapers, Wall-E-Desk, and Terminal
  Wallpapers as curated defaults with verified branches and starting folders.
- Added representative wallpaper covers and centered names to category cards.
- Added strong browsing, downloaded, selected, and active visual states.
- Moved determinate download progress into each wallpaper action button.
- Preserved the last browsed source across Android activity recreation.

## 1.1.0 - 2026-08-08

- Image-generation-guided UI redesign for the 1920×1080 head-unit profile.
- Artwork-first wallpaper cards with compact metadata and clearer Apply actions.
- Compact category tiles, selected category chips, and a color-coded breadcrumb.
- Grouped activation and rotation controls with inline loading/status feedback.
- Refined source rail with folder icons and a custom dark add-source dialog.
- Emulator-verified the design at the target head-unit profile.

## 1.0.0 - 2026-08-07

- Production landscape head-unit wallpaper interface.
- Wallz, elementary, and KDE Breeze default GitHub sources.
- User-added public repositories with branch and folder validation.
- Folder categories, recursive all-wallpaper view, and local search.
- Lazy 480×270 preview generation with bounded memory/disk caches.
- One-touch download, validation, selection, and live-wallpaper activation.
- Static and animated GIF wallpaper rendering with hidden-state pause.
- Manual and timed rotation schedules.
- CI, privacy, security, contribution, and architecture documentation.

[1.5.0]: https://github.com/MetalHepple/Deckscape/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/MetalHepple/Deckscape/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/MetalHepple/Deckscape/releases/tag/v1.3.0
