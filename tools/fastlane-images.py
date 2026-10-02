#!/usr/bin/env python3
"""Builds the fastlane store images (needs Pillow and DejaVu fonts).

1. ./gradlew :app:testDebugUnitTest --tests '*StoreScreenshotTest*'   (renders the screens)
2. python3 tools/fastlane-images.py

Writes icon.png (512x512), featureGraphic.png (1024x500, en-US only) and phoneScreenshots/
(720x1440, 2:1 as IzzyOnDroid and F-Droid require) for every locale. Images are palette-
quantised so the repository and the store listing stay small.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
LOGO = ROOT / "app/src/main/res/drawable-nodpi/logo.png"
SHOTS = ROOT / "app/build/screenshots/store"
META = ROOT / "fastlane/metadata/android"
LOCALES = {"en": "en-US", "tr": "tr", "ru": "ru", "fa": "fa", "ar": "ar"}
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BG = (243, 232, 255)  # launcher icon background
PURPLE = (74, 32, 120)


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").quantize(256, method=Image.Quantize.MEDIANCUT).save(path, optimize=True)


def logo(size: int) -> Image.Image:
    art = Image.open(LOGO).convert("RGBA")
    art = art.crop(art.getbbox())
    k = size / max(art.size)
    # Pixel art: nearest neighbour keeps the edges crisp.
    return art.resize((round(art.width * k), round(art.height * k)), Image.NEAREST)


def icon() -> None:
    img = Image.new("RGBA", (512, 512), BG + (255,))
    art = logo(400)
    img.alpha_composite(art, ((512 - art.width) // 2, (512 - art.height) // 2))
    save(img, META / "en-US/images/icon.png")


def feature() -> None:
    img = Image.new("RGBA", (1024, 500), BG + (255,))
    art = logo(360)
    img.alpha_composite(art, (60, (500 - art.height) // 2))
    d = ImageDraw.Draw(img)
    d.text((470, 150), "DPIMech", font=ImageFont.truetype(FONT, 84), fill=PURPLE)
    lines = ["Opens sites your provider", "blocks with DPI.", "No root. No remote server."]
    f = ImageFont.truetype(FONT_REGULAR, 34)
    for i, line in enumerate(lines):
        d.text((474, 265 + i * 46), line, font=f, fill=(40, 30, 60))
    save(img, META / "en-US/images/featureGraphic.png")


def screenshots() -> None:
    for src, dst in LOCALES.items():
        files = sorted((SHOTS / src).glob("*.png"))
        if not files:
            raise SystemExit(f"no screenshots in {SHOTS / src}; run the StoreScreenshotTest first")
        for f in files:
            img = Image.open(f)
            img = img.resize((720, round(720 * img.height / img.width)), Image.LANCZOS)
            save(img, META / dst / "images/phoneScreenshots" / f.name)


if __name__ == "__main__":
    icon()
    feature()
    screenshots()
    for p in sorted(META.rglob("*.png")):
        print(f"{p.relative_to(ROOT)}  {p.stat().st_size // 1024} KB  {Image.open(p).size}")
