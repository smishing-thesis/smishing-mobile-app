#!/usr/bin/env bash
# Calcula la versión siguiente de un release (#90), sin la "v", a partir del último tag y de las etiquetas del PR que se fusionó a `main`.
#
#   tools/next-version.sh <último tag, por ejemplo v1.0.0> "<etiquetas separadas por espacios o saltos de línea>"
#
# Gana la etiqueta más fuerte: `major` > `minor` > `patch`. Sin ninguna de las tres, cuenta como `patch`.
#   v1.0.0 + (nada)        -> 1.0.1
#   v1.0.0 + minor         -> 1.1.0
#   v1.4.2 + major, minor  -> 2.0.0
set -euo pipefail

last="${1:?Falta el último tag (por ejemplo v1.0.0)}"
labels="${2:-}"

base="${last#v}"
base="${base%%[-+]*}"
IFS=. read -r major minor patch <<< "$base"
for part in "$major" "$minor" "$patch"; do
  [[ "$part" =~ ^[0-9]+$ ]] || { echo "El tag '$last' no tiene la forma vX.Y.Z" >&2; exit 1; }
done

has() { printf '%s\n' "$labels" | tr -s ' \t' '\n\n' | grep -qx "$1"; }

if has major; then
  echo "$((major + 1)).0.0"
elif has minor; then
  echo "${major}.$((minor + 1)).0"
else
  echo "${major}.${minor}.$((patch + 1))"
fi
