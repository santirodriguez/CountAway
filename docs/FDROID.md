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

While the initial-inclusion merge request remains open, keep only the latest public stable CountAway release in its build recipe, pinned to that release's exact source commit.

After publishing a newer stable GitHub release and verifying its public APK URL:

1. replace the existing CountAway build entry in the same merge request;
2. update `CurrentVersion` and `CurrentVersionCode`;
3. keep the existing `Binaries`, signing-key, auto-update, and tag-detection configuration unless a concrete F-Droid requirement changes;
4. rerun the fdroiddata and reproducible-build checks;
5. notify the reviewer once the updated pipeline is green.

Once CountAway has been accepted into the official repository, normal releases should be left to the configured tag detection and `AutoUpdateMode` unless F-Droid requires manual intervention.

In other words: while inclusion is pending, keep the application current. After inclusion, let the machinery earn its keep.

## Reproducible upstream APKs

F-Droid uses the upstream GitHub release binary together with the CountAway signing certificate to verify reproducible builds. This also preserves Android signing identity between compatible upstream and F-Droid builds.

The expected signing certificate SHA-256 is:

```text
dfbf9e4ba5b71bc4f7e70ee58f514410f90fb1aee9e9ebe522af68ad93cad42a
```

The release workflow verifies this fingerprint explicitly and pins `apksigner` to Android Build Tools 34.0.0. Do not weaken verification, replace the signing identity, or add scanner exceptions merely to make a build pass; investigate reproducibility failures at their source.

## Stable tag gate

The fdroiddata recipe uses stable semantic tags for update detection. A normal branch commit or merge to `main` is not a new F-Droid version.

In the normal web release path, `prepare-draft-release` creates a draft GitHub Release configured with tag name `v<version>` and the exact validated target commit, but GitHub does not create the actual `refs/tags/v<version>` Git ref while the release remains a draft. The draft can therefore exist under an `untagged-...` URL while `v<version>` still does not resolve as a repository ref; that is expected and is not a release failure.

Publishing the validated draft is the stable-tag gate in this path. GitHub materializes `v<version>` on the configured target commit when the draft is published, and only then can tag-based update detection see the new version. A direct push of a stable tag remains an alternate supported path and starts the same public update path immediately.

Once CountAway is included, `AutoUpdateMode: Version` means an ordinary future update can be generated from a detected stable tag without a manual fdroiddata edit.

For that reason:

- use `release-candidate` for signed testing before the release is ready;
- use `prepare-draft-release` from the current `main` head to validate the final source and prepare the private draft;
- do not manually create `v<version>` merely because it is absent while the matching draft is unpublished;
- review the draft APK, checksum, notes, signing identity, upgrade behavior, and configured target commit before publication;
- treat publication of the draft as the deliberate creation of the stable tag and the start of the public F-Droid update path;
- never move an existing stable tag after publication.

There is intentionally no stable release tag for a release candidate or an unpublished prepared draft.

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

If the initial-inclusion merge request is still open, update that merge request only after the public binary check succeeds.

If CountAway has already been included, let the configured `AutoUpdateMode` handle normal future releases unless a maintainer or a concrete failure requires a manual fdroiddata update.

## Release checklist

For a future stable release:

1. Keep `versionName`, `versionCode`, `CHANGELOG.md`, `docs/releases/<version>.md`, and all three Fastlane changelogs synchronized.
2. Get Android CI green on the exact proposed release commit.
3. Build and smoke-test a signed `release-candidate`, including upgrade preservation.
4. Merge release changes only after review and explicit approval.
5. Run `prepare-draft-release` from the current `main` head and verify the expected signing certificate, APK metadata, checksum, and configured release target.
6. Review the unpublished draft. The missing `v<version>` Git ref at this stage is expected and must not be recreated manually.
7. Publish the draft only after the APK, checksum, notes, upgrade behavior, and target commit are approved. Publication creates the stable tag in the normal web path.
8. Verify that `v<version>` resolves to the exact approved release commit and that the public `Binaries` URL resolves.
9. If the initial-inclusion merge request is still open, replace its single build entry with the new release and rerun its checks. If CountAway is already included, let `AutoUpdateMode` handle the normal update unless manual intervention is required.
10. Wait for F-Droid CI and maintainer processing before considering the F-Droid update complete.

Once the inclusion merge request is accepted and CountAway is actually published, the README can be updated separately with the official F-Droid badge/link.
