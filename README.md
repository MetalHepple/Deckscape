<p align="center">
  <img src="brand/deckscape-icon-source.png" width="144" alt="Deckscape app icon">
</p>

<h1 align="center">Deckscape</h1>

<p align="center">
  A fast, landscape-first wallpaper gallery for Android displays.
</p>

<p align="center">
  <a href="https://github.com/MetalHepple/Deckscape/actions/workflows/android.yml"><img alt="Android CI" src="https://github.com/MetalHepple/Deckscape/actions/workflows/android.yml/badge.svg"></a>
  <a href="https://github.com/MetalHepple/Deckscape/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/MetalHepple/Deckscape?color=42D9E8"></a>
  <img alt="Android 9+" src="https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white">
  <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/license-MIT-42D9E8"></a>
  <img alt="No analytics" src="https://img.shields.io/badge/analytics-none-132A38">
</p>

Deckscape browses public GitHub wallpaper repositories without bundling their
artwork. Repository folders become visual categories, previews are generated
and cached on the device, and a selected static image or animated GIF is shown
through Android's standard live-wallpaper service. Each download can be fitted,
filled, stretched, or cropped for the display, and optional Day & Night pools
can change the scene automatically. Passive clock, weather, and optional
vehicle-data cards can be drawn over the wallpaper when enabled.

The interface is designed for touch-operated 16:9 Android head units. The core
app remains vehicle-neutral and contains no vehicle-maker SDK, but an optional
adapter can read BYD telemetry already collected by Overdrive on the same head
unit. Those cards are branded with the installed Overdrive app's icon and are
not offered when Overdrive is absent. Deckscape can run without them on other
Android 9+ devices that support live wallpapers and APK installation.

## Download

<p align="center">
  <a href="https://github.com/MetalHepple/Deckscape/releases/latest/download/Deckscape-1.8.0.apk"><strong>Download Deckscape 1.8.0 APK</strong></a>
</p>

