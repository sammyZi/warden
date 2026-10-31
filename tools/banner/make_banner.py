"""Builds docs/banner.png (1280x640, GitHub's social-preview size) and the README screenshots.

Usage: python tools/banner/make_banner.py RAW_DIR
RAW_DIR holds full-size phone screenshots (1080x2400) named home, apps, detail, map, log, data,
trackers, report, settings (.png). Fonts come from the app itself, so the banner matches it.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "core-design/src/main/res/font"
OUT = ROOT / "docs"
STATUS_BAR = 96  # px cropped off the top of each 1080x2400 capture (clock, battery, notifications)

INK = (14, 15, 13)
SURFACE = (28, 31, 30)
OUTLINE = (52, 57, 54)
PAPER = (243, 244, 238)
MUTED = (154, 156, 151)
LIME = (216, 242, 60)
VERMILION = (255, 107, 74)
GRADIENT = [(159, 211, 245), (198, 183, 232), (243, 169, 196), (247, 169, 126)]


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


def gradient(size, colors, vertical=False):
    """Linear gradient through the given stops (left to right, or top to bottom)."""
    w, h = size
    n = h if vertical else w
    strip = Image.new("RGB", (1, n) if vertical else (n, 1))
    for i in range(n):
        t = i / max(1, n - 1) * (len(colors) - 1)
        k = min(int(t), len(colors) - 2)
        f = t - k
        a, b = colors[k], colors[k + 1]
        c = tuple(round(a[j] + (b[j] - a[j]) * f) for j in range(3))
        strip.putpixel((0, i) if vertical else (i, 0), c)
    return strip.resize((w, h))


def gradient_text(canvas, xy, text, fnt):
    box = fnt.getbbox(text)
    w, h = box[2] - box[0], box[3] - box[1]
    mask = Image.new("L", (w, h))
    ImageDraw.Draw(mask).text((-box[0], -box[1]), text, font=fnt, fill=255)
    canvas.paste(gradient((w, h), GRADIENT), xy, mask)


def rounded_mask(size, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return mask


def device(shot, height):
    """
    A screenshot inside a modern phone: thin dark bezel, a metallic edge that catches the light,
    a punch-hole camera, and side buttons. Returns an RGBA image.
    """
    screen = shot.crop((0, STATUS_BAR, shot.width, shot.height))
    sw = round(screen.width * height / screen.height)
    screen = screen.resize((sw, height), Image.LANCZOS).convert("RGBA")
    bezel, edge, button = 11, 3, 5
    w, h = sw + 2 * (bezel + edge), height + 2 * (bezel + edge)
    out = Image.new("RGBA", (w + button, h), (0, 0, 0, 0))

    # Side buttons (power + volume) on the right edge.
    d = ImageDraw.Draw(out)
    for top, length in ((0.20, 0.09), (0.33, 0.14)):
        y0 = round(h * top)
        d.rounded_rectangle((w - 4, y0, w + button, y0 + round(h * length)), 3, fill=(70, 74, 70, 255))

    # Metallic edge: a vertical light-to-dark gradient in a rounded rectangle.
    metal = gradient((w, h), [(120, 124, 118), (58, 61, 57), (30, 32, 30), (84, 88, 83)], vertical=True).convert("RGBA")
    out.paste(metal, (0, 0), rounded_mask((w, h), 52))
    # Black bezel.
    body = Image.new("RGBA", (w - 2 * edge, h - 2 * edge), (12, 12, 12, 255))
    out.paste(body, (edge, edge), rounded_mask(body.size, 49))
    # Screen.
    out.paste(screen, (edge + bezel, edge + bezel), rounded_mask(screen.size, 38))
    # Punch-hole camera with a faint lens ring.
    cx, cy, r = w // 2, edge + bezel + 18, 8
    d = ImageDraw.Draw(out)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(8, 8, 8, 255))
    d.ellipse((cx - 3, cy - 3, cx + 1, cy + 1), fill=(40, 46, 60, 255))
    # A soft diagonal glare across the glass.
    glare = Image.new("L", out.size, 0)
    ImageDraw.Draw(glare).polygon([(0, 0), (w * 0.55, 0), (0, h * 0.42)], fill=26)
    glare = glare.filter(ImageFilter.GaussianBlur(30))
    shine = Image.new("RGBA", out.size, (255, 255, 255, 0))
    shine.putalpha(Image.composite(glare, Image.new("L", out.size, 0), out.split()[-1]))
    return Image.alpha_composite(out, shine)


def place(canvas, img, xy, angle=0.0, shadow=0.6):
    """Paste [img] rotated by [angle] degrees with a soft drop shadow; xy is its centre."""
    if angle:
        img = img.rotate(angle, resample=Image.BICUBIC, expand=True)
    x, y = xy[0] - img.width // 2, xy[1] - img.height // 2
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = img.split()[-1].point(lambda a: int(a * shadow))
    sh.paste((0, 0, 0, 255), (x + 14, y + 28), alpha)
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(26)))
    canvas.alpha_composite(img, (x, y))


def callout(canvas, xy, dot, title, sub):
    """A small floating card: coloured dot, a bold line and a muted one."""
    t, s = font("rubik.ttf", 24), font("rubik.ttf", 17)
    d = ImageDraw.Draw(canvas)
    w = int(max(d.textlength(title, font=t), d.textlength(sub, font=s))) + 70
    h = 70
    card = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cd = ImageDraw.Draw(card)
    cd.rounded_rectangle((0, 0, w - 1, h - 1), 18, fill=SURFACE + (238,), outline=OUTLINE + (255,), width=2)
    cd.ellipse((20, 25, 40, 45), fill=dot + (255,))
    cd.text((54, 12), title, font=t, fill=PAPER)
    cd.text((54, 42), sub, font=s, fill=MUTED)
    place(canvas, card, (xy[0] + w // 2, xy[1] + h // 2), shadow=0.5)


def backdrop(w, h):
    """Ink with the app's dotted-globe texture fading in from the right, and gradient glows."""
    bg = Image.new("RGBA", (w, h), INK + (255,))
    dots = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(dots)
    rnd = random.Random(7)
    cx, cy, R = 980, 330, 420
    for y in range(-R, R, 14):
        for x in range(-R, R, 14):
            if x * x + y * y < R * R and rnd.random() < 0.55:
                fade = 1 - math.hypot(x, y) / R
                d.ellipse((cx + x - 1.6, cy + y - 1.6, cx + x + 1.6, cy + y + 1.6), fill=(255, 255, 255, int(22 * fade)))
    bg.alpha_composite(dots)
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse((760, 120, 1180, 560), fill=LIME + (52,))
    g.ellipse((560, 330, 900, 700), fill=GRADIENT[2] + (34,))
    g.ellipse((1000, -120, 1360, 260), fill=GRADIENT[0] + (34,))
    bg.alpha_composite(glow.filter(ImageFilter.GaussianBlur(110)))
    return bg


