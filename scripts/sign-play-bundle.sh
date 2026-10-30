#!/usr/bin/env bash
# Sign one already-reviewed Android App Bundle with a dedicated Play upload key.
# Private key material and passwords remain outside the repository.
set -euo pipefail
export LC_ALL=C

usage() {
  cat <<'USAGE'
Usage:
  bash scripts/sign-play-bundle.sh INPUT_AAB OUTPUT_AAB KEYSTORE ALIAS CERTIFICATE_PEM \
    [BUNDLETOOL_JAR EXPECTED_PACKAGE EXPECTED_VERSION_NAME EXPECTED_VERSION_CODE [RECEIPT]]

Passwords are never accepted as command-line arguments.
If PLAY_UPLOAD_STORE_PASSWORD and PLAY_UPLOAD_KEY_PASSWORD are unset, the
script prompts on /dev/tty. Press Enter at the key-password prompt to reuse
the keystore password.
USAGE
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

if (( $# < 5 || $# > 10 )); then
  usage
  exit 2
fi

INPUT=$1
OUTPUT=$2
KEYSTORE=$3
ALIAS=$4
CERTIFICATE=$5
BUNDLETOOL=""
EXPECTED_PACKAGE=""
EXPECTED_VERSION_NAME=""
EXPECTED_VERSION_CODE=""
RECEIPT=""

if (( $# >= 6 )); then BUNDLETOOL=$6; fi
if (( $# >= 7 )); then EXPECTED_PACKAGE=$7; fi
if (( $# >= 8 )); then EXPECTED_VERSION_NAME=$8; fi
if (( $# >= 9 )); then EXPECTED_VERSION_CODE=$9; fi
if (( $# >= 10 )); then RECEIPT=${10}; fi
if [[ -z "$RECEIPT" ]]; then RECEIPT="$OUTPUT.receipt.txt"; fi

[[ -f "$INPUT" && -s "$INPUT" ]] || fail "Input AAB is missing or empty: $INPUT"
[[ -f "$KEYSTORE" && -s "$KEYSTORE" ]] || fail "Keystore is missing or empty: $KEYSTORE"
[[ -f "$CERTIFICATE" && -s "$CERTIFICATE" ]] || fail "Certificate is missing or empty: $CERTIFICATE"
if [[ -n "$BUNDLETOOL" ]]; then
  [[ -f "$BUNDLETOOL" && -s "$BUNDLETOOL" ]] || fail "bundletool JAR is missing or empty: $BUNDLETOOL"
fi

for cmd in jarsigner keytool openssl sha256sum mktemp realpath ln; do
  command -v "$cmd" >/dev/null 2>&1 || fail "Required command not found: $cmd"
done
if [[ -n "$BUNDLETOOL" ]]; then
  command -v java >/dev/null 2>&1 || fail "Required command not found: java"
fi

# Resolve every destination before a helper can write. Existing files, even a
# dangling symlink or hard link, must never be reused as signing outputs.
OUTPUT_PATH=$(realpath -m -- "$OUTPUT") || fail "Could not resolve output path"
RECEIPT_PATH=$(realpath -m -- "$RECEIPT") || fail "Could not resolve receipt path"
[[ "$OUTPUT_PATH" != "$RECEIPT_PATH" ]] || fail "Output and receipt paths must differ"

SOURCE_PATHS=()
for source in "$INPUT" "$KEYSTORE" "$CERTIFICATE"; do
  SOURCE_PATHS+=("$(realpath -e -- "$source")") || fail "Could not resolve input path: $source"
done
if [[ -n "$BUNDLETOOL" ]]; then
  SOURCE_PATHS+=("$(realpath -e -- "$BUNDLETOOL")") || fail "Could not resolve bundletool path"
fi
for destination in "$OUTPUT" "$RECEIPT"; do
  [[ ! -e "$destination" && ! -L "$destination" ]] || fail "Refusing to overwrite existing destination: $destination"
done
for destination in "$OUTPUT_PATH" "$RECEIPT_PATH"; do
  for source in "${SOURCE_PATHS[@]}"; do
    [[ "$destination" != "$source" ]] || fail "Destination aliases a signing input: $destination"
  done
done

OUTPUT_PARENT=${OUTPUT_PATH%/*}
RECEIPT_PARENT=${RECEIPT_PATH%/*}
[[ -d "$OUTPUT_PARENT" && -w "$OUTPUT_PARENT" ]] || fail "Output directory is unavailable: $OUTPUT_PARENT"
[[ -d "$RECEIPT_PARENT" && -w "$RECEIPT_PARENT" ]] || fail "Receipt directory is unavailable: $RECEIPT_PARENT"

if printenv PLAY_UPLOAD_STORE_PASSWORD >/dev/null 2>&1; then
  PLAY_UPLOAD_STORE_PASSWORD=$(printenv PLAY_UPLOAD_STORE_PASSWORD)
else
  [[ -r /dev/tty ]] || fail "Keystore password is unset and /dev/tty is unavailable"
  read -r -s -p "Keystore password: " PLAY_UPLOAD_STORE_PASSWORD < /dev/tty
  echo > /dev/tty
fi

if printenv PLAY_UPLOAD_KEY_PASSWORD >/dev/null 2>&1; then
  PLAY_UPLOAD_KEY_PASSWORD=$(printenv PLAY_UPLOAD_KEY_PASSWORD)
else
  [[ -r /dev/tty ]] || fail "Key password is unset and /dev/tty is unavailable"
  read -r -s -p "Key password (Enter = same as keystore): " PLAY_UPLOAD_KEY_PASSWORD < /dev/tty
  echo > /dev/tty
  if [[ -z "$PLAY_UPLOAD_KEY_PASSWORD" ]]; then
    PLAY_UPLOAD_KEY_PASSWORD=$PLAY_UPLOAD_STORE_PASSWORD
  fi
fi

export PLAY_UPLOAD_STORE_PASSWORD PLAY_UPLOAD_KEY_PASSWORD
TEMP_DIR=""
SIGNED_STAGE_DIR=""
SIGNED_STAGE=""
RECEIPT_STAGE=""
OUTPUT_PUBLISHED=false
RECEIPT_PUBLISHED=false
COMPLETE=false
cleanup() {
  status=$?
  trap - EXIT
  if [[ "$COMPLETE" != true ]]; then
    # Roll back only our own new hard links, never a pre-existing/raced file.
    if [[ "$RECEIPT_PUBLISHED" == true && -e "$RECEIPT_STAGE" && "$RECEIPT_PATH" -ef "$RECEIPT_STAGE" ]]; then
      rm -f -- "$RECEIPT_PATH"
    fi
    if [[ "$OUTPUT_PUBLISHED" == true && -e "$SIGNED_STAGE" && "$OUTPUT_PATH" -ef "$SIGNED_STAGE" ]]; then
      rm -f -- "$OUTPUT_PATH"
    fi
  fi
  if [[ -n "$RECEIPT_STAGE" ]]; then rm -f -- "$RECEIPT_STAGE"; fi
  if [[ -n "$SIGNED_STAGE_DIR" ]]; then rm -rf -- "$SIGNED_STAGE_DIR"; fi
  if [[ -n "$TEMP_DIR" ]]; then rm -rf -- "$TEMP_DIR"; fi
  unset PLAY_UPLOAD_STORE_PASSWORD PLAY_UPLOAD_KEY_PASSWORD
  exit "$status"
}
trap cleanup EXIT

TEMP_DIR=$(mktemp -d)
SIGNED_STAGE_DIR=$(mktemp -d "$OUTPUT_PARENT/.countaway-sign.XXXXXXXX")
SIGNED_STAGE="$SIGNED_STAGE_DIR/signed.aab"
RECEIPT_STAGE=$(mktemp "$RECEIPT_PARENT/.countaway-receipt.XXXXXXXX")
KEYSTORE_CERT="$TEMP_DIR/keystore-certificate.pem"

EXPECTED_CERT_SHA256=$(openssl x509 -in "$CERTIFICATE" -noout -fingerprint -sha256 | sed 's/^sha256 Fingerprint=//I' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')
[[ -n "$EXPECTED_CERT_SHA256" ]] || fail "Could not read certificate SHA-256 fingerprint"

keytool -exportcert -rfc -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env PLAY_UPLOAD_STORE_PASSWORD -file "$KEYSTORE_CERT" >/dev/null

KEYSTORE_CERT_SHA256=$(openssl x509 -in "$KEYSTORE_CERT" -noout -fingerprint -sha256 | sed 's/^sha256 Fingerprint=//I' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')
[[ "$KEYSTORE_CERT_SHA256" == "$EXPECTED_CERT_SHA256" ]] || fail "Keystore alias certificate does not match the supplied public upload certificate"

INPUT_SHA256=$(sha256sum "$INPUT" | awk '{print $1}')
KEYSTORE_SHA256=$(sha256sum "$KEYSTORE" | awk '{print $1}')
CERTIFICATE_SHA256=$(sha256sum "$CERTIFICATE" | awk '{print $1}')
if [[ -n "$BUNDLETOOL" ]]; then BUNDLETOOL_SHA256=$(sha256sum "$BUNDLETOOL" | awk '{print $1}'); fi

jarsigner -keystore "$KEYSTORE" -storepass:env PLAY_UPLOAD_STORE_PASSWORD -keypass:env PLAY_UPLOAD_KEY_PASSWORD -digestalg SHA-256 -sigalg SHA256withRSA -signedjar "$SIGNED_STAGE" "$INPUT" "$ALIAS"

[[ -f "$SIGNED_STAGE" && -s "$SIGNED_STAGE" ]] || fail "Signed AAB was not created"

if [[ -n "$BUNDLETOOL" ]]; then
  java -jar "$BUNDLETOOL" validate --bundle="$SIGNED_STAGE"

  if [[ -n "$EXPECTED_PACKAGE" ]]; then
    ACTUAL_PACKAGE=$(java -jar "$BUNDLETOOL" dump manifest --bundle="$SIGNED_STAGE" --module=base --xpath=/manifest/@package | tr -d '\r\n')
    [[ "$ACTUAL_PACKAGE" == "$EXPECTED_PACKAGE" ]] || fail "Package mismatch: $ACTUAL_PACKAGE != $EXPECTED_PACKAGE"
  fi

  if [[ -n "$EXPECTED_VERSION_NAME" ]]; then
    ACTUAL_VERSION_NAME=$(java -jar "$BUNDLETOOL" dump manifest --bundle="$SIGNED_STAGE" --module=base --xpath=/manifest/@android:versionName | tr -d '\r\n')
    [[ "$ACTUAL_VERSION_NAME" == "$EXPECTED_VERSION_NAME" ]] || fail "versionName mismatch: $ACTUAL_VERSION_NAME != $EXPECTED_VERSION_NAME"
  fi

  if [[ -n "$EXPECTED_VERSION_CODE" ]]; then
    ACTUAL_VERSION_CODE=$(java -jar "$BUNDLETOOL" dump manifest --bundle="$SIGNED_STAGE" --module=base --xpath=/manifest/@android:versionCode | tr -d '\r\n')
    [[ "$ACTUAL_VERSION_CODE" == "$EXPECTED_VERSION_CODE" ]] || fail "versionCode mismatch: $ACTUAL_VERSION_CODE != $EXPECTED_VERSION_CODE"
  fi
fi

# Verify the final staged bytes after bundletool and all other external helpers.
VERIFY_LOG="$TEMP_DIR/jarsigner-verify.txt"
jarsigner -verify -verbose:summary -certs "$SIGNED_STAGE" | tee "$VERIFY_LOG"
grep -Fq "jar verified." "$VERIFY_LOG" || fail "jarsigner did not report a verified bundle"

# Hash only after all signing/verification helpers have finished writing.
[[ $(sha256sum "$INPUT" | awk '{print $1}') == "$INPUT_SHA256" ]] || fail "Input AAB changed during signing"
[[ $(sha256sum "$KEYSTORE" | awk '{print $1}') == "$KEYSTORE_SHA256" ]] || fail "Keystore changed during signing"
[[ $(sha256sum "$CERTIFICATE" | awk '{print $1}') == "$CERTIFICATE_SHA256" ]] || fail "Certificate changed during signing"
if [[ -n "$BUNDLETOOL" ]]; then
  [[ $(sha256sum "$BUNDLETOOL" | awk '{print $1}') == "$BUNDLETOOL_SHA256" ]] || fail "bundletool changed during signing"
fi
OUTPUT_SHA256=$(sha256sum "$SIGNED_STAGE" | awk '{print $1}')

cat > "$RECEIPT_STAGE" <<EOF_RECEIPT
input_aab=$INPUT
input_sha256=$INPUT_SHA256
signed_aab=$OUTPUT
signed_sha256=$OUTPUT_SHA256
upload_certificate_sha256=$EXPECTED_CERT_SHA256
alias=$ALIAS
bundletool_validation=$([[ -n "$BUNDLETOOL" ]] && echo true || echo false)
expected_package=$EXPECTED_PACKAGE
expected_version_name=$EXPECTED_VERSION_NAME
expected_version_code=$EXPECTED_VERSION_CODE
EOF_RECEIPT

# A same-filesystem hard link publishes each new file without clobbering an
# existing destination. A raced receipt leaves the unrelated file untouched.
ln -T -- "$SIGNED_STAGE" "$OUTPUT_PATH" || fail "Refusing to overwrite output during publication: $OUTPUT"
OUTPUT_PUBLISHED=true
[[ $(sha256sum "$OUTPUT_PATH" | awk '{print $1}') == "$OUTPUT_SHA256" ]] || fail "Published signed AAB differs from verified bytes"
ln -T -- "$RECEIPT_STAGE" "$RECEIPT_PATH" || fail "Refusing to overwrite receipt during publication: $RECEIPT"
RECEIPT_PUBLISHED=true
COMPLETE=true

echo
echo "PASS: Play upload bundle signed and verified."
echo "SIGNED_AAB=$OUTPUT"
echo "SIGNED_SHA256=$OUTPUT_SHA256"
echo "UPLOAD_CERT_SHA256=$EXPECTED_CERT_SHA256"
echo "RECEIPT=$RECEIPT"
