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
visible card    user presses Apply
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
- Wallpaper library: app files directory, retained until app removal or future
  library-management action.

## Live wallpaper

The engine resolves the selected wallpaper by stable stored filename rather
than array index. Rotation updates that filename and timestamp. Static images
are sampled down above 4,096 pixels per axis for runtime memory safety. GIFs
use Android's platform `Movie` decoder at a 100 ms redraw cadence and stop
scheduling frames immediately when the wallpaper becomes hidden.

Android owns activation. HorizonDeck opens
`ACTION_CHANGE_LIVE_WALLPAPER`; it never writes a manufacturer theme database.
