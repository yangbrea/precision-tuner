#!/usr/bin/env bash
#
# Fetch the VSCO2 CE Upright Piano samples (CC0) used as the app's
# reference-tone sounds.
#
# Source: https://github.com/schollz/VSCO-2-CE (CC0 1.0 Universal)
#   Keys/Upright Piano/Player_dyn2_rr1_<idx>.wav (medium velocity layer)
#   MappingChart.txt maps sample index -> MIDI note (midi = 21 + 2 * idx)
#
# Usage: ./tools/fetch-vsco2-piano.sh <dest_dir>
# Output: <dest_dir>/*.wav named by MIDI note number + MappingChart.txt
set -euo pipefail

DEST="${1:?用法: $0 <dest_dir>}"
BASE="https://raw.githubusercontent.com/schollz/VSCO-2-CE/HEAD/Keys/Upright%20Piano"
mkdir -p "$DEST"

for idx in $(seq 0 2 44); do
    n=$(printf '%03d' "$idx")
    curl -fsSL "$BASE/Player_dyn2_rr1_${n}.wav" -o "$DEST/tmp_${n}.wav" &
done
wait

# Rename by MIDI note from the mapping chart (midi = 21 + 2 * index).
for idx in $(seq 0 2 44); do
    n=$(printf '%03d' "$idx")
    midi=$((21 + 2 * idx))
    mv "$DEST/tmp_${n}.wav" "$DEST/${midi}.wav"
done

curl -fsSL "$BASE/MappingChart.txt" -o "$DEST/MappingChart.txt"
curl -fsSL "$BASE/Info.txt" -o "$DEST/Info.txt"

echo "已下载 $(ls "$DEST"/*.wav | wc -l) 个采样到 $DEST"
