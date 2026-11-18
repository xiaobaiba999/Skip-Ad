#!/usr/bin/env python3
"""生成开屏卫士应用图标：紫粉渐变圆角方块 + 白色盾牌与闪电"""
import os
from PIL import Image, ImageDraw

def make_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 圆角背景 + 对角渐变（紫→粉）
    grad = Image.new("RGBA", (size, size))
    gd = ImageDraw.Draw(grad)
    c1, c2 = (124, 58, 237), (217, 70, 239)  # violet-500 → fuchsia-500
    for y in range(size):
        t = y / size
        r = int(c1[0] + (c2[0] - c1[0]) * t)
        g = int(c1[1] + (c2[1] - c1[1]) * t)
        b = int(c1[2] + (c2[2] - c1[2]) * t)
        gd.line([(0, y), (size, y)], fill=(r, g, b, 255))

    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=255)
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)

    s = size / 100.0  # 归一化坐标

    # 白色盾牌
    shield = [
        (50, 16), (80, 28), (80, 50),
        (80, 68), (50, 84), (20, 68),
        (20, 50), (20, 28),
    ]
    pts = [(x * s, y * s) for x, y in shield]
    d.polygon(pts, fill=(255, 255, 255, 235))

    # 盾牌内紫色闪电（从盾牌里"劈"出来）
    bolt = [
        (54, 26), (40, 52), (50, 52),
        (44, 74), (62, 46), (51, 46), (58, 26),
    ]
    bpts = [(x * s, y * s) for x, y in bolt]
    d.polygon(bpts, fill=(124, 58, 237, 255))

    return img

out_root = "/home/z/my-project/android-app/res"
for dpi, px in [("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
                ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)]:
    icon = make_icon(px)
    icon.save(os.path.join(out_root, dpi, "ic_launcher.png"))
    print(f"{dpi}: {px}px OK")

# 同时导出一张512预览图到download供查看
make_icon(512).save("/home/z/my-project/android-app/dist/icon-preview.png")
print("done")
