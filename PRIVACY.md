# Privacy

Deckscape does not include analytics, advertising, user accounts, telemetry,
precise/background location, or third-party tracking SDKs.

The app connects directly over HTTPS to:

- `api.github.com` to read public repository metadata, directory listings,
  Deckscape contributors, and repository-level licence summaries;
- `raw.githubusercontent.com` to fetch user-selected wallpapers and fallback
  previews;
- `github.com` and GitHub's release-asset hosts to check for and download
  Deckscape updates;
- `wsrv.nl` to request reduced 480×270 previews while **Data saver** is on.

Repository choices, catalog responses, generated previews, display profiles,
day/night assignments, settings, and downloaded wallpapers remain in the
app's private storage on the Android device. Contributor metadata is cached for
24 hours and source-licence metadata for seven days, with a stale offline
fallback. Android cloud backup and device-transfer backup are disabled for the
application. Removing the app removes this local data under normal Android
package-management behaviour.

GitHub and, while Data saver is enabled, wsrv.nl receive ordinary network
metadata such as the requesting IP address and user agent. The raw GitHub image
URL is included in the wsrv.nl preview request so that service can resize it.
Data saver is enabled by default and can be disabled from **Settings**; cached
previews can be cleared there as well. Deckscape's developer does not operate
an intermediary server.

## Approximate location and ambient light

Day & Night scheduling is optional and off by default. If the Android device
has an ambient-light sensor, automatic scheduling uses that sensor only while
the live wallpaper is visible. Light readings stay on the device and require no
location permission.

On devices without a light sensor, selecting **Refresh automatic location** in
the visible Settings panel requests Android's approximate location permission.
Deckscape asks for one foreground fix, stops listening immediately, rounds
latitude and longitude to 0.1 degrees, and stores only that rounded coordinate
and its timestamp in private app preferences. Sunrise and sunset are then
calculated locally. The coordinate is not placed in a GitHub, preview-service,
update, contributor, licence, or support request. Deckscape does not request
precise or background location. Declining permission or using **Manual times**
keeps Day & Night usable without location.

The **Support** control opens the external Ko-fi website only when the user
selects it. Deckscape does not embed a payment SDK or receive payment details.

While the app is open, Deckscape checks its fixed public GitHub repository for
a stable update at most once per day. A newer APK is downloaded automatically
over any available connection, including mobile data, and held in the app's
private cache until the user chooses to install it. Failed, invalid, and
obsolete update files are deleted. No update analytics or device identifier is
sent by Deckscape.

Last updated: 2026-08-10.
