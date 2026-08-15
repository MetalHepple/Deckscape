# BYD vehicle telemetry through Overdrive

Investigation date: 13 August 2026

This note records why Deckscape's first vehicle-data implementation uses a
small provider adapter instead of linking directly to BYD's vehicle APIs. It
also defines the privacy and reliability boundaries for the wallpaper cards.

## Result

The supported path is a read-only request to an installed Overdrive instance:

```text
GET http://127.0.0.1:8080/api/mqtt/telemetry
```

Deckscape does not call an Overdrive control endpoint. The live-wallpaper
engine polls only while it is visible and at least one vehicle card is enabled. It
keeps accepted values in process memory, expires them from display after five
minutes, and stops polling when the wallpaper becomes hidden.

The adapter is manufacturer-neutral at its interface. The initial provider
depends on telemetry collected by Overdrive from a BYD head unit, but the cards
are branded as Overdrive-powered rather than as BYD integrations. They are
omitted from the widget chooser and layout editor when Overdrive is absent. A
future non-BYD provider can implement the same `VehicleTelemetryProvider`
contract without changing the renderer or layout editor.

## Why Deckscape does not read the BYD APIs directly

Inspection of the head unit and Overdrive source found that the useful BYD
read permissions are not a normal third-party application API. In particular,
the installed system declares the air-conditioning and instrument GET
permissions as signature protected. Overdrive requests these permissions but
collects the vehicle data from a separately launched `app_process` daemon with
shell-level access. Reproducing that arrangement in Deckscape would make a
wallpaper application unnecessarily privileged, BYD-specific, and difficult
to distribute safely.

Deckscape therefore requests no BYD permission, contains no BYD SDK, and sends
no vehicle command. Overdrive remains a separate, user-installed application.

## Source validation

The implementation was checked against the MIT-licensed
[Overdrive source](https://github.com/yash-srivastava/Overdrive-release) at
commit `72b5341d5b5c5fb63c2b10d6e14c7bf00fcb3fd5` and against Overdrive 36.5 on
the target Android 10 head unit.

The localhost route returns Overdrive's assembled cached telemetry. That is
preferable to its broader vehicle-state route, which also refreshes unrelated
cloud-lock state. A live read confirmed usable SOC, SOH, remaining battery
energy, 12 V voltage, EV range, battery and outside temperatures, and all four
tyre pressures and temperatures. Cabin temperature is a measured
`AC_TEMP_INSIDE` reading rather than the climate set point; it can be absent
while the parked vehicle is asleep.

Overdrive exposes both `soh` and `soh_oem`. Deckscape prefers Overdrive's
headline `soh`, then falls back to `soh_oem`. The displayed value should be
understood as Overdrive's battery-health result and not as a Deckscape
calculation.

## Accepted fields

Deckscape parses an exact allowlist from the response's `telemetry` object:

| Card | Accepted Overdrive fields | Display |
| --- | --- | --- |
| Overdrive battery | `soc`, `soh` or `soh_oem`, `capacity`, `volt_12v`, `ev_range_km`, `is_charging`, `charge_power` | SOC, SOH, remaining kWh, 12 V voltage, EV range and charging state/power |
| Overdrive temperatures | `inside_temp` or `cabin_temp`, `ext_temp`, `batt_temp` or `cell_t_avg` | Measured cabin, outside and traction-battery temperatures |
| Overdrive tyres | `tyre_p_fl`, `tyre_p_fr`, `tyre_p_rl`, `tyre_p_rr`, and matching `tyre_t_*` fields | Four pressures converted from kPa to bar and four temperatures |

Each value is range checked before display. A missing, stale, sentinel, or
out-of-range value is shown as unavailable instead of being guessed.

## Privacy and transport boundary

The Overdrive response can also contain location, vehicle identity, door, trip,
and other fields. Deckscape does not enumerate, log, retain, display, or
transmit them. It immediately reads only the allowlisted field names above and
discards the response.

The client connects to the literal IPv4 loopback address. It performs no DNS
lookup, follows no redirect, accepts only a successful JSON response, and
enforces short connection/read timeouts plus header and body size limits. The
manifest queries only whether the Overdrive package is installed; it adds no
network destination or BYD privilege.

## Useful follow-ons

The same source contains enough read-only data for later cards such as charging
detail, energy/range, 12 V health, and efficiency. The battery card already
surfaces the most useful parts of those groups without adding dashboard
clutter. Doors, locks, commands, location, and trip history are deliberately
excluded from this wallpaper feature because they add privacy or safety risk
without improving at-a-glance driving information.
