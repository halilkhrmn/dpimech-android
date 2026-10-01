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

## 13. DNS: check and advise now, own DoH resolver later (2026-10-01)
Bypassed apps send DNS as plain UDP to 1.1.1.1 through hev-socks5-tunnel and ciadpi. Some providers
answer every port-53 query themselves, and a VPN app cannot point Android's resolver at a local DoH
server (port 53 cannot be bound, the system resolver only talks to addresses on the VPN). hev's
`mapdns` only moves resolution into ciadpi, which uses the network's DNS. A real DoH path needs a
SOCKS5 front in the app or a ciadpi patch; both were left for later. For now the Strategy Lab checks
the phone's DNS and UDP 53 to the tunnel's server against DNS over HTTPS (1.1.1.1, as on desktop)
and, on a mismatch, advises Android's Private DNS, which encrypts DNS for the VPN network as well.

## 14. Per-network memory is per provider (AS number), not per Wi-Fi name (2026-10-01)
Reading the Wi-Fi name (SSID) needs the location permission on Android 10+ and location turned on.
What decides which strategy works is the provider's DPI, so the memory is keyed by the provider's AS
number from the same ipwho.is lookup the Lab uses. Two Wi-Fi networks of the same provider share a
strategy, which is what the user wants anyway.

## 15. QUIC switch postponed: needs a ciadpi change (2026-10-01)
Dropping UDP/443 for bypassed apps cannot be expressed with ciadpi 0.17 groups: when every group is
limited, ciadpi appends a catch-all group, so a datagram always finds one; `--no-udp` drops all UDP
(DNS, Discord voice). A small patch in ciadpi (or upstream option) is needed; apps fall back to TCP
on their own when QUIC is blocked, so this is not urgent.

## 16. Whole phone with the country's blocked sites is the default setup (2026-10-01)
Most users want "blocked sites open", not a per-app setup. The wizard therefore suggests a profile
for every app (`ALL_EXCEPT` with no exceptions) that is limited to the sites commonly blocked in the
user's country (`CountryPreset`, country from the ISP lookup or the phone's locale). Because the
domain filter scopes every ciadpi group, other traffic passes through untouched. Per-app profiles
stay available. Country lists are kept short and only name widely reported blocks.

## 17. AppCompat for the in-app language (2026-10-01)
Android 13+ has per-app languages built in; older versions need AppCompat's
`setApplicationLocales` and its locale holder service. That is the one reason for the AppCompat
dependency (no Google services involved, F-Droid compatible).

## 18. Screens checked with Robolectric screenshots (2026-10-01)
The dev container has no emulator (no KVM). Robolectric with native graphics and Roborazzi renders
the Compose screens on the JVM; the images are looked at by hand and uploaded by CI. They are not
compared against references yet, so they catch crashes and let a reviewer see layout changes.

## 19. Reproducible builds from the start (2026-10-01)
IzzyOnDroid and F-Droid can verify that a published APK was built from the tagged source. NDK build
ids and PNG crunching were the only differences found; both are off. The release workflow builds
clean with JDK 17 (the verifiers' default) and keeps `version-control-info.textproto`.

## 20. IzzyOnDroid's AI policy is an open question for the owner (2026-10-01)
IzzyOnDroid rejects apps whose code was written by generative AI and treats hidden AI use as a
reason for rejection. This code was written with an AI assistant, so the listing cannot be prepared
"around" that: the request must say so honestly, or another channel must be used. Options are in
docs/IZZYONDROID.md; the choice is the owner's.

## 21. Mobile operator from MCC+MNC before the provider lookup (2026-10-01)
On mobile data Android gives the operator code and name without any permission, so the provider is
known at once and also offline. `NetworkInfo` keys the memory by AS number when ipwho.is answers and
by `mobile:<MCC+MNC>` otherwise. The Android ISP table adds Türkiye's operator codes and Türk
Telekom's mobile AS (20978); the desktop table should get the AS number too.

## 22. No QR profile sharing (2026-10-01)
Dropped by the owner. It would have needed a profile format agreed with the desktop app and a
camera/QR dependency for little gain; profiles are quick to make with the wizard and the Lab.
Phase 3 is the home-screen widget and shortcuts.

## 23. DoH and the QUIC switch through a packet filter in front of hev (2026-10-01)
Decisions #13 and #15 left both waiting for a ciadpi change. Instead, when either is on, the TUN
is not given to hev-socks5-tunnel directly: the service reads it (non-blocking, `poll`) and hands
hev one end of an `AF_UNIX SOCK_SEQPACKET` pair, which hev reads and writes like a TUN (one packet
per read/write). Each packet the apps send is checked: UDP to port 53 (any server, so hard-coded
resolvers are covered too) is answered over DNS over HTTPS by DPIMech itself, outside the VPN;
UDP to port 443 is dropped when the QUIC switch is on; everything else goes to hev unchanged.
DoH uses the chosen DNS server's endpoint by IP (no lookup needed first); unknown servers use
Cloudflare. When DoH fails the query falls back to the old path (plain DNS through ciadpi), and
after three failures in a row DoH rests for a minute so lookups do not wait for timeouts. With
both switches off nothing changes (hev gets the TUN), so the copy costs nothing for those users.
The filter is Kotlin, not native code, to keep "native code only from pinned submodules".

## 24. Always-on VPN replays the last start request (2026-10-01)
When Android starts the VPN service for always-on VPN (or restarts it), the intent carries no
profile, and the engine module does not know the app's profile store. The service therefore keeps
its own copy of the last start request (`StartMemory`: profile JSON and switches) and replays it.
Changes made to a profile while the bypass is off reach the copy the next time the app turns the
bypass on. "Start when the phone starts" is a separate switch in the app for people who do not
want to change system settings; it uses the app's current profile.

