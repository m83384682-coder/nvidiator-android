"""Draws Nvidiator's launcher icon: a speech bubble with sound waves on purple. Our own design,
not NVDA's logo. Run once; the PNGs are committed."""
import os
from PIL import Image, ImageDraw, ImageFont

RES = os.path.join(os.path.dirname(__file__), "..", "src", "main", "res")
PURPLE = (0x6C, 0x2D, 0xC7, 255)
DARK = (0x12, 0x12, 0x16, 255)
WHITE = (255, 255, 255, 255)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def foreground(size):
    """Artwork on a transparent square, kept inside the adaptive-icon safe zone (66%)."""
    S = size * 4
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = S / 2
    r = S * 0.24
    # speech bubble
    d.ellipse([c - r - S * 0.06, c - r, c + r - S * 0.06, c + r], fill=WHITE)
    d.polygon([(c - S * 0.20, c + r * 0.55), (c - S * 0.30, c + r * 1.15), (c - S * 0.02, c + r * 0.85)], fill=WHITE)
    f = ImageFont.truetype(FONT, int(S * 0.17))
    d.text((c - S * 0.06, c), "NV", font=f, fill=PURPLE, anchor="mm")
    # sound waves
    for i, rr in enumerate((0.30, 0.37)):
        w = int(S * 0.03)
        box = [c - S * rr, c - S * rr, c + S * rr, c + S * rr]
        d.arc(box, -40, 40, fill=WHITE, width=w)
    return im.resize((size, size), Image.LANCZOS)


def legacy(size):
    S = size * 4
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([S * 0.04, S * 0.04, S * 0.96, S * 0.96], fill=PURPLE)
    fg = foreground(S * 3 // 2).crop((S // 4, S // 4, S // 4 + S, S // 4 + S))
    im.alpha_composite(fg)
    return im.resize((size, size), Image.LANCZOS)


dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
for name, k in dens.items():
    dd = os.path.join(RES, "mipmap-" + name)
    os.makedirs(dd, exist_ok=True)
    legacy(int(48 * k)).save(os.path.join(dd, "ic_launcher.png"))
    foreground(int(108 * k)).save(os.path.join(dd, "ic_launcher_foreground.png"))
any_dir = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(any_dir, exist_ok=True)
with open(os.path.join(any_dir, "ic_launcher.xml"), "w") as fh:
    fh.write('<?xml version="1.0" encoding="utf-8"?>\n'
             '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
             '    <background android:drawable="@color/ic_launcher_background" />\n'
             '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
             '    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />\n'
             '</adaptive-icon>\n')
with open(os.path.join(RES, "values", "colors.xml"), "w") as fh:
    fh.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
             '    <color name="ic_launcher_background">#6C2DC7</color>\n</resources>\n')
legacy(512).save(os.path.join(os.path.dirname(__file__), "..", "icon-512.png"))
print("ok")