The signed APK supports Android 9 (API 28) and newer. Download it directly from
the [latest GitHub release](https://github.com/MetalHepple/Deckscape/releases/latest),
then open it on the Android device and approve installation from that source if
prompted.

The release page includes a SHA-256 checksum alongside the APK so downloads can
be verified independently.

See [CHANGELOG.md](CHANGELOG.md) for changes since the first public release.

Android owns the final live-wallpaper confirmation step. Existing installations
can be updated in place with later Deckscape releases from this repository.

### App updates

Deckscape checks this repository's latest stable release at most once per day
while the app is open. When a newer version is available, its small APK is
downloaded automatically over any available internet connection, including
metered or mobile-data connections. The app never installs an update silently:
open **Update** and press **Install update**, then approve Android's ordinary
package-installer screens.

Before offering the install, Deckscape verifies the release asset's SHA-256,
package name, version, and signing certificate. Android may require Deckscape
to be allowed as an installation source the first time. Deckscape hands the
verified APK directly to Android's package installer so vendor head units that
do not implement the standard per-app source-settings screen can still apply
their own installation policy. If a device blocks that installer too, use the
**Release page** action and install the APK through its browser or file manager.
Some head units revert to their stock wallpaper while an app update is
installed; if that happens, open Deckscape and activate its live wallpaper
again.

## Screenshots

<table>
  <tr>
    <td><img src="docs/images/deckscape-overview.png" alt="Deckscape category overview"></td>
    <td><img src="docs/images/deckscape-options.png" alt="Deckscape wallpaper display options"></td>
  </tr>
  <tr>
    <td align="center"><strong>Image-backed categories</strong></td>
    <td align="center"><strong>Fit, role, slideshow, and device controls</strong></td>
  </tr>
  <tr>
    <td><img src="docs/images/deckscape-custom-crop.png" alt="Deckscape custom wallpaper crop"></td>
    <td><img src="docs/images/deckscape-library.png" alt="Deckscape day and night wallpaper roles"></td>
  </tr>
  <tr>
    <td align="center"><strong>Touch-controlled crop and zoom</strong></td>
    <td align="center"><strong>Grouped Day & Night library</strong></td>
  </tr>
  <tr>
    <td><img src="docs/images/deckscape-settings.png" alt="Deckscape day and night settings"></td>
    <td><img src="docs/images/deckscape-about.png" alt="Deckscape About and update panel"></td>
  </tr>
  <tr>
    <td align="center"><strong>Scheduling, display, and storage settings</strong></td>
    <td align="center"><strong>Version, contributors, licences, and updates</strong></td>
  </tr>
</table>

Screenshots use public catalog previews in an isolated emulator at the target
1920×1080, 240-dpi profile. They contain no vehicle, account, or location data.

## Features

- Large, landscape-first controls and a four-column wallpaper grid.
- Seven curated catalogs, with support for additional public GitHub repositories.
- Image-backed folder categories plus an optional recursive **All wallpapers** view.
- JPEG, PNG, WebP, and animated GIF wallpapers.
- Explicit **Get** and **Set** actions: Get stores the original without changing
  the wallpaper or slideshow; Set includes an on-device image and shows it.
- Download progress is shown inside the selected card's **Get** action.
- Tap a wallpaper image to open a cached preview, then browse the current folder
  or search results with large previous/next controls and one contextual
  **Get** or **Set** action beside **Close**.
- Animated GIFs play inside the in-app preview before installation.
- Clear **On device**, **Selected**, and **Now showing** states distinguish a
  saved wallpaper, an inactive selection, and the wallpaper currently displayed.
- Consistent **Set**, confirmed **Delete**, and **Options** controls in Browse and
  Library, with All, Day, and Night library views.
- Per-wallpaper **Fill**, **Fit**, **Stretch**, and touch-controlled **Custom crop**.
- One global display default for wallpapers without a custom choice.
- Optional Day & Night roles with automatic or manual changeover.
- Ambient-light scheduling on equipped devices, with local sunrise/sunset as a
  privacy-preserving fallback from a foreground fix that is immediately rounded
  into an approximate on-device saved area.
- Off/keep-current, 1-minute, 1-hour, 6-hour, and 1-day rotation schedules.
- Optional non-interactive clock/date, current-weather, and Overdrive-powered
  vehicle cards with independent drag placement, explicit provider labels, and
  privacy disclosures.
- GIF playback capped at 10 fps and paused completely while hidden.
- Verified GitHub update checks with automatic downloads on Wi-Fi or mobile data.
- Friendly public GitHub contributor profiles with cached avatars, plus
  repository-level licence summaries.
- Bounded network, disk, decoder, and memory use suitable for head units.
- No account, analytics, advertising, background location, or storage permission.

## How previews work

GitHub exposes raw files rather than reduced wallpaper thumbnails. With **Data
saver** enabled, Deckscape requests a 480×270 JPEG from the open-source
[images.weserv.nl](https://github.com/weserv/images) service only when a card
becomes visible. The response is host-, type-, size-, and decoder-validated,
then retained in a bounded 96 MB on-device cache.

If the service is unavailable, Deckscape falls back to a separately bounded
download from the selected repository and generates the preview locally. Data
saver can be disabled and the preview cache cleared from **Settings**.

Opening an animated GIF preview fetches the validated original GIF within the
12 MB animation limit and stores it in the same bounded temporary cache. It is
not added to the local library unless **Get** is selected, and Get alone does
not change the wallpaper or slideshow.

Previous and next navigation follows a snapshot of the currently visible
wallpapers, including the active search filter, and skips category folders.

See [PRIVACY.md](PRIVACY.md) for the exact network destinations and stored data.

## Using the slideshow

**Get** downloads a wallpaper into Deckscape's private library without changing
the current display. With Day & Night off, every download rotates; Manual
assignment keeps a new download outside scheduled rotation until it is set, and
Auto by brightness assigns and includes it automatically. **Set** includes an
on-device image and makes it current without removing any other included
wallpapers. **Delete** removes the downloaded file and its slideshow membership
after confirmation.

Use **Library** in the top bar to see the complete downloaded set, set or delete
wallpapers, and identify the current image. Its **All**, **Day**, and **Night**
views group assigned images without changing their stored roles;
**Both** images intentionally appear in Day and Night. The adjacent status pill
opens Deckscape's one-time activation guide when setup is needed.

Use **Slideshow: off** in Library, or **Off – keep current** under the Settings
slideshow interval, to keep the selected wallpaper fixed. Other downloads stay
in Library and can be selected with **Set** at any time. Turning the slideshow
back on restores the last timed interval.

When Day & Night is off and the slideshow is on, every downloaded wallpaper
participates in rotation; there is no separate slideshow subset. Day/Night
filters, assignment badges, and role options remain hidden until the feature is
turned on. With the slideshow off, the current selection also takes precedence
over automatic Day/Night changes.

For quick assignment, tap a downloaded wallpaper's role badge in Browse or
Library to cycle **Both → Day → Night → Both**. Use **Options** when you want to
choose a role directly alongside its display settings.

## Display and Day & Night options

Open **Options** on any downloaded wallpaper to choose how it fills the screen.
**Fill** removes bars by cropping evenly, **Fit** keeps the whole image visible,
and **Stretch** forces the source to the display shape. **Custom crop** adds a
live touch preview: drag to choose the focal point and use the slider to zoom.
The same transform is used for the in-app preview and live wallpaper renderer,
including animated GIFs.

In **Manual** assignment, the same panel can assign a wallpaper to **Both**,
**Day**, or **Night**. **Auto by brightness** instead measures a small
downsample of every downloaded wallpaper entirely on the device, assigns the
darker half to Night and the brighter half to Day, and shows the result as a
read-only badge. A one-wallpaper library remains eligible for both periods.
Turn the feature on from **Settings** after both periods have an eligible
wallpaper.
In **Automatic** mode Deckscape uses the device's ambient-light sensor when one
exists. Otherwise it calculates local sunrise and sunset from the shared saved
area also used by Weather. Older or non-Google head units can require Android's
precise GPS permission to obtain a fix, but Deckscape immediately rounds it to
0.1 degrees and discards the precise result. The approximate area stays
on-device unless the user separately enables Weather and accepts its Open-Meteo
disclosure. Sunrise and sunset are recalculated for the current date whenever
needed. An optional once-daily foreground check keeps the area current when
Deckscape is opened; it can be switched off in Weather options. Choose Manual
mode to avoid location entirely or set a fixed schedule.

## Wallpaper widgets

The top-bar **Widgets** control opens one workspace for both choosing and
positioning passive clock/date, weather, and vehicle cards. A large 16:9 canvas
uses the captured Home screen and live/cached values; enabled cards can be
dragged directly there. A scrollable catalogue beside it shows a full live
example and ON/OFF state for every available card, so adding more widget types
does not shrink the canvas. Tap a catalogue card to toggle it. Cards snap into
horizontal or vertical alignment with other enabled cards; **Snap: on/off**
controls that help. Deckscape does not simulate vehicle controls or use a
dashboard template; without a capture, the canvas uses the current wallpaper.
The downloaded original is never modified, and the wallpaper cards have no
touch controls. Android's wallpaper setup preview hides the cards so the user
can inspect the unobstructed wallpaper before applying it.

**Capture dashboard**, snapping, reset, and Done share a consistently sized top
toolbar that remains clear of the car's bottom system bar. Android displays its
screen-sharing consent prompt; after approval Deckscape hides all
wallpaper cards, opens Home, waits three seconds, saves one frame in app-private
storage, and restores the previous card states. The image is used only behind
the drag editor, is never uploaded, and can be retaken or deleted there. A
successful or failed capture returns to the same Widgets workspace; the
completion notification is retained as a fallback for devices that refuse the
automatic task switch. A 20-second recovery timer and next-launch check restore
the prior card states if capture is interrupted. Everything visible on Home can
appear in this private reference, so the confirmation screen explains the scope
before Android asks.

The clock uses only the device time and redraws on the exact device-minute
boundary, including after device time, date, or time-zone changes. Weather is
off by default and has a separate consent screen because it sends the shared
0.1-degree saved area to
[Open-Meteo](https://open-meteo.com/) over HTTPS. It refreshes at most hourly
while the wallpaper is visible, keeps a small private offline cache, and stops
network work when hidden. **Weather & location options** can manually update
the area or turn the once-daily foreground check on or off. The card retains a
short **Open-Meteo** attribution.
See [PRIVACY.md](PRIVACY.md) for the exact disclosure and retention details.

The three Overdrive-powered cards show battery (SOC, SOH, remaining kWh,
range, 12 V voltage and charging), measured cabin/outdoor/pack temperatures,
and four tyre pressures and temperatures. They are off by default and require
the separately installed
[Overdrive](https://github.com/yash-srivastava/Overdrive-release) app. Deckscape
detects the package before offering the cards and displays the installed app's
own icon; no copied Overdrive logo is bundled. It reads Overdrive's cached
telemetry over `127.0.0.1` only while at least one vehicle card is enabled and
the wallpaper is visible. It requests no BYD privilege, cannot control the
vehicle, ignores unrelated telemetry fields, and keeps the latest accepted
values in process memory only. Sleeping vehicles can honestly show unavailable
cabin or outdoor sensors until Overdrive receives a reading.
The provider choice, field mapping, and device findings are recorded in the
[BYD/Overdrive telemetry investigation](docs/investigations/BYD_OVERDRIVE_TELEMETRY.md).

Manual **Set now** choices are respected until the next normal slideshow
interval. If removing or deleting a wallpaper empties either period, Day &
Night turns off safely instead of leaving the live wallpaper without a choice.

## Default catalogs

| Display name | Repository | Starting folder |
| --- | --- | --- |
| Wallz | [`fr0st-xyz/wallz`](https://github.com/fr0st-xyz/wallz) | repository root |
| elementary | [`elementary/wallpapers`](https://github.com/elementary/wallpapers) | `backgrounds` |
| KDE Breeze | [`KDE/breeze`](https://github.com/KDE/breeze) | `wallpapers/Next/contents` |
| Vyrx | [`vyrx-dev/Wallpapers`](https://github.com/vyrx-dev/Wallpapers) | repository root |
| Aesthetic | [`D3Ext/aesthetic-wallpapers`](https://github.com/D3Ext/aesthetic-wallpapers) | `images` |
| Wall-E-Desk | [`JoshuaThadi/Wall-E-Desk`](https://github.com/JoshuaThadi/Wall-E-Desk) | repository root |
| ItsTerm1n4l | [`ItsTerm1n4l/Wallpapers`](https://github.com/ItsTerm1n4l/Wallpapers) | `images` |

No wallpaper from these repositories is packaged in the APK. Wallpaper rights
remain with the respective creators and sources; review
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) before redistributing artwork.

## Add a repository

Open **Add source** and enter one of the following:

```text
owner/repository
https://github.com/owner/repository
https://github.com/owner/repository/tree/branch/optional/folder
```

An additional starting folder and display name are optional. Only public
repositories are supported. Branch names containing `/` are not yet accepted.
Long-press a custom source to remove it; downloaded wallpapers remain local.

See [docs/SOURCE_FORMAT.md](docs/SOURCE_FORMAT.md) for validation and category rules.

## Build from source

Requirements:

- JDK 17
- Android SDK 36

Windows PowerShell:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

macOS or Linux:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The debug APK is created at
`app/build/outputs/apk/debug/app-debug.apk`. Install it with an explicitly
selected device serial:

```bash
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Deckscape uses the application ID `uk.darkbyte.deckscape`. Release signing is
intentionally not stored in this repository; configure a private keystore in
your own release environment. Every published update must use the same release
signing key, be named `Deckscape-VERSION.apk`, and include a matching
`Deckscape-VERSION.apk.sha256` asset (or GitHub-provided SHA-256 digest).

## Architecture and safety

Deckscape uses Android framework views and networking without a large UI or
image-loading dependency. Downloads are reconstructed from validated repository
coordinates, restricted to HTTPS GitHub endpoints, streamed through byte caps,
and decoded before an atomic move into app-private storage.

Android owns wallpaper activation. Deckscape opens the ordinary live-wallpaper
confirmation screen and never modifies a manufacturer theme database. Configure
the app only while parked when it is used on a vehicle display.

Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for component boundaries,
caching, and rendering details, and [SECURITY.md](SECURITY.md) for vulnerability
reporting.

## Repository layout

```text
app/          Android application and unit tests
brand/        Original app-icon source and image-generation provenance
docs/         Architecture, source-format documentation, and README images
.github/      CI, issue templates, and contribution metadata
```

Contributions are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md), and
run the complete verification command before opening a pull request.

## Support Deckscape

Deckscape is free and open source. If you would like to support its development,
you can [buy me a coffee on Ko-fi](https://ko-fi.com/metalhepple). Donations are
entirely optional and do not unlock additional features.

## License

Deckscape source code and original project-specific brand assets are available
under the [MIT License](LICENSE). Downloaded or previewed wallpaper artwork is
not covered by that license.
