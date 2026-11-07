#!/usr/bin/env python3
"""Validate the text and contact mechanism served by CountAway's public Site.

The release workflow downloads the landing and privacy HTML with curl --fail.
The privacy contact gate accepts a real HTML form, inline or linked on the same
site, rather than publishing personal identity or mailbox information.
A successful check proves discoverable form markup, not backend delivery; the
Site owner must separately accept a real submission and server-side anti-spam.
"""
from __future__ import annotations

import argparse
from html.parser import HTMLParser
from pathlib import Path
import re
import sys
import unicodedata
from urllib.parse import urljoin, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

SITE_ROOT = "https://countaway.cajapersonal.org"
PRIVACY_URL = SITE_ROOT + "/privacy/"
PAGE_MARKERS = {
    "project": (("project headline", "Count the days that matter."),),
    "privacy": (
        ("application identity", "CountAway"),
        ("English policy heading", "What CountAway does not collect"),
        ("Spanish policy heading", "Qu\u00e9 datos no recopila CountAway"),
        ("Catalan policy heading", "Quines dades no recopila CountAway"),
        ("English language label", "English"),
        ("Spanish language label", "Espa\u00f1ol"),
        ("Catalan language label", "Catal\u00e0"),
    ),
}


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", unicodedata.normalize("NFKC", value)).strip()


class SiteParser(HTMLParser):
    SKIPPED = frozenset({"script", "style", "template", "noscript", "svg"})
    BLOCKS = frozenset({"br", "hr", "p", "div", "section", "article", "main", "header",
                         "footer", "aside", "li", "ul", "ol", "h1", "h2", "h3", "h4",
                         "h5", "h6", "td", "th", "tr", "table", "blockquote", "pre"})

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.fragments: list[str] = []
        self.skip_depth = 0
        self.links: list[tuple[str, str]] = []
        self.active_link: dict[str, object] | None = None
        self.forms: list[set[str]] = []
        self.active_form: set[str] | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in self.SKIPPED:
            self.skip_depth += 1
            return
        if self.skip_depth:
            return
        attributes = dict(attrs)
        if tag == "a":
            self.active_link = {"href": attributes.get("href") or "", "label": []}
        elif tag == "form":
            self.active_form = set()
        elif self.active_form is not None:
            if tag == "input":
                kind = (attributes.get("type") or "text").lower()
                if kind == "email":
                    self.active_form.add("email")
                elif kind == "submit":
                    self.active_form.add("submit")
            elif tag == "textarea":
                self.active_form.add("message")
            elif tag == "button" and (attributes.get("type") or "submit").lower() == "submit":
                self.active_form.add("submit")
        if tag in {"br", "hr"}:
            self.fragments.append(" ")

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        self.handle_starttag(tag, attrs)
        if tag == "input":
            return
        self.handle_endtag(tag)

    def handle_endtag(self, tag: str) -> None:
        if self.skip_depth:
            if tag in self.SKIPPED:
                self.skip_depth -= 1
            return
        if tag == "a" and self.active_link is not None:
            self.links.append((str(self.active_link["href"]), normalize("".join(self.active_link["label"]))))
            self.active_link = None
        elif tag == "form" and self.active_form is not None:
            self.forms.append(self.active_form)
            self.active_form = None
        if tag in self.BLOCKS:
            self.fragments.append(" ")

    def handle_data(self, data: str) -> None:
        if not self.skip_depth:
            self.fragments.append(data)
            if self.active_link is not None:
                self.active_link["label"].append(data)


def parse_html(markup: str) -> SiteParser:
    parser = SiteParser()
    parser.feed(markup)
    parser.close()
    return parser


def missing_markers(markup: str, page: str) -> list[str]:
    if page not in PAGE_MARKERS:
        raise ValueError(f"Unknown page type: {page}")
    text = normalize("".join(parse_html(markup).fragments))
    return [f"{page}: missing {label} ({phrase!r})" for label, phrase in PAGE_MARKERS[page]
            if normalize(phrase) not in text]


def is_contact_form(page: SiteParser) -> bool:
    return any({"email", "message", "submit"}.issubset(fields) for fields in page.forms)


def is_same_site_contact_link(href: str, label: str) -> str | None:
    if not any(token in label.casefold() for token in ("contact", "consult", "support", "soporte", "message", "mensaje", "missatge")):
        return None
    url = urljoin(PRIVACY_URL, href)
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != "countaway.cajapersonal.org":
        return None
    if parsed.path.rstrip("/") in ("", "/privacy"):
        return None
    return url


def public_email_links(markup: str, page: str) -> list[str]:
    links = parse_html(markup).links
    return [f"{page}: public email contact link remains"
            for href, _label in links
            if href.lower().startswith("mailto:")
            or "/cdn-cgi/l/email-protection" in href.lower()]


class SameSiteRedirects(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        url = urlparse(newurl)
        if url.scheme != "https" or url.netloc != "countaway.cajapersonal.org":
            raise ValueError("Contact page redirects outside CountAway")
        return super().redirect_request(request, fp, code, msg, headers, newurl)


def fetch_contact_html(url: str) -> str:
    opener = build_opener(SameSiteRedirects())
    response = opener.open(Request(url, headers={"User-Agent": "CountAway-release-check/1"}), timeout=12)
    with response:
        if not response.headers.get("Content-Type", "").lower().startswith("text/html"):
            raise ValueError("Contact URL did not return HTML")
        content = response.read(2097153)
        if len(content) > 2097152:
            raise ValueError("Contact page exceeds validation size limit")
        return content.decode("utf-8")


def contact_problems(privacy_markup: str, fetch=fetch_contact_html) -> list[str]:
    page = parse_html(privacy_markup)
    if is_contact_form(page):
        return []
    candidates = [target for href, label in page.links
                  if (target := is_same_site_contact_link(href, label))]
    if not candidates:
        return ["privacy: no visible embedded contact form or same-site form link"]
    errors = []
    for url in dict.fromkeys(candidates):
        try:
            html = fetch(url)
            if is_contact_form(parse_html(html)):
                return []
            errors.append(f"contact page {url}: missing email/message/submit form controls")
        except (OSError, ValueError, UnicodeError) as error:
            errors.append(f"contact page {url}: unavailable ({error})")
    return errors


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--project-html", type=Path, required=True)
    ap.add_argument("--privacy-html", type=Path, required=True)
    args = ap.parse_args()
    pages: dict[str, str] = {}
    errors: list[str] = []
    for page, path in (("project", args.project_html), ("privacy", args.privacy_html)):
        try:
            markup = path.read_text(encoding="utf-8")
            if not markup.strip():
                raise ValueError("downloaded HTML is empty")
            pages[page] = markup
            missing = missing_markers(markup, page)
            errors.extend(missing)
            errors.extend(public_email_links(markup, page))
            if not missing:
                print(f"public_site=PASS page={page} checks={len(PAGE_MARKERS[page])}")
        except (OSError, ValueError, UnicodeError) as error:
            errors.append(f"{page}: cannot validate downloaded HTML ({error})")
    if "privacy" in pages:
        issues = contact_problems(pages["privacy"])
        errors.extend(issues)
        if not issues:
            print("public_site=PASS privacy_contact=discoverable_form note=delivery_and_antispam_require_separate_acceptance")
    for error in errors:
        print(f"public_site=BLOCKED {error}", file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
