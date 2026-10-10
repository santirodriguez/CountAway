#!/usr/bin/env bash
# Device-specific delivery evidence; uses the ephemeral CI test key only for test installation.
set -euo pipefail
bundle=$1
bundletool=$2
output=$3
mkdir -p "$output"
python3 scripts/verify-localizations.py
java -jar "$bundletool" dump config --bundle="$bundle" > "$output/bundle-config.json"
python3 - "$output/bundle-config.json" <<'PYCODE'
import json, sys
config = json.load(open(sys.argv[1]))
dimensions = config['optimizations']['splitsConfig']['splitDimension']
assert any(d.get('value') == 'LANGUAGE' and d.get('negate') is True for d in dimensions), dimensions
PYCODE
cat > "$output/device-en.json" <<'JSON'
{"supportedAbis":["x86_64"],"supportedLocales":["en"],"screenDensity":420,"sdkVersion":33}
JSON
test_keystore="${RUNNER_TEMP:-/tmp}/countaway-bundle-test.jks"
if [[ ! -f "$test_keystore" ]]; then
  keytool -genkeypair -keystore "$test_keystore" -storepass android -keypass android \
    -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' \
    -keyalg RSA -keysize 2048 -validity 2 -noprompt
fi
java -jar "$bundletool" build-apks --bundle="$bundle" --output="$output/device-en.apks" \
  --device-spec="$output/device-en.json" --overwrite \
  --ks="$test_keystore" --ks-key-alias=androiddebugkey \
  --ks-pass=pass:android --key-pass=pass:android
unzip -q -o "$output/device-en.apks" -d "$output/device-en"
aapt=$(find "${ANDROID_HOME}/build-tools" -mindepth 2 -maxdepth 2 -name aapt | sort -V | tail -1)
master="$output/device-en/splits/base-master.apk"
test -s "$master"
"$aapt" dump configurations "$master" > "$output/device-en-configurations.txt"
python3 - "$output" <<'PYCODE'
import pathlib, re, sys, xml.etree.ElementTree as ET
p = pathlib.Path(sys.argv[1])
catalog = ET.parse("app/src/main/res/xml/supported_languages.xml").getroot()
default = catalog.attrib["defaultTag"]
tags = [node.attrib["tag"] for node in catalog.findall("language")]
configs = (p / "device-en-configurations.txt").read_text().splitlines()
for tag in tags:
    if tag == default:
        continue
    language = tag.split("-", 1)[0]
    assert any(re.match(r"^" + re.escape(language) + r"(-|$)", line.strip()) for line in configs), (tag, configs)
language_re = "|".join(re.escape(tag.split("-", 1)[0]) for tag in tags)
assert not any(re.search(r"base-(" + language_re + r")(-|\.)", f.name) for f in (p / "device-en").rglob("*.apk"))
(p / "language-delivery.txt").write_text(
    "language_splits=false\n"
    "device_spec_languages=en\n"
    f"packaged_languages={','.join(tags)}\n"
    "signing=test_debug_only\n"
)
PYCODE
