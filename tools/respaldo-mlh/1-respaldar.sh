#!/usr/bin/env bash
# MLH — PASO 1: respaldar datos de la app ACTUAL (sin desinstalar)
set -euo pipefail
cd "$(dirname "$0")"
PKG=com.mlh.skinanalyzer
OUT="$(pwd)/copia"
mkdir -p "$OUT"

find_adb() {
  if command -v adb >/dev/null 2>&1; then echo adb; return; fi
  if [[ -x ./adb ]]; then echo ./adb; return; fi
  if [[ -x ./platform-tools/adb ]]; then echo ./platform-tools/adb; return; fi
  echo ""
}

ADB=$(find_adb)
if [[ -z "$ADB" ]]; then
  echo "No se encontró adb. Descargue Platform Tools y coloque adb aquí."
  echo "https://developer.android.com/tools/releases/platform-tools"
  exit 1
fi

echo "============================================================"
echo "  MLH — PASO 1: RESPALDAR (app actual, NO borrar todavía)"
echo "============================================================"
"$ADB" start-server
"$ADB" devices
echo
read -r -p "Tablet conectada y depuración aceptada? Enter para seguir..."

if "$ADB" shell "run-as $PKG ls" >/dev/null 2>&1; then
  echo "Copiando datos..."
  "$ADB" exec-out "run-as $PKG tar -c databases files shared_prefs 2>/dev/null" > "$OUT/mlh_datos.tar"
  SZ=$(wc -c < "$OUT/mlh_datos.tar" | tr -d ' ')
  if [[ "$SZ" -lt 100 ]]; then
    echo "Archivo vacío; usando adb backup..."
    "$ADB" backup -f "$OUT/mlh_datos.ab" -noapk "$PKG"
    echo "Listo: $OUT/mlh_datos.ab"
  else
    echo "RESPALDO LISTO: $OUT/mlh_datos.tar ($SZ bytes)"
  fi
else
  echo "run-as no disponible. Usando adb backup (acepte en la tablet)..."
  "$ADB" backup -f "$OUT/mlh_datos.ab" -noapk "$PKG"
  echo "RESPALDO LISTO: $OUT/mlh_datos.ab"
fi
echo "Siguiente: ./2-instalar-nueva.sh"
