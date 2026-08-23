# F-Droid inclusion

This document tracks CountAway's upstream requirements for the official F-Droid repository.

F-Droid documentation:

- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/docs/Build_Metadata_Reference/
- https://f-droid.org/docs/Reproducible_Builds/
- https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/

## Upstream readiness

CountAway is intentionally straightforward for F-Droid:

- source is public;
- license is Apache-2.0;
- the Android project uses the Gradle wrapper and standard Maven repositories;
- runtime code has no proprietary SDK dependency;
- the manifest has no Internet permission;
- Fastlane metadata lives under `fastlane/metadata/android/`;
- English metadata uses F-Droid's `en-US` locale, alongside `es` and `ca`;
- phone screenshots are stored as PNG files under the `en-US` Fastlane metadata;
- official releases use stable semantic `v<version>` tags;
- release APKs are signed with the same long-lived upstream key.

The F-Droid category is `Timer`.

Do not add an F-Droid badge to the README until CountAway is actually published in the official repository.

## Current submission

The official inclusion merge request is `fdroid/fdroiddata!46416`.

The current initial-inclusion recipe intentionally contains only CountAway 1.1.2 (`versionCode 4`) and pins it to its exact release source commit:

```text
ee95dcabc5315b3f4f0c58633eccf94e1ca6621b
```

The relevant fdroiddata values are:

```yaml
Builds:
  - versionName: 1.1.2
    versionCode: 4
    commit: ee95dcabc5315b3f4f0c58633eccf94e1ca6621b
    subdir: app
    gradle:
      - yes

Binaries:
  https://github.com/santirodriguez/CountAway/releases/download/v%v/CountAway-v%v.apk

AllowedAPKSigningKeys: dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
CurrentVersion: 1.1.2
CurrentVersionCode: 4
```

Do not move the 1.1.2 tag, replace its published APK, regenerate it under the same version, or change the pinned source commit. Historical upstream changelogs and release notes for older versions remain valid project history, but they are not current build entries in the initial-inclusion recipe.

## Reproducible upstream APKs

F-Droid uses the upstream GitHub release binary together with the CountAway signing certificate to verify reproducible builds. This also preserves Android signing identity between compatible upstream and F-Droid builds.

The expected signing certificate SHA-256 is:

```text
dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a
```

The release workflow verifies this fingerprint explicitly and pins `apksigner` to Android Build Tools 34.0.0. Do not weaken verification, replace the signing identity, or add scanner exceptions merely to make a build pass; investigate reproducibility failures at their source.

## Stable tag gate

The fdroiddata recipe uses stable semantic tags for update detection. A normal branch commit or merge to `main` is not a new F-Droid version, but creating a new stable `v<version>` tag can be detected as one.

Once CountAway is included, `AutoUpdateMode: Version` means an ordinary future update can be generated from a detected stable tag without a manual fdroiddata edit. Treat creation of a stable tag as the start of the public F-Droid update path, not as a harmless staging action.

For that reason:

- manual release-workflow runs produce release candidates only;
- the workflow does not use a manual mode to create a stable tag;
- a stable release tag must be created explicitly on the exact intended release commit;
- do not create a stable tag merely to test release automation or metadata;
- do not create the stable tag until the signed release candidate, release notes, Fastlane changelogs, upgrade behavior, and final source commit are already approved;
- after creating the stable tag, review the resulting draft release promptly and publish only if all workflow verification remains green.

There is intentionally no stable release tag for a release candidate.

## Public binary gate

The exact upstream `Binaries` URL must resolve publicly before a new F-Droid build can rely on it.

For version `<version>`, verify after publishing the GitHub Release:

```text
https://github.com/santirodriguez/CountAway/releases/download/v<version>/CountAway-v<version>.apk
```

A practical check is:

```bash
VERSION="<version>"
curl --fail --location --silent --show-error --output /dev/null \
  "https://github.com/santirodriguez/CountAway/releases/download/v${VERSION}/CountAway-v${VERSION}.apk"
```

If F-Droid has already generated an automatic update from the stable tag, do not create a competing manual version update unless a maintainer or a concrete failure requires it. If a manual fdroiddata change is required, make it only after the public binary check succeeds and point it to the exact release commit.

## Release checklist

For a future stable release:

1. Keep `versionName`, `versionCode`, `CHANGELOG.md`, `docs/releases/<version>.md`, and all three Fastlane changelogs synchronized.
2. Get Android CI green on the exact proposed release commit.
3. Build and smoke-test a signed release candidate, including upgrade preservation, before creating the stable tag.
4. Merge release changes only after review and explicit approval.
5. Create `v<version>` explicitly on the exact final release commit only when the release is ready to proceed publicly; never move an existing stable tag.
6. Verify the release workflow reports the expected signing-certificate SHA-256 and APK metadata.
7. Review the draft GitHub Release and publish it only after the APK, checksum, and notes are approved.
8. Verify the exact public `Binaries` URL resolves after publication.
9. Let the configured F-Droid automatic update path handle the normal version update; make a manual fdroiddata change only when required and only after the public binary check succeeds.
10. Wait for F-Droid CI and maintainer processing before considering the F-Droid update complete.

Once the inclusion merge request is accepted and CountAway is actually published, the README can be updated separately with the official F-Droid badge/link.
