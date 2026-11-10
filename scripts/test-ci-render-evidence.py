#!/usr/bin/env python3
"""Small, emulator-free checks for the Android render evidence verifier."""

import io
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("verify-ci-render-evidence.py")
PNG = b"\x89PNG\r\n\x1a\n" + b"original pixel bytes"


class RenderArchiveTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.archive = Path(self.tmp.name) / "evidence.tar"
        self.manifest = Path(self.tmp.name) / "manifest.txt"

    def make_archive(self, entries):
        with tarfile.open(self.archive, mode="w") as output:
            for name, payload in entries.items():
                item = tarfile.TarInfo(name)
                item.size = len(payload)
                output.addfile(item, io.BytesIO(payload))

    def invoke(self):
        return subprocess.run([sys.executable, str(SCRIPT), str(self.archive),
                               str(self.manifest)], text=True, capture_output=True)

    def test_accepts_real_layout_and_share_sources(self):
        self.make_archive({"layout-evidence/editor.png": PNG,
                           "layout-evidence/notes.txt": b"metadata",
                           "share-evidence/card.png": PNG})
        result = self.invoke()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("render_archive=verified png=1", result.stdout)
        self.assertIn("layout-evidence/editor.png", self.manifest.read_text())

    def test_rejects_missing_archive(self):
        result = self.invoke()
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.manifest.exists())

    def test_rejects_non_tar_bytes(self):
        self.archive.write_bytes(b"not a tar archive" * 512)
        self.assertNotEqual(0, self.invoke().returncode)

    def test_rejects_truncation(self):
        self.make_archive({"layout-evidence/editor.png": PNG})
        self.archive.write_bytes(self.archive.read_bytes()[:-700])
        self.assertNotEqual(0, self.invoke().returncode)

    def test_rejects_missing_layout_render(self):
        self.make_archive({"share-evidence/card.png": PNG})
        self.assertNotEqual(0, self.invoke().returncode)

    def test_rejects_invalid_png_header(self):
        self.make_archive({"layout-evidence/editor.png": b"not really a png"})
        self.assertNotEqual(0, self.invoke().returncode)

    def test_rejects_unsafe_paths(self):
        self.make_archive({"../escape.png": PNG,
                           "layout-evidence/editor.png": PNG})
        self.assertNotEqual(0, self.invoke().returncode)


if __name__ == "__main__":
    unittest.main()
