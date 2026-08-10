# Architecture

## Flow

```text
GitHub Contents / Git Trees API
                |
                v
    validated catalog + 2 h cache
                |
       +--------+---------+
       v                  v
 visible card       Download / Show now
       |                  |
       v                  v
 wsrv.nl thumbnail   bounded raw download
 or raw fallback           |
       |              decoder validation
 480×270 JPEG              |
 preview cache             v
                 private wallpaper library
                           |
             +-------------+-------------+
             v                           v
 per-wallpaper profile          Day/Night eligibility
             |                           |
             +-------------+-------------+
                           v
                 WallpaperService engine
```

Directory listings reuse the cached recursive Git tree to select one safe,
representative child image for each category card. The full wallpaper is still
not downloaded merely to show the category: it follows the same bounded preview
pipeline as an ordinary visible wallpaper card.

Opening a wallpaper creates a `PreviewSequence` snapshot from the current
folder or filtered results, omitting directories. `WallpaperPreviewDialog`
keeps navigation within that snapshot and advances a request generation on
every move, so a slower callback from the previous image cannot replace the
currently selected preview. GIF animation is cleared immediately on navigation
or dismissal.

## Trust boundaries

`RepositorySource` validates GitHub owner, repository, branch, and path input.
Raw downloads are reconstructed by the app and accepted only when the final
HTTPS host is `raw.githubusercontent.com` and the first two path segments match
the selected owner and repository.

Data-saver preview URLs are constructed only from those validated raw URLs.
The initial and final preview endpoint must remain HTTPS `wsrv.nl`; the response
must be an image and is capped at 2 MB before decoding. If that path fails, the
app falls back to the separately bounded raw-source preview path. Users can
disable Data saver in the app.

Both previews and installs enforce streamed byte limits. Installed files also
undergo format/dimension decoding before their partial file is atomically
renamed into the private library.

`GitHubMetadataClient` separately loads public contributor and repository-level
licence data for About. Requests remain under `api.github.com`, enforce a
512 KB response cap, filter bot contributors, merge the creator's linked and
anonymous commit identities, and use independent cache freshness windows.
Public profile images are accepted only from GitHub's numeric
`avatars.githubusercontent.com/u/<id>` endpoint, capped at 512 KB and 1,024
pixels per axis before bounded downsampling for display. Wallpaper rendering
never depends on this metadata.

## Caches

- Catalog JSON: app cache directory, two-hour freshness, stale offline fallback,
  8 MB pruning threshold.
- Preview JPEGs: 480×270, memory LRU plus 96 MB disk ceiling, pruned to 72 MB;
  wsrv.nl by default with direct GitHub fallback.
- Animated preview GIFs: fetched only when Preview is opened, capped at 12 MB,
  decoded before display, and covered by the shared preview-cache ceiling.
- Wallpaper library: app files directory, retained until app removal or future
  library-management action.
- Contributor metadata: app cache directory, 24-hour freshness and stale
  offline fallback.
- Contributor profile images: app cache directory, seven-day freshness, 4 MB
  ceiling pruned to 3 MB, with an initial-letter fallback.
- Source-licence metadata: app cache directory, seven-day freshness and stale
  offline fallback.

## Live wallpaper

The engine resolves the selected wallpaper by stable stored filename rather
than array index. Rotation updates that filename and timestamp. Static images
are sampled down above 4,096 pixels per axis for runtime memory safety. GIFs
use Android's platform `Movie` decoder at a 100 ms redraw cadence and stop
scheduling frames immediately when the wallpaper becomes hidden.

`WallpaperProfileStore` keeps one immutable `WallpaperProfile` per stable
filename. Profiles contain a display mode, bounded custom zoom/focal point, and
a Both/Day/Night role. `WallpaperTransform` is the single pure transform used by
the touch crop preview and live renderer. It calculates Fill, Fit, Stretch, and
Custom matrices from the decoded source and the actual locked canvas size, so
manufacturer-reported wallpaper hints cannot introduce unexpected bars.

The app-private wallpaper library and a private exclusion set define slideshow
membership. Every validated download joins automatically; selecting **Show
now** updates the current filename and a manual-override flag without removing
other files. The Library panel reads the same state and generates bounded local
previews through the shared preview cache.

When Day & Night is enabled, `DayNightSettings` filters included files by their
roles. Both period pools must remain non-empty. `WallpaperEngineService`
switches immediately when the current period changes, but otherwise preserves
a manual Show now choice until the next normal slideshow interval. Emptying a
pool through an exclusion or delete automatically disables the feature.

Automatic period detection prefers an ambient-light sensor. The sensor is
registered only while the wallpaper is visible; `AmbientLightTracker` uses
separate day/night thresholds plus settling and hysteresis windows to avoid
rapid changes. Without a sensor, a foreground-only approximate location fix is
rounded to 0.1 degrees and used by `DayPhaseResolver` for on-device solar
calculations. Manual times are the permission-free fallback. No background
location service, remote solar API, or wakeup alarm is used.

Android owns activation. Deckscape opens
`ACTION_CHANGE_LIVE_WALLPAPER`; it never writes a manufacturer theme database.

## Settings and metadata

`MainActivity` owns the landscape Settings, Options, About, and licence panels.
All mutating wallpaper actions flow through `WallpaperStore`,
`WallpaperProfileStore`, or `DayNightSettings`, followed by a package-scoped
library-changed broadcast. The engine reloads its files and profiles from those
stores rather than accepting file paths or settings from external intents.

Approximate location is requested only after an explicit Settings action.
Android backup and device-transfer extraction are disabled, so private
wallpapers, coordinates, profiles, and cached metadata are not opted into cloud
backup by the app.

## Application updates

`UpdateManager` performs a daily foreground check against the fixed latest
release endpoint for `MetalHepple/Deckscape`. It accepts stable semantic
versions only and delegates transport to `UpdateClient`, which follows at most
five redirects through an explicit GitHub release-host allowlist. JSON,
checksum, and APK responses are streamed through independent byte limits.

A newer APK is downloaded automatically on any available network, including a
metered mobile connection, to an app-private `.part` file. The client verifies
its declared length and SHA-256 before an atomic rename. `UpdateVerifier` then
parses the archive and requires the Deckscape package name, exact advertised
version, a higher version code, and the same signer set as the installed app.
Non-debug builds additionally require the pinned production signer.

Only the fully verified cached APK can be exposed through
`UpdateFileProvider`. That provider is non-exported and maps one exact read-only
content URI. Installation remains an explicit user action handled by Android's
package installer; Deckscape neither requests privileged installation access
nor attempts a silent update.
