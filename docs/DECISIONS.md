# Decisions

Numbered, never renumbered. Superseded decisions stay, marked **Superseded by #N**.

## 1. Separate repository (2026-10-01)
Desktop and Android have different release cycles, toolchains (Cargo vs Gradle/NDK) and stores.
IzzyOnDroid takes the APK from the repo's latest release and F-Droid follows tags; desktop installers and
tags in the same repo would confuse both. The shared strategy list is fetched by URL from the desktop repo,
so nothing is lost by splitting.

## 2. ByeDPI as the only engine (2026-10-01)
Without root, Android offers no packet-level hooks (no WinDivert/NFQUEUE equivalent), so zapret/nfqws is out.
ByeDPI works as a local SOCKS5 server, runs the same `bye_dpi` strategies as desktop, and is proven on
Android (ByeByeDPI and others). sing-box/Xray fragment add little over ByeDPI for this purpose and would
make the APK much larger.

## 3. hev-socks5-tunnel for TUN → SOCKS5 (2026-10-01)
Small C library, MIT, handles TCP and UDP, already paired with ByeDPI in several Android apps.

## 4. No remote tunnels, no root mode, no Android TV (2026-10-01)
Scope is DPI bypass for selected apps only. WARP/WireGuard/VLESS, a root mode and TV support were
considered and dropped by the owner. Keeps the app small and free of the F-Droid NonFreeNet anti-feature.

## 5. Engines bundled and built from source (2026-10-01)
Unlike desktop (#7 there), Android engines are compiled into the APK from pinned submodules.
F-Droid forbids downloading executables at runtime, and Android cannot run downloaded binaries cleanly anyway.

## 6. Kotlin + Jetpack Compose + Material 3 Expressive, minSdk 26 (2026-10-01)
Current Android UI toolkit, dynamic colour on Android 12+. API 26 covers almost all phones in use while
keeping notification channels, adaptive icons and modern `VpnService` behaviour available.

## 7. Application ID `io.github.halilkhrmn.dpimech` (2026-10-01)
Based on a domain the owner controls (GitHub Pages), as F-Droid prefers. Cannot change after first release.
