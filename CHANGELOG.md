# Changelog

All notable Deckscape changes are documented here. Versions follow semantic
versioning, and public Android `versionCode` values increase once per release.

## [1.7.2] - 2026-08-12

### Fixed

- Update verification now supports Android 10 vendor builds that return an
  empty modern signing-certificate field for downloaded APK archives.
- The verifier prefers Android's modern signing metadata and uses the legacy
  certificate field only when the modern field is unavailable. Existing strict
  checks for the installed signer, pinned Deckscape release certificate,
  package name, version, and increasing version code remain unchanged.

## [1.7.1] - 2026-08-12

### Fixed

- Update installation no longer redirects first to Android's per-app
  unknown-source settings screen. Some vendor head units resolve that standard
  intent to an OEM settings page which immediately reports that the feature is
  unsupported.
- Verified update APKs are now handed directly to Android's user-confirmed
  package installer so each head unit can apply its own installation policy.
  If no installer can be launched, Deckscape keeps the update dialog available
  and directs the user to the release page for a browser or file-manager install.

## [1.7.0] - 2026-08-12

### Added

- Large previous and next controls turn Preview into a gallery that follows the
  current folder or filtered search results.
- Preview now provides direct **Get** and **Set** actions with in-place download
  progress.
- The downloaded Library has **All**, **Day**, and **Night** views with live
  counts; Both-role wallpapers intentionally appear in both scheduled views.

### Changed

- Tapping a wallpaper thumbnail now opens its preview directly; the existing
  Preview button remains available.
- **Get** now downloads to the on-device Library only. It does not select the
  wallpaper or add it to rotation; **Set** is the explicit include-and-show
  action in the catalogue, Preview, and Library.
- Tapping a Library card's role badge now cycles **Both → Day → Night → Both**
  and refreshes the grouped counts immediately.
- Automatic sunrise/sunset now requests a reliable foreground GPS fix on older
  head units, allows up to one minute for a cold fix, and exposes an immediate
  cancel action while searching.
- Location denial, cancellation, and timeout preserve the chosen schedule mode;
  the visible Automatic/Manual selector no longer disagrees with saved state.
- Automatic scheduling now shows today's calculated sunrise and sunset as
  read-only times. The editable fallback fields appear only in Manual mode.
- Reusing a recent on-device location now explicitly says that a new GPS fix
  was unnecessary.
- About now presents each GitHub contributor as one friendly profile, including
  a cached profile image and a direct profile link.
- The creator's anonymous and GitHub-linked commit identities are merged into
  `Paul Hepple (@MetalHepple)` and raw commit counts are no longer displayed.

### Security and reliability

- Contributor avatars are restricted to GitHub's numeric avatar endpoint,
  capped before decoding, cached privately for offline use, and pruned within a
  4 MB disk ceiling. Initials remain visible when an avatar is unavailable.
- Preview navigation uses a stable visible-list snapshot and ignores late image
  callbacks after the user has moved to another wallpaper.
- Recent on-device fixes are age-bounded: fixes under 24 hours can be accepted
  immediately and fixes over seven days are never used as a timeout fallback.
- Every acquired coordinate is rounded to 0.1 degrees before private storage;
  precise input is discarded and background location is never requested.

## [1.6.0] - 2026-08-10

### Added

- Per-wallpaper **Options** with **Fill**, **Fit**, **Stretch**, and a touch-
  controlled **Custom crop** that saves zoom and focal position.
- A global display default, used by wallpapers that remain set to **Use
  default**.
- Optional Day & Night wallpaper pools. Each downloaded wallpaper can be used
  for both periods or assigned specifically to Day or Night.
- Automatic changeover using an ambient-light sensor when available, otherwise
  on-device sunrise/sunset calculations from a one-time approximate location.
- Configurable manual day and night times for devices without a sensor or when
  location access is declined.
- A landscape-first **Settings** panel for scheduling, fit, slideshow interval,
  Data Saver, preview-cache cleanup, and storage totals.
- An expanded **About** panel with the current version and update state,
  dynamically cached GitHub contributors, project/source licences, repository
  access, and an optional Ko-fi support link.

### Changed

- Installed wallpaper cards and the Library use a consistent **Options** action;
  **Show now**, slideshow membership, and confirmed device deletion remain
  available inside the options panel.
- The live engine now renders against the actual canvas dimensions and applies
  the same display transform to still images and animated GIFs.
- Day/night changes take effect immediately, while a manual **Show now** choice
  remains in place until the next normal slideshow interval.
- The app requests approximate location only from the visible Settings flow,
  rounds it to 0.1 degrees, stores it locally, and performs solar calculations
  entirely on the device.

### Security and reliability

- Display transforms are bounded, deterministic, and shared between preview
  and live rendering to prevent a crop preview from differing from the result.
- Day & Night cannot be enabled until both periods have at least one eligible
  wallpaper, and it switches itself off safely if a later library edit empties
  either pool.
- GitHub contributor and licence metadata uses bounded HTTPS responses, a
  host allowlist, expiring private caches, and stale offline fallback.
- The local test suite now contains 53 tests, alongside strict Android lint and
  minified release-build verification.

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

[1.7.1]: https://github.com/MetalHepple/Deckscape/compare/v1.7.0...v1.7.1
[1.7.0]: https://github.com/MetalHepple/Deckscape/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/MetalHepple/Deckscape/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/MetalHepple/Deckscape/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/MetalHepple/Deckscape/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/MetalHepple/Deckscape/releases/tag/v1.3.0
