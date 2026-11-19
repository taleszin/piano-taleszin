# Gera o ícone do Piano (teclas sobre círculo laranja da Taleszin OS).
import os, sys
from PIL import Image, ImageDraw
base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res")
for nome, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    s = 4 * px
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((s*0.04, s*0.04, s*0.96, s*0.96), fill=(255, 120, 30, 255))
    x0, x1, y0, y1 = s*0.22, s*0.78, s*0.30, s*0.72
    w = (x1 - x0) / 4
    d.rounded_rectangle((x0, y0, x1, y1), radius=s*0.03, fill=(255, 255, 255, 255))
    for i in range(1, 4):
        d.line((x0 + i*w, y0, x0 + i*w, y1), fill=(40, 40, 40, 255), width=max(2, s//90))
    for i in (1, 2):  # duas teclas pretas (Dó# e Ré#)
        cx = x0 + i*w
        d.rounded_rectangle((cx - w*0.32, y0, cx + w*0.32, y0 + (y1-y0)*0.6), radius=s*0.01, fill=(30, 30, 30, 255))
    out = os.path.join(base, "mipmap-" + nome)
    os.makedirs(out, exist_ok=True)
    im.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))
