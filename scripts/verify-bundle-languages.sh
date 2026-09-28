#!/usr/bin/env bash
# Device-specific delivery evidence; uses the existing CI debug key only for test installation.
set -euo pipefail
bundle=$1
bundletool=$2
output=$3
mkdir -p "$output"
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
java -jar "$bundletool" build-apks --bundle="$bundle" --output="$output/device-en.apks" \
  --device-spec="$output/device-en.json" --overwrite \
  --ks="$HOME/.android/debug.keystore" --ks-key-alias=androiddebugkey \
  --ks-pass=pass:android --key-pass=pass:android
unzip -q -o "$output/device-en.apks" -d "$output/device-en"
aapt=$(find "${ANDROID_HOME}/build-tools" -mindepth 2 -maxdepth 2 -name aapt | sort -V | tail -1)
master="$output/device-en/splits/base-master.apk"
test -s "$master"
"$aapt" dump configurations "$master" > "$output/device-en-configurations.txt"
python3 - "$output" <<'PYCODE'
import pathlib,re,sys
p=pathlib.Path(sys.argv[1])
configs=(p/'device-en-configurations.txt').read_text().splitlines()
for language in ('es','ca'):
    assert any(re.match(r'^'+language+r'(-|$)',line.strip()) for line in configs), (language,configs)
assert not any(re.search(r'base-(en|es|ca)(-|\.)',f.name) for f in (p/'device-en').rglob('*.apk'))
(p/'language-delivery.txt').write_text('language_splits=false\ndevice_spec_languages=en\npackaged_languages=en,es,ca\nsigning=test_debug_only\n')
PYCODE
