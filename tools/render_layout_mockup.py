"""
render_layout_mockup.py — draws a schematic of the three adaptive layouts at their
real aspect ratios so the layout density can be reviewed without a device.

Not a screenshot: boxes/typography are approximations of the Compose tree.

    python tools/render_layout_mockup.py            # writes docs/layout-mockup.png
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

BG = (10, 11, 15)
PANEL_BG = (14, 15, 20)
SURFACE = (23, 26, 34)
SURFACE_HIGH = (35, 39, 53)
PRIMARY = (79, 195, 247)
ACCENT = (255, 193, 7)
ON = (230, 232, 239)
MUTED = (154, 163, 178)
OK = (102, 187, 106)
OUTLINE = (52, 58, 74)

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhl.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\arial.ttf",
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


# --------------------------------------------------------------------- pieces
def status_strip(draw, x, y, w, h, f, label):
    rr(draw, (x, y, x + w, y + h), SURFACE, radius=0)
    cy = y + h / 2
    draw.ellipse((x + 12, cy - 5, x + 22, cy + 4), fill=OK)
    text(draw, (x + 30, cy), label, f["small"], OK, anchor="lm")
    text(draw, (x + w - 14, cy), "断开", f["small"], PRIMARY, anchor="rm")


def tab_row(draw, x, y, w, h, f, labels, active=0):
    rr(draw, (x, y, x + w, y + h), SURFACE, radius=0)
    gap = 6
    pad = 8
    cell = (w - pad * 2 - gap * (len(labels) - 1)) / len(labels)
    for i, lab in enumerate(labels):
        cx = x + pad + i * (cell + gap)
        if i == active:
            rr(draw, (cx, y + 4, cx + cell, y + h - 4), PRIMARY, radius=10)
            text(draw, (cx + cell / 2, y + h / 2), lab, f["small"], (0, 33, 46), anchor="mm")
        else:
            text(draw, (cx + cell / 2, y + h / 2), lab, f["small"], MUTED, anchor="mm")


def bottom_bar(draw, x, y, w, h, f, labels, active=0):
    rr(draw, (x, y, x + w, y + h), SURFACE, radius=0)
    cell = w / len(labels)
    for i, lab in enumerate(labels):
        cx = x + cell * i + cell / 2
        color = PRIMARY if i == active else MUTED
        text(draw, (cx, y + h / 2), lab, f["small"], color, anchor="mm")
        if i == active:
            draw.ellipse((cx - 3, y + h - 12, cx + 3, y + h - 6), fill=PRIMARY)


def rail(draw, x, y, w, h, f, labels, active=0):
    rr(draw, (x, y, x + w, y + h), SURFACE_HIGH, radius=0)
    cell_h = 52
    for i, lab in enumerate(labels):
        cy = y + 10 + i * (cell_h + 6)
        box = (x + 8, cy, x + w - 8, cy + cell_h)
        if i == active:
            rr(draw, box, PRIMARY, radius=10)
            text(draw, ((box[0] + box[2]) / 2, cy + cell_h / 2), lab, f["small"], (0, 33, 46), anchor="mm")
        else:
            text(draw, ((box[0] + box[2]) / 2, cy + cell_h / 2), lab, f["small"], MUTED, anchor="mm")


def wheel(draw, cx, cy, r):
    # schematic hue ring: 12 wedges
    import math

    for i in range(36):
        a0 = math.radians(i * 10)
        a1 = math.radians((i + 1) * 10 + 0.6)
        hue = i * 10
        draw.pieslice(
            (cx - r, cy - r, cx + r, cy + r),
            start=i * 10,
            end=(i + 1) * 10 + 0.6,
            fill=_hsv(hue, 1.0, 1.0),
        )
        _ = (a0, a1)
    rr(draw, (cx - r * 0.58, cy - r * 0.58, cx + r * 0.58, cy + r * 0.58), SURFACE, radius=int(r * 0.58))
    draw.ellipse((cx - r * 0.44, cy - r * 0.44, cx + r * 0.44, cy + r * 0.44), fill=_hsv(200, 1, 1))
    # thumb at hue 200 -> ~200 deg
    t = math.radians(200)
    tx, ty = cx + math.cos(t) * r * 0.76, cy + math.sin(t) * r * 0.76
    draw.ellipse((tx - 7, ty - 7, tx + 7, ty + 7), outline=ON, width=3)


def _hsv(h, s, v):
    import colorsys

    r, g, b = colorsys.hsv_to_rgb(h / 360.0, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


def chips(draw, x, y, labels, f, w, h=22, active=0):
    gap = 6
    for i, lab in enumerate(labels):
        cw = w / len(labels) - gap
        cx = x + i * (cw + gap)
        rr(
            draw,
            (cx, y, cx + cw, y + h),
            PRIMARY if i == active else SURFACE_HIGH,
            radius=7,
        )
        text(
            draw,
            (cx + cw / 2, y + h / 2),
            lab,
            f["tiny"],
            (0, 33, 46) if i == active else ON,
            anchor="mm",
        )


def slider(draw, x, y, w, value, f, label, value_text):
    text(draw, (x, y), label, f["tiny"], ON)
    text(draw, (x + w, y), value_text, f["tiny"], PRIMARY, anchor="ra")
    ty = y + 20
    draw.line((x + 6, ty, x + w - 6, ty), fill=OUTLINE, width=4)
    draw.line((x + 6, ty, x + 6 + (w - 12) * value, ty), fill=PRIMARY, width=4)
    px = x + 6 + (w - 12) * value
    draw.ellipse((px - 7, ty - 7, px + 7, ty + 7), fill=PRIMARY)


def presets(draw, x, y, r, gap=6, n=8):
    colors = [_hsv(0, 1, 1), _hsv(28, 1, 1), _hsv(48, 1, 1), _hsv(145, 1, 1),
              _hsv(193, 1, 1), _hsv(220, 1, 1), _hsv(277, 1, 1), (255, 255, 255)]
    for i in range(n):
        cx = x + r + i * (r * 2 + gap)
        draw.ellipse((cx - r, y, cx + r, y + r * 2), fill=colors[i % len(colors)])


# --------------------------------------------------------------------- panels
def panel_rail(draw, ox, oy, f):
    W, H = 800, 480
    rr(draw, (ox, oy, ox + W, oy + H), PANEL_BG, radius=10)
    status_strip(draw, ox, oy + 10, W, 34, f, "已连接  3F:2A:9C:11:04:8E")
    rail(draw, ox, oy + 44, 88, H - 44, f, ["色彩", "模式", "设备", "关于"], active=0)
    cx0 = ox + 88
    cw = W - 88
    text(draw, (cx0 + 14, oy + 58), "色彩", f["label"], ON)
    text(draw, (cx0 + 14, oy + 78), "分区", f["tiny"], MUTED)
    chips(draw, cx0 + 14, oy + 96, ["分区1", "分区2", "全区"], f, cw / 2 - 30, active=2)

    wheel(draw, cx0 + cw * 0.26, oy + 250, 130)

    rx = cx0 + cw * 0.52
    rw = cw * 0.44
    text(draw, (rx, oy + 100), "预设色", f["tiny"], MUTED)
    presets(draw, rx, oy + 118, 15)
    slider(draw, rx, oy + 172, rw, 0.64, f, "亮度", "64")
    text(draw, (rx, oy + 220), "开关", f["tiny"], MUTED)
    chips(draw, rx, oy + 238, ["开启", "关闭", "跟随"], f, rw, active=0)
    text(draw, (rx, oy + 296), "速度 / 灯珠等见「模式」页", f["tiny"], MUTED)
    text(draw, (ox + 12, oy + H - 16), "800×480 @ mdpi → 800×480dp → Rail", f["tiny"], ACCENT)


def panel_top_tabs(draw, ox, oy, f):
    W, H = 533, 320
    rr(draw, (ox, oy, ox + W, oy + H), PANEL_BG, radius=10)
    status_strip(draw, ox, oy + 10, W, 26, f, "已连接  3F:2A:9C:11:04:8E")
    tab_row(draw, ox, oy + 36, W, 34, f, ["色彩", "模式", "设备", "关于"], active=0)

    top = oy + 74
    # single scrollable column: wheel capped by width, then controls
    wheel(draw, ox + W / 2, top + 88, 82)
    chips(draw, ox + 14, top + 180, ["分区1", "分区2", "全区"], f, W - 28, active=2)
    slider(draw, ox + 14, top + 210, W - 28, 0.64, f, "亮度", "64")
    text(draw, (ox + 12, oy + H - 12), "800×480 @ hdpi → 533×320dp → TopTabs（内容可滚动）", f["tiny"], ACCENT)


def panel_phone(draw, ox, oy, f):
    W, H = 360, 800
    rr(draw, (ox, oy, ox + W, oy + H), PANEL_BG, radius=18)
    status_strip(draw, ox, oy + 12, W, 36, f, "已连接  3F:2A:9C:11:04:8E")

    top = oy + 60
    wheel(draw, ox + W / 2, top + 150, 138)
    y = top + 300
    text(draw, (ox + 16, y), "分区", f["tiny"], MUTED)
    chips(draw, ox + 16, y + 18, ["分区1", "分区2", "全区"], f, W - 32, active=2)
    y += 62
    text(draw, (ox + 16, y), "预设色", f["tiny"], MUTED)
    presets(draw, ox + 16, y + 18, 13, gap=4)
    y += 62
    slider(draw, ox + 16, y, W - 32, 0.64, f, "亮度", "64")
    y += 48
    text(draw, (ox + 16, y), "开关", f["tiny"], MUTED)
    chips(draw, ox + 16, y + 18, ["开启", "关闭", "跟随"], f, W - 32, active=0)

    bottom_bar(draw, ox + 2, oy + H - 62, W - 4, 60, f, ["色彩", "模式", "设备", "关于"], active=0)
    text(draw, (ox + 12, oy + H - 74), "360×800dp → BottomNav", f["tiny"], ACCENT)


# ----------------------------------------------------------------------- main
def main():
    f = {
        "title": load_font(26),
        "label": load_font(17),
        "small": load_font(12),
        "tiny": load_font(10),
    }

    pad = 36
    gap = 34
    widths = [800, 533, 360]
    height = 800
    total_w = pad * 2 + sum(widths) + gap * 2
    total_h = 74 + height + 40

    img = Image.new("RGB", (total_w, total_h), BG)
    d = ImageDraw.Draw(img)

    text(d, (pad, 24), "氛围灯 v2 —— 三套自适应布局（按可用宽高运行时切换）", f["title"], ON)
    text(d, (pad, 56), "示意图，非真机截图；用于评审 800×480 车载屏与手机上的布局密度", f["tiny"], MUTED)

    x = pad
    top = 84
    panel_rail(d, x, top, f)
    x += widths[0] + gap
    panel_top_tabs(d, x, top, f)
    x += widths[1] + gap
    panel_phone(d, x, top, f)

    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "docs", "layout-mockup.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("written", out, img.size)


if __name__ == "__main__":
    main()
