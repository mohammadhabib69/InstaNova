import os
import math
from PIL import Image, ImageDraw

def render_icon(size=512, is_round=False):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # 1. Base linear gradient: #0B1020 -> #4C1D95 (top-left to bottom-right)
    # 0B1020: (11, 16, 32), 4C1D95: (76, 29, 149)
    bg = Image.new("RGBA", (size, size))
    bg_draw = ImageDraw.Draw(bg)
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2.0 * size)
            r = int(11 + t * (76 - 11))
            g = int(16 + t * (29 - 16))
            b = int(32 + t * (149 - 32))
            # Radial cyan glow in center
            # Cyan: 34, 211, 238
            dx = (x - size / 2.0) / (size / 2.0)
            dy = (y - size / 2.0) / (size / 2.0)
            dist = math.sqrt(dx * dx + dy * dy)
            if dist < 0.8:
                glow = (1.0 - dist / 0.8) * 0.35
                r = min(255, int(r * (1 - glow) + 34 * glow))
                g = min(255, int(g * (1 - glow) + 211 * glow))
                b = min(255, int(b * (1 - glow) + 238 * glow))
            bg.putpixel((x, y), (r, g, b, 255))
            
    # Draw orbit and star on high-res overlay
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ov_draw = ImageDraw.Draw(overlay)
    
    # Orbit: ellipse rotated by -25 degrees, centered at size/2, size/2
    # Major radius: 0.28 * size, minor radius: 0.11 * size
    orbit_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    orbit_draw = ImageDraw.Draw(orbit_img)
    cx, cy = size / 2.0, size / 2.0
    rx, ry = 0.26 * size, 0.11 * size
    stroke_w = max(2, int(size * 0.015))
    
    # Draw high-res ellipse points
    points = []
    num_pts = 360
    for i in range(num_pts):
        th = 2.0 * math.pi * i / num_pts
        ex = cx + rx * math.cos(th)
        ey = cy + ry * math.sin(th)
        points.append((ex, ey))
    orbit_draw.line(points + [points[0]], fill=(255, 255, 255, 255), width=stroke_w, joint="curve")
    
    # Orbit dot: at angle 0 (right rim)
    dot_r = max(3, int(size * 0.022))
    dot_x = cx + rx
    dot_y = cy
    orbit_draw.ellipse([dot_x - dot_r, dot_y - dot_r, dot_x + dot_r, dot_y + dot_r], fill=(255, 255, 255, 255))
    
    # Rotate orbit by -25 deg around center
    rotated_orbit = orbit_img.rotate(25, resample=Image.Resampling.BICUBIC, center=(cx, cy))
    bg.paste(rotated_orbit, (0, 0), rotated_orbit)
    
    # 4-point concave star in center
    # M12 2 C12.6 7.4 16.6 11.4 22 12 C16.6 12.6 12.6 16.6 12 22 C11.4 16.6 7.4 12.6 2 12 C7.4 11.4 11.4 7.4 12 2 Z
    star_img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    star_draw = ImageDraw.Draw(star_img)
    
    star_pts = []
    # Generate cubic Bezier segments (normalized 0 to 1, centered at 0.5, 0.5)
    def bezier_pt(p0, p1, p2, p3, t):
        u = 1.0 - t
        tt = t * t
        uu = u * u
        uuu = uu * u
        ttt = tt * t
        x = uuu * p0[0] + 3 * uu * t * p1[0] + 3 * u * tt * p2[0] + ttt * p3[0]
        y = uuu * p0[1] + 3 * uu * t * p1[1] + 3 * u * tt * p2[1] + ttt * p3[1]
        return (x, y)
    
    # 4 segments
    segs = [
        ((12, 2), (12.6, 7.4), (16.6, 11.4), (22, 12)),
        ((22, 12), (16.6, 12.6), (12.6, 16.6), (12, 22)),
        ((12, 22), (11.4, 16.6), (7.4, 12.6), (2, 12)),
        ((2, 12), (7.4, 11.4), (11.4, 7.4), (12, 2))
    ]
    
    scale = (size * 0.38) / 20.0
    for p0, p1, p2, p3 in segs:
        for step in range(25):
            t = step / 25.0
            bx, by = bezier_pt(p0, p1, p2, p3, t)
            # Map from 2..22 to screen coords centered at cx, cy
            sx = cx + (bx - 12.0) * scale
            sy = cy + (by - 12.0) * scale
            star_pts.append((sx, sy))
            
    star_draw.polygon(star_pts, fill=(255, 255, 255, 255))
    bg.paste(star_img, (0, 0), star_img)
    
    if is_round:
        mask = Image.new("L", (size, size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse([0, 0, size, size], fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(bg, (0, 0), mask)
        return out
    else:
        # Subtle rounded rectangle for square legacy icon (radius ~ 22%)
        rad = int(size * 0.22)
        mask = Image.new("L", (size, size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.rounded_rectangle([0, 0, size, size], radius=rad, fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(bg, (0, 0), mask)
        return out

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

base_dir = "app/src/main/res"
for folder, sz in sizes.items():
    dir_path = os.path.join(base_dir, folder)
    os.makedirs(dir_path, exist_ok=True)
    
    sq = render_icon(size=512, is_round=False).resize((sz, sz), Image.Resampling.LANCZOS)
    sq.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")
    
    rnd = render_icon(size=512, is_round=True).resize((sz, sz), Image.Resampling.LANCZOS)
    rnd.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")
    print(f"Generated {folder} at {sz}x{sz}")
