#!/bin/sh
# Pack the release APK as a zip a phone archive app will open.
# Info-ZIP 3.0 sets a "made by 3.0" header and a max-compression flag, and
# Samsung's archive app reports that as an unknown or damaged archive.
# Storing the APK raw also leaves the APK's own zip trailer visible inside
# the outer file, which the same app rejects. This writes one deflated entry
# with DOS / zip 2.0 headers and no extra fields.
set -e
cd "$(dirname "$0")/.."
APK="${1:-app/build/outputs/apk/release/app-release.apk}"
python3 - "$APK" << 'PY'
import sys, zipfile, os
apk_path, = sys.argv[1:]
data = open(apk_path, "rb").read()
if data[:2] != b"PK":
    raise SystemExit("release file is not an apk")
os.makedirs("dist", exist_ok=True)
os.makedirs("/opt/cursor/artifacts", exist_ok=True)
out = "dist/revline.zip"
info = zipfile.ZipInfo("revline.apk", date_time=(2024, 1, 1, 0, 0, 0))
info.compress_type = zipfile.ZIP_DEFLATED
info.create_system = 0
info.external_attr = 0x20
with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=False) as z:
    z.writestr(info, data, compresslevel=6)
blob = open(out, "rb").read()
if blob.count(b"PK\x05\x06") != 1:
    raise SystemExit("zip still has more than one archive trailer")
with zipfile.ZipFile(out) as z:
    got = z.read("revline.apk")
    ent = z.getinfo("revline.apk")
if got != data:
    raise SystemExit("extracted apk does not match the release")
if ent.flag_bits != 0 or ent.compress_type != 8 or ent.create_system != 0 or ent.extra:
    raise SystemExit(f"header not phone-safe: flags={ent.flag_bits:#x} method={ent.compress_type} os={ent.create_system} extra={ent.extra!r}")
for dest in ("/opt/cursor/artifacts/revline.zip",):
    open(dest, "wb").write(blob)
open("dist/revline.apk", "wb").write(data)
open("/opt/cursor/artifacts/revline.apk", "wb").write(data)
print(f"packed {len(blob)} bytes flags={ent.flag_bits:#x} os={ent.create_system}")
PY
unzip -t dist/revline.zip >/dev/null
