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

[[ -f "$INPUT" && -s "$INPUT" ]] || fail "Input AAB is missing or empty: $INPUT"
[[ -f "$KEYSTORE" && -s "$KEYSTORE" ]] || fail "Keystore is missing or empty: $KEYSTORE"
[[ -f "$CERTIFICATE" && -s "$CERTIFICATE" ]] || fail "Certificate is missing or empty: $CERTIFICATE"
[[ "$INPUT" != "$OUTPUT" ]] || fail "Input and output paths must differ"
[[ ! -e "$OUTPUT" ]] || fail "Refusing to overwrite existing output: $OUTPUT"

for cmd in jarsigner keytool openssl sha256sum mktemp; do
  command -v "$cmd" >/dev/null 2>&1 || fail "Required command not found: $cmd"
done

if [[ -n "$BUNDLETOOL" ]]; then
  [[ -f "$BUNDLETOOL" && -s "$BUNDLETOOL" ]] || fail "bundletool JAR is missing or empty: $BUNDLETOOL"
  command -v java >/dev/null 2>&1 || fail "Required command not found: java"
fi

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
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"; unset PLAY_UPLOAD_STORE_PASSWORD PLAY_UPLOAD_KEY_PASSWORD' EXIT
KEYSTORE_CERT="$TEMP_DIR/keystore-certificate.pem"

EXPECTED_CERT_SHA256=$(openssl x509 -in "$CERTIFICATE" -noout -fingerprint -sha256 | sed 's/^sha256 Fingerprint=//I' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')
[[ -n "$EXPECTED_CERT_SHA256" ]] || fail "Could not read certificate SHA-256 fingerprint"

keytool -exportcert -rfc -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env PLAY_UPLOAD_STORE_PASSWORD -file "$KEYSTORE_CERT" >/dev/null

KEYSTORE_CERT_SHA256=$(openssl x509 -in "$KEYSTORE_CERT" -noout -fingerprint -sha256 | sed 's/^sha256 Fingerprint=//I' | tr '[:upper:]' '[:lower:]' | tr -d ':[:space:]')
[[ "$KEYSTORE_CERT_SHA256" == "$EXPECTED_CERT_SHA256" ]] || fail "Keystore alias certificate does not match the supplied public upload certificate"

INPUT_SHA256=$(sha256sum "$INPUT" | awk '{print $1}')

jarsigner -keystore "$KEYSTORE" -storepass:env PLAY_UPLOAD_STORE_PASSWORD -keypass:env PLAY_UPLOAD_KEY_PASSWORD -digestalg SHA-256 -sigalg SHA256withRSA -signedjar "$OUTPUT" "$INPUT" "$ALIAS"

[[ -f "$OUTPUT" && -s "$OUTPUT" ]] || fail "Signed AAB was not created"

VERIFY_LOG="$TEMP_DIR/jarsigner-verify.txt"
jarsigner -verify -verbose:summary -certs "$OUTPUT" | tee "$VERIFY_LOG"
grep -Fq "jar verified." "$VERIFY_LOG" || fail "jarsigner did not report a verified bundle"

POST_INPUT_SHA256=$(sha256sum "$INPUT" | awk '{print $1}')
[[ "$POST_INPUT_SHA256" == "$INPUT_SHA256" ]] || fail "Input AAB changed during signing"

if [[ -n "$BUNDLETOOL" ]]; then
  java -jar "$BUNDLETOOL" validate --bundle="$OUTPUT"

  if [[ -n "$EXPECTED_PACKAGE" ]]; then
    ACTUAL_PACKAGE=$(java -jar "$BUNDLETOOL" dump manifest --bundle="$OUTPUT" --module=base --xpath=/manifest/@package | tr -d '\r\n')
    [[ "$ACTUAL_PACKAGE" == "$EXPECTED_PACKAGE" ]] || fail "Package mismatch: $ACTUAL_PACKAGE != $EXPECTED_PACKAGE"
  fi

  if [[ -n "$EXPECTED_VERSION_NAME" ]]; then
    ACTUAL_VERSION_NAME=$(java -jar "$BUNDLETOOL" dump manifest --bundle="$OUTPUT" --module=base --xpath=/manifest/@android:versionName | tr -d '\r\n')
    [[ "$ACTUAL_VERSION_NAME" == "$EXPECTED_VERSION_NAME" ]] || fail "versionName mismatch: $ACTUAL_VERSION_NAME != $EXPECTED_VERSION_NAME"
  fi

  if [[ -n "$EXPECTED_VERSION_CODE" ]]; then
    ACTUAL_VERSION_CODE=$(java -jar "$BUNDLETOOL" dump manifest --bundle="$OUTPUT" --module=base --xpath=/manifest/@android:versionCode | tr -d '\r\n')
    [[ "$ACTUAL_VERSION_CODE" == "$EXPECTED_VERSION_CODE" ]] || fail "versionCode mismatch: $ACTUAL_VERSION_CODE != $EXPECTED_VERSION_CODE"
  fi
fi

OUTPUT_SHA256=$(sha256sum "$OUTPUT" | awk '{print $1}')
if [[ -z "$RECEIPT" ]]; then RECEIPT="$OUTPUT.receipt.txt"; fi

cat > "$RECEIPT" <<EOF
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
EOF

echo
echo "PASS: Play upload bundle signed and verified."
echo "SIGNED_AAB=$OUTPUT"
echo "SIGNED_SHA256=$OUTPUT_SHA256"
echo "UPLOAD_CERT_SHA256=$EXPECTED_CERT_SHA256"
echo "RECEIPT=$RECEIPT"
