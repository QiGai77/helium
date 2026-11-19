#!/bin/bash
# usage: ./build.sh [01 02 ...]   builds SVGs and renders 2x PNGs
cd "$(dirname "$0")"; source env.sh
python3 screens.py "$@" || exit 1
args=()
for f in ../*.svg; do b=$(basename "$f" .svg); args+=("$f" "../png/$b@2x.png"); done
python3 render.py "${args[@]}"
