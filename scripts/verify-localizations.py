#!/usr/bin/env python3
"""Validate CountAway's enabled and candidate Android language resources."""
from pathlib import Path
import argparse
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
CATALOG = RES / "xml/supported_languages.xml"
FORMAT = re.compile(r"%(?:(\d+)\$)?([a-zA-Z%])")
FORMAT_TYPES = set("bBhHsScCdoxXeEfgGaAtT")
LANGUAGE_VALUES_DIR = re.compile(r"^values-(?:b\+[A-Za-z0-9+]+|[a-z]{2,3}(?:-r[A-Z]{2})?)$")


def die(message):
    raise SystemExit(message)


def load_catalog():
    root = ET.parse(CATALOG).getroot()
    default = root.attrib.get("defaultTag", "").strip()
    languages = []
    for node in root.findall("language"):
        item = {key: node.attrib.get(key, "").strip() for key in (
            "tag", "qualifier", "endonym", "fdroidLocale", "playLocale", "icon"
        )}
        missing = [key for key, value in item.items() if not value]
        if missing:
            die(f"{CATALOG}: missing {', '.join(missing)}")
        languages.append(item)
    if not languages:
        die(f"{CATALOG}: no enabled languages")
    for key in ("tag", "qualifier", "fdroidLocale", "playLocale"):
        values = [item[key] for item in languages]
        if len(values) != len(set(values)):
            die(f"{CATALOG}: duplicate {key}")
    if default not in [item["tag"] for item in languages]:
        die(f"{CATALOG}: defaultTag is not enabled")
    return default, languages


def signature(text):
    result = []
    implicit = 0
    for match in FORMAT.finditer(text or ""):
        kind = match.group(2)
        if kind == "%":
            continue
        if kind not in FORMAT_TYPES:
            die(f"unsupported format token %{kind} in {text!r}")
        if match.group(1):
            index = int(match.group(1))
        else:
            implicit += 1
            index = implicit
        result.append((index, kind.lower()))
    return tuple(result)


def resources(directory):
    values = {}
    nontranslatable = set()
    for path in sorted(directory.glob("*.xml")):
        root = ET.parse(path).getroot()
        if root.tag != "resources":
            continue
        for node in root:
            if node.tag not in {"string", "plurals", "string-array"}:
                continue
            name = node.attrib.get("name")
            if not name:
                die(f"{path}: {node.tag} without name")
            key = (node.tag, name)
            if key in values or key in nontranslatable:
                die(f"{path}: duplicate {node.tag}/{name}")
            if node.attrib.get("translatable") == "false":
                nontranslatable.add(key)
                continue
            if node.tag == "string":
                values[key] = ("string", signature("".join(node.itertext())))
            elif node.tag == "plurals":
                aggregate = set()
                branches = {}
                for item in node.findall("item"):
                    quantity = item.attrib.get("quantity", "")
                    item_signature = signature("".join(item.itertext()))
                    branches[quantity] = item_signature
                    aggregate.update(item_signature)
                if "other" not in branches:
                    die(f"{path}: plurals/{name} is missing quantity=other")
                values[key] = ("plurals", frozenset(aggregate))
            else:
                values[key] = ("string-array", tuple(
                    signature("".join(item.itertext())) for item in node.findall("item")
                ))
    return values, nontranslatable


def validate_directory(label, directory, baseline, nontranslatable):
    if not directory.is_dir():
        die(f"{label}: missing {directory}")
    current, localized_nontranslatable = resources(directory)
    accidental = set(current) & nontranslatable
    if accidental:
        die(f"{label}: overrides nontranslatable resources: {sorted(accidental)}")
    missing = set(baseline) - set(current)
    extra = set(current) - set(baseline)
    if missing:
        die(f"{label}: missing resources: {sorted(missing)}")
    if extra:
        die(f"{label}: extra resources: {sorted(extra)}")
    for key, expected in baseline.items():
        actual = current[key]
        if expected != actual:
            die(f"{label}: resource contract mismatch for {key}: {actual} != {expected}")
    unexpected_nontranslatable = localized_nontranslatable - nontranslatable
    if unexpected_nontranslatable:
        die(f"{label}: unexpected translatable=false resources: {sorted(unexpected_nontranslatable)}")


def candidate_directories(enabled_qualifiers):
    return sorted(
        path for path in RES.iterdir()
        if path.is_dir()
        and LANGUAGE_VALUES_DIR.match(path.name)
        and path.name not in enabled_qualifiers
    )


def validate(resource_dir=None):
    default, languages = load_catalog()
    default_entry = next(item for item in languages if item["tag"] == default)
    baseline, nontranslatable = resources(RES / default_entry["qualifier"])

    for language in languages:
        if language["tag"] == default:
            continue
        validate_directory(language["tag"], RES / language["qualifier"], baseline, nontranslatable)

    enabled_qualifiers = {item["qualifier"] for item in languages}
    candidates = candidate_directories(enabled_qualifiers)

    if resource_dir:
        directory = RES / resource_dir
        if not LANGUAGE_VALUES_DIR.match(resource_dir):
            die(f"{resource_dir}: not a supported language values directory")
        validate_directory(resource_dir, directory, baseline, nontranslatable)
        if directory not in candidates and resource_dir not in enabled_qualifiers:
            candidates.append(directory)

    for directory in candidates:
        validate_directory(f"candidate:{directory.name}", directory, baseline, nontranslatable)

    candidate_names = ",".join(path.name for path in candidates) or "none"
    print(
        f"localizations=ok default={default} "
        f"enabled={','.join(x['tag'] for x in languages)} "
        f"candidates={candidate_names} resources={len(baseline)}"
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--print-fdroid-locales", action="store_true")
    parser.add_argument("--print-tags", action="store_true")
    parser.add_argument(
        "--resource-dir",
        help="Validate one language resource directory, e.g. values-b+zh+Hans, without enabling it.",
    )
    args = parser.parse_args()
    _, languages = load_catalog()
    if args.print_fdroid_locales:
        print("\n".join(item["fdroidLocale"] for item in languages))
    elif args.print_tags:
        print("\n".join(item["tag"] for item in languages))
    else:
        validate(args.resource_dir)


if __name__ == "__main__":
    main()
