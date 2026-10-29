"""
render_layout_mockup.py — schematic of the app on the two target screens, drawn in
the Modern Gradient style (violet→fuchsia gradients, glass surfaces, glow orbs).

Not a screenshot: shapes/typography approximate the Compose tree.
    python tools/render_layout_mockup.py     # writes docs/layout-mockup.png

Note: every translucent element is rendered onto its own layer and composited with
`Image.alpha_composite`, because ImageDraw writes RGBA pixels verbatim (it does not
blend), which would turn white-with-alpha into solid white.
"""

from __future__ import annotations

import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

# --- palette (mirrors ui/theme/Theme.kt) -----------------------------------
INK_INDIGO = (30, 27, 75)
SLATE_950 = (2, 6, 23)
VIOLET_500 = (139, 92, 246)
VIOLET_600 = (124, 58, 237)
PURPLE_500 = (168, 85, 247)
FUCHSIA_500 = (217, 70, 239)
FUCHSIA_600 = (192, 38, 211)
CYAN_500 = (6, 182, 212)
OK_GREEN = (52, 211, 153)
WHITE = (255, 255, 255)

MUTED_70 = (206, 202, 226)
MUTED_55 = (170, 166, 198)
MUTED_40 = (132, 128, 158)

PRESETS = [
    (255, 214, 170), (255, 241, 224), (255, 255, 255), (238, 245, 255),
    (255, 240, 200), (255, 191, 0), (255, 215, 0), (255, 140, 0),
    (255, 99, 71), (255, 0, 0), (200, 20, 20), (139, 0, 0),
    (255, 20, 147), (255, 105, 180), (255, 182, 193), (199, 21, 133),
    (138, 43, 226), (128, 0, 128), (75, 0, 130), (75, 0, 180),
    (0, 0, 255), (0, 71, 171), (0, 150, 255), (0, 191, 255),
    (0, 255, 255), (64, 224, 208), (120, 240, 190), (0, 255, 0),
    (0, 200, 83), (0, 128, 0), (173, 255, 47), (154, 205, 50),
]

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhl.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def load_font(size: int):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def _ints(box):
    return tuple(int(round(v)) for v in box)


def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def vgrad(img, box, c1, c2):
    x0, y0, x1, y1 = _ints(box)
    h = max(1, y1 - y0)
    d = ImageDraw.Draw(img)
    for y in range(h):
        d.line((x0, y0 + y, x1, y0 + y), fill=lerp(c1, c2, y / (h - 1) if h > 1 else 0))


def composited(img, box, draw_fn):
    """Draw on a transparent layer and alpha-composite it — real blending."""
    x0, y0, x1, y1 = _ints(box)
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer), w, h)
    img.alpha_composite(layer, (x0, y0))


def grad_rounded(img, box, c1, c2, radius):
    """Horizontal c1->c2 gradient clipped to a rounded rect."""
    x0, y0, x1, y1 = _ints(box)
    w, h = max(1, x1 - x0), max(1, y1 - y0)
    grad = Image.new("RGBA", (w, h))
    gd = ImageDraw.Draw(grad)
    for x in range(w):
        gd.line((x, 0, x, h), fill=lerp(c1, c2, x / (w - 1) if w > 1 else 0) + (255,))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    img.paste(grad, (x0, y0), mask)


def glass(img, box, radius, fill_alpha=34, border_alpha=64):
    """backdrop-blur-xl bg-white/10 border border-white/20."""

    def draw(d, w, h):
        d.rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=WHITE + (fill_alpha,))
        d.rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, outline=WHITE + (border_alpha,), width=1)

    composited(img, box, draw)


def glow(img, panel_box, orbs):
    x0, y0, x1, y1 = _ints(panel_box)
    layer = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for (cx, cy, r, color, alpha) in orbs:
        ld.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(46))
    img.alpha_composite(layer, (x0, y0))


def text(d, xy, s, font, fill, anchor="la"):
    d.text(xy, s, font=font, fill=fill, anchor=anchor)


