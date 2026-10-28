#!/bin/sh
# Pack the release APK into a deflated zip. Storing the APK raw leaves its own
# zip trailer visible, and phone archive apps then report the file as damaged.
set -e
cd "$(dirname "$0")/.."
APK="${1:-app/build/outputs/apk/release/app-release.apk}"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
cp "$APK" "$STAGE/revline.apk"
cat > "$STAGE/Read me.txt" << 'EOF'
Extract both files, then tap revline.apk and choose Install.
If Revline is already installed from an older sideload, uninstall that copy first. That clears the save.
EOF
( cd "$STAGE" && zip -9 -X revline.zip "Read me.txt" revline.apk )
unzip -t "$STAGE/revline.zip" >/dev/null
mkdir -p dist /opt/cursor/artifacts
cp "$STAGE/revline.zip" dist/revline.zip
cp "$STAGE/revline.zip" /opt/cursor/artifacts/revline.zip
cp "$APK" dist/revline.apk
cp "$APK" /opt/cursor/artifacts/revline.apk
echo "packed $(wc -c < dist/revline.zip) bytes"
