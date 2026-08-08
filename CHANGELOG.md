# Changelog

## 1.3.0 — 2026-08-08

- Established the Deckscape product identity and public project metadata.
- Changed the Android application ID and Java namespace to `uk.darkbyte.deckscape`.
- Centralized product/version metadata for UI labels and network user agents.
- Hardened GitHub redirect validation, cache initialization, preview lifecycle
  cleanup, custom-source recovery, and installed-image decoding.
- Added package and API documentation plus identity-focused unit tests.
- Adopted the MIT License and expanded the GitHub README and community files.
- Published the first update-compatible signed APK through GitHub Releases.

## 1.2.1 — 2026-08-08

- Matched the wallpaper-status control to the 48 dp height, padding, border,
  corner radius, and spacing used by the adjacent top-bar controls.

## 1.2.0 — 2026-08-08

- Added Vyrx Wallpapers, Aesthetic Wallpapers, Wall-E-Desk, and Terminal
  Wallpapers as curated defaults with verified branches and starting folders.
- Added representative wallpaper covers and centered names to category cards.
- Added strong browsing, downloaded, selected, and active visual states.
- Moved determinate download progress into each wallpaper action button.
- Preserved the last browsed source across Android activity recreation.

## 1.1.0 — 2026-08-08

- Image-generation-guided UI redesign for the 1920×1080 head-unit profile.
- Artwork-first wallpaper cards with compact metadata and clearer Apply actions.
- Compact category tiles, selected category chips, and a color-coded breadcrumb.
- Grouped activation and rotation controls with inline loading/status feedback.
- Refined source rail with folder icons and a custom dark add-source dialog.
- Emulator-verified the design at the target head-unit profile.

## 1.0.0 — 2026-08-07

- Production landscape head-unit wallpaper interface.
- Wallz, elementary, and KDE Breeze default GitHub sources.
- User-added public repositories with branch and folder validation.
- Folder categories, recursive all-wallpaper view, and local search.
- Lazy 480×270 preview generation with bounded memory/disk caches.
- One-touch download, validation, selection, and live-wallpaper activation.
- Static and animated GIF wallpaper rendering with hidden-state pause.
- Manual and timed rotation schedules.
- CI, privacy, security, contribution, and architecture documentation.
