# Submitting to F-Droid (main repository)

F-Droid builds the app itself from the tagged source and publishes it. Its inclusion policy has no
rule against AI-assisted code (checked 2026-10-01), unlike IzzyOnDroid (see `IZZYONDROID.md`).

## What is ready

- `docs/fdroid/io.github.halilkhrmn.dpimech.yml`: build metadata for fdroiddata. Checked with
  `fdroid lint` and formatted exactly as `fdroid rewritemeta` writes it (fdroiddata's CI checks both).
- `fastlane/metadata/android/{en-US,tr,ru}`: descriptions, icon, featureGraphic, screenshots,
  changelog — F-Droid reads them from the tag.
- F-Droid-style build checked: fresh clone with submodules, F-Droid's scanner (0 problems; the
  only warning is hev's unused Windows `wintun.dll`, removed before the scan by `rm`), wrapper removed
  (F-Droid uses its own `gradlew-fdroid`; Gradle 9.8.0 is in its checksum list), no signing
  variables, `gradle assembleRelease` in `app/` → `app/build/outputs/apk/release/app-universal-release-unsigned.apk`.
- Reproducible build: with `Binaries` + `AllowedAPKSigningKeys`, F-Droid compares its build with
  the APK on the GitHub release and, if identical, ships *our* signed APK. Users can then move
  between GitHub, F-Droid and Obtainium without reinstalling.

## Steps

1. ~~**Release key**~~ — done (2026-10-01).
2. ~~**First release**~~ — `v0.1.0` published with `dpimech-0.1.0-universal.apk` (2026-10-01).
3. ~~**Signing certificate hash**~~ — `dd5e5e13f10e026a494c95c7e23a0b8fca475af71c710d690069a32495ed26ca`
   (`CN=Halil Kahraman, O=DPIMech`) is in `AllowedAPKSigningKeys`. A clean rebuild of the tag
   matches the signed release APK (`apksigcopier compare`), so the reproducible-build check
   should pass on F-Droid's side too.
4. **GitLab account** at gitlab.com, then fork <https://gitlab.com/fdroid/fdroiddata>.
5. In the fork, new branch `io.github.halilkhrmn.dpimech`; copy the YAML to
   `metadata/io.github.halilkhrmn.dpimech.yml`; commit "New app: DPIMech"; push.
6. Open a merge request to `fdroid/fdroiddata` `master` using the "App inclusion" template. Say:
   - what it does (fastlane short description),
   - that you are the author,
   - why it needs `QUERY_ALL_PACKAGES` (the per-app picker) and `VpnService` (local only, no
     remote server),
   - that the build is reproducible and the release APK is attached to each GitHub release,
   - how the code was written (AI-assisted, reviewed and tested by you) — being open avoids
     surprises later.
7. Their CI builds it; reviewers may ask for changes (answer in the MR). Typical review time is
   days to a few weeks. After merge, the app appears in the client within about a day or two.

## Every later release

- Raise `versionCode` and `versionName`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- Tag `vX.Y.Z`. F-Droid's `checkupdates` sees the tag (`UpdateCheckMode: Tags`) and adds the build
  block by itself (`AutoUpdateMode: Version`); nothing to send by hand.

## While waiting

The GitHub release APK works on every phone; Obtainium can follow GitHub releases directly
(`https://github.com/halilkhrmn/dpimech-android`).

## Merge request text (copy into the "App inclusion" template)

> **DPIMech** — open sites and apps that the internet provider blocks with DPI, on the phone, without
> root and without a remote server.
>
> - Source: https://github.com/halilkhrmn/dpimech-android (GPL-3.0-or-later), I am the author.
> - Android companion of the desktop app https://github.com/halilkhrmn/dpimech.
> - Runs ByeDPI (https://github.com/hufrea/byedpi) and hev-socks5-tunnel
>   (https://github.com/heiher/hev-socks5-tunnel), both pinned git submodules built from source with
>   ndk-build. No prebuilt binaries; hev's unused Windows `wintun.dll` is removed with `rm`.
> - `VpnService` is only used locally to hand the chosen apps' traffic to ByeDPI on 127.0.0.1;
>   nothing goes to a remote server. `QUERY_ALL_PACKAGES` is needed for the per-app picker.
> - NonFreeNet: the app asks ipwho.is for the provider's name (per-network strategy memory).
> - Reproducible build: the GitHub release APK is signed by me; a clean build of the tag matches it
>   (`apksigcopier compare`), hence `Binaries` + `AllowedAPKSigningKeys`.
> - Fastlane metadata in en-US, tr, ru.
> - The code was written with an AI assistant and reviewed and tested by me on a real phone.

