#!/usr/bin/env python3
"""Tests for the release screenshot freshness preflight (no Android tools required)."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("verify-release-screenshots.py")
SPEC = importlib.util.spec_from_file_location("verify_release_screenshots", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Cannot load screenshot verification script")
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class ReleaseScreenshotTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.old = {}
        for index in range(1, 7):
            name = f"{index}.png"
            file = self.root / name
            file.write_bytes(f"published-1.2.3-image-{index}".encode())
            self.old[name] = checker.git_blob_id(file)

    def verify(self, version):
        checker.verify_fresh_screenshots(version, self.root, self.old)

    def test_git_blob_id_matches_git_object_format(self):
        path = self.root / "hello.txt"
        path.write_bytes(b"hello")
        self.assertEqual("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0", checker.git_blob_id(path))
        self.assertEqual(40, len(checker.git_blob_id(path)))

    def test_rechecking_published_123_is_allowed(self):
        self.verify("1.2.3")

    def test_six_unmodified_old_screenshots_block_next_candidate(self):
        with self.assertRaisesRegex(ValueError, r"1\.png.*6\.png"):
            self.verify("1.2.4")

    def test_one_old_screenshot_blocks_even_when_five_are_new(self):
        for index in range(1, 6):
            (self.root / f"{index}.png").write_bytes(f"new-1.2.4-capture-{index}".encode())
        with self.assertRaisesRegex(ValueError, r"6\.png"):
            self.verify("1.2.4")

    def test_six_replaced_screenshots_advance_to_visual_review(self):
        for index in range(1, 7):
            (self.root / f"{index}.png").write_bytes(f"new-1.2.4-capture-{index}".encode())
        self.verify("1.2.4")

    def test_missing_file_fails_even_for_published_baseline(self):
        (self.root / "3.png").unlink()
        with self.assertRaisesRegex(ValueError, r"3\.png"):
            self.verify("1.2.3")


if __name__ == "__main__":
    unittest.main()
