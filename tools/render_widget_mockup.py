"""
render_widget_mockup.py — design review sheet for the four desktop cards.

Draws each size at 1.6x on a desktop-like backdrop. Not a screenshot: shapes
approximate the RemoteViews layouts.

    python tools/render_widget_mockup.py     # writes docs/widget-mockup.png
"""

from __future__ import annotations

import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

INK_INDIGO = (30, 27, 75)
SLATE_950 = (2, 6, 23)
VIOLET_500 = (139, 92, 246)
VIOLET_600 = (124, 58, 237)
PURPLE_500 = (168, 85, 247)
FUCHSIA_500 = (217, 70, 239)
FUCHSIA_600 = (192, 38, 211)
CYAN_500 = (6, 182, 212)
WHITE = (255, 255, 255)
MUTED_70 = (206, 202, 226)
MUTED_55 = (170, 166, 198)

# white / pink / red / purple / blue / cyan  (matches WidgetActions.PALETTE and the
# widget_dot_*.xml gradients: base colour with a slightly lighter top-left)
SWATCHES = [
    ((255, 250, 245), (255, 255, 255)),
    ((255, 140, 198), (255, 105, 180)),
    ((255, 77, 77), (255, 0, 0)),
    ((168, 90, 240), (138, 43, 226)),
    ((77, 77, 255), (0, 0, 255)),
    ((125, 255, 255), (0, 229, 229)),
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


def grad_rounded(img, box, c1, c2, radius):
    x0, y0, x1, y1 = _ints(box)
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    grad = Image.new("RGBA", (w, h))
    gd = ImageDraw.Draw(grad)
    for x in range(w):
        gd.line((x, 0, x, h), fill=lerp(c1, c2, x / (w - 1) if w > 1 else 0) + (255,))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    img.paste(grad, (x0, y0), mask)


def card(img, box, radius=30):
    grad_rounded(img, box, VIOLET_600, FUCHSIA_600, radius)

    def border(d, w, h):
        d.rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, outline=WHITE + (70,), width=2)

    composited(img, box, border)


def pill(img, box, label, f, solid_gradient=False):
    if solid_gradient:
        grad_rounded(img, box, (255, 255, 255), (241, 231, 255), 20)
        color = (124, 58, 237)
    else:
        def fill(d, w, h):
            d.rounded_rectangle((0, 0, w - 1, h - 1), radius=20, fill=WHITE + (48,))
            d.rounded_rectangle((0, 0, w - 1, h - 1), radius=20, outline=WHITE + (82,), width=1)

        composited(img, box, fill)
        color = WHITE

    x0, y0, x1, y1 = _ints(box)
    ImageDraw.Draw(img).text(
        ((x0 + x1) / 2, (y0 + y1) / 2), label, font=f, fill=color, anchor="mm"
    )


def dot(img, cx, cy, r, colors):
    base, light = colors
    d = ImageDraw.Draw(img)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=base)
    box = _ints((cx - r, cy - r, cx + r, cy + r))
    w, h = box[2] - box[0], box[3] - box[1]
    if w <= 0 or h <= 0:
        return
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for k, a in ((0.70, 52), (0.50, 40)):
        hr = w * k / 2
        ld.ellipse((w * 0.30 - hr, h * 0.26 - hr, w * 0.30 + hr, h * 0.26 + hr), fill=light + (a,))
    circle = Image.new("L", (w, h), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, w - 1, h - 1), fill=255)
    rch, gch, bch, ach = layer.split()
    ach = ImageChops.multiply(ach, circle)
    img.alpha_composite(Image.merge("RGBA", (rch, gch, bch, ach)), (box[0], box[1]))
    d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=WHITE + (110,), width=2)


def text(d, xy, s, f, fill, anchor="la"):
    d.text(xy, s, font=f, fill=fill, anchor=anchor)


# --------------------------------------------------------------- card renderers
def draw_small(img, x, y, f, s):
    card(img, (x, y, x + 176, y + 64))
    d = ImageDraw.Draw(img)
    text(d, (x + 20, y + 22), "氛围灯", f["bold"], WHITE)
    text(d, (x + 20, y + 44), "已开灯", f["tiny"], MUTED_70)
    circle_power(img, x + 176 - 12 - 29, y + 32, 29, "开", f["label"])


def draw_wide(img, x, y, f, s):
    card(img, (x, y, x + 400, y + 64))
    circle_power(img, x + 12 + 29, y + 32, 29, "开", f["label"])
    for i in range(6):
        cx = x + 128 + i * (36 + 10) + 18
        dot(img, cx, y + 32, 18, SWATCHES[i])


def draw_square(img, x, y, f, s):
    card(img, (x, y, x + 176, y + 176))
    d = ImageDraw.Draw(img)
    text(d, (x + 18, y + 26), "氛围灯", f["bold"], WHITE)
    circle_power(img, x + 176 - 16 - 29, y + 14 + 29, 29, "开", f["label"])
    for row in range(2):
        for col in range(3):
            cx = x + 46 + col * 44
            cy = y + 92 + row * 52
            dot(img, cx, cy, 19, SWATCHES[row * 3 + col])


