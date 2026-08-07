# Security policy

## Supported version

Security fixes are made against the latest release and the default branch.

## Reporting a vulnerability

Please use GitHub's private **Report a vulnerability** form after this project
is published. Do not include private repository URLs, credentials, Android ADB
keys, vehicle location screenshots, or other personal data in a public issue.

## Security boundaries

- Public GitHub repositories only; no token or credential storage.
- HTTPS only, restricted to GitHub API, the selected repository's raw host, and
  the optional `wsrv.nl` preview endpoint.
- Streamed size limits and image-dimension validation before installation.
- Preview-service redirects, content types, response sizes, and decoded images
  are validated; a service failure falls back to the bounded direct path.
- App-private wallpaper storage; no shared-storage permission.
- No private vehicle-maker settings or theme databases are modified.
