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
  - [ ] First test on a real phone (CI APK)
  - [ ] Release signing
- [ ] **Phase 2 — Smarts:** Strategy Lab, ISP detection, per-network memory, DNS, watchdog, QUIC switch, wizard
- [ ] **Phase 3 — Extras:** widget, shortcuts, QR profile sharing
- [ ] **Phase 4 — Release:** GitHub Releases, IzzyOnDroid, F-Droid

## Work log

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
  test could not run in the dev container (kernel without IPv6, hev opens AF_INET6 sockets): CI runs it.
- Open/next: install the CI APK on a phone and try Discord/YouTube profiles; DNS is plain UDP to
  1.1.1.1 through ciadpi until DoH/DoT (Phase 2); engine restart/watchdog (Phase 2); release signing.

### 2026-10-01 — Plan
- Done: researched engines usable without root, agreed scope with the owner, wrote `PLAN.md`,
  `DECISIONS.md`, `AGENTS.md`.
- Verified how: scope reviewed in conversation with the owner.
- Next: Gradle skeleton, native submodules (byedpi, hev-socks5-tunnel), CI that builds a debug APK.
