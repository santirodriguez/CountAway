# Releasing CountAway

CountAway releases are built from the repository and signed with a long-lived Android signing key that must never be committed.

## One-time signing key setup

Generate the release key on a trusted local machine and keep an offline backup:

```bash
keytool -genkeypair -v \
  -keystore countaway-release.jks \
  -alias countaway \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Do not commit, email, or otherwise publish the keystore or its passwords. The repository already ignores `*.jks` and `*.keystore` files.

Store the following repository secrets in GitHub Actions:

- `COUNTAWAY_RELEASE_KEYSTORE_B64`: base64-encoded contents of the keystore
- `COUNTAWAY_RELEASE_STORE_PASSWORD`: keystore password
- `COUNTAWAY_RELEASE_KEY_ALIAS`: key alias, normally `countaway`
- `COUNTAWAY_RELEASE_KEY_PASSWORD`: key password

On GNU/Linux, the keystore value can be prepared with:

```bash
base64 -w 0 countaway-release.jks
```

On macOS:

```bash
base64 < countaway-release.jks | tr -d '\n'
```

## Release invariants

The release process is intentionally strict:

- `versionName` and `versionCode` come from `app/build.gradle.kts`; the workflow does not maintain a second version value.
- A manual workflow run explicitly selects either `release-candidate` or `prepare-draft-release`.
- `prepare-draft-release` is allowed only from `main` and only for the current `main` head.
- Draft release preparation creates or reuses stable tag `v<version>` only after build, metadata, signing, and checksum validation succeed.
- The tag version must exactly match `versionName` in the release source.
- A prepared release must have a matching `CHANGELOG.md` section, `docs/releases/<version>.md`, and Fastlane changelogs named after the exact `versionCode` for `en-US`, `es`, and `ca`.
- The release APK must be signed by certificate SHA-256 `dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a`.
- GitHub Actions dependencies are pinned to immutable commit SHAs.

Creating a stable `v<version>` tag is a release gate. It must be a deliberate action because external update systems, including F-Droid, can use stable tags for version detection.

## Release toolchain

The release workflow uses JDK 17 and explicitly installs Android Build Tools 34.0.0 for `zipalign`, `apksigner`, and `aapt`.

The Gradle/AGP build already produces aligned APK output. The workflow verifies that alignment instead of rewriting the APK, then signs the exact Gradle output with the pinned `apksigner`. This is intentional: F-Droid documents compatibility constraints around newer `apksigner` output and `apksigcopier` used for reproducible builds.

Do not replace the pinned signing toolchain with “latest” without re-validating the F-Droid reproducible-build path.

## Build a release candidate

Run the **CountAway Release** workflow manually from the branch and commit that should be tested, and choose `release-candidate`.

There is no version input. The workflow derives `versionName` and `versionCode` directly from `app/build.gradle.kts` and rejects ambiguous or invalid values.

A release-candidate run:

1. resolves the application version from Gradle;
2. runs tests and lint;
3. builds the R8/resource-shrunk release APK;
4. verifies APK alignment and signs with the pinned Android Build Tools;
5. verifies the signing certificate SHA-256, package name, version code, and version name;
6. generates a SHA-256 checksum and signing-certificate report;
7. uploads the release candidate and R8 mapping as workflow artifacts.

Public release files use this naming convention:

```text
CountAway-v<version>.apk
CountAway-v<version>.apk.sha256
```

The signing report and R8 mapping are verification/debug artifacts and do not need to be attached to the public release.

## Prepare a draft release

After the release candidate is approved and the final release commit is on `main`:

1. confirm `versionName` and `versionCode` are final;
2. confirm `CHANGELOG.md`, `docs/releases/<version>.md`, and all three Fastlane changelogs are present and correct;
3. confirm Android CI is green on that exact commit;
4. run **CountAway Release** manually from `main` and choose `prepare-draft-release`.

The workflow derives the version from Gradle, verifies that the selected commit is still the current `main` head, rebuilds the exact source, validates release metadata, signing identity, package/version information, and checksum, then creates or reuses `v<version>` on that exact commit and creates or updates a draft GitHub Release.

A direct push of an existing valid `v<version>` tag remains supported, but the normal web release path is `prepare-draft-release` from `main`.

The draft release receives only:

```text
CountAway-v<version>.apk
CountAway-v<version>.apk.sha256
```

The draft must remain unpublished until its tag target, release notes, APK, checksum, signing identity, and installation behavior have been reviewed.

## Publish

Publishing is intentionally separate from preparation. Before publishing the GitHub Release:

- verify CI on the exact release commit;
- install and smoke-test the signed APK on a real Android device or emulator;
- verify an upgrade from the previous public CountAway release preserves countdowns and existing widgets;
- verify the SHA-256 checksum;
- verify the signing certificate SHA-256 matches the expected fingerprint above;
- confirm the final release notes and public assets;
- confirm the release is still a draft.

Only then publish the prepared GitHub Release.

For the mandatory public-asset check before updating F-Droid, continue with [`FDROID.md`](FDROID.md).
