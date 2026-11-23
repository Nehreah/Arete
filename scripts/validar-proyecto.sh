#!/usr/bin/env bash
# Validación reproducible de Aréte. Guarda un log por tarea y no oculta fallos.
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/build/validacion-$STAMP"
mkdir -p "$OUT"
SUMMARY="$OUT/resumen.txt"
: > "$SUMMARY"

printf 'Aréte — validación local\nFecha: %s\nDirectorio: %s\n' "$(date -Is)" "$ROOT" | tee -a "$SUMMARY"
printf 'JAVA_HOME=%s\nANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "${JAVA_HOME:-no definido}" "${ANDROID_HOME:-no definido}" "${ANDROID_SDK_ROOT:-no definido}" | tee -a "$SUMMARY"

if ! command -v java >/dev/null 2>&1; then
  echo 'BLOQUEO: Java no está instalado o no está en PATH.' | tee -a "$SUMMARY"
  exit 2
fi
java -version >> "$SUMMARY" 2>&1
if [[ ! -x ./gradlew ]]; then
  echo 'BLOQUEO: no se encuentra ./gradlew ejecutable.' | tee -a "$SUMMARY"
  exit 2
fi

TASKS=(":app:testDebugUnitTest" ":app:lintDebug" ":app:assembleDebug" ":app:assembleRelease")
FAIL=0
for task in "${TASKS[@]}"; do
  safe="${task//:/_}"
  log="$OUT/${safe}.log"
  echo "\n== $task ==" | tee -a "$SUMMARY"
  if ./gradlew "$task" --no-daemon --stacktrace >"$log" 2>&1; then
    echo "OK: $task (log: ${log#$ROOT/})" | tee -a "$SUMMARY"
  else
    code=$?
    echo "FALLO: $task (código $code; log: ${log#$ROOT/})" | tee -a "$SUMMARY"
    tail -n 18 "$log" | sed 's/^/  /' | tee -a "$SUMMARY"
    FAIL=1
  fi
done
if [[ "$FAIL" -eq 0 ]]; then
  echo '\nRESULTADO: todas las tareas finalizaron correctamente.' | tee -a "$SUMMARY"
else
  echo '\nRESULTADO: hay tareas fallidas; revisar logs individuales antes de modificar dependencias.' | tee -a "$SUMMARY"
fi
echo "Informe: $SUMMARY"
exit "$FAIL"
