#!/usr/bin/env bash
# Render the own Sticker-style icons (app/src/main/res/drawable/ic_own_*.xml) into a contact
# sheet, docs/icons-preview.png. Each cell shows one icon at 192 px and 96 px, both on a
# dark-green, forest-like background (left) and on white (right), labelled with its name.
#
# Needs python3 (stdlib), rsvg-convert and ImageMagick 7 (magick). Usage:
#   tools/render-icons-preview.sh [output.png]
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
out="${1:-$repo/docs/icons-preview.png}"
res="$repo/app/src/main/res"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

cell_w=416
cell_h=352
# Shared forest-ish background: deterministic dark-green plasma (blotchy light and shade, like
# foliage) to mimic a busy wallpaper behind the widget. Kept smooth so the PNG stays small.
magick -seed 7 -size ${cell_w}x${cell_h} plasma:'#10261A-#3A6B45' -colorspace gray -blur 0x4 \
  -auto-level +level-colors '#0B1D12,#44714A' "$work/forest.png"

cells=()
for xml in "$res"/drawable/ic_own_*.xml; do
  name="$(basename "$xml" .xml)"
  name="${name#ic_own_}"
  python3 "$repo/tools/vd2svg.py" "$xml" -o "$work/$name.svg"
  rsvg-convert -w 192 -h 192 "$work/$name.svg" -o "$work/$name-192.png"
  rsvg-convert -w 96 -h 96 "$work/$name.svg" -o "$work/$name-96.png"
  # left half: forest background; right half: white panel
  magick "$work/forest.png" \
    -fill white -draw "rectangle 208,36 415,351" \
    "$work/$name-192.png" -geometry +8+44 -composite \
    "$work/$name-96.png" -geometry +56+244 -composite \
    "$work/$name-192.png" -geometry +216+44 -composite \
    "$work/$name-96.png" -geometry +264+244 -composite \
    -fill white -font Helvetica-Bold -pointsize 20 -gravity north -annotate +0+8 "$name" \
    -fill '#C9D8CC' -font Helvetica -pointsize 12 -gravity northwest -annotate +8+332 "192 px / 96 px" \
    +gravity "$work/cell-$name.png"
  cells+=("$work/cell-$name.png")
done

magick montage "${cells[@]}" -tile 3x -geometry +4+4 -background "#0B1A10" -depth 8 \
  -define png:compression-level=9 "$out"
echo "wrote $out ($(wc -c <"$out" | tr -d ' ') bytes, ${#cells[@]} icons)"