def tabs(img, x, y, w, h, f, active=0):
    labels = ["色彩", "模式", "设备", "关于"]
    glass(img, (x, y, x + w, y + h), radius=16)
    pad, gap = 5, 6
    cell = (w - pad * 2 - gap * 3) / 4
    d = ImageDraw.Draw(img)
    for i, lab in enumerate(labels):
        cx = x + pad + i * (cell + gap)
        box = (cx, y + pad, cx + cell, y + h - pad)
        if i == active:
            grad_rounded(img, box, VIOLET_600, FUCHSIA_600, 12)
            text(d, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), lab, f["tab"], WHITE, anchor="mm")
        else:
            text(d, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), lab, f["tab"], MUTED_55, anchor="mm")


def status_row(img, x, y, w, h, f):
    d = ImageDraw.Draw(img)
    cy = y + h / 2
    d.ellipse((x + 14, cy - 5, x + 24, cy + 5), fill=OK_GREEN)
    text(d, (x + 32, cy), "已连接  3F:2A:9C:11:04:8E", f["small"], OK_GREEN, anchor="lm")
    btn = (x + w - 78, cy - 15, x + w - 12, cy + 15)
    glass(img, btn, radius=14)
    text(d, ((btn[0] + btn[2]) / 2, cy), "断开", f["label"], MUTED_70, anchor="mm")


def swatch(img, cx, cy, d_px, color, selected):
    r = d_px / 2
    if selected:
        side = int(d_px * 2.4)
        layer = Image.new("RGBA", (side, side), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.ellipse((side * 0.30, side * 0.30, side * 0.70, side * 0.70), fill=color + (150,))
        layer = layer.filter(ImageFilter.GaussianBlur(14))
        img.alpha_composite(layer, (int(cx - side / 2), int(cy - side / 2)))

    d = ImageDraw.Draw(img)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color + (255,))

    # soft top-left sheen, clipped to the circle. `paste(mask)` would ignore the
    # layer's own alpha and stamp opaque black, so multiply the alpha by the circle.
    box = _ints((cx - r, cy - r, cx + r, cy + r))
    w, h = box[2] - box[0], box[3] - box[1]
    if w > 0 and h > 0:
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        for k, a in ((0.66, 34), (0.48, 30), (0.30, 24)):
            hr = w * k / 2
            cxs, cys = w * 0.22, h * 0.20
            ld.ellipse((cxs - hr, cys - hr, cxs + hr, cys + hr), fill=WHITE + (a,))
        circle = Image.new("L", (w, h), 0)
        ImageDraw.Draw(circle).ellipse((0, 0, w - 1, h - 1), fill=255)
        rch, gch, bch, ach = layer.split()
        ach = ImageChops.multiply(ach, circle)
        img.alpha_composite(Image.merge("RGBA", (rch, gch, bch, ach)), (box[0], box[1]))

    d = ImageDraw.Draw(img)
    if selected:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=WHITE + (255,), width=3)
        luma = (0.299 * color[0] + 0.587 * color[1] + 0.114 * color[2]) / 255
        marker = (0, 0, 0) if luma > 0.6 else WHITE
        mr = r * 0.24
        d.ellipse((cx - mr, cy - mr, cx + mr, cy + mr), fill=marker + (255,))
    else:
        def ring(sd, ww, hh):
            sd.ellipse((0, 0, ww - 1, hh - 1), outline=WHITE + (52,), width=1)

        composited(img, box, ring)


def swatch_grid(img, x, y, w, h, cols, gap, pad, selected=2):
    rows = len(PRESETS) // cols
    cell_w = (w - pad * 2 - gap * (cols - 1)) / cols
    cell_h = (h - pad - gap * (rows - 1)) / rows
    d_px = min(cell_w, cell_h)
    total_w = cols * d_px + gap * (cols - 1)
    left = x + (w - total_w) / 2
    for r in range(rows):
        for c in range(cols):
            idx = r * cols + c
            cx = left + c * (d_px + gap) + d_px / 2
            cy = y + pad / 2 + r * (d_px + gap) + d_px / 2
            swatch(img, cx, cy, d_px, PRESETS[idx], idx == selected)


def chip(img, box, label, f, active=False, gradient=None):
    d = ImageDraw.Draw(img)
    if active:
        c1, c2 = gradient or (VIOLET_600, FUCHSIA_600)
        grad_rounded(img, box, c1, c2, 16)
    else:
        glass(img, box, radius=16)
    text(d, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label, f["small"],
         WHITE if active else MUTED_70, anchor="mm")


