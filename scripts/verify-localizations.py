#!/usr/bin/env python3
"""Validate CountAway's enabled language catalog and Android text resources."""
from pathlib import Path
import argparse
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
CATALOG = RES / "xml/supported_languages.xml"
FORMAT = re.compile(r"%(?:(\d+)\$)?([a-zA-Z%])")
FORMAT_TYPES = set("bBhHsScCdoxXeEfgGaAtT")


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


def validate():
    default, languages = load_catalog()
    default_entry = next(item for item in languages if item["tag"] == default)
    baseline, nontranslatable = resources(RES / default_entry["qualifier"])
    for language in languages:
        directory = RES / language["qualifier"]
        if not directory.is_dir():
            die(f"{language['tag']}: missing {directory}")
        current, localized_nontranslatable = resources(directory)
        if language["tag"] == default:
            continue
        accidental = set(current) & nontranslatable
        if accidental:
            die(f"{language['tag']}: overrides nontranslatable resources: {sorted(accidental)}")
        missing = set(baseline) - set(current)
        extra = set(current) - set(baseline)
        if missing:
            die(f"{language['tag']}: missing resources: {sorted(missing)}")
        if extra:
            die(f"{language['tag']}: extra resources: {sorted(extra)}")
        for key, expected in baseline.items():
            actual = current[key]
            if expected != actual:
                die(f"{language['tag']}: resource contract mismatch for {key}: {actual} != {expected}")
        unexpected_nontranslatable = localized_nontranslatable - nontranslatable
        if unexpected_nontranslatable:
            die(f"{language['tag']}: unexpected translatable=false resources: {sorted(unexpected_nontranslatable)}")
    print(f"localizations=ok default={default} enabled={','.join(x['tag'] for x in languages)} resources={len(baseline)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--print-fdroid-locales", action="store_true")
    parser.add_argument("--print-tags", action="store_true")
    args = parser.parse_args()
    _, languages = load_catalog()
    if args.print_fdroid_locales:
        print("\n".join(item["fdroidLocale"] for item in languages))
    elif args.print_tags:
        print("\n".join(item["tag"] for item in languages))
    else:
        validate()


if __name__ == "__main__":
    main()
