"""
render_layout_mockup.py — schematic of the current UI on the two target screens.

Not a screenshot: boxes/typography approximate the Compose tree; used to review
density and proportions (800x480 car head unit vs. a phone).

    python tools/render_layout_mockup.py     # writes docs/layout-mockup.png
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

BG = (10, 11, 15)
PANEL_BG = (14, 15, 20)
SURFACE = (23, 26, 34)
SURFACE_HIGH = (35, 39, 53)
PRIMARY = (79, 195, 247)
ON_PRIMARY = (0, 33, 46)
ON = (230, 232, 239)
MUTED = (154, 163, 178)
OK = (102, 187, 106)
OUTLINE = (52, 58, 74)

# must mirror ui/Presets.kt
PRESETS = [
    # row 1
    (255, 214, 170), (255, 241, 224), (255, 255, 255), (238, 245, 255),
    (255, 240, 200), (255, 191, 0), (255, 215, 0), (255, 140, 0),
    # row 2
    (255, 99, 71), (255, 0, 0), (200, 20, 20), (139, 0, 0),
    (255, 20, 147), (255, 105, 180), (255, 182, 193), (199, 21, 133),
    # row 3
    (138, 43, 226), (128, 0, 128), (75, 0, 130), (75, 0, 180),
    (0, 0, 255), (0, 71, 171), (0, 150, 255), (0, 191, 255),
    # row 4
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


def rr(draw, box, fill, radius=8, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def text(draw, xy, s, font, fill, anchor="la"):
    draw.text(xy, s, font=font, fill=fill, anchor=anchor)


def tabs(draw, x, y, w, h, f, active=0):
    labels = ["色彩", "模式", "设备", "关于"]
    rr(draw, (x, y, x + w, y + h), SURFACE, radius=0)
    pad, gap = 8, 8
    cell = (w - pad * 2 - gap * 3) / 4
    for i, lab in enumerate(labels):
        cx = x + pad + i * (cell + gap)
        box = (cx, y + 7, cx + cell, y + h - 7)
        if i == active:
            rr(draw, box, PRIMARY, radius=12)
            text(draw, ((box[0] + box[2]) / 2, y + h / 2), lab, f["tab"], ON_PRIMARY, anchor="mm")
        else:
            text(draw, ((box[0] + box[2]) / 2, y + h / 2), lab, f["tab"], MUTED, anchor="mm")


def status(draw, x, y, w, h, f):
    rr(draw, (x, y, x + w, y + h), PANEL_BG, radius=0)
    cy = y + h / 2
    draw.ellipse((x + 12, cy - 5, x + 22, cy + 5), fill=OK)
    text(draw, (x + 30, cy), "已连接  3F:2A:9C:11:04:8E", f["small"], OK, anchor="lm")
    text(draw, (x + w - 14, cy), "断开", f["label"], PRIMARY, anchor="rm")


def swatch_grid(draw, x, y, w, h, cols, gap, pad, f, selected=2):
    rows = len(PRESETS) // cols
    cell_w = (w - pad * 2 - gap * (cols - 1)) / cols
    cell_h = (h - pad - gap * (rows - 1)) / rows
    d = min(cell_w, cell_h)
    total_w = cols * d + gap * (cols - 1)
    left = x + (w - total_w) / 2
    for r in range(rows):
        for c in range(cols):
            idx = r * cols + c
            cx = left + c * (d + gap) + d / 2
            cy = y + pad / 2 + r * (d + gap) + d / 2
            color = PRESETS[idx]
            draw.ellipse((cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2), fill=color)
            if idx == selected:
                luma = (0.299 * color[0] + 0.587 * color[1] + 0.114 * color[2]) / 255
                marker = (0, 0, 0) if luma > 0.6 else (255, 255, 255)
                draw.ellipse((cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2), outline=marker, width=3)
                rr(draw, (cx - d * 0.13, cy - d * 0.13, cx + d * 0.13, cy + d * 0.13), marker, radius=int(d * 0.13))


def chip(draw, box, label, f, active=False):
    rr(draw, box, PRIMARY if active else SURFACE_HIGH, radius=10)
    text(draw, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label, f["small"],
         ON_PRIMARY if active else ON, anchor="mm")


def control_bar(draw, x, y, w, h, f, stacked=False):
    rr(draw, (x, y, x + w, y + h), SURFACE, radius=0)
    pad = 12
    inner = x + pad
    iw = w - pad * 2
    if stacked:
        row_h = 48
        zone = ["分区1", "分区2", "全区"]
        cw = (iw - 8 * 2) / 3
        for i, lab in enumerate(zone):
            chip(draw, (inner + i * (cw + 8), y + 8, inner + i * (cw + 8) + cw, y + 8 + row_h), lab, f, active=(i == 2))
        power = ["开启", "关闭", "跟随"]
        for i, lab in enumerate(power):
            chip(draw, (inner + i * (cw + 8), y + 8 + row_h + 6, inner + i * (cw + 8) + cw, y + 8 + row_h * 2 + 6), lab, f, active=(i == 0))
        by = y + 8 + row_h * 2 + 6 + 6
    else:
        zone = ["分区1", "分区2", "全区"]
        power = ["开启", "关闭", "跟随"]
        n = 6
        gap = 8
        cw = (iw - gap * (n - 1) - 12) / n
        labels = zone + power
        for i, lab in enumerate(labels):
            active = (i == 2) or (i == 3)
            chip(draw, (inner + i * (cw + gap), y + 8, inner + i * (cw + gap) + cw, y + 8 + 48), lab, f, active=active)
        by = y + 8 + 48 + 6

    text(draw, (inner, by + 22), "亮度", f["small"], MUTED, anchor="lm")
    tx0, tx1 = inner + 52, inner + iw - 46
    ty = by + 22
    draw.line((tx0, ty, tx1, ty), fill=OUTLINE, width=4)
    draw.line((tx0, ty, tx0 + (tx1 - tx0) * 0.64, ty), fill=PRIMARY, width=4)
    px = tx0 + (tx1 - tx0) * 0.64
    draw.ellipse((px - 8, ty - 8, px + 8, ty + 8), fill=PRIMARY)
    text(draw, (inner + iw, ty), "64", f["small"], PRIMARY, anchor="rm")


def panel(draw, ox, oy, W, H, f, caption, wide):
    rr(draw, (ox, oy, ox + W, oy + H), PANEL_BG, radius=10)
    top = oy + 10
    tabs(draw, ox, top, W, 58, f)
    status(draw, ox, top + 58, W, 40, f)
    control_h = 116 if wide else 168
    grid_top = top + 98
    grid_h = H - 98 - control_h
    cols = 8 if wide else 4
    swatch_grid(draw, ox, grid_top, W, grid_h, cols, 10 if wide else 8, 10, f,
                selected=2)
    control_bar(draw, ox, oy + H - control_h, W, control_h, f, stacked=not wide)
    text(draw, (ox + 12, oy + H + 6), caption, f["tiny"], (255, 193, 7))


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
    total_h = 74 + max(h1, h2) + 44

    img = Image.new("RGB", (total_w, total_h), BG)
    d = ImageDraw.Draw(img)

    text(d, (pad, 22), "氛围灯 v2 —— 统一顶栏布局（车内 800×480 与手机）", f["title"], ON)
    text(d, (pad, 54), "示意图，非真机截图：顶部菜单 + 32 色预设网格 + 底部控制条，无圆形色盘", f["tiny"], MUTED)

    panel(d, pad, 84, w1, h1, f, "车机  800×480dp（8 列 × 4 行）", wide=True)
    panel(d, pad + w1 + gap, 84, w2, h2, f, "手机 360×800dp（4 列 × 8 行）", wide=False)

    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       "docs", "layout-mockup.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("written", out, img.size)


if __name__ == "__main__":
    main()
