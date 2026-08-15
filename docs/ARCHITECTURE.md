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
 visible card          Get / Set
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
                           |
                   +-------+-------+
                   |               |
             image transform   passive overlays
                                   |
                  clock / cached weather / vehicle cards
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
or dismissal. The same dialog exposes one state-aware Get or Set callback; its
disabled Selected and Now showing states are derived from both the stored
selection and Android's active live-wallpaper component. Its controls update
only while the initiating item remains visible.

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

The optional weather overlay uses one fixed HTTPS endpoint at
`api.open-meteo.com/v1/forecast`. Only the separately disclosed, stored
0.1-degree coordinate is accepted as input. Redirects are disabled, response
type and status are checked, JSON is capped at 64 KB, and temperature,
condition code, coordinate, and timestamp ranges are validated before caching.
The renderer never performs network or JSON work.

The vehicle-data boundary is a provider interface rather than a BYD dependency
in the renderer. Its first adapter detects an installed Overdrive package;
without it, vehicle cards are omitted from configuration, layout, and live
rendering without deleting their saved state. When present, the renderer loads
Overdrive's installed application icon at runtime instead of bundling a copied
brand asset. The adapter
reads the app's telemetry-preview endpoint through a raw socket fixed to
`127.0.0.1:8080`. It does not follow redirects or resolve a hostname; only a
complete `200 application/json` response within the header/body/time limits is
accepted. Parsing is an allowlist and discards location, identity, doors, trip
history, and all other fields. Normalized ranges reject known HAL sentinels.
The resulting snapshot is process-memory-only, is displayed for at most five
minutes, and is requested only while a vehicle card is enabled and the
wallpaper is visible. Deckscape holds no BYD GET/SET permissions and exposes no
control path.

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
- Weather: one private current-condition snapshot tied to the rounded
  coordinate; fetched no more than hourly while visible and displayed for at
  most six hours when offline.

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

`WallpaperOverlayRenderer` composes passive clock/date, weather, and vehicle cards only
after the wallpaper transform. Preview wallpaper engines intentionally skip
overlay composition and weather work, so Android's wallpaper setup surface is
unobstructed. Each card has an independently persisted,
normalized centre point; the renderer clamps its actual dp-sized bounds inside
the locked canvas. `DashboardLayoutEditorView` renders a 1920×1080 logical
preview using the same wallpaper transform and overlay renderer, with an
app-private dashboard reference and no synthetic system-control template. It is
the main pane of the Widgets workspace and renders enabled cards only. A
scrollable column of independent `WallpaperWidgetTileView` examples handles
selection and can grow without reducing the dashboard canvas. Until a reference
exists the canvas shows only the transformed wallpaper. The logical canvas is
letterboxed when necessary, and pointer input
is transformed back into its 16:9 coordinates before normalized positions are
saved. `OverlaySnapper` independently aligns nearby horizontal and vertical card
centres within a bounded threshold; the editor can disable it without changing
the saved positions. The normalized result scales to other surface sizes.
Overlay preferences are cached by each engine, weather snapshots are read from
a separate private store, and vehicle snapshots never leave process memory.
The visible engine owns one cancelable weather request and one cancelable local
vehicle request; hiding or destroying the engine disconnects them and stops
their executors. `WallpaperRedrawScheduler` schedules a visible static
clock at the exact next device-minute boundary; time and time-zone broadcasts
also request an immediate redraw. Static wallpapers without a clock keep the
15-second cadence, while GIF frames reuse the same cached snapshot without
repeated preference, parser, or network work.

`DashboardCaptureService` owns the one-frame MediaProjection transaction. The
activity first records every enabled flag. After Android returns a
user-approved projection token, all flags are atomically disabled and a
transparent foreground bridge activity starts the service before opening Home. This refreshes
Android 10's short background-return grace period at capture time instead of relying on when the
main activity was launched. The wallpaper engine is notified before Home is opened. The service
drains frames for three seconds, atomically writes a bounded PNG to private
storage, restores the recorded flags, releases the projection, and stops. The
same restoration runs for cancellation, timeout, capture failure, service
destruction, a 20-second stale transaction, and the next activity launch. On
completion, the service moves the exact bridge-owned Deckscape app task to the
foreground; the bridge then finishes and persistent capture state makes the main activity reopen the Widgets
workspace even if Android recreated it. The completion notification remains as
a fallback and is dismissed when that workspace resumes. On
Android 14 and later, the consent intent is configured for the entire display;
Android 9–13 use the platform's full-display projection flow.

