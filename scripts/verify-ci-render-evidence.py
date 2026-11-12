#!/usr/bin/env python3
"""Verify an original Android render TAR before accepting it as CI evidence."""

import argparse
import hashlib
from pathlib import Path, PurePosixPath
import sys
import tarfile

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
ALLOWED_ROOTS = {"layout-evidence", "share-evidence"}


def verify(archive: Path) -> tuple[list[str], int, int, str]:
    if not archive.is_file() or archive.stat().st_size < 2048:
        raise ValueError("render archive missing or too small")
    size = archive.stat().st_size
    if size % 512 != 0:
        raise ValueError("render archive has an incomplete TAR block")

    checksum = hashlib.sha256()
    with archive.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            checksum.update(chunk)
        stream.seek(-1024, 2)
        if stream.read(1024) != b"\x00" * 1024:
            raise ValueError("render archive is missing the TAR end marker")

    names: list[str] = []
    png_count = 0
    file_count = 0
    with tarfile.open(archive, mode="r:") as members:
        for member in members:
            name = PurePosixPath(member.name)
            if (name.is_absolute() or ".." in name.parts or not name.parts
                    or name.parts[0] not in ALLOWED_ROOTS):
                raise ValueError(f"unsafe render archive path: {member.name}")
            if not (member.isfile() or member.isdir()):
                raise ValueError(f"unexpected render archive entry: {member.name}")
            names.append(member.name + ("/" if member.isdir() else ""))
            if not member.isfile():
                continue
            file_count += 1
            stream = members.extractfile(member)
            if stream is None:
                raise ValueError(f"unreadable render archive entry: {member.name}")
            with stream:
                header = stream.read(8)
                if (member.name.startswith("layout-evidence/")
                        and member.name.endswith(".png")):
                    if header != PNG_SIGNATURE:
                        raise ValueError(f"invalid render PNG: {member.name}")
                    png_count += 1
                while stream.read(1024 * 1024):
                    pass  # Read the entire entry; detect truncated TAR payloads.
    if png_count == 0:
        raise ValueError("render archive does not contain a layout-evidence PNG")
    return names, png_count, file_count, checksum.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    try:
        names, png_count, file_count, digest = verify(args.archive)
    except (OSError, ValueError, tarfile.TarError) as exc:
        print(f"Render archive verification failed: {exc}", file=sys.stderr)
        return 1
    args.manifest.write_text("\n".join(names) + "\n", encoding="utf-8")
    print(f"render_archive=verified png={png_count} files={file_count} sha256={digest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
