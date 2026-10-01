# Progress

## Phases

- [ ] **Phase 1 — Core:** native build, VpnService, profiles, per-app bypass, hostlist, strategy list, QS tile
  - [x] Gradle skeleton (core / engine / app), version catalog, wrapper, CI workflow
  - [x] `core`: strategy file, argument policy, ciadpi command, packs, ISP table, Lab scoring, profiles
  - [x] Native: byedpi v0.17.3 + hev-socks5-tunnel 2.18.0 submodules, ndk-build for 4 ABIs
  - [x] Linux tests: real ciadpi (domain filter), TUN end-to-end (CI)
  - [x] `engine`: VpnService, ciadpi process, hev JNI, foreground notification with stop
  - [x] `app`: home, profile editor (packs, domains, strategy, apps, domain filter), app picker
  - [x] Remote strategy list (default.json) + community list, cached
  - [x] Quick Settings tile
  - [x] Landing page (GitHub Pages), desktop logo as launcher icon
  - [ ] First test on a real phone (CI APK)
  - [x] Release signing: workflow and docs ready; owner adds the keystore secrets
- [ ] **Phase 2 — Smarts:** Strategy Lab, ISP detection, per-network memory, DNS, watchdog, QUIC switch, wizard
  - [x] Strategy Lab (core runner + screen), tested against real ciadpi and a fake-DPI server
  - [x] ISP detection (ipwho.is), ISP presets marked and tested first
  - [x] Watchdog: restart on exit at once, after two failed SOCKS5 probes when hung
  - [x] Per-network memory (per provider AS number), strategy swapped on network change
  - [x] DNS check (system DNS and UDP 53 vs DNS over HTTPS) with Private DNS advice
  - [ ] Own DoH/DoT resolver inside the tunnel (DECISIONS #13)
  - [ ] QUIC switch (needs a ciadpi change, DECISIONS #15)
  - [x] Easy mode: first-start wizard, whole-phone country preset, "use the best and turn on"
  - [x] Automatic strategy per network (background test, switch, remember)
- [ ] **Phase 3 — Extras:** widget, shortcuts, QR profile sharing
- [ ] **Phase 4 — Release:** GitHub Releases, IzzyOnDroid, F-Droid
  - [x] Fastlane metadata (en/tr/ru), store images, reproducible release build
  - [ ] First signed release (owner: key and secrets)
  - [ ] IzzyOnDroid request (decide on the AI policy first, docs/IZZYONDROID.md)

## Work log

### 2026-10-01 — Logs, problem report, Material You switch, store listing
- Done: Logs screen (live, copy, clear); "Report a problem" (GitHub issue or e-mail, the user sees
  the report first); notification settings (strategy change, bypass stopped) with event
  notifications from the service; settings grouped in sections; Material You switch (power button
  stays green); fastlane metadata in en/tr/ru with generated store images; reproducible builds.
- Verified how: 61 tests (core + 15 screen renders + 15 store screenshots), lint clean; release
  APK manifest has no debuggable/testOnly/cleartext flags, 2.8 MB; two clean release builds in
  different directories are byte-identical; fastlane texts within limits, HTML well-formed.
- Open/next: owner decides on IzzyOnDroid given its AI policy (docs/IZZYONDROID.md), sets the
  GitHub repository description, creates the release key; phone test.

### 2026-10-01 — Friendlier app: navigation, wizard, settings, automatic strategy
- Done: bottom navigation (Home, Test, Settings, About); home with one big green ON / grey OFF button
  and a status line; settings (language, automatic strategy, DNS server, list update, wizard,
  always-on VPN, battery, notifications); About screen; first-start wizard (whole phone with the
  country's commonly blocked sites, or only some apps → test → turn on); automatic strategy in the
  VPN service per provider; country presets (TR, RU) and five new site packs.
- Verified how: 58 tests (core + 12 Robolectric screen renders), lint clean, debug build; screenshots
  of every main screen checked by eye (light, dark, Turkish). Probe hosts of the new packs answer HTTPS.
- Open/next: phone test of the wizard and the automatic strategy on a real network change.

### 2026-10-01 — Phase 2: Strategy Lab, watchdog, DNS check, per-network memory, release workflow
- Done: `LabRunner` (baseline, 4 engines at a time, confirmation rounds), `Socks5`, `SiteCheck`,
  `Watchdog`, `IspLookup`, `DnsCheck`, per-provider strategy memory in `Profile`; Lab screen with
  ISP presets, DNS warnings and "use the best and turn on"; watchdog and network-change handling
  in the VPN service; foreground-service type fallback; signed release workflow with ABI splits.
- Verified how: 43 core tests (1 skipped locally: TUN end-to-end). New `LabIntegrationTest` runs the
  real ciadpi against a local HTTPS server whose fake DPI resets connections when the first TLS
  record names the blocked host: baseline and a no-op strategy fail, `-r 1+s` passes and is
  confirmed (5/5 repeated runs). DNS wire format tested against a fake DNS server and a real DoH
  answer. Signed release build of all five APKs verified with apksigner (throwaway key).
- Open/next: phone test; owner creates the release keystore and secrets (docs/RELEASING.md);
  DoH inside the tunnel and the QUIC switch need engine work (DECISIONS #13, #15).

### 2026-10-01 — Landing page and icon
- Done: `site/` in the desktop page's style (same logo, favicon, flags) with a Desktop / Android
  switcher; the desktop page gets the matching switcher in halilkhrmn/dpimech#8. `pages.yml` deploys
  `site/` from `main`. Launcher icon is now the desktop octopus. README in en/tr/ru.
- Verified how: both pages rendered in Chromium at 1000 px and 390 px (no script errors, no sideways
  scroll); icon checked under a round mask.
- Open/next: owner sets Settings → Pages → Source: "GitHub Actions" once in this repository.

### 2026-10-01 — Phase 1 skeleton: core, engines, VPN service, UI
- Done: Gradle project (AGP 9.4.1, Kotlin 2.4.20, Gradle 9.8); `core` module ported from desktop
  (`default.json` parser, ByeDPI argument policy, packs, ISP table, Lab scoring) plus Android parts
  (ciadpi argv with per-group `--hosts` for the domain filter, VPN app lists, hev config, saved
  profiles); engines as pinned submodules built with ndk-build (arm64, armv7, x86_64, x86; 16 KB
  pages); `engine` module (VpnService, ciadpi child process, hev JNI); `app` module (Compose UI,
  app picker, strategy list refresh, Quick Settings tile, en/tr/ru strings); CI workflow.
- Verified how: 25 core tests pass locally (+1 skipped: TUN end-to-end); ciadpi integration test against the real binary (listed hosts and
  subdomains desynced, others untouched, also with `-A` auto groups); `assembleDebug`,
  `assembleRelease` (2 MB, R8 keeps the JNI class) and `lintDebug` clean locally. The TUN end-to-end
  test could not run in the dev container (kernel without IPv6, hev opens AF_INET6 sockets); it passes
  in CI (run 3: TCP and UDP of a uid routed into a TUN reach the target through hev and ciadpi).
  CI also builds the debug APK and runs lint.
- Open/next: install the CI APK on a phone and try Discord/YouTube profiles; DNS is plain UDP to
  1.1.1.1 through ciadpi until DoH/DoT (Phase 2); engine restart/watchdog (Phase 2); release signing.

### 2026-10-01 — Plan
- Done: researched engines usable without root, agreed scope with the owner, wrote `PLAN.md`,
  `DECISIONS.md`, `AGENTS.md`.
- Verified how: scope reviewed in conversation with the owner.
- Next: Gradle skeleton, native submodules (byedpi, hev-socks5-tunnel), CI that builds a debug APK.
