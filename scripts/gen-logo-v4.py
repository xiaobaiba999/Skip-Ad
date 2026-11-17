#!/usr/bin/env python3
"""v4.0.0 全新人物 Logo「跳跳侠」：翡翠渐变底 + 白色冲刺小人
输出：APK 五密度 mipmap + Web PWA 图标(192/512) + favicon(64) + maskable
设计：圆脑袋白风镜小人向前冲刺，披风飞扬，三道速度线——
      「广告露头就被跳走」的具象化。
"""
import os
from PIL import Image, ImageDraw

REPO = "/home/z/skip-ad-repo"
OUT_APK = REPO + "/android-app/res/mipmap-{d}/ic_launcher.png"
OUT_WEB = REPO + "/public"

EMERALD_LIGHT = (52, 211, 153)    # #34d399
EMERALD_DEEP = (4, 120, 87)       # #047857
WHITE = (255, 255, 255, 255)
MINT = (209, 250, 229, 255)       # #d1fae5 披风
LENS = (6, 78, 59, 255)           # #064e3b 风镜
SPEED = (167, 243, 208, 230)      # #a7f3d0 速度线
LENS_DOT = (52, 211, 153, 255)


def diag_gradient(size, c1, c2):
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


def rounded_line(d, p1, p2, width, fill):
    """圆头粗线：线段 + 两端圆"""
    d.line([p1, p2], fill=fill, width=width)
    r = width // 2
    d.ellipse([p1[0] - r, p1[1] - r, p1[0] + r, p1[1] + r], fill=fill)
    d.ellipse([p2[0] - r, p2[1] - r, p2[0] + r, p2[1] + r], fill=fill)


def bezier(p0, p1, p2, n=16):
    """二次贝塞尔采样"""
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        pts.append((x, y))
    return pts


def draw_mascot(layer, S):
    """在透明层上画跳跳侠（S=画布边长，几何基于 96 视口 ×S/96）
    v2：整体上移收进安全区；披风贝塞尔平滑；风镜居中；速度线错落"""
    d = ImageDraw.Draw(layer)
    k = S / 96.0
    dy = 2.0  # 整体下移：减少头顶留白

    def P(x, y):
        return (x * k, (y + dy) * k)

    # 三道速度线（左侧，长短错落有节奏）
    for (x1, y, x2) in [(6, 30, 22), (3, 44, 21), (8, 58, 18)]:
        rounded_line(d, P(x1, y), P(x2, y), int(5 * k), SPEED)

    # 披风（身后，浅薄荷，平滑贝塞尔轮廓，加长飘动感）
    cape = []
    cape += bezier(P(47, 38), P(26, 54), P(26, 76))       # 肩 → 外侧长弧 → 下摆尖
    cape += bezier(P(26, 76), P(35, 74), P(44, 68))       # 下摆尖 → 内收弧
    cape += [P(52, 64), P(48, 40)]                         # 内肩闭合（身体覆盖衔接处）
    d.polygon(cape, fill=MINT)

    # 后腿 / 前腿（蹬跳姿态）
    rounded_line(d, P(58, 70), P(48, 81), int(9 * k), WHITE)
    rounded_line(d, P(66, 68), P(77, 77), int(9 * k), WHITE)

    # 身体（前倾胶囊：粗圆头线 + 旋转 8°）
    body = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bd = ImageDraw.Draw(body)
    rounded_line(bd, P(54, 42), P(57, 76), int(23 * k), WHITE)
    body = body.rotate(-8, resample=Image.BICUBIC, center=P(56, 58))
    layer.alpha_composite(body)

    # 前臂（水平前伸——冲刺语义明确）
    rounded_line(d, P(65, 50), P(83, 48), int(8 * k), WHITE)

    # 脑袋（大白圆）+ 风镜（居中）
    d = ImageDraw.Draw(layer)
    cx, cy = P(60, 21)
    head_r = 15.5 * k
    d.ellipse([cx - head_r, cy - head_r, cx + head_r, cy + head_r], fill=WHITE)
    vw, vh = 23.0 * k, 8.5 * k
    d.rounded_rectangle([cx - vw / 2, cy - vh / 2 - 1.5 * k, cx + vw / 2, cy + vh / 2 - 1.5 * k],
                        radius=int(4.2 * k), fill=LENS)
    # 瞳孔看向前方（右侧=前进方向）
    lx = cx + 3.5 * k
    ly = cy - 1.5 * k
    dot = 2.0 * k
    d.ellipse([lx - dot, ly - dot, lx + dot, ly + dot], fill=LENS_DOT)


def make_icon(size, maskable=False, radius_ratio=0.225):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    grad = diag_gradient(size, EMERALD_LIGHT, EMERALD_DEEP)

    if maskable:
        # maskable：满幅方底（系统自己裁圆形/圆角），小人以 86% 尺寸绘制后居中贴入
        img.paste(grad, (0, 0))
        inner_s = int(size * 0.86)
        layer = Image.new("RGBA", (inner_s, inner_s), (0, 0, 0, 0))
        draw_mascot(layer, inner_s)
        off = (size - inner_s) // 2
        img.alpha_composite(layer, (off, off))
        return img

    # 普通图标：圆角方底
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255)
    img.paste(grad, (0, 0), mask)
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw_mascot(layer, size)
    img.alpha_composite(layer)
    return img


def main():
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dpi, s in sizes.items():
        p = OUT_APK.format(d=dpi)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        make_icon(s).save(p)
        print("OK", p)

    if os.path.isdir(OUT_WEB):
        make_icon(192).save(OUT_WEB + "/icon-192.png")
        make_icon(512).save(OUT_WEB + "/icon-512.png")
        make_icon(64).save(OUT_WEB + "/favicon.png")
        make_icon(192, maskable=True).save(OUT_WEB + "/icon-192-maskable.png")
        make_icon(512, maskable=True).save(OUT_WEB + "/icon-512-maskable.png")
        make_icon(512).save(OUT_WEB + "/../android-app/dist/icon-preview.png")
        print("OK web icons")

    # 预览图（并排：普通 + maskable）
    prev = Image.new("RGBA", (1080, 560), (242, 247, 243, 255))
    prev.paste(make_icon(480), (20, 40), make_icon(480))
    m = make_icon(480, maskable=True)
    prev.paste(m, (560, 40), m)
    prev.convert("RGB").save(REPO + "/android-app/dist/logo-preview.png")
    print("OK preview")


if __name__ == "__main__":
    main()
