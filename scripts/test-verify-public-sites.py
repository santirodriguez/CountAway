#!/usr/bin/env python3
"""Regression checks for public policy and a form-only contact mechanism."""
from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).with_name("verify-public-sites.py")
SPEC = importlib.util.spec_from_file_location("verify_public_sites", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Cannot load validator")
sites = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sites)

PROJECT = "<h1>Count the <span>days</span> that matter.</h1>"
POLICY = """<h1>CountAway Privacy</h1>
<h2>What CountAway does not collect</h2>
<h2>Qu&eacute; datos no recopila CountAway</h2>
<h2>Quines dades no&nbsp;recopila CountAway</h2>
<nav>English | Espa&ntilde;ol | Catal&agrave;</nav>"""
FORM = """<form method='post' action='/api/send'>
<input type='email' name='reply' required>
<textarea name='message' required></textarea>
<button type='submit'>Send</button>
</form>"""


class SiteReleaseTests(unittest.TestCase):
    def test_policy_without_personal_identity_or_email(self):
        self.assertEqual(sites.missing_markers(POLICY, "privacy"), [])
        self.assertEqual(sites.missing_markers(PROJECT, "project"), [])

    def test_embedded_accessible_form(self):
        self.assertEqual(sites.contact_problems(POLICY + FORM), [])

    def test_linked_contact_form_on_same_site(self):
        page = POLICY + "<a href='/contact/'>Contact us</a>"
        seen = []
        def load(url):
            seen.append(url)
            return FORM
        self.assertEqual(sites.contact_problems(page, fetch=load), [])
        self.assertEqual(seen, ["https://countaway.cajapersonal.org/contact/"])

    def test_mailto_or_external_link_does_not_count(self):
        page = POLICY + "<a href='mailto:example@example.com'>Contact</a><a href='https://other.test/form'>Contact</a>"
        self.assertIn("no visible", sites.contact_problems(page)[0])

    def test_broken_or_static_contact_page_blocks(self):
        page = POLICY + "<a href='/contact/'>Contact</a>"
        self.assertIn("missing email", sites.contact_problems(page, fetch=lambda _: "<h1>Contact</h1>")[0])

    def test_missing_email_input_blocks(self):
        self.assertIn("no visible", sites.contact_problems(POLICY + FORM.replace("type='email'", "type='text'"))[0])

    def test_missing_submit_button_blocks(self):
        self.assertIn("no visible", sites.contact_problems(POLICY + FORM.replace("<button type='submit'>Send</button>", ""))[0])

    def test_script_only_form_does_not_count(self):
        self.assertIn("no visible", sites.contact_problems(POLICY + "<script>" + FORM + "</script>")[0])

    def test_rejects_homepage_mailto_or_cloudflare_email_link(self):
        self.assertEqual(len(sites.public_email_links("<a href='mailto:foo@example.test'>Support</a>", "project")), 1)
        self.assertEqual(len(sites.public_email_links("<a href='/cdn-cgi/l/email-protection#hash'>Support</a>", "project")), 1)

    def test_missing_language_still_blocks(self):
        content = POLICY.replace("Quines dades no&nbsp;recopila CountAway", "Nothing")
        self.assertIn("Catalan policy heading", sites.missing_markers(content, "privacy")[0])

    def test_redirect_to_foreign_host_is_blocked(self):
        from urllib.request import Request
        with self.assertRaises(ValueError):
            sites.SameSiteRedirects().redirect_request(
                Request("https://countaway.cajapersonal.org/contact/"), None, 302, "Redirect", {}, "https://foreign.test/contact"
            )


if __name__ == "__main__":
    unittest.main()
