# BYD wallpaper protection and recovery

## Failure and replacement

On the inspected BYD AUTO / DiLink3.0 Android 10 firmware, the account-theme
service can select stock wallpaper well after wake-up. Force-stopping the stock
provider can kill a dependent theme process, whose job Android subsequently
retries. A 45-second force-stop loop therefore does not provide durable protection.

The next development experiment disabled the entire stock wallpaper package.
Deckscape survived a genuine reboot, but BYD's theme app crashed because its
wallpaper provider was unavailable. That experiment was restored successfully
through the app and is not the implementation intended for release.

The current implementation leaves the package and provider enabled. It changes
only the package-scoped `WRITE_WALLPAPER` AppOp for `com.byd.wallpaperhome` to
`ignore`, after setup consent and native Android activation. The inspected OEM
binding code checks `WallpaperManager.isWallpaperSupported()` before selecting
a wallpaper; this firmware implements that check using the calling package's
wallpaper AppOp. The provider can consequently remain available to BYD themes.

Do not use `--uid`: the stock package shares system UID 1000 with unrelated
components. Package and UID modes are distinct in Android's
[AppOps implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-10.0.0_r1/services/core/java/com/android/server/appop/AppOpsService.java).
The app rejects output containing UID overrides rather than modifying them.
It never changes account data, theme databases, other packages, or the selected
wallpaper through privileged commands. No watchdog or wake-time polling remains.

## Transaction and recovery contract

- Gate local hardware/API and connected ADB identity, API 29, and current user 0.
- Use the existing app-private, non-backed-up ADB key; no general console.
- Confirm Deckscape selection before and after changing the operation. Preview
  engines never start ADB or modify permissions.
- Parse one exact package operation. An absent entry with default mode `allow`
  is allowed; unknown output, multiple entries, and UID overrides fail closed.
- Save the original mode synchronously before mutation. Never overwrite it on
  retry. Known Android modes are preserved exactly during restoration.
- Read back `ignore`, flush Android's batched AppOps settings to storage, then
  read back again. Retry the flush even when reconnecting to an already-set mode.
- If selection is lost during setup, restore the saved permission rather than
  forcibly reselecting Deckscape. Uncertain failures retain recovery information.
- Recovery-only migration re-enables a package disabled by the unreleased test
  build only when its saved original-state receipt is valid. Apply the new
  restriction before recovering that provider. No new package-disable command
  exists. Unowned disabled packages are not silently enabled during setup.
- Restore opts out first, restores and flushes the saved permission, verifies
  it, and clears the receipt. With no receipt, explicit restore uses `allow`.
- App UI distinguishes a check performed in the current app session from a
  permanent guarantee; firmware updates or external tools can change settings.

## Returning to stock wallpaper

Before uninstalling Deckscape or clearing app data:

1. Open **Settings → BYD wallpaper → Restore BYD**.
2. Complete any local Android debugging authorization requested.
3. Choose a wallpaper in BYD Themes, which opens after restoration succeeds.

Do not launch Android's live-wallpaper preview for the stock BYD service. The
physical restore test exposed a preview failure that closed the activity and
left the display in portrait without changing the wallpaper. Restoring the
permission alone deliberately does not change the selected wallpaper. The
app now uses BYD Themes; if that app cannot open, it explains that permission
was restored and asks the user to select a wallpaper manually. Deckscape's own
activation continues to use Android's normal confirmation screen.

If restoration fails, keep Deckscape installed and retry after debugging is
available. The permission restriction is an Android setting and outlives the app.
If the app was removed, reinstall it and use Restore BYD, or use an authorized
ADB connection to the verified head unit (replace `SERIAL` with its explicit serial):

```text
adb -s SERIAL shell cmd appops set --user 0 com.byd.wallpaperhome WRITE_WALLPAPER allow
adb -s SERIAL shell cmd appops write-settings
adb -s SERIAL shell cmd appops get --user 0 com.byd.wallpaperhome WRITE_WALLPAPER
```

Confirm `allow` or `No operations` with `Default mode: allow`, then choose stock
wallpaper normally. Never reset all AppOps or modify UID 1000. If the earlier
unreleased whole-package experiment remains disabled without its recovery
receipt, separately restore only that package with `pm default-state --user 0
com.byd.wallpaperhome` and verify user 0 has `enabled=0` before choosing stock.

## Validation status

Version 1.9.1 replaces the 1.9.0 temporary guard with the package-scoped
permission implementation. On the tested BYD AUTO / DiLink3.0 Android 10 head
unit, native activation, permission restoration, the corrected Restore BYD
chooser, normal BYD Themes browsing, quickboot and full Android reboot passed.
Deckscape returned automatically after reboot with the restriction retained,
the stock package enabled and its provider available to the BYD theme service.
Overnight wallpaper persistence was also reported.

These results establish wallpaper persistence on the tested firmware, not
complete display/wake reliability across every vehicle or sleep path. A separate
recurring backlight fault was directly attributed to Overdrive ACC Sentry
remaining in its cached OFF state while the vehicle hardware reported ON.
Deckscape remained selected and bound with protection intact during that fault.
This release does not modify Overdrive or claim to resolve every black screen.

The experimental Deckscape theme picker and custom theme prototype are excluded
from this release. Applying and reverting themes through that picker still need
physical validation.

Pure-logic tests cover command scope and parsing, original-mode journaling,
retries, disk flush, selection loss and rollback, restoration, and recovery of
the earlier private package-disable experiment. Release checks also include lint,
debug/release assembly and landscape presentation at 1920×1080 / 240 dpi.
Private vehicle logs and OEM decompilation are retained outside the repository.