def draw_big(img, x, y, f, s):
    card(img, (x, y, x + 400, y + 176))
    d = ImageDraw.Draw(img)
    text(d, (x + 20, y + 30), "氛围灯", f["bold"], WHITE)
    circle_power(img, x + 400 - 18 - 34, y + 14 + 34, 34, "开", f["label"])
    for i in range(6):
        cx = x + 40 + i * (54 + 10) + 25
        dot(img, cx, y + 92, 26, SWATCHES[i])
    text(d, (x + 20, y + 146), "已开灯", f["tiny"], MUTED_70)
    pill(img, (x + 312, y + 134, x + 384, y + 164), "打开", f["label"], solid_gradient=True)


def circle_power(img, cx, cy, r, label, f, on=True):
    """Round power button: brand gradient when on, dark glass when off."""
    box = (cx - r, cy - r, cx + r, cy + r)
    if on:
        grad_rounded(img, box, (168, 85, 247), (217, 70, 239), int(r))

        def ring(d, w, h):
            d.ellipse((0, 0, w - 1, h - 1), outline=WHITE + (110,), width=2)

        composited(img, box, ring)
        color = WHITE
    else:
        def glass(d, w, h):
            d.ellipse((0, 0, w - 1, h - 1), fill=(0, 0, 0, 60))
            d.ellipse((0, 0, w - 1, h - 1), outline=WHITE + (140,), width=2)

        composited(img, box, glass)
        color = WHITE
    ImageDraw.Draw(img).text((cx, cy), label, font=f, fill=color, anchor="mm")


def draw_tall(img, x, y, f, s):
    """2x4 portrait card, CarPlay style: round power, 3x2 swatches, hue strip."""
    card(img, (x, y, x + 176, y + 400))
    d = ImageDraw.Draw(img)

    text(d, (x + 16, y + 30), "氛围灯", f["bold"], WHITE)
    circle_power(img, x + 176 - 16 - 32, y + 16 + 32, 32, "开", f["label"], on=True)

    # 3 rows x 2 cols of 36dp swatches (1.6x -> 58px), 16dp gap
    for row in range(3):
        for col in range(2):
            cx = x + 46 + col * 84
            cy = y + 127 + row * 75
            dot(img, cx, cy, 29, SWATCHES[row * 2 + col])

    # segmented hue strip (6 steps of 60 degrees)
    sx, sy, sw, sh = x + 16, y + 315, 144, 45
    seg = sw / 6
    strip = [SWATCHES[2][1], SWATCHES[2][0], (0, 255, 0), (0, 255, 255), (0, 0, 255), (255, 0, 255)]
    hue_colors = [(255, 0, 0), (255, 255, 0), (0, 255, 0), (0, 255, 255), (0, 0, 255), (255, 0, 255)]
    for i, c in enumerate(hue_colors):
        bx = (sx + i * seg, sy, sx + (i + 1) * seg - 2, sy + sh)
        rr_box = (int(bx[0]), int(bx[1]), int(bx[2]), int(bx[3]))
        d.rounded_rectangle(rr_box, radius=6, fill=c)

    text(d, (x + 16, y + 372), "已开灯", f["tiny"], MUTED_70)


def main():
    f = {
        "title": font(26),
        "h": font(17),
        "bold": font(17),
        "label": font(15),
        "tiny": font(13),
    }

    W, H = 980, 580
    img = Image.new("RGBA", (W, H), (6, 5, 14, 255))
    vgrad(img, (0, 0, W, H), INK_INDIGO, SLATE_950)

    # ambient orbs so the cards sit on something
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for (cx, cy, r, c, a) in ((90, 40, 240, VIOLET_500, 90), (880, 90, 220, FUCHSIA_500, 80),
                              (520, 580, 260, CYAN_500, 60)):
        ld.ellipse((cx - r, cy - r, cx + r, cy + r), fill=c + (a,))
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(50)))

    d = ImageDraw.Draw(img)
    text(d, (36, 22), "氛围灯 · 桌面卡片（App Widget）", f["title"], WHITE)
    text(d, (36, 54), "所有控件后台直连 FF:FF:10:98:3C:D9，不唤起 App；只有点卡片空白处才打开 App",
         f["tiny"], MUTED_55)

    draw_wide(img, 36, 104, f, None)
    draw_small(img, 470, 104, f, None)
    d = ImageDraw.Draw(img)
    text(d, (36, 176), "4×1  开关 + 六色", f["tiny"], (255, 193, 7))
    text(d, (470, 176), "2×1  只有开关", f["tiny"], (255, 193, 7))

    draw_big(img, 36, 206, f, None)
    draw_square(img, 470, 206, f, None)
    d = ImageDraw.Draw(img)
    text(d, (36, 390), "4×2  完整卡片（含打开按钮）", f["tiny"], (255, 193, 7))
    text(d, (470, 390), "2×2  3×2 色块方卡", f["tiny"], (255, 193, 7))

    draw_tall(img, 744, 104, f, None)
    d = ImageDraw.Draw(img)
    text(d, (744, 512), "2×4  竖版（新增）", f["tiny"], (255, 193, 7))
    text(d, (36, 424), "2×4 竖卡为 CarPlay 风格：圆形开关 + 2×3 色块 + 分段色带（RemoteViews 不支持拖拽滑块）",
         f["tiny"], MUTED_55)

    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "docs", "widget-mockup.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.convert("RGB").save(out)
    print("written", out, img.size)


if __name__ == "__main__":
    main()
