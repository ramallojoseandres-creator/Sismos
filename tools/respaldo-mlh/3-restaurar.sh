#!/usr/bin/env bash
# MLH — PASO 3: restaurar datos en la app nueva
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

if ! "$ADB" shell "pm path $PKG" >/dev/null 2>&1; then
  echo "App nueva no instalada. Ejecute ./2-instalar-nueva.sh"
  exit 1
fi

if [[ -f "$OUT/mlh_datos.tar" ]]; then
  "$ADB" shell am force-stop "$PKG" || true
  "$ADB" push "$OUT/mlh_datos.tar" /data/local/tmp/mlh_datos.tar
  "$ADB" shell "run-as $PKG sh -c 'cd /data/data/$PKG && tar -xf /data/local/tmp/mlh_datos.tar'"
  "$ADB" shell "rm /data/local/tmp/mlh_datos.tar" >/dev/null 2>&1 || true
  echo "RESTAURADO. Abra la app y revise Pacientes."
elif [[ -f "$OUT/mlh_datos.ab" ]]; then
  echo "En la tablet pulse Restaurar..."
  "$ADB" restore "$OUT/mlh_datos.ab"
  echo "Listo. Abra la app y revise Pacientes."
else
  echo "No hay respaldo en $OUT"
  exit 1
fi
