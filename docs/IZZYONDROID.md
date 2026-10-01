# IzzyOnDroid (then F-Droid)

Checked against <https://izzyondroid.org/docs/general/AppInclusionPolicy/>,
<https://izzyondroid.org/docs/general/Fastlane/> and
<https://izzyondroid.org/docs/reproducibleBuilds/RBDevHints/> on 2026-10-01.

## Checklist

| Requirement | State |
|---|---|
| FOSS licence (OSI/FSF) | GPL-3.0-or-later; ByeDPI and hev-socks5-tunnel are MIT (compatible) |
| Code public on GitHub | yes |
| No proprietary parts, no trackers / ads / analytics | none (AndroidX, Kotlin, kotlinx-serialization only) |
| No `usesCleartextTraffic` | not set; release manifest checked |
| No downloads of executables | engines are inside the APK; only plain-text strategy lists are fetched |
| Unique package name, stable | `io.github.halilkhrmn.dpimech` |
| Repository has a proper description | **owner: set the GitHub "About" text** (e.g. the short description below) |
| Fastlane: short (≤ 80) + full description, icon, screenshots | `fastlane/metadata/android/{en-US,tr,ru}`; validated in CI |
| Screenshots ≤ 2:1 | 720×1440, made by `StoreScreenshotTest` + `tools/fastlane-images.py` |
| featureGraphic 1024×500 | yes |
| Changelog per versionCode | `en-US/changelogs/1.txt` (add one for every release *before* tagging) |
| APK signed with a release key, not debuggable / testOnly | release workflow; **owner: create the key and secrets** (`docs/RELEASING.md`) |
| APK attached to a tagged GitHub release | release workflow (`vX.Y.Z`, matches `versionName`) |
| APK ≤ 30 MB | universal 2.8 MB, per-ABI ~1.9 MB |
| Reproducible build | two clean builds in different directories give byte-identical APKs (unsigned); no NDK build ids, no PNG crunching, JDK 17 in the release workflow |
| Not a game, not "controversial", end-user app | yes |

## Blocker to decide first: the AI policy

IzzyOnDroid's policy says: *"Vibe-coded apps will be rejected"* and *"the code itself should be free
of [LLM-generated content]"*; *"any lack of transparency discovered by us can lead to the project
being degraded to rejected state"*.

This app's code was written with an AI assistant (the commits say so, and the website says
"Developed with AI assistance"). Hiding that would break their transparency rule, and they look for
it. So an inclusion request is likely to be refused as things stand. Options for the owner:

1. Ask first: open a short question in their issue tracker describing how the app was made
   (AI-assisted, reviewed and tested by a person, tests and reproducible builds) before a full request.
2. Skip IzzyOnDroid: go to F-Droid directly (check its current policy on AI-assisted code first), or
   publish an own F-Droid-compatible repository (fdroidserver on GitHub Pages) and Obtainium
   instructions; users add it once and get updates.
3. GitHub Releases only for now (the website already offers the APK).

## Inclusion request (draft)

Request at <https://gitlab.com/IzzyOnDroid/repo/-/issues> using their "Inclusion request" template:

- **App:** DPIMech for Android — `io.github.halilkhrmn.dpimech`
- **Source:** https://github.com/halilkhrmn/dpimech-android (GPL-3.0-or-later)
- **APK:** attached to GitHub releases (`dpimech-<version>-universal.apk`, plus per-ABI APKs)
- **Fastlane:** `fastlane/metadata/android`
- **Summary:** Open sites your provider blocks with DPI – only for the apps you choose, no root
- **Notes:** uses `VpnService` locally only (no remote server); `QUERY_ALL_PACKAGES` for the app
  picker (choose which apps get the bypass); native code built from pinned submodules (ByeDPI,
  hev-socks5-tunnel); downloads only plain-text strategy lists from the desktop repository and
  ByeDPIManager; ipwho.is is asked for the provider name (per-network memory, ISP presets);
  reproducible: CI builds from the tag with JDK 17.
- **AI:** state honestly how the code was written (see the blocker above).
