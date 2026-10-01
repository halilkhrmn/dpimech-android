# Submitting to F-Droid (main repository)

F-Droid builds the app itself from the tagged source and publishes it. Its inclusion policy has no
rule against AI-assisted code (checked 2026-10-01), unlike IzzyOnDroid (see `IZZYONDROID.md`).

## What is ready

- `docs/fdroid/io.github.halilkhrmn.dpimech.yml`: build metadata for fdroiddata. Checked with
  `fdroid lint` and formatted exactly as `fdroid rewritemeta` writes it (fdroiddata's CI checks both).
- `fastlane/metadata/android/{en-US,tr,ru}`: descriptions, icon, featureGraphic, screenshots,
  changelog — F-Droid reads them from the tag.
- F-Droid-style build checked: fresh clone with submodules, F-Droid's scanner (0 problems; the
  only warning is hev's unused Windows `wintun.dll`, removed by `scandelete`), wrapper removed
  (F-Droid uses its own `gradlew-fdroid`; Gradle 9.8.0 is in its checksum list), no signing
  variables, `gradle assembleRelease` in `app/` → `app/build/outputs/apk/release/app-universal-release-unsigned.apk`.
- Reproducible build: with `Binaries` + `AllowedAPKSigningKeys`, F-Droid compares its build with
  the APK on the GitHub release and, if identical, ships *our* signed APK. Users can then move
  between GitHub, F-Droid and Obtainium without reinstalling.

## Steps

1. **Release key** (once): create it and the four GitHub secrets as in `RELEASING.md`.
2. **First release**: `git tag v0.1.0 && git push origin v0.1.0`. Wait for the release workflow;
   the release must contain `dpimech-0.1.0-universal.apk`.
3. **Signing certificate hash** for the metadata:
   `apksigner verify --print-certs dpimech-0.1.0-universal.apk | grep SHA-256` → put the 64 hex
   characters (no colons) in `AllowedAPKSigningKeys`.
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
