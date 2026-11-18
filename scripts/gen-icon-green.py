#!/usr/bin/env python3
"""v3.5.0 全新绿色 Logo：翡翠渐变盾牌 + 白色闪电（重构设计）
输出：APK 五密度 mipmap + Web PWA 图标(192/512) + favicon(64)
设计：对角渐变 #34d399 → #059669（emerald 亮→深），盾牌饱满对称，
     白色闪电居中带轻微内阴影感，边缘高光弧提升立体感。
"""
import os
from PIL import Image, ImageDraw, ImageFilter

OUT_APK = "/home/z/my-project/android-app/res/mipmap-{d}/ic_launcher.png"
OUT_WEB = "/home/z/my-project/public"

def diag_gradient(size, c1, c2):
    """对角线渐变（左上→右下）"""
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * size - 2)
            r = int(c1[0] + (c2[0] - c1[0]) * t)
            g = int(c1[1] + (c2[1] - c1[1]) * t)
            b = int(c1[2] + (c2[2] - c1[2]) * t)
            px[x, y] = (r, g, b, 255)
    return img

def make_icon(size, mask_radius_ratio=0.225):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    # 对角渐变底：emerald-400 → emerald-600（绿色主视觉）
    grad = diag_gradient(size, (52, 211, 153), (5, 150, 105))

    # 圆角遮罩
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * mask_radius_ratio), fill=255)
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)

    s = size / 100.0  # 归一化坐标（0~100）

    # ── 白色盾牌（饱满对称，底部尖角） ──
    shield = [
        (50, 10),              # 顶点
        (83, 22),              # 右肩
        (83, 48),              # 右腰
        (79, 68),              # 右下收
        (50, 90),              # 底尖
        (21, 68),              # 左下收
        (17, 48),              # 左腰
        (17, 22),              # 左肩
    ]
    pts = [(x * s, y * s) for x, y in shield]
    d.polygon(pts, fill=(255, 255, 255, 255))

    # 盾牌内绿色闪电（用背景渐变色调挖空，形成「劈开盾牌」的效果）
    bolt = [
        (55, 22), (38, 52), (48, 52),
        (42, 78), (63, 45), (52, 45),
        (58, 22),
    ]
    bpts = [(x * s, y * s) for x, y in bolt]
    # 闪电用深一号的绿（emerald-700），比挖空更有层次
    d.polygon(bpts, fill=(4, 120, 87, 255))

    # 顶部高光弧（立体感）
    hl = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.arc([int(6 * s), int(4 * s), int(94 * s), int(92 * s)], start=205, end=335,
           fill=(255, 255, 255, 90), width=max(1, int(2.5 * s)))
    hl = hl.filter(ImageFilter.GaussianBlur(radius=size * 0.008))
    img = Image.alpha_composite(img, hl)

    # 重新套圆角（高光可能溢出）
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out

# ── APK 五密度 ──
for dpi, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    path = OUT_APK.format(d=dpi)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    make_icon(px).save(path)
    print(f"APK {dpi}: {px}px → {path}")

# ── Web PWA 图标（maskable 留 10% 安全边） ──
for px in [192, 512]:
    icon = make_icon(px, mask_radius_ratio=0.19)
    icon.save(f"{OUT_WEB}/icon-{px}.png")
    print(f"WEB icon-{px}.png")

# maskable 版（内容缩到 80%，全出血底）
for px in [192, 512]:
    full = Image.new("RGBA", (px, px), (5, 150, 105, 255))  # 全出血 emerald-600
    inner = make_icon(int(px * 0.78), mask_radius_ratio=0.30)
    off = int(px * 0.11)
    full.paste(inner, (off, off), inner)
    full.save(f"{OUT_WEB}/icon-{px}-maskable.png")
    print(f"WEB icon-{px}-maskable.png")

# ── favicon 64 ──
make_icon(64).save(f"{OUT_WEB}/favicon.png")
print("WEB favicon.png")
print("ALL ICONS DONE")
