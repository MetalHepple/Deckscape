# Privacy

Deckscape does not include analytics, advertising, user accounts, telemetry,
background location, or third-party tracking SDKs.

The app connects directly over HTTPS to:

- `api.github.com` to read public repository metadata, directory listings,
  Deckscape contributors, and repository-level licence summaries;
- `avatars.githubusercontent.com` to fetch the public profile images shown for
  contributors in About;
- `raw.githubusercontent.com` to fetch user-selected wallpapers and fallback
  previews;
- `github.com` and GitHub's release-asset hosts to check for and download
  Deckscape updates;
- `wsrv.nl` to request reduced 480×270 previews while **Data saver** is on;
- `api.open-meteo.com` to request current conditions only after the user enables
  the optional wallpaper weather card and accepts its separate disclosure.

Repository choices, catalog responses, generated previews, display profiles,
day/night assignments, settings, the latest cached weather result, and
downloaded wallpapers remain in the
app's private storage on the Android device. Contributor metadata is cached for
24 hours. Contributor profile images are cached for seven days in a bounded
4 MB cache, and source-licence metadata is cached for seven days, all with an
offline fallback. Android cloud backup and device-transfer backup are disabled
for the application. Removing the app removes this local data under normal
Android package-management behaviour.

If **Auto by brightness** is selected for Day & Night assignment, Deckscape
decodes a small sample of each downloaded wallpaper locally and stores only its
calculated brightness and Day/Night role in the existing private display
profile. The image sample and result are never sent to a network service.

GitHub, Open-Meteo, and, while Data saver is enabled, wsrv.nl receive ordinary
network metadata such as the requesting IP address and user agent. The raw
GitHub image URL is included in the wsrv.nl preview request so that service can
resize it.
Data saver is enabled by default and can be disabled from **Settings**; cached
previews can be cleared there as well. Deckscape's developer does not operate
an intermediary server.

## Location and ambient light

Day & Night scheduling is optional and off by default. If the Android device
has an ambient-light sensor, automatic scheduling uses that sensor only while
the live wallpaper is visible. Light readings stay on the device and require no
location permission.

Weather and solar scheduling use one shared approximate saved area. Selecting
**Update shared saved area** in visible Settings or Weather options requests
one foreground location fix. Android 12 and newer can grant approximate access.
Android 11 and older head units without a working network-location provider may
require foreground precise permission so Deckscape can receive a GPS fix. The
app explains this before opening Android's permission prompt.

Regardless of the permission granted, Deckscape stops listening after the
first fix, immediately rounds latitude and longitude to 0.1 degrees, discards
the precise result, and stores only that rounded coordinate and its timestamp
in private app preferences. A search lasts at most one minute and can be
cancelled. If Weather or location-based Day & Night is in use, Deckscape can
perform this same bounded foreground check once per 24 hours when the app is
opened; the daily check is enabled by default and can be switched off in Weather
options. It never runs while the app is hidden. Sunrise and sunset are
recalculated locally for the current date. The coordinate is not placed in a
GitHub, preview-service, update, contributor, licence, or support request.
Deckscape never requests background location. Declining permission or using
**Manual times** keeps Day & Night usable without location.

## Wallpaper weather

The passive clock/date and weather cards are optional and off by default. The
clock and date use the device clock and do not make a network request. Before
weather is enabled, Deckscape shows a separate disclosure explaining that the
stored 0.1-degree coordinate will be sent to `api.open-meteo.com` over HTTPS.
The precise location fix is never sent. If no rounded coordinate has been
saved, Weather requests the same bounded foreground fix described above. Its
options can update the shared area manually or disable daily foreground checks.

While the live wallpaper is visible, Deckscape requests only current air
temperature and a weather-condition code, no more than once per hour. It makes
no scheduled background weather requests while the wallpaper is hidden. The
latest valid result is cached privately for an offline fallback and is no
longer displayed after six hours. Turning weather off stops further requests.

Open-Meteo receives the rounded coordinate, requesting IP address, and ordinary
HTTP metadata. Its published privacy terms say free-API server logs can contain
coordinates and are deleted after 90 days. Weather data is credited to
[Open-Meteo](https://open-meteo.com/) under CC BY 4.0 with a short provider name
on the non-interactive wallpaper card. The in-app disclosure identifies the
provider; weather remains entirely optional.

## Optional vehicle cards

The three Overdrive-powered vehicle cards are optional, off by default, and
require a separately installed Overdrive app on the same Android device. They
are not shown as widget choices unless that package is detected, and their
header uses the installed app's own icon. Deckscape does not include the BYD
SDK, request BYD car permissions, connect to a vehicle cloud service, or send
vehicle commands. While the live wallpaper is visible and at least one vehicle
card is enabled, Deckscape reads Overdrive's read-only telemetry preview at
`http://127.0.0.1:8080/api/mqtt/telemetry`. The client is hard-coded
to the IPv4 loopback address, does not follow redirects, accepts JSON only, and
enforces connection, read, header, and body limits.

Overdrive's response can contain more telemetry than the cards need, including
location and vehicle identity fields. Deckscape immediately allowlists only
SOC, SOH, remaining energy, 12 V voltage, EV range, charging state/power,
measured cabin/outdoor/battery temperatures, and four tyre pressures and
temperatures. It does
not inspect, log, store, display, or transmit location, identity, door, trip,
or other response fields. Accepted vehicle values remain in process memory
only and expire from display after five minutes; they are never written to
Deckscape's files or preferences. Polling stops when the wallpaper is hidden or
all vehicle cards are off. Overdrive remains responsible for how it obtains and
handles its own data; its project information is included in Deckscape's
in-app third-party notices.

## Private dashboard reference

The widget layout editor can optionally use a screenshot of the device's Home
screen. Deckscape contains no simulated dashboard template and starts capture
only after its own explanation and Android's screen-sharing confirmation.
It temporarily disables every wallpaper card before opening Home, waits three
seconds, captures one frame, and restores their previous enabled states on
success, cancellation, failure, or service shutdown. An interrupted-capture
recovery check restores them after 20 seconds or on the next app launch.

The PNG is stored only in Deckscape's private application files and is not sent
over the network or included in the wallpaper artwork. It can contain anything
visible on Home, including vehicle, account, notification, or location details.
The user can retake it or delete it from the layout editor; uninstalling the app
also removes it under normal Android package-management behaviour.

The **Support** control opens the external Ko-fi website only when the user
selects it. Deckscape does not embed a payment SDK or receive payment details.

While the app is open, Deckscape checks its fixed public GitHub repository for
a stable update at most once per day. A newer APK is downloaded automatically
over any available connection, including mobile data, and held in the app's
private cache until the user chooses to install it. Failed, invalid, and
obsolete update files are deleted. No update analytics or device identifier is
sent by Deckscape.

Last updated: 2026-08-15.
