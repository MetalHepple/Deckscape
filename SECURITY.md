# Security policy

## Supported versions

Security fixes are made against the latest release and the `main` branch.
Older builds may be asked to upgrade before a report is investigated.

## Reporting a vulnerability

After the repository is published, use GitHub's private **Report a
vulnerability** form instead of a public issue. Include affected versions,
reproduction steps, impact, and any proposed mitigation.

Do not include credentials, Android ADB keys, private repository URLs, vehicle
location screenshots, or other personal data. If private reporting is not yet
enabled, contact the repository owner without publishing exploit details.

## Security boundaries

- Public GitHub repositories only; no token or credential storage.
- HTTPS only, restricted to GitHub API, the selected repository's raw host, and
  the optional `wsrv.nl` preview endpoint.
- Redirect destinations, content types where applicable, response sizes, and
  decoded images are validated before data is trusted.
- Streamed byte limits and image-dimension limits protect download and preview paths.
- App-private wallpaper storage; no shared-storage permission or cloud backup.
- Android's normal live-wallpaper activation flow; no manufacturer databases or
  privileged platform components are modified.

## Disclosure

Please allow a reasonable period for validation and a fix before public
disclosure. Good-faith reports that respect user privacy are appreciated.
