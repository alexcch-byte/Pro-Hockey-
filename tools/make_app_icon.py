import os
import math
from PIL import Image, ImageDraw, ImageFilter

def create_app_icon(size=1024):
    """
    Renders a top-tier 3D arcade hockey app icon:
      - Deep stadium navy backdrop with glowing arena spotlights
      - Polished ice rink surface with faceoff circle and red line
      - True crossed composite hockey sticks with authentic curved taped blades
      - High-impact speeding 3D hockey puck with knurled rubber rim, golden star crest, and specular glints
      - Supersonic 'On Fire' flame and cyan lightning trails
      - Golden championship stars
    """
    SS = 2
    W = size * SS
    H = size * SS
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    cx = W * 0.5
    cy = H * 0.5

    # 1. Base Arena Navy Gradient (rich midnight blue)
    for y in range(H):
        t = y / float(H)
        r = int(9 + 10 * t)
        g = int(16 + 18 * t)
        b = int(32 + 38 * t)
        draw.line([(0, y), (W, y)], fill=(r, g, b, 255))

    # 2. Stadium Lighting & Spotlights
    lighting = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ldraw = ImageDraw.Draw(lighting)

    # Dramatic spotlights from top corners pointing toward center
    spot_left = [(W * 0.04, 0), (W * 0.28, 0), (cx + W * 0.22, H), (cx - W * 0.16, H)]
    ldraw.polygon(spot_left, fill=(45, 115, 215, 32))
    spot_right = [(W * 0.72, 0), (W * 0.96, 0), (cx + W * 0.16, H), (cx - W * 0.22, H)]
    ldraw.polygon(spot_right, fill=(45, 115, 215, 32))

    # Center overhead stadium flare
    for r in range(int(W * 0.44), 0, -8):
        alpha = int(48 * (1.0 - r / (W * 0.44)))
        ldraw.ellipse([cx - r * 1.35, cy - r * 0.88 - H * 0.06, cx + r * 1.35, cy + r * 0.88 - H * 0.06],
                      fill=(28, 105, 195, alpha))

    # Ice surface reflections in lower half
    ice_y = int(H * 0.62)
    for y in range(ice_y, H):
        t = (y - ice_y) / float(H - ice_y)
        alpha = int(50 + 40 * t)
        ldraw.line([(0, y), (W, y)], fill=(18, 55, 100, alpha))

    # Red center line & cyan faceoff circle on the ice
    ldraw.line([(0, cy + H * 0.24), (W, cy + H * 0.24)], fill=(225, 45, 60, 70), width=int(16 * SS))
    ldraw.ellipse([cx - W * 0.36, cy + H * 0.04, cx + W * 0.36, cy + H * 0.44], outline=(40, 130, 235, 75), width=int(14 * SS))

    img = Image.alpha_composite(img, lighting)
    draw = ImageDraw.Draw(img)

    # 3. Supersonic "On Fire" Flame Trail behind Puck
    puck_cx = cx
    puck_cy = cy + H * 0.04
    puck_rx = W * 0.21
    puck_ry = H * 0.13

    fire = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    fdraw = ImageDraw.Draw(fire)

    # Multi-stage soaring flame plume shooting upwards
    flame_stages = [
        # (width, height, color, y_offset)
        (W * 0.50, H * 0.54, (220, 38, 38, 150), H * 0.06),   # Deep Crimson
        (W * 0.40, H * 0.45, (249, 115, 22, 190), H * 0.04),  # Bright Orange
        (W * 0.30, H * 0.36, (251, 191, 36, 220), H * 0.02),  # Radiant Gold
        (W * 0.19, H * 0.25, (254, 240, 138, 245), 0),        # Hot Yellow
        (W * 0.10, H * 0.15, (255, 255, 255, 255), -H * 0.02) # Brilliant Core
    ]

    for fw, fh, col, off_y in flame_stages:
        poly = [
            (puck_cx, puck_cy + off_y),
            (puck_cx - fw * 0.5, puck_cy - off_y),
            (puck_cx - fw * 0.45, puck_cy - fh * 0.35),
            (puck_cx - fw * 0.25, puck_cy - fh * 0.75),
            (puck_cx, puck_cy - fh),
            (puck_cx + fw * 0.25, puck_cy - fh * 0.75),
            (puck_cx + fw * 0.45, puck_cy - fh * 0.35),
            (puck_cx + fw * 0.5, puck_cy - off_y)
        ]
        fdraw.polygon(poly, fill=col)

    # Cyan speed streaks
    for angle in [-40, -20, 20, 40]:
        rad = math.radians(angle)
        sx = puck_cx + math.sin(rad) * puck_rx * 0.95
        sy = puck_cy - math.cos(rad) * puck_ry * 0.95
        ex = puck_cx + math.sin(rad) * puck_rx * 1.9
        ey = puck_cy - math.cos(rad) * puck_ry * 2.3
        fdraw.line([(sx, sy), (ex, ey)], fill=(0, 229, 255, 190), width=int(8 * SS))

    fire = fire.filter(ImageFilter.GaussianBlur(radius=10 * SS))
    img = Image.alpha_composite(img, fire)
    draw = ImageDraw.Draw(img)

    # 4. Crossed Hockey Sticks (Authentic Crossed "X" Pattern)
    # Stick 1: Handle at top-left, Blade at bottom-right
    # Stick 2: Handle at top-right, Blade at bottom-left
    def draw_stick(handle_pt, heel_pt, blade_dir_right=True):
        stk = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        sdraw = ImageDraw.Draw(stk)

        dx = heel_pt[0] - handle_pt[0]
        dy = heel_pt[1] - handle_pt[1]
        dist = math.hypot(dx, dy)
        ux = dx / dist
        uy = dy / dist
        nx = -uy
        ny = ux

        thick = W * 0.044

        # Shaft polygon
        s_poly = [
            (handle_pt[0] + nx * thick * 0.5, handle_pt[1] + ny * thick * 0.5),
            (heel_pt[0] + nx * thick * 0.5, heel_pt[1] + ny * thick * 0.5),
            (heel_pt[0] - nx * thick * 0.5, heel_pt[1] - ny * thick * 0.5),
            (handle_pt[0] - nx * thick * 0.5, handle_pt[1] - ny * thick * 0.5)
        ]
        sdraw.polygon(s_poly, fill=(30, 34, 42, 255))

        # Specular light highlight along top edge
        sdraw.line([
            (handle_pt[0] + nx * thick * 0.28, handle_pt[1] + ny * thick * 0.28),
            (heel_pt[0] + nx * thick * 0.28, heel_pt[1] + ny * thick * 0.28)
        ], fill=(78, 88, 106, 210), width=int(3 * SS))

        # Grip tape bands near handle
        for i in range(4):
            frac = 0.06 + i * 0.038
            bx = handle_pt[0] + dx * frac
            by = handle_pt[1] + dy * frac
            sdraw.line([
                (bx + nx * thick * 0.5, by + ny * thick * 0.5),
                (bx - nx * thick * 0.5, by - ny * thick * 0.5)
            ], fill=(255, 193, 7, 245), width=int(5 * SS))

        # Butt end cap
        sdraw.ellipse([handle_pt[0] - 8 * SS, handle_pt[1] - 8 * SS,
                       handle_pt[0] + 8 * SS, handle_pt[1] + 8 * SS], fill=(245, 175, 5, 255))

        # Blade sweeps along the ice
        blade_len = W * 0.22
        b_sign = 1.0 if blade_dir_right else -1.0
        
        # Blade points: heel -> mid -> curved toe
        b_toe = (heel_pt[0] + b_sign * blade_len, heel_pt[1] - H * 0.05)
        b_mid = (heel_pt[0] + b_sign * blade_len * 0.52, heel_pt[1] + H * 0.015)

        blade_pts = [
            (heel_pt[0] + nx * thick * 0.5, heel_pt[1] + ny * thick * 0.5),
            (b_mid[0], b_mid[1] - thick * 0.45),
            (b_toe[0], b_toe[1] - thick * 0.35),
            (b_toe[0] + b_sign * 8 * SS, b_toe[1] + thick * 0.35),
            (b_mid[0], b_mid[1] + thick * 0.45),
            (heel_pt[0] - nx * thick * 0.5, heel_pt[1] - ny * thick * 0.5)
        ]
        # White tape wrapping
        sdraw.polygon(blade_pts, fill=(242, 246, 250, 255))

        # Blade tape wraps (slanted tape lines)
        for t_step in [0.25, 0.45, 0.65, 0.85]:
            tx = heel_pt[0] + (b_toe[0] - heel_pt[0]) * t_step
            ty = heel_pt[1] + (b_toe[1] - heel_pt[1]) * t_step
            sdraw.line([(tx, ty - thick * 0.45), (tx, ty + thick * 0.45)],
                       fill=(160, 174, 192, 210), width=int(4 * SS))

        # Black rubber puck scuff
        scuff_x = heel_pt[0] + (b_toe[0] - heel_pt[0]) * 0.48
        scuff_y = heel_pt[1] + (b_toe[1] - heel_pt[1]) * 0.48
        sdraw.ellipse([scuff_x - 14 * SS, scuff_y - 6 * SS, scuff_x + 14 * SS, scuff_y + 6 * SS],
                      fill=(40, 45, 52, 180))

        return stk

    # Top-Left to Bottom-Right
    stick1 = draw_stick((W * 0.16, H * 0.22), (W * 0.68, H * 0.74), blade_dir_right=True)
    # Top-Right to Bottom-Left
    stick2 = draw_stick((W * 0.84, H * 0.22), (W * 0.32, H * 0.74), blade_dir_right=False)

    # Stick drop shadow
    stick_combo = Image.alpha_composite(stick1, stick2)
    shadow_mask = stick_combo.split()[3]
    s_shadow = Image.new("RGBA", (W, H), (0, 0, 0, 160))
    s_shadow.putalpha(shadow_mask)
    s_shadow_offset = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    s_shadow_offset.paste(s_shadow, (0, int(15 * SS)))
    s_shadow_offset = s_shadow_offset.filter(ImageFilter.GaussianBlur(radius=8 * SS))

    img = Image.alpha_composite(img, s_shadow_offset)
    img = Image.alpha_composite(img, stick1)
    img = Image.alpha_composite(img, stick2)
    draw = ImageDraw.Draw(img)

    # 5. Prominent 3D Vulcanized Hockey Puck
    puck = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(puck)

    # Puck contact shadow on sticks and ice
    pshadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ps_draw = ImageDraw.Draw(pshadow)
    ps_draw.ellipse([puck_cx - puck_rx * 1.18, puck_cy - puck_ry * 0.8 + 24 * SS,
                     puck_cx + puck_rx * 1.18, puck_cy + puck_ry * 0.8 + 24 * SS],
                    fill=(0, 0, 0, 190))
    pshadow = pshadow.filter(ImageFilter.GaussianBlur(radius=10 * SS))
    img = Image.alpha_composite(img, pshadow)

    # 3D cylinder edge (thickness of puck)
    cyl_height = int(34 * SS)
    for dy in range(cyl_height, 0, -1):
        shade = int(18 + 14 * (1.0 - dy / cyl_height))
        pdraw.ellipse([puck_cx - puck_rx, puck_cy - puck_ry + dy,
                       puck_cx + puck_rx, puck_cy + puck_ry + dy],
                      fill=(shade, shade + 2, shade + 5, 255))

    # Knurled diamond waffle tread ticks on the cylinder rim
    for i in range(32):
        ang = i * (math.pi / 16.0)
        gx = puck_cx + math.cos(ang) * puck_rx * 0.98
        gy = puck_cy + math.sin(ang) * puck_ry
        pdraw.line([(gx, gy), (gx, gy + cyl_height * 0.85)], fill=(10, 12, 16, 230), width=int(3 * SS))

    # Puck face (tilted top ellipse)
    pdraw.ellipse([puck_cx - puck_rx, puck_cy - puck_ry,
                   puck_cx + puck_rx, puck_cy + puck_ry],
                  fill=(36, 40, 50, 255), outline=(62, 70, 84, 255), width=int(4 * SS))

    # Beveled inner groove
    inner_rx = puck_rx * 0.86
    inner_ry = puck_ry * 0.86
    pdraw.ellipse([puck_cx - inner_rx, puck_cy - inner_ry,
                   puck_cx + inner_rx, puck_cy + inner_ry],
                  fill=(26, 30, 38, 255), outline=(16, 18, 24, 255), width=int(3 * SS))

    # Metallic Gold Trim Ring
    gold_rx = puck_rx * 0.74
    gold_ry = puck_ry * 0.74
    pdraw.ellipse([puck_cx - gold_rx, puck_cy - gold_ry,
                   puck_cx + gold_rx, puck_cy + gold_ry],
                  outline=(255, 193, 7, 240), width=int(4 * SS))

    # Golden Championship Shield Emblem in Puck Center
    shield_w = puck_rx * 0.48
    shield_h = puck_ry * 0.75
    shield_pts = [
        (puck_cx - shield_w, puck_cy - shield_h * 0.5),
        (puck_cx + shield_w, puck_cy - shield_h * 0.5),
        (puck_cx + shield_w * 0.85, puck_cy + shield_h * 0.2),
        (puck_cx, puck_cy + shield_h),
        (puck_cx - shield_w * 0.85, puck_cy + shield_h * 0.2)
    ]
    pdraw.polygon(shield_pts, fill=(255, 215, 0, 255))

    # Inner shield accent
    in_pts = [
        (puck_cx - shield_w * 0.75, puck_cy - shield_h * 0.35),
        (puck_cx + shield_w * 0.75, puck_cy - shield_h * 0.35),
        (puck_cx + shield_w * 0.62, puck_cy + shield_h * 0.18),
        (puck_cx, puck_cy + shield_h * 0.78),
        (puck_cx - shield_w * 0.62, puck_cy + shield_h * 0.18)
    ]
    pdraw.polygon(in_pts, fill=(217, 119, 6, 255))

    # Bold 5-Point Golden Star Crest inside Shield
    def draw_inner_star(sx, sy, r_outer, r_inner):
        s_pts = []
        for i in range(10):
            r = r_outer if i % 2 == 0 else r_inner
            ang = i * (math.pi / 5.0) - math.pi / 2.0
            s_pts.append((sx + math.cos(ang) * r, sy + math.sin(ang) * r))
        pdraw.polygon(s_pts, fill=(255, 255, 255, 255))

    draw_inner_star(puck_cx, puck_cy, shield_w * 0.48, shield_w * 0.22)

    # Puck Specular Highlight (glossy sheen on top-left edge)
    sheen = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sh_draw = ImageDraw.Draw(sheen)
    sh_draw.arc([puck_cx - puck_rx * 0.96, puck_cy - puck_ry * 0.96,
                 puck_cx + puck_rx * 0.96, puck_cy + puck_ry * 0.96],
                start=180, end=310, fill=(255, 255, 255, 220), width=int(5 * SS))
    sheen = sheen.filter(ImageFilter.GaussianBlur(radius=3 * SS))
    puck = Image.alpha_composite(puck, sheen)

    img = Image.alpha_composite(img, puck)
    draw = ImageDraw.Draw(img)

    # 6. Championship Stars across the top
    def draw_star(sx, sy, star_r, color=(255, 215, 0, 255)):
        pts = []
        for i in range(10):
            r = star_r if i % 2 == 0 else star_r * 0.44
            ang = i * (math.pi / 5.0) - math.pi / 2.0
            pts.append((sx + math.cos(ang) * r, sy + math.sin(ang) * r))
        # Star shadow
        s_shad = [(p[0], p[1] + 4 * SS) for p in pts]
        draw.polygon(s_shad, fill=(0, 0, 0, 130))
        draw.polygon(pts, fill=color)

    star_y = cy - H * 0.33
    draw_star(cx, star_y, W * 0.055, color=(255, 225, 0, 255))
    draw_star(cx - W * 0.18, star_y + H * 0.035, W * 0.042, color=(245, 185, 10, 245))
    draw_star(cx + W * 0.18, star_y + H * 0.035, W * 0.042, color=(245, 185, 10, 245))

    # 7. Subtle Corner Border Vignette
    vignette = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    vdraw = ImageDraw.Draw(vignette)
    vdraw.rounded_rectangle([16 * SS, 16 * SS, W - 16 * SS, H - 16 * SS], radius=int(W * 0.22),
                            outline=(255, 255, 255, 35), width=int(3 * SS))
    img = Image.alpha_composite(img, vignette)

    # 8. Downsample to target size with high-quality Lanczos filter
    final_img = img.resize((size, size), Image.Resampling.LANCZOS)
    return final_img

