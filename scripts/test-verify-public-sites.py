#!/usr/bin/env python3
"""Regression tests for CountAway's live pages and canonical privacy contact."""
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

PROJECT = "<h1>Count the <span>days</span> that matter.</h1>"
PRIVACY = """<h1>CountAway</h1>
<h2>What CountAway does not collect</h2>
<h2>Qu&eacute; datos no recopila CountAway</h2>
<h2>Quines dades no&nbsp;recopila CountAway</h2>
<nav>English | Espa&ntilde;ol | Catal&agrave;</nav>"""
CANONICAL = """# CountAway Privacy Policy

## English
Support: support@cajapersonal.org
## Español
Contacto: support@cajapersonal.org
## Català
Contacte: support@cajapersonal.org
"""


class PublicSiteTests(unittest.TestCase):
    def test_public_policy_without_raw_html_email_is_valid(self):
        self.assertEqual(sites.missing_site_markers(PRIVACY, "privacy"), [])
        self.assertEqual(sites.missing_site_markers(PROJECT, "project"), [])

    def test_entity_and_tag_boundaries_normalize(self):
        self.assertEqual(sites.missing_site_markers(PRIVACY, "privacy"), [])

    def test_missing_catalan_heading_blocks(self):
        html = PRIVACY.replace("Quines dades no&nbsp;recopila CountAway", "missing")
        self.assertTrue(any("Catalan policy heading" in e for e in sites.missing_site_markers(html, "privacy")))

    def test_script_only_heading_does_not_count(self):
        html = PRIVACY.replace("<h2>What CountAway does not collect</h2>", "<script>What CountAway does not collect</script>")
        self.assertTrue(any("English policy heading" in e for e in sites.missing_site_markers(html, "privacy")))

    def test_project_headline_still_required(self):
        self.assertEqual(len(sites.missing_site_markers("<h1>Other app</h1>", "project")), 1)

    def test_canonical_policy_contact_in_all_languages(self):
        self.assertEqual(sites.policy_contact_problems(CANONICAL), [])

    def test_missing_contact_in_spanish_blocks(self):
        html = CANONICAL.replace("Contacto: support@cajapersonal.org", "Contacto: none")
        self.assertIn("privacy source: missing support contact in ## Español", sites.policy_contact_problems(html))

    def test_missing_all_contacts_blocks(self):
        self.assertEqual(len(sites.policy_contact_problems(CANONICAL.replace("support@cajapersonal.org", "missing@example.com"))), 3)

    def test_missing_policy_title_blocks(self):
        self.assertTrue(any("title" in e for e in sites.policy_contact_problems(CANONICAL.replace("# CountAway Privacy Policy", "# Other"))))

    def test_real_repository_privacy_policy(self):
        canonical = Path(__file__).resolve().parents[1] / "PRIVACY.md"
        if canonical.is_file():
            self.assertEqual(sites.policy_contact_problems(canonical.read_text(encoding="utf-8")), [])


if __name__ == "__main__":
    unittest.main()
