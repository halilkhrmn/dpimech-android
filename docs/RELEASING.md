# Releasing

## One-time: signing key

The key signs every release forever (Android refuses updates signed with another key), so keep a
backup outside GitHub.

```sh
keytool -genkeypair -v -keystore dpimech-release.jks -storetype PKCS12 \
  -alias dpimech -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Halil Kahraman, O=DPIMech"
base64 -w0 dpimech-release.jks   # → DPIMECH_KEYSTORE_BASE64
```

On Windows (PowerShell). `keytool` comes with any JDK; Android Studio has one in
`C:\Program Files\Android\Android Studio\jbr\bin`:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore dpimech-release.jks -storetype PKCS12 -alias dpimech -keyalg RSA -keysize 4096 `
  -validity 10000 -dname "CN=Halil Kahraman, O=DPIMech"
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\dpimech-release.jks")) | Set-Clipboard
```

The second line copies the `DPIMECH_KEYSTORE_BASE64` value to the clipboard.

Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `DPIMECH_KEYSTORE_BASE64` | output of the `base64` command |
| `DPIMECH_KEYSTORE_PASSWORD` | the keystore password |
| `DPIMECH_KEY_ALIAS` | `dpimech` |
| `DPIMECH_KEY_PASSWORD` | the key password (same as the store password for PKCS12) |

## Each release

1. Raise `versionCode` (by one) and `versionName` in `app/build.gradle.kts`; note it in `docs/PROGRESS.md`.
2. Merge to `main`.
3. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`.
4. `.github/workflows/release.yml` checks that the tag matches `versionName`, runs the tests, builds
   signed APKs (`dpimech-<version>-universal.apk` and one per ABI) with `SHA256SUMS`, and creates
   the GitHub release. The website's download button picks up the universal APK.

Local signed build: `DPIMECH_KEYSTORE=… DPIMECH_KEYSTORE_PASSWORD=… DPIMECH_KEY_ALIAS=… DPIMECH_KEY_PASSWORD=… ./gradlew :app:assembleRelease`.

## Later

- IzzyOnDroid: request inclusion once a signed release exists (it reads the newest release's APK).
- F-Droid: metadata in `fastlane/metadata/android/{en-US,tr,ru}`; F-Droid builds from the tag.
