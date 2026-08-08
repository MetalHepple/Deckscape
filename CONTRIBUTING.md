# Contributing to Deckscape

Thank you for helping improve Deckscape. The project favours a small, auditable
Android framework implementation that remains responsive on modest head-unit
hardware and does not bundle third-party wallpaper artwork.

## Before starting

- Search existing issues before proposing a duplicate change.
- Open an issue first for a large UI, storage, networking, or dependency change.
- Never commit credentials, signing keys, private repository URLs, device
  screenshots, vehicle locations, or downloaded wallpaper collections.
- Confirm that any image added to the repository has clear redistribution terms.

## Development setup

Use JDK 17 and Android SDK 36. From the repository root, run:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease
```

On Windows, use `gradlew.bat`. Strict lint treats warnings as errors, and the
release build runs R8/resource shrinking to catch production-only problems.

## Code expectations

- Keep network destinations explicit, HTTPS-only, and independently validated
  after redirects.
- Stream untrusted responses through byte limits before decoding them.
- Keep disk writes atomic where practical and clean up partial files on failure.
- Stop timers, animation, callbacks, and background work when their owner closes.
- Add unit tests for pure parsing, validation, selection, and scheduling logic.
- Document classes and non-obvious contracts; avoid comments that merely repeat code.
- Explain APK-size, memory, CPU, and bandwidth impact when adding dependencies.
- Preserve 48 dp touch targets and test UI changes at 1920×1080, 240 dpi.

## Pull requests

Keep each pull request focused. Include:

1. What changed and why.
2. Testing performed and the Android profile used.
3. Before/after screenshots for visible UI changes.
4. Security, privacy, bandwidth, storage, or compatibility implications.

By contributing, you agree that your contribution is licensed under the
project's [MIT License](LICENSE).