def control_bar(img, x, y, w, h, f, stacked):
    glass(img, (x, y, x + w, y + h), radius=24)
    pad = 12
    inner = x + pad
    iw = w - pad * 2
    row_h = 44
    d = ImageDraw.Draw(img)

    if stacked:
        cw = (iw - 16) / 3
        for i, lab in enumerate(["分区1", "分区2", "全区"]):
            chip(img, (inner + i * (cw + 8), y + 10, inner + i * (cw + 8) + cw, y + 10 + row_h),
                 lab, f, active=(i == 2))
        for i, lab in enumerate(["开启", "关闭", "跟随"]):
            chip(img, (inner + i * (cw + 8), y + 10 + row_h + 6,
                       inner + i * (cw + 8) + cw, y + 10 + row_h * 2 + 6), lab, f, active=(i == 0))
        by = y + 10 + row_h * 2 + 6 + 6
    else:
        labels = ["分区1", "分区2", "全区", "开启", "关闭", "跟随"]
        gap = 8
        cw = (iw - gap * 5 - 12) / 6
        for i, lab in enumerate(labels):
            chip(img, (inner + i * (cw + gap), y + 10, inner + i * (cw + gap) + cw, y + 10 + row_h),
                 lab, f, active=(i == 2 or i == 3))
        by = y + 10 + row_h + 6

    ty = by + 22
    text(d, (inner, ty), "亮度", f["small"], MUTED_55, anchor="lm")
    tx0, tx1 = inner + 52, inner + iw - 46
    px = tx0 + (tx1 - tx0) * 0.64

    def track(sd, tw, th):
        mid = th / 2
        sd.line((0, mid, tw, mid), fill=WHITE + (41,), width=4)

    composited(img, (tx0, ty - 12, tx1, ty + 13), track)
    grad_rounded(img, (tx0, ty - 2, px, ty + 2), VIOLET_500, FUCHSIA_500, 2)
    d.ellipse((px - 8, ty - 8, px + 8, ty + 8), fill=FUCHSIA_500)
    text(d, (inner + iw, ty), "64", f["small"], VIOLET_500, anchor="rm")


def panel(img, ox, oy, W, H, f, caption, wide):
    box = (ox, oy, ox + W, oy + H)
    vgrad(img, box, INK_INDIGO, SLATE_950)
    glow(img, box, [
        (W * 0.10, H * 0.06, W * 0.42, VIOLET_500, 96),
        (W * 0.92, H * 0.18, W * 0.36, FUCHSIA_500, 86),
        (W * 0.60, H * 1.02, W * 0.46, CYAN_500, 70),
    ])

    tabs(img, ox + 10, oy + 5, W - 20, 54, f)
    status_row(img, ox, oy + 64, W, 40, f)

    control_h = 120 if wide else 172
    grid_top = oy + 104
    grid_h = H - 104 - control_h
    swatch_grid(img, ox, grid_top, W, grid_h, 8 if wide else 4, 10 if wide else 8, 10)

    control_bar(img, ox + 8, oy + H - control_h, W - 16, control_h - 8, f, stacked=not wide)
    text(ImageDraw.Draw(img), (ox + 12, oy + H + 8), caption, f["tiny"], (255, 193, 7))


def main():
    f = {
        "title": load_font(26),
        "tab": load_font(17),
        "label": load_font(15),
        "small": load_font(12),
        "tiny": load_font(11),
    }

    pad, gap = 36, 40
    w1, h1 = 800, 480
    w2, h2 = 360, 800
    total_w = pad * 2 + w1 + gap + w2
    total_h = 84 + max(h1, h2) + 48

    img = Image.new("RGBA", (total_w, total_h), (6, 5, 14, 255))
    d = ImageDraw.Draw(img)

    text(d, (pad, 24), "氛围灯 v2.2 —— 现代渐变风（Modern Gradient）", f["title"], WHITE)
    text(d, (pad, 56), "渐变光晕背景 + 玻璃质感顶栏/控制条 + 32 色预设；主渐变 violet→fuchsia", f["tiny"], MUTED_55)

    panel(img, pad, 84, w1, h1, f, "车机  800×480dp（8 列 × 4 行）", wide=True)
    panel(img, pad + w1 + gap, 84, w2, h2, f, "手机 360×800dp（4 列 × 8 行）", wide=False)

    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "docs", "layout-mockup.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.convert("RGB").save(out)
    print("written", out, img.size)


if __name__ == "__main__":
    main()
