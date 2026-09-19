# F-Droid

This document tracks CountAway's upstream contract with the official F-Droid repository.

F-Droid documentation:

- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/Reproducible_Builds/
- https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/

## Accepted metadata

CountAway's initial inclusion merge request, `fdroid/fdroiddata!46416`, was merged into official `fdroiddata` on September 18, 2026.

The accepted metadata currently identifies CountAway 1.1.7 / versionCode 7 and preserves:

- category `Timer`;
- source repository `https://github.com/santirodriguez/CountAway.git`;
- upstream binary pattern `CountAway-v%v.apk`;
- the historical signing certificate;
- `AutoUpdateMode: Version`;
- stable semantic tag detection.

Metadata acceptance is not the same as public package availability. Before CountAway 1.1.8 can be prepared for publication, Gate A below must prove that the initial package is operational in the public F-Droid repository.

## Upstream readiness

CountAway remains intentionally straightforward for F-Droid:

- source is public;
- license is Apache-2.0;
- the Android project uses the Gradle wrapper and standard Maven repositories;
- runtime code has no proprietary SDK dependency;
- the manifest has no Internet permission;
- Fastlane metadata lives under `fastlane/metadata/android/`;
- English metadata uses F-Droid's `en-US` locale, alongside `es` and `ca`;
- phone screenshots are stored as PNG files under the `en-US` Fastlane metadata;
- official releases use stable semantic `v<version>` tags;
- release APKs use the same long-lived upstream signing identity.

Do not add an F-Droid badge to the README until Gate A has been verified against the public repository.

## Reproducible upstream APKs

F-Droid uses the upstream GitHub release binary together with the CountAway signing certificate to verify reproducible builds. This also preserves Android signing identity between compatible upstream and F-Droid builds.

The expected signing certificate SHA-256 is:

```text
dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a
```

The release workflow verifies this fingerprint explicitly and pins `apksigner` to Android Build Tools 34.0.0. Do not weaken verification, replace the signing identity, add scanner exceptions, or publish a tag merely to force a reproducibility test. Investigate mismatches at their source.

For a release candidate, retain the exact source SHA, signed/unsigned APK SHA-256, certificate, APK size, dependency/permission reports, R8 mapping, and independent rebuild comparison. A mismatch blocks readiness.

## Gate A — initial public F-Droid publication

Gate A is satisfied only when all of the following are true:

1. the CountAway package appears in F-Droid's public package API/index;
2. an official F-Droid CountAway APK is directly downloadable;
3. the downloaded APK verifies with the expected CountAway signing certificate above;
4. the maintainer installs/smoke-tests that official F-Droid APK successfully.

A merged metadata change, merged inclusion MR, passing inclusion pipeline, F-Droid website 404, API error, or failed fetch is not by itself proof that Gate A passed or failed.

The CountAway release workflow verifies items 1–3 during stable draft preparation and fails closed on missing/error evidence. Item 4 remains a human release checklist requirement. No Gate A network logic is included in the Android app.

## Stable tag and upstream binary gates

The fdroiddata recipe uses stable semantic tags for update detection. A normal branch commit or merge to `main` is not a new F-Droid version.

In the normal web release path, `prepare-draft-release` creates a draft GitHub Release configured with tag name `v<version>` and the exact validated target commit, but GitHub does not create the actual `refs/tags/v<version>` Git ref while the release remains a draft. Publishing the validated draft is the deliberate stable-tag gate.

Before F-Droid can consume a newly published CountAway version, verify the exact upstream binary URL publicly:

```text
https://github.com/santirodriguez/CountAway/releases/download/v<version>/CountAway-v<version>.apk
```

A practical check is:

```bash
VERSION="<version>"
curl --fail --location --silent --show-error --output /dev/null \
  "https://github.com/santirodriguez/CountAway/releases/download/v${VERSION}/CountAway-v${VERSION}.apk"
```

Never move an existing stable tag after publication.

## Normal updates after inclusion

Because official metadata now uses `AutoUpdateMode: Version` with stable tag detection, ordinary future CountAway releases should normally be discovered from the published stable tag without manually editing fdroiddata.

After publishing a new upstream release:

1. verify the stable tag resolves to the exact approved commit;
2. verify the public APK URL and checksum;
3. verify the public GitHub asset is the reviewed immutable APK;
4. allow F-Droid's configured update machinery to detect the new version;
5. verify F-Droid builds/reproduces the version, indexes it, and exposes the APK;
6. investigate rather than bypass any reproducibility or signing mismatch.

Manual fdroiddata intervention is appropriate only if a maintainer requests it or the configured update path demonstrably fails.

## Gate B — 1.1.8 on F-Droid

Gate B happens after the upstream CountAway 1.1.8 release is public. It must not be used to create a circular dependency that blocks the upstream release before a public binary exists.

Gate B is complete when F-Droid:

- detects versionName 1.1.8 / versionCode 8 from the stable tag;
- builds/reproduces it successfully;
- indexes it;
- exposes its official APK publicly.

A green upstream release, public GitHub binary, or tag alone is not Gate B.

## Release checklist

For CountAway 1.1.8 and later stable releases:

1. Keep `versionName`, `versionCode`, `CHANGELOG.md`, `docs/releases/<version>.md`, Fastlane changelogs, and screenshots synchronized.
2. Get normal Android CI green and review its retained reports; remember that pull-request CI may validate a merge ref rather than the raw branch SHA.
3. Build the signed `release-candidate` from the exact proposed SHA and review its retained checksum, signing, package/SDK, permission, dependency, native-code, R8, APK-size, and independent-rebuild evidence.
4. Run device/emulator acceptance, including upgrade preservation from the previous public release.
5. Verify Gate A from the official public F-Droid package/API, downloadable APK, signing identity, and a maintainer installation smoke test.
6. Merge release changes only after review and explicit approval.
7. Run `prepare-draft-release` from the current exact `main` SHA; review the rebuilt evidence and F-Droid Gate A check.
8. Review the unpublished draft. The missing `v<version>` Git ref at this stage is expected and must not be recreated manually.
9. Publish the draft only after the APK, checksum, notes, screenshots, upgrade behavior, exact target commit, and validation evidence are approved. Publication creates the stable tag in the normal web path.
10. Verify `v<version>` resolves to the exact approved commit and the public upstream APK/checksum URLs resolve.
11. Let F-Droid's configured auto-update machinery detect the stable version unless maintainers or a concrete failure require manual intervention.
12. Track Gate B until the new F-Droid version is built/reproduced, indexed, and publicly downloadable.

Only after Gate A is verifiably complete should the README be updated separately with the official F-Droid badge/link.
