#!/usr/bin/env python3
"""Validate already-created Google Play PNG exports without modifying source captures."""
from pathlib import Path
import struct
import sys

PNG = b"\x89PNG\r\n\x1a\n"

def png_info(path):
    data = path.read_bytes()
    if not data.startswith(PNG) or len(data) < 33:
        raise ValueError("not a PNG")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    return width, height, bit_depth, color_type, color_type in (4, 6)

def validate(path, kind):
    width, height, bit_depth, color_type, has_alpha = png_info(path)
    if kind == "icon":
        assert (width, height) == (512, 512), (width, height)
        assert bit_depth == 8 and color_type in (2, 6), (bit_depth, color_type)
        assert path.stat().st_size <= 1024 * 1024, path.stat().st_size
    elif kind == "feature":
        assert (width, height) == (1024, 500), (width, height)
        assert bit_depth == 8 and color_type == 2 and not has_alpha, (bit_depth, color_type, has_alpha)
    elif kind == "screenshot":
        assert 320 <= width <= 3840 and 320 <= height <= 3840, (width, height)
        assert max(width, height) <= 2 * min(width, height), (width, height)
        assert bit_depth == 8 and color_type == 2 and not has_alpha, (bit_depth, color_type, has_alpha)

def main():
    if len(sys.argv) < 3 or sys.argv[1] not in {"icon", "feature", "screenshot"}:
        raise SystemExit("usage: verify-play-assets.py <icon|feature|screenshot> <png> [<png> ...]")
    for value in sys.argv[2:]:
        path = Path(value)
        validate(path, sys.argv[1])
        print(f"{sys.argv[1]}=ok path={path}")

if __name__ == "__main__":
    main()
