#!/usr/bin/env bash
# CI-only introspection tool: dumps compact javap signatures for the classes
# listed in tools/api-dump-classes.txt, resolved from the Mojang-mapped
# Minecraft jar and remapped Fabric API jars on the compile classpath.
# Intended to run from the gradle task apiDumpJar (working dir = project root).
set -u
set -o pipefail

BASE="${CI_WORKSPACE:-build}"
OUT="$BASE/api-dump"
LIBS_OUT="$BASE/libs"
mkdir -p "$OUT" "$LIBS_OUT"

CPFILE="$BASE/api-dump/api-classpath.txt"
if [ ! -f "$CPFILE" ]; then
  echo "No classpath dump found; aborting introspection." > "$OUT/ERROR.txt"
  exit 0
fi

mapfile -t JARS < "$CPFILE"
CP="$(IFS=:; printf '%s' "${JARS[*]}")"
CP="$(echo "$CP" | sed 's/ /\\ /g')"

JCP_FILE="$OUT/javap-classpath"
echo "$CP" > "$JCP_FILE"

: > "$OUT/api-dump.txt"
: > "$OUT/missing.txt"

javap_cmd() { javap -classpath "$CP" -p "$1" 2>> "$OUT/missing.txt"; }

while read -r cls; do
  [ -z "$cls" ] && continue
  case "$cls" in \#*) continue;; esac
  {
    echo "===== $cls ====="
    javap_cmd "$cls"
    echo
  } >> "$OUT/api-dump.txt"
done < "${CI_CLASSLIST:-$PWD/tools/api-dump-classes.txt}"

{
  echo "===== fabric-api jar listings ====="
  for j in "${JARS[@]}"; do
    case "$j" in
      *fabric-api*)
        echo "--- $j"
        # fat distribution: list top-level entries then extracted nested module jars
        unzip -l "$j" 2>/dev/null | grep -oE "jars/fabric[a-z0-9._-]*\.jar" | sort -u
        ;;
    esac
  done
} >> "$OUT/api-dump.txt"

# fabric api classes live in the remapped per-module jars that are already on
# $CP, so use the full classpath for them (no nested extraction needed).
{
  echo "===== fabric-api module javap dumps ====="
  for cls in $(cat tools/api-dump-fabric-classes.txt 2>/dev/null); do
    {
      echo "===== $cls ====="
      javap -classpath "$CP" -p "$cls" 2>> "$OUT/fabric-missing.txt"
      echo
    }
  done
} >> "$OUT/api-dump.txt"

{
  echo "=== fabric api modules found ==="
  echo "-- direct classpath fabric jars:"
  for j in "${JARS[@]}"; do
    case "$j" in *fabric*) echo "$j" ;; esac
  done
  echo "-- nested fat-jar entries:"
  for j in "${JARS[@]}"; do
    case "$j" in *fabric-api*) unzip -l "$j" 2>/dev/null | grep -oE "(META-INF/jars/|jars/)fabric[a-z0-9._-]*\.jar" | sort -u ;; esac
  done
} >> "$OUT/api-dump.txt"

# package everything is handled by the gradle `apiDumpJar` task (archives $OUT).
# Write a small summary header for convenience.
{
  echo "=== summary ==="
  echo "classes requested: $(grep -c '[^#[:space:]]' "${CI_CLASSLIST:-$PWD/tools/api-dump-classes.txt}" 2>/dev/null || echo '?')"
  echo "missing vanilla classes: $(grep -c 'Error' "$OUT/missing.txt" 2>/dev/null || echo 0)"
  echo "missing fabric classes: $(grep -c 'Error' "$OUT/fabric-missing.txt" 2>/dev/null || echo 0)"
  echo
} > "$OUT/SUMMARY.txt"
echo "Introspection dump: $OUT/api-dump.txt ($(wc -l < "$OUT/api-dump.txt") lines)"
