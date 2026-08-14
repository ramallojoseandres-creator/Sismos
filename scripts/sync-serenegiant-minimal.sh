#!/usr/bin/env bash
# Extract OEM serenegiant UVC stack (bytecode) from MJ-008 APK — avoids broken jadx sources.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEX="$ROOT/refs/mj008_original/extract/classes3.dex"
OUT="$ROOT/app/libs/serenegiant-uvc.jar"
D2J="${D2J_HOME:-/tmp/dex2jar/dex-tools-v2.4}/d2j-dex2jar.sh"
if [[ ! -f "$DEX" ]]; then
  echo "Missing $DEX — extract OEM APK to refs/mj008_original/extract/"
  exit 1
fi
if [[ ! -x "$D2J" ]]; then
  echo "Downloading dex2jar…"
  curl -sL -o /tmp/dex-tools.zip "https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip"
  unzip -q -o /tmp/dex-tools.zip -d /tmp/dex2jar
  D2J="/tmp/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh"
fi
TMP="$(mktemp -d)"
"$D2J" "$DEX" -o "$TMP/full.jar" >/dev/null
mkdir -p "$TMP/extract" "$(dirname "$OUT")"
(cd "$TMP/extract" && jar xf "$TMP/full.jar")
jar cf "$OUT" -C "$TMP/extract" com/serenegiant
rm -rf "$TMP"
echo "Wrote $(jar tf "$OUT" | wc -l) classes to $OUT"
