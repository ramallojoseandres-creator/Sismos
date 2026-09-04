#!/usr/bin/env bash
# MLH — PASO 2: desinstalar vieja e instalar APK nueva
set -euo pipefail
cd "$(dirname "$0")"
PKG=com.mlh.skinanalyzer
OUT="$(pwd)/copia"

find_adb() {
  if command -v adb >/dev/null 2>&1; then echo adb; return; fi
  if [[ -x ./adb ]]; then echo ./adb; return; fi
  if [[ -x ./platform-tools/adb ]]; then echo ./platform-tools/adb; return; fi
  echo ""
}
ADB=$(find_adb)
[[ -n "$ADB" ]] || { echo "No adb"; exit 1; }

if [[ ! -f "$OUT/mlh_datos.tar" && ! -f "$OUT/mlh_datos.ab" ]]; then
  echo "ERROR: no hay respaldo. Ejecute primero ./1-respaldar.sh"
  exit 1
fi

APK="${1:-}"
if [[ -z "$APK" ]]; then
  read -r -p "Ruta del APK nuevo: " APK
fi
[[ -f "$APK" ]] || { echo "No existe $APK"; exit 1; }

echo "Desinstalando $PKG..."
"$ADB" uninstall "$PKG" || true
echo "Instalando $APK..."
"$ADB" install -r "$APK"
echo "Listo. Siguiente: ./3-restaurar.sh"