if __name__ == "__main__":
    out_dir = r"G:\My Drive\Android Studio\Hockey\app\src\main\res"
    icon = create_app_icon(1024)
    
    # Save preview image for user inspection
    preview_path = r"C:\Users\strid\.gemini\antigravity\brain\172aa32a-72ac-42ed-8ef3-9faa5ac7e3ff\new_app_icon_preview.png"
    icon.save(preview_path, "PNG")
    print(f"Saved preview: {preview_path}")

    # Generate standard Android mipmap sizes
    mipmap_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192
    }
    
    for folder, px in mipmap_sizes.items():
        fdir = os.path.join(out_dir, folder)
        os.makedirs(fdir, exist_ok=True)
        scaled = icon.resize((px, px), Image.Resampling.LANCZOS)
        out_file = os.path.join(fdir, "ic_launcher.png")
        scaled.save(out_file, "PNG")
        # Round variant as well
        scaled.save(os.path.join(fdir, "ic_launcher_round.png"), "PNG")
        print(f"Saved {out_file} ({px}x{px})")

    # Also save Play Store 512x512
    icon.resize((512, 512), Image.Resampling.LANCZOS).save(
        r"G:\My Drive\Android Studio\Hockey\app\src\main\ic_launcher-playstore.png", "PNG"
    )

    # Also update iOS AppIcon 1024x1024
    ios_icon_dir = r"G:\My Drive\Android Studio\Hockey\ios\PowerPlayHockeyApp\Assets.xcassets\AppIcon.appiconset"
    if os.path.exists(ios_icon_dir):
        icon.save(os.path.join(ios_icon_dir, "AppIcon.png"), "PNG")
        print("Saved iOS 1024x1024 AppIcon.png")