def banner(raw):
    """
    Layout follows current app-store practice: the hero phone straight-on and large (readable even as
    a small social preview), running off the bottom edge; one supporting phone behind it; a short,
    big headline; a quiet backdrop so the app's own UI carries the colour.
    """
    W, H = 1280, 640
    canvas = backdrop(W, H)
    shots = {n: Image.open(raw / f"{n}.png").convert("RGB") for n in ("home", "map")}

    # Map behind and to the left, a touch smaller and dimmer; Home in front, cut by the bottom edge.
    back = device(shots["map"], 560)
    dim = Image.new("RGBA", back.size, (14, 15, 13, 70))
    back = Image.alpha_composite(back, Image.composite(dim, Image.new("RGBA", back.size, (0, 0, 0, 0)), back.split()[-1]))
    place(canvas, back, (860, 390), shadow=0.5)
    place(canvas, device(shots["home"], 640), (1065, 430), shadow=0.8)

    callout(canvas, (600, 470), VERMILION, "385 blocked today", "trackers and ads, per app")

    d = ImageDraw.Draw(canvas)
    small = font("rubik.ttf", 20)
    d.rounded_rectangle((72, 76, 72 + d.textlength("ANDROID FIREWALL", font=small) + 32, 112), 18, outline=LIME, width=2)
    d.text((88, 83), "ANDROID FIREWALL", font=small, fill=LIME)
    gradient_text(canvas, (66, 138), "Warden", font("pixelify_sans.ttf", 150))
    d.text((72, 330), "See where your", font=font("rubik.ttf", 44), fill=PAPER)
    d.text((72, 384), "apps really go.", font=font("rubik.ttf", 44), fill=PAPER)
    d.text((74, 450), "Block trackers per app. No root.", font=font("rubik.ttf", 24), fill=MUTED)
    x = 74
    chip = font("rubik.ttf", 19)
    for label in ["On-device", "No accounts", "Open source"]:
        w = d.textlength(label, font=chip) + 30
        d.rounded_rectangle((x, 520, x + w, 558), 19, fill=SURFACE, outline=OUTLINE, width=2)
        d.text((x + 15, 528), label, font=chip, fill=MUTED)
        x += w + 10
    canvas.convert("RGB").save(OUT / "banner.png", optimize=True)


def screenshots(raw):
    out = OUT / "screenshots"
    out.mkdir(exist_ok=True)
    for name in ("home", "apps", "detail", "map", "log", "data", "trackers", "report", "settings"):
        src = raw / f"{name}.png"
        if not src.exists():
            continue
        img = Image.open(src).convert("RGB")
        img = img.crop((0, STATUS_BAR, img.width, img.height))
        img = img.resize((540, round(img.height * 540 / img.width)), Image.LANCZOS)
        img.save(out / f"{name}.png", optimize=True)


if __name__ == "__main__":
    raw_dir = Path(sys.argv[1])
    screenshots(raw_dir)
    banner(raw_dir)
    print("wrote", OUT / "banner.png", "and", OUT / "screenshots")
