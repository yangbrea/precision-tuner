#!/usr/bin/env bash
#
# Regenerate all launcher icon densities from a square source image.
#
# Usage:
#   ./tools/update-icon.sh <source.png> [scale] [--build]
#
#   <source.png>  square PNG (any size; 1024+ recommended)
#   [scale]       foreground size as a fraction of the 108dp canvas
#                 (default 0.85). Content must stay inside the adaptive
#                 safe zone: keep the image border uniform and centered.
#   [--build]     also run assembleDebug with CREPE enabled afterwards.
#
# What it does:
#   - samples the dominant edge color of the image (used as the adaptive
#     icon background and the foreground canvas, so masks have no seam)
#   - writes that color to res/values/colors.xml (ic_launcher_background)
#   - regenerates legacy ic_launcher.png at 48/72/96/144/192 px
#   - regenerates adaptive ic_launcher_foreground.png at
#     108/162/216/324/432 px, image centered at <scale> of the canvas
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RES_DIR="$PROJECT_DIR/app/src/main/res"

SCALE="0.85"
BUILD=false
SOURCE=""

for arg in "$@"; do
    case "$arg" in
        --build) BUILD=true ;;
        -*) echo "错误：无法识别参数 $arg" >&2; exit 1 ;;
        *)
            if [[ -z "$SOURCE" ]]; then SOURCE="$arg"
            else SCALE="$arg"; fi
            ;;
    esac
done

if [[ -z "$SOURCE" || ! -f "$SOURCE" ]]; then
    echo "用法: $0 <source.png> [scale] [--build]" >&2
    exit 1
fi

python3 - "$SOURCE" "$SCALE" "$RES_DIR" <<'PYEOF'
import os
import re
import sys
from collections import Counter

from PIL import Image

src, scale, res_dir = sys.argv[1], float(sys.argv[2]), sys.argv[3]

im = Image.open(src).convert('RGB')
w, h = im.size
if w != h:
    sys.exit(f"错误：源图必须是正方形（当前 {w}x{h}）")

# Dominant edge color -> adaptive background + foreground canvas base.
edge = Counter()
for x in range(0, w, 40):
    edge[im.getpixel((x, 2))] += 1
    edge[im.getpixel((x, h - 3))] += 1
for y in range(0, h, 40):
    edge[im.getpixel((2, y))] += 1
    edge[im.getpixel((w - 3, y))] += 1
bg = edge.most_common(1)[0][0]
bg_hex = '#%02X%02X%02X' % bg

# colors.xml: ic_launcher_background
colors_path = os.path.join(res_dir, 'values', 'colors.xml')
xml = open(colors_path).read()
new_xml, n = re.subn(
    r'(<color name="ic_launcher_background">)#[0-9A-Fa-f]{6}(</color>)',
    r'\g<1>%s\g<2>' % bg_hex,
    xml,
)
if n != 1:
    sys.exit(f"错误：colors.xml 中未找到 ic_launcher_background（匹配 {n} 处）")
open(colors_path, 'w').write(new_xml)

# Densities: (legacy px, adaptive foreground canvas px)
densities = {
    'mdpi': (48, 108),
    'hdpi': (72, 162),
    'xhdpi': (96, 216),
    'xxhdpi': (144, 324),
    'xxxhdpi': (192, 432),
}
for density, (legacy_size, canvas) in densities.items():
    folder = os.path.join(res_dir, f'mipmap-{density}')
    os.makedirs(folder, exist_ok=True)
    im.resize((legacy_size, legacy_size), Image.LANCZOS).save(
        os.path.join(folder, 'ic_launcher.png'))
    fg = Image.new('RGBA', (canvas, canvas), (*bg, 255))
    side = int(round(canvas * scale))
    small = im.resize((side, side), Image.LANCZOS).convert('RGBA')
    offset = (canvas - side) // 2
    fg.paste(small, (offset, offset))
    fg.save(os.path.join(folder, 'ic_launcher_foreground.png'))

print(f"background: {bg_hex} | scale: {scale:.2f} | densities regenerated: "
      f"{', '.join(densities)}")
PYEOF

echo "完成：legacy 图标 + 自适应前景已重新生成，ic_launcher_background 已更新。"

if [[ "$BUILD" == true ]]; then
    echo "开始构建 assembleDebug（含 CREPE）..."
    GRADLE_USER_HOME="$PROJECT_DIR/.gradle-home" \
        "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" \
        -PtinyCrepeEnabled=true assembleDebug
fi
