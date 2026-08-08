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

## Build a release candidate

Run the **CountAway Release** workflow manually with:

- the version from `app/build.gradle.kts`;
- mode `release-candidate`.

The workflow:

1. verifies the requested version;
2. runs tests and lint;
3. builds the R8/resource-shrunk release APK;
4. aligns and signs the APK;
5. verifies the Android signature, package name, version code, and version name;
6. generates a SHA-256 checksum and signing-certificate report;
7. uploads the release candidate and R8 mapping as workflow artifacts.

For 1.0.0, the public release files are intended to be:

```text
CountAway-v1.0.0.apk
CountAway-v1.0.0.apk.sha256
```

The signing report and R8 mapping are verification/debug artifacts and do not need to be attached to the public release.

## Prepare a draft release

After the release candidate is approved, run the **CountAway Release** workflow again from `main` with:

- the approved version;
- mode `prepare-draft-release`.

The workflow rebuilds and verifies the final APK from the exact selected `main` commit. Only after those checks succeed, it creates or reuses tag `v<version>` at that exact commit and creates or updates a GitHub Release as a draft using `docs/releases/<version>.md`.

The draft release receives only the public release assets:

```text
CountAway-v<version>.apk
CountAway-v<version>.apk.sha256
```

The draft must remain unpublished until its tag target, release notes, APK, and checksum have been reviewed.

A push of an already-created `v*` tag also follows the verified build and draft-release path.

## Publish

Publishing is intentionally separate from preparation. Before publishing the GitHub Release:

- verify CI on the exact release commit;
- install and smoke-test the signed APK on a real Android device or emulator;
- verify the SHA-256 checksum;
- record the signing certificate SHA-256 fingerprint somewhere durable;
- confirm the final release notes and public assets;
- confirm the release is still a draft.

Only then publish the prepared GitHub Release.
