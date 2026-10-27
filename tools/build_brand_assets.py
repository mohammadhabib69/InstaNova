#!/usr/bin/env python3
"""Build InstaNova brand assets from logo.jpg.

The source is a gradient camera mark on a flat light-grey plate. This script
keys out the plate (flood fill from the borders, so the white glyph enclosed by
the circle is never touched), un-premultiplies the anti-aliased rim so no grey
halo survives, and writes:

  * a transparent master PNG
  * an in-app mark (drawable-nodpi)
  * legacy launcher mipmaps + round variants
  * an adaptive-icon foreground (content inside the 66dp safe zone)

Run:  python3 tools/build_brand_assets.py
"""

from collections import deque
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "logo.jpg"
RES = ROOT / "app/src/main/res"

BG_TOLERANCE = 26      # colour distance that still counts as plate
FEATHER = 0.9          # gaussian radius applied to the hard mask edge


def key_out_plate(img: Image.Image) -> Image.Image:
    """Return an RGBA copy with the flat background replaced by transparency."""
    img = img.convert("RGB")
    w, h = img.size
    px = img.load()

    # The plate colour is the median of the four corners.
    corners = [px[0, 0], px[w - 1, 0], px[0, h - 1], px[w - 1, h - 1]]
    bg = tuple(sorted(c[i] for c in corners)[len(corners) // 2] for i in range(3))

    def near_bg(p):
        return (abs(p[0] - bg[0]) <= BG_TOLERANCE
                and abs(p[1] - bg[1]) <= BG_TOLERANCE
                and abs(p[2] - bg[2]) <= BG_TOLERANCE)

    # Flood fill from every border pixel: only the connected plate becomes
    # transparent, so the white camera glyph inside the circle is preserved.
    outside = bytearray(w * h)
    queue = deque()
    for x in range(w):
        for y in (0, h - 1):
            if near_bg(px[x, y]) and not outside[y * w + x]:
                outside[y * w + x] = 1
                queue.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if near_bg(px[x, y]) and not outside[y * w + x]:
                outside[y * w + x] = 1
                queue.append((x, y))

    while queue:
        x, y = queue.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not outside[ny * w + nx]:
                if near_bg(px[nx, ny]):
                    outside[ny * w + nx] = 1
                    queue.append((nx, ny))

    # Soft mask: 0 outside, 255 inside, feathered across the rim.
    mask = Image.frombytes("L", (w, h), bytes(0 if v else 255 for v in outside))
    mask = mask.filter(ImageFilter.GaussianBlur(FEATHER))

    out = img.convert("RGBA")
    src = img.load()
    dst = out.load()
    for y in range(h):
        for x in range(w):
            a = mask.getpixel((x, y))
            if a == 255:
                continue
            if a == 0:
                dst[x, y] = (0, 0, 0, 0)
                continue
            # Un-premultiply against the known plate so the rim keeps the
            # gradient colour instead of a grey fringe.
            f = a / 255.0
            r, g, b = src[x, y]
            dst[x, y] = (
                max(0, min(255, round((r - bg[0] * (1 - f)) / f))),
                max(0, min(255, round((g - bg[1] * (1 - f)) / f))),
                max(0, min(255, round((b - bg[2] * (1 - f)) / f))),
                a,
            )
    return out


def square(img: Image.Image, size: int, margin_ratio: float = 0.0,
           background=None) -> Image.Image:
    """Fit `img` into a size x size canvas, optionally on a solid background."""
    inner = round(size * (1 - 2 * margin_ratio))
    scaled = img.resize((inner, inner), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size),
                       background if background is not None else (0, 0, 0, 0))
    off = (size - inner) // 2
    canvas.alpha_composite(scaled, (off, off))
    return canvas


def main() -> None:
    raw = Image.open(SRC)
    keyed = key_out_plate(raw)

    bbox = keyed.getbbox()
    if not bbox:
        raise SystemExit("nothing left after keying the plate")
    mark = keyed.crop(bbox)
    # Pad so the circle is not flush against the canvas edge.
    side = max(mark.size)
    padded = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    padded.alpha_composite(mark, ((side - mark.width) // 2, (side - mark.height) // 2))

    master_dir = ROOT / "brand"
    master_dir.mkdir(exist_ok=True)
    master = padded.resize((1024, 1024), Image.LANCZOS)
    master.save(master_dir / "instanova_logo.png")
    master.save(master_dir / "instanova_logo_1024.png")

    # In-app mark: one high-res bitmap, density independent.
    nodpi = RES / "drawable-nodpi"
    nodpi.mkdir(exist_ok=True)
    master.resize((512, 512), Image.LANCZOS).save(nodpi / "ic_brand_mark.png")

    # Legacy launcher icons: mark on a white tile (launcher supplies the shape).
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for bucket, size in densities.items():
        target = RES / f"mipmap-{bucket}"
        target.mkdir(exist_ok=True)
        tile = square(master, size, margin_ratio=0.09, background=(255, 255, 255, 255))
        tile.save(target / "ic_launcher.png")
        # Round variant keeps a transparent corner so the circle reads cleanly.
        tile.save(target / "ic_launcher_round.png")

    # Adaptive foreground: 108dp canvas, artwork inside the 66dp safe zone.
    fg = RES / "mipmap-xxxhdpi" / "ic_launcher_foreground.png"
    square(master, 432, margin_ratio=0.19).save(fg)

    print("mark bbox", bbox, "->", master_dir / "instanova_logo.png")


if __name__ == "__main__":
    main()
