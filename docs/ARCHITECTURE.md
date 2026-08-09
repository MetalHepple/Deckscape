# Architecture

## Flow

```text
GitHub Contents / Git Trees API
              │
              ▼
  validated catalog + 2 h cache
              │
      ┌───────┴────────┐
      ▼                ▼
visible card  user presses Download/Show now
      │                │
      ▼                ▼
wsrv.nl thumbnail bounded raw download
or raw fallback          │
      │           decoder validation
480×270 JPEG            │
preview cache           │
                       ▼
              private wallpaper library
                       │
                       ▼
               WallpaperService engine
```

Directory listings reuse the cached recursive Git tree to select one safe,
representative child image for each category card. The full wallpaper is still
not downloaded merely to show the category: it follows the same bounded preview
pipeline as an ordinary visible wallpaper card.

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

## Caches

- Catalog JSON: app cache directory, two-hour freshness, stale offline fallback,
  8 MB pruning threshold.
- Preview JPEGs: 480×270, memory LRU plus 96 MB disk ceiling, pruned to 72 MB;
  wsrv.nl by default with direct GitHub fallback.
- Animated preview GIFs: fetched only when Preview is opened, capped at 12 MB,
  decoded before display, and covered by the shared preview-cache ceiling.
- Wallpaper library: app files directory, retained until app removal or future
  library-management action.

## Live wallpaper

The engine resolves the selected wallpaper by stable stored filename rather
than array index. Rotation updates that filename and timestamp. Static images
are sampled down above 4,096 pixels per axis for runtime memory safety. GIFs
use Android's platform `Movie` decoder at a 100 ms redraw cadence and stop
scheduling frames immediately when the wallpaper becomes hidden.

The app-private wallpaper library is also the slideshow membership list. Every
validated download joins the rotation automatically; selecting **Show now**
only updates the stable current filename. The Slideshow panel reads this same
library and generates bounded local previews through the shared preview cache.

Android owns activation. Deckscape opens
`ACTION_CHANGE_LIVE_WALLPAPER`; it never writes a manufacturer theme database.

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
