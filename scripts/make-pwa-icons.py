#!/usr/bin/env python3
"""PWA 图标：与「开屏卫士」APK 同款盾牌+闪电，输出到 public/（192/512）"""
import os
from PIL import Image, ImageDraw


def make_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
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

    s = size / 100.0
    shield = [(50, 16), (80, 28), (80, 50), (80, 68), (50, 84), (20, 68), (20, 50), (20, 28)]
    d.polygon([(x * s, y * s) for x, y in shield], fill=(255, 255, 255, 235))

    bolt = [(54, 26), (40, 52), (50, 52), (44, 74), (62, 46), (51, 46), (58, 26)]
    d.polygon([(x * s, y * s) for x, y in bolt], fill=(124, 58, 237, 255))
    return img


out = "/home/z/my-project/public"
os.makedirs(out, exist_ok=True)
make_icon(192).save(os.path.join(out, "icon-192.png"))
make_icon(512).save(os.path.join(out, "icon-512.png"))
print("public/icon-192.png + public/icon-512.png OK")
