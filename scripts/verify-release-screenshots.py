#!/usr/bin/env python3
"""Reject screenshots reused byte-for-byte from CountAway's prior public releases.

The format and dimensions are checked separately by prepare-play-assets.py.
This check only proves that older files were replaced, not that a capture is
accurate; real-device capture and maintainer visual approval remain mandatory.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

SCREENSHOT_DIR = Path("fastlane/metadata/android/en-US/images/phoneScreenshots")
PUBLISHED_BASELINE_VERSION = "1.2.4"
# Pinned to tag v1.2.4 (fb0cd3f); no current-release image content is changed.
PUBLISHED_SCREENSHOT_BLOBS = {
    "1.png": "6fc1389787d2fa2e772632533d29a3788d24fffc",
    "2.png": "b11e5a48c7b16eb83b036a5713de4777b3b0d520",
    "3.png": "16dd7b0fbdcf72fd06d81f703fe5ef042e6744d9",
    "4.png": "989681fc28a52f1ebaa717d098ad1e08307daaab",
    "5.png": "c120c65b7ed9b9f49b39f54a441afe386008549c",
    "6.png": "359d7e8ed4412e07ae9b9c047f2f6af9aa5f0212",
}
# Preserve the already-tested pre-v1.2.4 freshness boundary as well.
HISTORICAL_V123_BLOBS = {
    "1.png": "621de701835518c2c7d3804db4dc8dfa2a536bca",
    "2.png": "0a0788b6d58032e5b3709efdc800a22e68b8fb9d",
    "3.png": "aab7edb088dd52ac74eb5965e379a66c58016e90",
    "4.png": "4deacbc50083aafcbd1e311794aa11138bdfb368",
    "5.png": "4f1ffc593a4c61b5219e63b7844b976f40eedc24",
    "6.png": "fefb08da36f90d6330a1c291ccde43657436edea",
}


def git_blob_id(path: Path) -> str:
    """Calculate a Git SHA-1 blob ID without a Git checkout or subprocess."""
    data = path.read_bytes()
    return hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()


def verify_fresh_screenshots(
    version: str,
    directory: Path = SCREENSHOT_DIR,
    baseline: dict[str, str] = PUBLISHED_SCREENSHOT_BLOBS,
    previous_baseline: dict[str, str] = HISTORICAL_V123_BLOBS,
) -> None:
    names = tuple(f"{index}.png" for index in range(1, 7))
    missing = [name for name in names if not (directory / name).is_file()]
    if missing:
        raise ValueError("Missing release screenshot(s): " + ", ".join(missing))

    # Validate past releases only against predecessors, never their own pixels.
    if version == "1.2.3":
        return
    checks = [("1.2.3", previous_baseline)]
    if version != PUBLISHED_BASELINE_VERSION:
        checks.append((PUBLISHED_BASELINE_VERSION, baseline))

    actual = {name: git_blob_id(directory / name) for name in names}
    for old_version, old_blobs in checks:
        if set(old_blobs) != set(names) or len(set(old_blobs.values())) != 6:
            raise ValueError(f"Unusable immutable screenshot baseline: {old_version}")
        stale = [name for name, digest in actual.items()
                 if digest in set(old_blobs.values())]
        if stale:
            raise ValueError(
                f"CountAway {version} still reuses public {old_version} screenshot(s): "
                + ", ".join(stale)
                + ". Capture all six screens from the current release candidate, "
                  "including its real About/version screen, and obtain visual approval."
            )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="Current release versionName")
    parser.add_argument("--directory", type=Path, default=SCREENSHOT_DIR)
    args = parser.parse_args()
    try:
        verify_fresh_screenshots(args.version, args.directory)
    except (ValueError, OSError) as error:
        parser.exit(1, f"release_screenshots=BLOCKED: {error}\n")
    print(f"release_screenshots=not_reused version={args.version} count=6 "
          "note=visual_approval_still_required")


if __name__ == "__main__":
    main()