The app-private wallpaper library and a private exclusion set define slideshow
membership. A validated **Get** is initially placed in that exclusion set, so
Manual Day/Night assignment does not add it to a scheduled pool. The exclusion
is ignored while Day & Night is off; Auto by brightness removes it after local
classification. **Set** removes the exclusion, updates the current filename and
a manual-override flag, and does not remove other files. The Library panel reads
the same state, generates bounded local previews through the shared preview
cache, and filters its view by stored Both/Day/Night roles. Both-role files
appear in each scheduled view without being duplicated on disk. Each Library
role badge is backed by a 48dp focusable touch target and cycles through the
three roles in a deterministic order; saving triggers the same group refresh
and incomplete-schedule safety check as the full Options panel.

When Day & Night is enabled, `DayNightSettings` filters included files by their
roles. Both period pools must remain non-empty. `WallpaperEngineService`
switches immediately when the current period changes, but otherwise preserves
a manual Set choice until the next normal slideshow interval. Emptying a
pool through an exclusion or delete automatically disables the feature.

When Day & Night is disabled, the engine deliberately bypasses the legacy
exclusion set and rotates through every validated file in the private library;
the UI hides period filters and role metadata in the same state. With automatic
assignment enabled, `WallpaperLuminanceClassifier` decodes at most a small
sample of each local image and records its mean perceived RGB luminance in the
wallpaper profile. `DayNightAutoSorter` deterministically assigns the darker
half to Night and the brighter half to Day; a single image is assigned to Both.
No image sample or brightness value leaves the device.

A zero rotation interval is an explicit fixed-wallpaper mode. While it is
active and a selected download exists, the engine renders that one file and
does not perform timed or Day/Night phase changes. Selecting another wallpaper
changes the fixed file; the remaining downloads and their assignments are left
untouched. The last non-zero interval is retained so the Library toggle can
resume the previous cadence.

Automatic period detection prefers an ambient-light sensor. The sensor is
registered only while the wallpaper is visible; `AmbientLightTracker` uses
separate day/night thresholds plus settling and hysteresis windows to avoid
rapid changes. Without a sensor, `DayNightSettings` reads the same
`SavedAreaSettings` used by Weather. A foreground-only location fix can be
requested explicitly from Settings or Weather options. Android 11 and older require fine foreground
access because non-Google head units may expose GPS without a functional network
provider; Android 12 and newer accepts the user's approximate choice. The result
is immediately rounded to 0.1 degrees and used by `DayPhaseResolver` for
on-device solar calculations. The engine recalculates the boundaries from the
current date and reacts to date, time, and time-zone changes. Settings reads
today's boundaries from that same resolver and presents them as read-only
sunrise/sunset values; the manual time spinners are shown only when Manual mode
is selected.

`CoarseLocationClient` checks fused, network, GPS, and passive providers, accepts
only a fix from the last 24 hours immediately, and permits a fix up to seven days
old solely as a timeout fallback. A fresh request runs for at most 60 seconds,
can be cancelled from the visible activity, and stops when the Activity leaves
the foreground. `SavedAreaRefreshPolicy` permits at most one automatic attempt
per 24 hours, only when Deckscape is foreground and Weather or location-based
solar scheduling is in use. The user can disable it in Weather options. Manual
times are the permission-free fallback. No background location service, remote
solar API, or wakeup alarm is used.

Android owns activation. Deckscape opens
`ACTION_CHANGE_LIVE_WALLPAPER`; it never writes a manufacturer theme database.

## Settings and metadata

`MainActivity` owns the landscape Settings, Options, About, and licence panels.
All mutating wallpaper actions flow through `WallpaperStore`,
`WallpaperProfileStore`, or `DayNightSettings`, followed by a package-scoped
library-changed broadcast. The engine reloads its files and profiles from those
stores rather than accepting file paths or settings from external intents.

Location is requested only after an explicit Settings action and an in-app
explanation. A timeout, denial, or cancellation preserves the selected schedule
mode instead of silently changing the spinner. Android backup and device-transfer
extraction are disabled, so private wallpapers, rounded coordinates, profiles,
and cached metadata are not opted into cloud backup by the app.

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
