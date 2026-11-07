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
        self.baselines = {}
        for version in ("1.2.3", "1.2.4"):
            digests = {}
            for index in range(1, 7):
                path = self.root / f"{index}.png"
                path.write_bytes(f"public-{version}-screenshot-{index}".encode())
                digests[path.name] = checker.git_blob_id(path)
            self.baselines[version] = digests
        self.copy_published("1.2.4")

    def copy_published(self, version):
        for index in range(1, 7):
            (self.root / f"{index}.png").write_bytes(
                f"public-{version}-screenshot-{index}".encode())

    def verify(self, version):
        checker.verify_fresh_screenshots(
            version, self.root, self.baselines["1.2.4"], self.baselines["1.2.3"])

    def test_git_blob_id_matches_git_object_format(self):
        file = self.root / "hello.txt"
        file.write_bytes(b"hello")
        self.assertEqual("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0",
                         checker.git_blob_id(file))

    def test_reference_is_exactly_six_distinct_public_v124_blobs(self):
        self.assertEqual(set(self.baselines["1.2.4"]), set(checker.PUBLISHED_SCREENSHOT_BLOBS))
        self.assertEqual(6, len(set(checker.PUBLISHED_SCREENSHOT_BLOBS.values())))
        self.assertTrue(all(len(v) == 40 for v in checker.PUBLISHED_SCREENSHOT_BLOBS.values()))

    def test_historical_v124_revalidation_passes_its_own_images(self):
        self.verify("1.2.4")

    def test_historical_v123_revalidation_still_passes(self):
        self.copy_published("1.2.3")
        self.verify("1.2.3")

    def test_v123_reuse_is_rejected_in_v124(self):
        self.copy_published("1.2.3")
        with self.assertRaisesRegex(ValueError, "public 1.2.3"):
            self.verify("1.2.4")

    def test_unchanged_v124_images_block_v125(self):
        with self.assertRaisesRegex(ValueError, r"1\.png.*6\.png"):
            self.verify("1.2.5")

    def test_rotated_v124_images_still_block_v125(self):
        originals = [(self.root / f"{n}.png").read_bytes() for n in range(1, 7)]
        for n in range(1, 7):
            (self.root / f"{n}.png").write_bytes(originals[n % 6])
        with self.assertRaisesRegex(ValueError, r"1\.png.*6\.png"):
            self.verify("1.2.5")

    def test_one_v124_image_mixed_with_new_images_is_rejected(self):
        for n in range(1, 7):
            if n != 4:
                (self.root / f"{n}.png").write_bytes(f"candidate-image-{n}".encode())
        with self.assertRaisesRegex(ValueError, r"4\.png"):
            self.verify("1.2.5")

    def test_v123_image_renamed_to_v125_slot_is_rejected(self):
        self.copy_published("1.2.3")
        reused = (self.root / "6.png").read_bytes()
        for n in range(1, 7):
            (self.root / f"{n}.png").write_bytes(f"new-image-{n}".encode())
        (self.root / "2.png").write_bytes(reused)
        with self.assertRaisesRegex(ValueError, r"2\.png"):
            self.verify("1.2.5")

    def test_missing_file_fails_even_for_historical_version(self):
        (self.root / "3.png").unlink()
        for version in ("1.2.3", "1.2.4", "1.2.5"):
            with self.subTest(version=version), self.assertRaisesRegex(ValueError, r"3\.png"):
                self.verify(version)

    def test_six_new_images_advance_only_to_visual_review(self):
        for n in range(1, 7):
            (self.root / f"{n}.png").write_bytes(f"new-image-{n}".encode())
        self.verify("1.2.5")


if __name__ == "__main__":
    unittest.main()
