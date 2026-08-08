# Privacy

Deckscape does not include analytics, advertising, user accounts, telemetry,
location access, or third-party tracking SDKs.

The app connects directly over HTTPS to:

- `api.github.com` to read public repository metadata and directory listings;
- `raw.githubusercontent.com` to fetch user-selected wallpapers and fallback
  previews;
- `wsrv.nl` to request reduced 480×270 previews while **Data saver** is on.

Repository choices, catalog responses, generated previews, settings, and
downloaded wallpapers remain in the app's private storage on the Android
device. Android cloud backup and device-transfer backup are disabled for the
application. Removing the app removes this local data under normal Android
package-management behaviour.

GitHub and, while Data saver is enabled, wsrv.nl receive ordinary network
metadata such as the requesting IP address and user agent. The raw GitHub image
URL is included in the wsrv.nl preview request so that service can resize it.
Data saver is enabled by default and can be disabled from **Info**; cached
previews can be cleared there as well. Deckscape's developer does not operate
an intermediary server.

Last updated: 2026-08-08.
