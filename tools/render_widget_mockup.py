"""
render_widget_mockup.py — design sheet for the five desktop cards, drawn in a
CarPlay-inspired style: near-black tiles, hairline borders, vivid iOS control colours.

Not a screenshot: shapes approximate the RemoteViews layouts.

    python tools/render_widget_mockup.py     # writes docs/widget-mockup.png
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# --- CarPlay / iOS palette ------------------------------------------------
SURFACE_TOP = (30, 24, 38)       # faint violet tint at the top of a tile
SURFACE_BOTTOM = (10, 10, 14)
TILE_BORDER = (255, 255, 255, 31)

IOS_GREEN = (48, 209, 88)        # systemGreen — power ON
IOS_GRAY = (58, 58, 60)          # systemGray3 — power OFF
IOS_SECONDARY = (142, 142, 147)  # secondaryLabel
WHITE = (255, 255, 255)
DARK_LABEL = (11, 11, 15)

BACKDROP_TOP = (26, 20, 34)
BACKDROP_BOTTOM = (6, 5, 14)

# 白 / 粉 / 红 / 紫 / 蓝 / 青  — must match WidgetActions.SWATCHES
SWATCH_COLORS = [
    (255, 255, 255),
    (255, 105, 180),
    (255, 59, 48),
    (191, 90, 242),
    (10, 132, 255),
    (100, 210, 255),
]

# hue strip segments (60° steps) — must match widget_hue_0/2/4/6/8/10
HUE_COLORS = [
    (255, 0, 0),
    (255, 255, 0),
    (0, 255, 0),
    (0, 255, 255),
    (0, 0, 255),
    (255, 0, 255),
]

FONTS = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhl.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def font(size: int):
    for p in FONTS:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except OSError:
                continue
    return ImageFont.load_default()


def _ints(b):
    return tuple(int(round(v)) for v in b)


def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def vgrad(img, box, c1, c2):
    x0, y0, x1, y1 = _ints(box)
    d = ImageDraw.Draw(img)
    h = max(1, y1 - y0)
    for y in range(h):
        d.line((x0, y0 + y, x1, y0 + y), fill=lerp(c1, c2, y / (h - 1) if h > 1 else 0))


def composited(img, box, fn):
    x0, y0, x1, y1 = _ints(box)
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    fn(ImageDraw.Draw(layer), w, h)
    img.alpha_composite(layer, (x0, y0))


def tile(img, box, radius=22):
    """Near-black tile with a hairline border, clipped to rounded corners."""
    x0, y0, x1, y1 = _ints(box)
    w, h = x1 - x0, y1 - y0
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for y in range(h):
        ld.line((0, y, w, y), fill=lerp(SURFACE_TOP, SURFACE_BOTTOM, y / max(1, h - 1)) + (255,))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    img.paste(layer, (x0, y0), mask)

    def border(d, ww, hh):
        d.rounded_rectangle((0, 0, ww - 1, hh - 1), radius=radius, outline=TILE_BORDER, width=2)

    composited(img, box, border)


def power(img, cx, cy, r, label, f, on=True):
    fill = IOS_GREEN if on else IOS_GRAY
    box = (cx - r, cy - r, cx + r, cy + r)
    ImageDraw.Draw(img).ellipse(box, fill=fill)

    def ring(d, w, h):
        d.ellipse((0, 0, w - 1, h - 1), outline=WHITE + (64,), width=2)

    composited(img, box, ring)
    ImageDraw.Draw(img).text((cx, cy), label, font=f, fill=WHITE, anchor="mm")


def dot(img, cx, cy, r, color):
    d = ImageDraw.Draw(img)
    box = (cx - r, cy - r, cx + r, cy + r)
    d.ellipse(box, fill=color)
    ring = (0, 0, 0, 51) if sum(color) > 600 else WHITE + (64,)
    d.ellipse(box, outline=ring, width=2)


def open_pill(img, box, label, f):
    box = _ints(box)
    ImageDraw.Draw(img).rounded_rectangle(box, radius=16, fill=WHITE)
    ImageDraw.Draw(img).text(
        ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label, font=f, fill=DARK_LABEL, anchor="mm"
    )


def hue_strip(img, x, y, w, h, gap=2, radius=6):
    seg = (w - gap * (len(HUE_COLORS) - 1)) / len(HUE_COLORS)
    for i, c in enumerate(HUE_COLORS):
        bx = x + i * (seg + gap)
        ImageDraw.Draw(img).rounded_rectangle(
            (int(bx), int(y), int(bx + seg), int(y + h)), radius=radius, fill=c
        )


def text(d, xy, s, f, fill, anchor="la"):
    d.text(xy, s, font=font, fill=fill, anchor=anchor)


# ------------------------------------------------------------------ cards
def draw_small(img, x, y, f):
    tile(img, (x, y, x + 176, y + 76))
    d = ImageDraw.Draw(img)
    d.text((x + 16, y + 22), "氛围灯", font=f["bold"], fill=WHITE)
    d.text((x + 16, y + 46), "已开灯", font=f["small"], fill=IOS_SECONDARY)
    power(img, x + 176 - 14 - 28, y + 38, 28, "开", f["label"])


def draw_wide(img, x, y, f):
    tile(img, (x, y, x + 400, y + 76))
    power(img, x + 14 + 28, y + 38, 28, "开", f["label"])
    for i in range(6):
        dot(img, x + 96 + i * 52, y + 38, 18, SWATCH_COLORS[i])


def draw_square(img, x, y, f):
    tile(img, (x, y, x + 176, y + 176))
    d = ImageDraw.Draw(img)
    d.text((x + 16, y + 26), "氛围灯", font=f["bold"], fill=WHITE)
    power(img, x + 176 - 14 - 22, y + 32, 22, "开", f["small"])
    for row in range(2):
        for col in range(3):
            dot(img, x + 44 + col * 44, y + 92 + row * 50, 19, SWATCH_COLORS[row * 3 + col])


def draw_tall(img, x, y, f):
    """2x4 — the CarPlay dashboard tile: power, 2x3 swatches, hue strip."""
    tile(img, (x, y, x + 176, y + 400))
    d = ImageDraw.Draw(img)
    d.text((x + 16, y + 30), "氛围灯", font=f["bold"], fill=WHITE)
    power(img, x + 176 - 14 - 25, y + 34, 25, "开", f["label"])

    for row in range(3):
        for col in range(2):
            dot(img, x + 52 + col * 72, y + 118 + row * 72, 28, SWATCH_COLORS[row * 2 + col])

    hue_strip(img, x + 16, y + 322, 144, 44)
    d.text((x + 16, y + 376), "已开灯", font=f["small"], fill=IOS_SECONDARY)


def draw_big(img, x, y, f):
    """4x2 — power, six swatches, state + open. No hue strip: 110dp has no room for a
    fourth row (power 42 + dots 40 + strip 24 + state 12 exceeds the tile)."""
    tile(img, (x, y, x + 400, y + 176))
    d = ImageDraw.Draw(img)
    d.text((x + 18, y + 28), "氛围灯", font=f["bold"], fill=WHITE)
    power(img, x + 400 - 16 - 26, y + 32, 26, "开", f["label"])
    for i in range(6):
        dot(img, x + 46 + i * 58, y + 90, 21, SWATCH_COLORS[i])
    d.text((x + 18, y + 146), "已开灯", font=f["small"], fill=IOS_SECONDARY)
    open_pill(img, (x + 318, y + 134, x + 384, y + 160), "打开", f["small"])


def main():
    f = {
        "title": font(26),
        "bold": font(17),
        "label": font(14),
        "small": font(13),
        "tiny": font(11),
        "caption": font(12),
    }

    W, H = 1000, 590
    img = Image.new("RGBA", (W, H), (6, 5, 14, 255))
    vgrad(img, (0, 0, W, H), BACKDROP_TOP, BACKDROP_BOTTOM)

    # wallpaper-ish glow so the dark tiles read as tiles
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for (cx, cy, r, c, a) in ((120, 40, 240, (139, 92, 246), 70), (900, 560, 260, (217, 70, 239), 55)):
        ld.ellipse((cx - r, cy - r, cx + r, cy + r), fill=c + (a,))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(60)))

    d = ImageDraw.Draw(img)
    d.text((36, 20), "氛围灯 · 桌面卡片（App Widget）", font=f["title"], fill=WHITE)
    d.text((36, 52), "CarPlay 风格 + 按 187B（800x480 / 5 寸）校准：色块随卡片自适应，开关 52dp≈7mm，色带 36dp",
           font=f["small"], fill=(170, 165, 185))

    draw_wide(img, 36, 100, f)
    draw_small(img, 470, 100, f)
    d = ImageDraw.Draw(img)
    d.text((36, 182), "4×1  圆形开关 + 六色", font=f["caption"], fill=(255, 193, 7))
    d.text((470, 182), "2×1  圆形开关 + 状态", font=f["caption"], fill=(255, 193, 7))

    draw_big(img, 36, 210, f)
    draw_square(img, 470, 210, f)
    d = ImageDraw.Draw(img)
    d.text((36, 394), "4×2  开关 + 六色 + 打开", font=f["caption"], fill=(255, 193, 7))
    d.text((470, 394), "2×2  开关 + 3×2 色块", font=f["caption"], fill=(255, 193, 7))

    draw_tall(img, 760, 100, f)
    d = ImageDraw.Draw(img)
    d.text((760, 512), "2×4  竖卡（CarPlay 磁贴）", font=f["caption"], fill=(255, 193, 7))
    d.text((36, 430), "色块颜色即下发颜色（iOS 系统色）；色带为分段式——RemoteViews 不支持拖拽滑块",
           font=f["caption"], fill=(170, 165, 185))

    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "docs", "widget-mockup.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.convert("RGB").save(out)
    print("written", out, img.size)


if __name__ == "__main__":
    main()
