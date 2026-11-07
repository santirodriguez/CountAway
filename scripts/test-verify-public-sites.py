#!/usr/bin/env python3
"""Offline regression tests for the CountAway public Site HTML release gate."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest


SCRIPT = Path(__file__).with_name("verify-public-sites.py")
SPEC = importlib.util.spec_from_file_location("verify_public_sites", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load public Site validator")
sites = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sites)


PROJECT = "<html><body><h1>Count the <span>days</span> that matter.</h1></body></html>"
PRIVACY = """<html><body>
  <main>
    <p>CountAway is an app <strong>developed by</strong> Santiago Rodriguez</p>
    <h2>What CountAway does not collect</h2>
    <h2>Qu&eacute; datos no recopila CountAway</h2>
    <h2>Quines dades no&nbsp;recopila CountAway</h2>
    <p>support@cajapersonal.org</p>
    <nav>English | Espa&ntilde;ol | Catal&agrave;</nav>
  </main>
</body></html>"""


class PublicSiteHTMLTests(unittest.TestCase):
    def test_accepts_tag_boundaries_entities_and_nbsp(self) -> None:
        self.assertEqual(sites.missing_markers(PROJECT, "project"), [])
        self.assertEqual(sites.missing_markers(PRIVACY, "privacy"), [])

    def test_rejects_missing_catalan_policy_with_specific_diagnostic(self) -> None:
        html = PRIVACY.replace("Quines dades no&nbsp;recopila CountAway", "Unrelated text")
        self.assertEqual(
            sites.missing_markers(html, "privacy"),
            ["privacy: missing Catalan policy heading ('Quines dades no recopila CountAway')"],
        )

    def test_script_data_does_not_satisfy_policy_checks(self) -> None:
        html = PRIVACY.replace(
            "<h2>Quines dades no&nbsp;recopila CountAway</h2>",
            "<script>const hidden = 'Quines dades no recopila CountAway';</script>",
        )
        self.assertIn("Catalan policy heading", sites.missing_markers(html, "privacy")[0])

    def test_rejects_missing_project_headline(self) -> None:
        self.assertEqual(
            sites.missing_markers("<html><h1>Other app</h1></html>", "project"),
            ["project: missing project headline ('Count the days that matter.')"],
        )

    def test_rejects_script_only_project_headline(self) -> None:
        html = "<html><script>var x = 'Count the days that matter.'</script></html>"
        self.assertEqual(len(sites.missing_markers(html, "project")), 1)

    def test_detects_removed_contact_and_language(self) -> None:
        html = PRIVACY.replace("support@cajapersonal.org", "missing@example.test").replace(
            "Espa&ntilde;ol", "OTHER"
        )
        errors = sites.missing_markers(html, "privacy")
        self.assertTrue(any("privacy contact" in error for error in errors))
        self.assertTrue(any("Spanish language label" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
