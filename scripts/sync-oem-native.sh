#!/usr/bin/env bash
# Sync OEM native libraries from refs/ (not committed) into the app module.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/refs/mj008_original/extract/lib/arm64-v8a"
DST="$ROOT/app/src/main/jniLibs/arm64-v8a"
if [[ ! -d "$SRC" ]]; then
  echo "Missing $SRC — place MJ-008 OEM APK under refs/mj008_original/"
  exit 1
fi
mkdir -p "$DST"
for lib in libsalon.so libc++_shared.so libUVCCamera.so libusb100.so libuvc.so; do
  cp -f "$SRC/$lib" "$DST/"
done
echo "Synced $(ls "$DST" | wc -l) libs to $DST"
