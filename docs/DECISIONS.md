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

## 8. ciadpi runs as a child process, hev-socks5-tunnel in-process (2026-10-01)
ciadpi keeps its settings in globals and is written as a program (`main`, getopt), so running it twice
in one process needs patches (other apps start a fresh process per run for this reason). As an
executable packaged as `libciadpi.so` it lands in nativeLibraryDir, the one place apps may execute
from; each profile and each Strategy Lab test gets its own clean instance that can be killed and
restarted, and a crash does not take the VPN service with it. Needs `extractNativeLibs`
(`jniLibs.useLegacyPackaging = true`). hev-socks5-tunnel must own the TUN file descriptor, so it runs
in-process through its upstream JNI (`PKGNAME`/`CLSNAME` set in `native/Application.mk`).

## 9. Own package outside the VPN instead of `protect()` (2026-10-01)
ciadpi's sockets belong to DPIMech's uid. Keeping DPIMech itself out of the VPN (it is never in the
allow-list; it is always in the deny-list) stops traffic loops without passing every socket back to
Kotlin for `VpnService.protect()`. ciadpi's `--protect-path` stays a managed option for later use.

## 10. ndk-build as a Gradle task, not CMake (2026-10-01)
hev-socks5-tunnel and its third-party parts maintain `Android.mk` files upstream; rewriting them as
CMake would have to track every upstream change. A small Gradle task runs `ndk-build` and feeds the
result to `jniLibs`, which also lets the ciadpi executable be packaged (AGP only packages shared
libraries from `externalNativeBuild`). 16 KB page alignment and `-ffile-prefix-map` are set for
Android 15+ devices and reproducible builds.

## 11. compileSdk 37, targetSdk 36, stable Material 3 (2026-10-01)
androidx.core 1.19 needs compileSdk 37. targetSdk stays at 36 until Android 17 behaviour changes are
tested on a phone. Material 3 Expressive components are alpha only (material3 1.5.0-alphaXX); stable
material3 from the Compose BOM is used, with dynamic colour.

## 12. Linux tests for the engines (2026-10-01)
Real-world testing needs a phone, but the data path can be checked on Linux: core tests run the real
ciadpi with `ByeDpiCommand` output (domain filter, groups), and an end-to-end test routes one uid
through a TUN → hev-socks5-tunnel → ciadpi, the same chain as on Android. CI runs both (root and
IPv6 needed for the second).
