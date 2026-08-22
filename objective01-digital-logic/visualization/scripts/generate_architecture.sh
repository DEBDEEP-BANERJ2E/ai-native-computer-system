#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
input="$root/visualization/architecture/objective1_overview.dot"
dot -Tsvg "$input" -o "${input%.dot}.svg"
dot -Tpng -Gdpi=180 "$input" -o "${input%.dot}.png"
echo "Architecture diagram written to $root/visualization/architecture"