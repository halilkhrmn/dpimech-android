# AGENTS.md — DPIMech for Android

Guide for anyone (human or AI agent) working on this repo. **Read this first, then `docs/PLAN.md`
and `docs/PROGRESS.md`.**

Android companion of the desktop app [halilkhrmn/dpimech](https://github.com/halilkhrmn/dpimech):
per-app DPI bypass through `VpnService` + ByeDPI, no root, no remote server.

## Project docs — keep them current

| File | What it holds | When to update |
|---|---|---|
| `AGENTS.md` | Rules, architecture, commands, file map | When structure, commands or conventions change |
| `docs/PLAN.md` | Product plan, feature scope, phases | When scope or phases change |
| `docs/PROGRESS.md` | Phase checklist + dated work log | **At the end of every work session** |
| `docs/DECISIONS.md` | Numbered decisions with reasons | Whenever a non-obvious choice is made |

Work-log entries: newest on top, `### YYYY-MM-DD — short title`, then bullets for *done*, *verified how*, *open/next*.

## Rules

- Commits are authored as the owner: `Halil Kahraman <52932792+halilkhrmn@users.noreply.github.com>`
  (`git config user.name/user.email` in the clone). No AI co-author or session trailers in commit
  messages.
- Each release: add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (≤ 500 bytes)
  before tagging; keep short descriptions ≤ 80 characters.

- Scope is fixed in `docs/PLAN.md`: ByeDPI only, no root mode, no remote tunnels, no Android TV.
- Keep F-Droid compatible: no Google Play Services, Firebase, analytics or crash reporters; no
  executable downloads at runtime; native code only from pinned submodules.
- Strategy data comes from the desktop repo's `strategies/default.json`; do not fork the format.
  If Android needs a change, change it there.
- User-visible strings go through Android resources (`values/`, `values-tr/`, `values-ru/`).
- `README.md` is the main README and stays in English; `README.tr.md` and `README.ru.md` are
  translations of it. Change all three together.

## Commands

Needs JDK 17+ (21 used), Android SDK with platform `android-37.0` and NDK `29.0.14206865`
(`local.properties` → `sdk.dir=…`, not committed). Clone with `--recurse-submodules`.

| What | Command |
|---|---|
| Core unit tests (pure JVM, fast) | `./gradlew :core:test` |
| Build engines for Linux | `./native/build-host.sh` → `build/host/ciadpi`, `build/host/hev/bin/hev-socks5-tunnel` |
| + real ciadpi integration test | `CIADPI=$PWD/build/host/ciadpi ./gradlew :core:test` |
| + end-to-end TUN test (root, IPv6) | `sudo -E env PATH=$PATH CIADPI=… HEV=… ./gradlew :core:test --tests '*TunnelEndToEndTest'` |
| Debug APK | `./gradlew :app:assembleDebug` |
| Lint | `./gradlew :app:lintDebug` |
| Screen renders (Robolectric) → `app/build/screenshots/` | `./gradlew :app:testDebugUnitTest` |
| Store images → `fastlane/` (after the renders) | `python3 tools/fastlane-images.py` (needs Pillow) |
| Release APKs (signed with `DPIMECH_*` env, else unsigned) | `./gradlew :app:assembleRelease` — see `docs/RELEASING.md` |

Tests that need `$CIADPI`/`$HEV`/root are skipped when those are missing; CI runs all of them
(`.github/workflows/ci.yml`) and fails if the end-to-end test was skipped.

## Architecture and file map

```
core/     pure Kotlin, no Android: everything that can be unit-tested
  StrategyFile      shared default.json (format 1); embedded copy in resources/strategies/
  ArgPolicy         ciadpi option allowlist (port of desktop argpolicy.rs, ByeDPI table)
  ByeDpiCommand     strategy → ciadpi argv: placeholders, managed options, domain filter
  DomainPack, Isp   packs (+ Android package names) and ISP table from desktop catalog.rs
  Lab, LabRunner    Strategy Lab: scoring (same order as desktop) and the runner
  Socks5, SiteCheck SOCKS5 client for ciadpi; "does the site open" HTTPS check
  Watchdog          when to restart ciadpi (exit / failed health probes / give up)
  IspLookup, DnsCheck  provider detection (per-network memory key), DNS blocking check
  NetworkInfo       transport, mobile operator (MCC+MNC) and provider → per-network key
  CountryPreset     sites commonly blocked per country (whole-phone profile)
  AppSettings       language, DNS server, DoH, QUIC switch, automatic strategy, wizard flag
  IpPacket, TunnelDns  UDP packet parse/build; TunnelFilter verdicts, DnsMessage, DohClient
  TrafficStats      traffic samples for the home chart and the notification
  Ping              average connect+TLS time of the profile's sites through ciadpi
  QuickCheck        one site, directly and through a short-lived ciadpi (Test tab)
  ProfileBackup     profiles to/from a file (Settings → Backup)
  Profile, SavedProfiles, VpnApps, TunnelConfig, OnlineSource
engine/   Android library: BypassVpnService (+ watchdog, network changes, stats), PacketFilter
          (TUN ↔ hev with DoH / QUIC switch), EngineStats, LiveProgress (Live Update
          notifications), StartMemory (last start, for always-on VPN), Ciadpi (process),
          TProxy (hev JNI), EngineState, NetworkIdentity (app-wide network watcher),
          AndroidEngineLauncher (Lab)
app/      Compose UI: bottom bar (Home, Test = Strategy Lab, Settings, About), first-start wizard,
          profile editor, app picker; QS tile; widget/ (home-screen widget, per-widget profile in
          WidgetConfigActivity); shortcut/ (launcher shortcuts, ShortcutActivity); boot/ (start on boot); lab/ (LabService:
          Lab in a foreground service); repositories (profiles, settings, strategies, Lab)
native/   Android.mk/Application.mk for ndk-build; byedpi + hev-socks5-tunnel submodules (pinned tags)
fastlane/ store listing (en-US, tr, ru) for IzzyOnDroid / F-Droid; images from tools/fastlane-images.py
site/     landing page (plain HTML, same style and logo as the desktop page; deployed by pages.yml)
```

- The launcher icon is the desktop octopus (`crates/gui/assets/logo-source.png` there), as an
  adaptive icon with a light purple background; `site/logo.png` and `favicon.ico` are the desktop files.
- The two landing pages link to each other (Desktop / Android switcher); keep them in step.

- ciadpi runs as a **separate process** (`libciadpi.so` in nativeLibraryDir), one per profile or
  Lab test. hev-socks5-tunnel runs **in-process** through its JNI (it needs the TUN fd).
- DPIMech's own package is always outside the VPN, so ciadpi's sockets reach the network directly.
- The domain filter puts `--hosts <file>` at the start of every ciadpi group; when every group is
  limited, ciadpi adds an empty group, so other hosts pass untouched (checked by the ciadpi test).
