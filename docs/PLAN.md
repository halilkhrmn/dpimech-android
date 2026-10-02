# DPIMech for Android — Plan

Native Android companion of [DPIMech](https://github.com/halilkhrmn/dpimech): open sites and apps that
the internet provider blocks with DPI, without root and without sending traffic to anyone's server.

> **Not a VPN service.** Android's `VpnService` is only used to capture traffic on the phone. Every
> connection still goes straight to the site; nothing is tunnelled to a remote server, the IP address
> is not hidden, and sites blocked by IP stay blocked.

## Decisions (summary — details in `DECISIONS.md`)

| Topic | Choice |
|---|---|
| Repository | Separate repo `halilkhrmn/dpimech-android` (not a folder in the desktop repo) |
| Language / UI | Kotlin, Jetpack Compose, Material 3 Expressive, dynamic colour (Material You) |
| Minimum Android | 8.0 (API 26) |
| Engine | **ByeDPI (ciadpi)** only, built from source with the NDK, bundled in the APK |
| TUN → engine | **hev-socks5-tunnel** (TUN ↔ local SOCKS5), built from source with the NDK |
| Root | Not required, not supported (no root mode) |
| Remote tunnels (WARP, WireGuard, VLESS…) | **Out of scope** |
| Google services | None (no Play Services, no Firebase, no analytics) |
| Distribution | GitHub Releases → IzzyOnDroid → F-Droid |
| Application ID | `io.github.halilkhrmn.dpimech` |
| Licence | GPL-3.0 (same as desktop; ByeDPI and hev-socks5-tunnel are MIT) |

## Architecture

```
 selected apps ──► VpnService TUN ──► hev-socks5-tunnel ──► ciadpi (SOCKS5, 127.0.0.1)
                    (per-app allow/deny list)   (TCP + UDP)          │  split / disorder / fake …
                                                                     ▼  protect()ed sockets
                                                                  the real site
 other apps ─────────────────────────────────────────────────────►  the real site (untouched)
```

- `ciadpi` and `hev-socks5-tunnel` are compiled from git submodules with ndk-build. hev-socks5-tunnel
  is a shared library driven through its JNI inside the VPN service process; ciadpi is an executable
  shipped as `libciadpi.so` and run as a child process (one per profile / Lab test, see DECISIONS #8).
- DPIMech's own package is always outside the VPN, so engine sockets do not loop (DECISIONS #9).
- DNS inside the tunnel is answered by our own resolver (DoH/DoT, see Phase 2), so DNS-based blocking
  does not break the bypass.

### Module layout (planned)

```
app/            Compose UI, navigation, settings, Quick Settings tile, widget, shortcuts
core/           models, strategy file parser, domain packs, ISP table, Lab scoring (pure Kotlin, unit-tested)
engine/         VpnService, JNI bindings, engine lifecycle + watchdog, DNS resolver
native/         ndk-build files: byedpi/ and hev-socks5-tunnel/ submodules (hev brings its own JNI)
```

## Shared with the desktop app

- **Strategy list:** the `bye_dpi` section of
  `https://raw.githubusercontent.com/halilkhrmn/dpimech/main/strategies/default.json` (format 1).
  The APK ships a copy as fallback and fetches the newest one at runtime — a fix on desktop reaches
  Android without a release. It is plain data (argument strings), never code.
- **Site packs:** `strategies/packs.json` next to `default.json` (format 1): domains, probe hosts and
  the Android package names per pack. Both apps embed it; Android also fetches the newest one, so a
  new site reaches phones without a release (DECISIONS #26).
- **ISP table** (Türk Telekom, Superonline, Turkcell, Vodafone, TurkNet, …) and ISP preset matching.
- **Strategy Lab scoring:** confirmed first, then success rate, then speed (`LabResult::score`).
- **Placeholders:** `{sni}` (harmless SNI for fake packets) and `{hostlist}` behave as on desktop.
- **Online community list:** `romanvht/ByeDPIManager` `strategies.txt`, credited in the UI.
- **Translations:** Turkish, English, Russian; wording taken from the desktop `.po` files where it fits.

## Features

### Bypass scope
- **Per-app bypass**, two modes: *only selected apps* or *all apps except selected*
  (`addAllowedApplication` / `addDisallowedApplication`). Unselected apps never enter the VPN.
- **Domain filter (hostlist):** inside a selected app, only the profile's domains get the bypass
  strategy; other connections pass through untouched (ByeDPI hosts list).
- **Ready-made packs:** choosing the Discord profile also ticks the Discord app; YouTube ticks
  YouTube and known alternative clients, etc.
- **App picker:** search, icons, "show system apps" switch, one list per profile.

### Strategies and smarts
- **Strategy list** from `default.json` + online community list.
- **Strategy Lab:** runs each strategy on a temporary ciadpi instance against the pack's probe hosts,
  with a baseline run first; extra confirmation rounds for the best candidates; suggests the winner.
- **ISP detection** and ISP presets marked *recommended*.
- **Per-network strategy memory:** remembers the working strategy per network (each Wi-Fi, mobile
  data per operator) and switches automatically when the network changes.
- **Watchdog:** health probe through the local SOCKS5; restarts the engine within a second if it hangs.
- **DNS:** DoH inside the tunnel (packet filter in front of hev, DECISIONS #23) + a check that
  compares the system DNS with DoH to spot DNS blocking.
- **QUIC block switch:** drop UDP/443 for bypassed apps so browsers and YouTube fall back to TCP
  (same packet filter).

### Android integration
- **Quick Settings tile:** on/off from the notification shade.
- **Home-screen widget:** status + profile switch.
- **Shortcuts:** "turn on the Discord profile, then open Discord" (pinned shortcut / app shortcut).
- **Always-on VPN** support and **start on boot**.
- **Battery optimisation prompt** so the system does not kill the service.

### Interface
- Compose + Material 3 Expressive, dynamic colour, light/dark.
- **Home:** power button in one card with state, uptime, profile and network/DoH/QUIC chips; live
  traffic chart of the last minute (download area, upload dashed line) with totals, DNS and restarts.
- **Easy mode:** "Just make it work" wizard (pick sites → test → turn the best strategy on), as on desktop.
- **Advanced mode:** strategies, Strategy Lab, logs.
- Connection check with average ping per profile.
- Logs and problem report (GitHub issue or e-mail, as on desktop). No telemetry.

### Known limits (shown in Settings / help)
- Android allows **one VPN at a time**: another VPN or an ad-blocker in VPN mode cannot run together.
- Private DNS / ad-blocking DNS set in Android settings is replaced inside the tunnel by our resolver
  for bypassed apps.
- Sites blocked by IP address stay blocked (no remote tunnel by design).

## Phases

1. **Core** — native build (ciadpi + hev), VpnService, profiles, per-app bypass, hostlist,
   built-in + remote strategy list, Quick Settings tile, notification with stop action.
2. **Smarts** — Strategy Lab, ISP detection, per-network memory, DNS (DoH/DoT + check), watchdog,
   QUIC switch, Easy-mode wizard.
3. **Extras** — widget, shortcuts. (QR profile sharing dropped, DECISIONS #22.)
3b. **Live status and more shortcuts** (owner's list, 2026-10-01; done, see PROGRESS):
   - Strategy Lab and the automatic test in a foreground service with a progress notification, so
     they keep running when the user switches to another app; Android 16 Live Updates
     (promoted ongoing notification, `ProgressStyle`) where available.
   - Richer ongoing notification while on: state, strategy, restarts, average ping of the
     profile's sites (as on desktop), traffic (first part done: traffic, DNS, QUIC, restarts).
   - Per-profile widgets: each widget instance bound to a profile, with a configure screen
     (profile, look: compact / with traffic).
   - More long-press items on the app icon: static shortcuts for "Strategy test", "Logs",
     "Turn off", next to the profile shortcuts.
4b. **v0.2.0** (owner's list, 2026-10-01):
   - Start on boot and Android's always-on VPN (the service restarts the last profile).
   - Strategy lists updated automatically when older than a week.
   - "Next profile" button in the bypass notification.
   - Profile export / import (a file, for a new phone).
   - Quick site check: does a site open directly, and through the bypass?
   - More countries for the whole-phone preset: Iran, Kazakhstan, Belarus, Egypt (owner's choice);
     new languages Persian and Arabic for the app and the README (Russian covers KZ and BY).
   - Site packs shared with the desktop app (`packs.json` next to `default.json`).
4. **Release** — GitHub Releases (signed APK, per-ABI + universal), IzzyOnDroid, then F-Droid.

## Build, CI and testing

- Gradle (Kotlin DSL) + version catalog; NDK (ndk-build, run as a Gradle task) for `native/`.
- GitHub Actions: build debug APK on every push, unit tests for `core/`, lint; release workflow on tags
  builds signed APKs and attaches them to the GitHub release.
- The C engines can also be built and exercised on Linux (SOCKS5 + strategy arguments) without Android.
- Real-world testing happens on a phone: CI APK → install → report.

## F-Droid requirements (keep from day one)

- Everything built from source with a FLOSS toolchain; engines are git submodules pinned to tags.
- **No executable downloads at runtime** (unlike desktop, engines are inside the APK).
  Downloading `default.json` / community lists is data and allowed.
- No proprietary dependencies, no Google Play Services, no Firebase, no crash reporters.
- Fastlane metadata (`fastlane/metadata/android/{en-US,tr,ru}`) for store text and screenshots.
- Version tags `vX.Y.Z` and `versionCode` increments so F-Droid's auto-update can follow releases.
- Aim for reproducible builds (fixed NDK version, no timestamps in the APK).

## Open items

- Material 3 Expressive: Expressive components are still alpha (material3 1.5); the app uses stable
  material3 from the Compose BOM until they are stable (DECISIONS #11).
- Which alternative YouTube clients the YouTube pack should include.
- ~~UDP for Discord voice through ciadpi~~: works on the owner's phone (2026-10-01).
