# GitHub source format

HorizonDeck treats a source as six values:

| Field | Purpose |
| --- | --- |
| Display name | Human-readable label in the source rail |
| Owner | GitHub account or organisation |
| Repository | Public GitHub repository |
| Branch | Branch queried by the Contents and Git Trees APIs |
| Starting folder | Optional repository subtree exposed by HorizonDeck |
| Built-in | Whether the source ships as a curated default |

The app accepts `owner/repository`, a repository URL, or a GitHub
`/tree/branch/folder` URL. An extra starting folder entered in the dialog is
joined below the folder in the URL.

## Category rules

- Immediate folders beneath the configured starting folder appear as category
  chips and folder cards.
- Folder cards support deeper navigation and a breadcrumb/back action.
- **All wallpapers** uses GitHub's recursive Git Trees API, filters the result
  back to the configured starting folder, and displays compatible images in a
  flat list.
- Hidden path segments beginning with `.` are excluded.
- The all-wallpaper result is capped at 5,000 compatible files and reports
  GitHub tree truncation in the status line.

## Compatible files and limits

| Type | Extensions | Install cap |
| --- | --- | --- |
| Static | `.jpg`, `.jpeg`, `.png`, `.webp` | 40 MB |
| Animated | `.gif` | 12 MB |

Data-saver previews request a 480×270 JPEG and enforce a 2 MB response cap.
The direct GitHub fallback may fetch static sources up to 24 MB or GIF sources
up to 12 MB. Oversized files remain visible and can still receive a data-saver
preview, but show **No preview** if that service is unavailable.

## Future thumbnail manifest

Version 1.0 does not require a repository-specific manifest. A future optional
`.horizondeck/catalog.json` can add explicit thumbnail and attribution URLs
without changing the source or category model; arbitrary repositories will
continue to use the data-saver path with local thumbnail generation as the
fallback.
