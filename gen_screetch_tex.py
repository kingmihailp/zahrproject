#!/usr/bin/env python3
"""
Generates screetch.png and screetch_eyes.png (64×64 RGBA).

UV layout (all indices 0-based):
  body      texOffs(0,0)   W=10 H=10 D=10  → front: u=10..19, v=10..19
  eyeL      texOffs(0,32)  W=2  H=2  D=1   → front: u=1..2,   v=33..34
  eyeR      texOffs(8,32)  W=2  H=2  D=1   → front: u=9..10,  v=33..34
  jawCenter texOffs(0,44)  W=4  H=2  D=1   → front: u=1..4,   v=45..46
  jawLeft   texOffs(11,44) W=2  H=2  D=1   → front: u=12..13, v=45..46
  jawRight  texOffs(18,44) W=2  H=2  D=1   → front: u=19..20, v=45..46
  legLeft   texOffs(28,32) W=8  H=2  D=2
  legRight  texOffs(28,36) W=8  H=2  D=2
  legTop    texOffs(28,40) W=2  H=8  D=2
  legBottom texOffs(36,40) W=2  H=8  D=2
"""

from PIL import Image

SIZE = 64

TRANSPARENT = (0, 0, 0, 0)
BODY_DARK   = (22, 22, 32, 255)   # основной тёмный цвет тела
BODY_MID    = (38, 38, 54, 255)   # чуть светлее — передняя грань
BODY_EDGE   = (16, 16, 24, 255)   # рёбра/грани
LEG_COLOR   = (28, 28, 42, 255)   # лапки чуть темнее тела
EYE_RED     = (210, 30, 30, 255)  # красный глаз (main texture)
EYE_GLOW    = (255, 50, 50, 255)  # ярко-красный (eyes texture, full-bright)
PINK        = (190, 80, 80, 255)  # дёсна / углы улыбки
WHITE       = (240, 240, 240, 255)# зубы
DARK_MOUTH  = (10,  10,  10, 255) # промежуток между зубами


def fill(img, u, v, w, h, color):
    """Fill a rectangle [u, u+w) × [v, v+h) with color."""
    for dy in range(h):
        for dx in range(w):
            img.putpixel((u + dx, v + dy), color)


def uv_full(u0, v0, W, H, D):
    """Return (u0, v0, total_w, total_h) of the full UV block for a cube."""
    return u0, v0, 2 * D + 2 * W, D + H


def uv_front(u0, v0, W, H, D):
    """Return (u, v, w, h) of the FRONT face of a cube."""
    return u0 + D, v0 + D, W, H


# ─────────────────────────────────────────────────────────────────────────────
main = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)
eyes = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)

# ── Body 10×10×10 at texOffs(0,0) ────────────────────────────────────────────
W, H, D = 10, 10, 10
u0, v0 = 0, 0

# top face   (u0+D, v0, W, D)
fill(main, u0+D,       v0,   W, D, BODY_DARK)
# bottom face (u0+D+W, v0, W, D)
fill(main, u0+D+W,     v0,   W, D, BODY_DARK)
# left face  (u0, v0+D, D, H)
fill(main, u0,         v0+D, D, H, BODY_EDGE)
# front face (u0+D, v0+D, W, H)
fill(main, u0+D,       v0+D, W, H, BODY_MID)
# right face (u0+D+W, v0+D, D, H)
fill(main, u0+D+W,     v0+D, D, H, BODY_EDGE)
# back face  (u0+D+W+D, v0+D, W, H)
fill(main, u0+D+W+D,   v0+D, W, H, BODY_DARK)

# ── Eyes ──────────────────────────────────────────────────────────────────────
# eyeL texOffs(0,32) W=2 H=2 D=1
eW, eH, eD = 2, 2, 1
# paint full UV block dark so there's no stray white
fill(main, *uv_full(0, 32, eW, eH, eD), BODY_DARK)
# front face = red
fill(main, *uv_front(0, 32, eW, eH, eD), EYE_RED)
fill(eyes, *uv_front(0, 32, eW, eH, eD), EYE_GLOW)

# eyeR texOffs(8,32)
fill(main, *uv_full(8, 32, eW, eH, eD), BODY_DARK)
fill(main, *uv_front(8, 32, eW, eH, eD), EYE_RED)
fill(eyes, *uv_front(8, 32, eW, eH, eD), EYE_GLOW)

# ── Smile ─────────────────────────────────────────────────────────────────────
# jawCenter texOffs(0,44) W=4 H=2 D=1  → front u=1..4, v=45..46
fill(main, *uv_full(0, 44, 4, 2, 1), PINK)
fu, fv, fw, fh = uv_front(0, 44, 4, 2, 1)   # fu=1, fv=45, fw=4, fh=2
# row v=45: alternating teeth (white) / gap (dark)
for i in range(fw):
    color = WHITE if i % 2 == 0 else DARK_MOUTH
    main.putpixel((fu + i, fv), color)
# row v=46: gum
fill(main, fu, fv + 1, fw, 1, PINK)

# jawLeft texOffs(11,44) W=2 H=2 D=1  (угол улыбки)
fill(main, *uv_full(11, 44, 2, 2, 1), PINK)

# jawRight texOffs(18,44) W=2 H=2 D=1
fill(main, *uv_full(18, 44, 2, 2, 1), PINK)

# ── Legs ──────────────────────────────────────────────────────────────────────
# legLeft   texOffs(28,32) W=8 H=2 D=2
fill(main, *uv_full(28, 32, 8, 2, 2), LEG_COLOR)
# legRight  texOffs(28,36) W=8 H=2 D=2
fill(main, *uv_full(28, 36, 8, 2, 2), LEG_COLOR)
# legTop    texOffs(28,40) W=2 H=8 D=2
fill(main, *uv_full(28, 40, 2, 8, 2), LEG_COLOR)
# legBottom texOffs(36,40) W=2 H=8 D=2
fill(main, *uv_full(36, 40, 2, 8, 2), LEG_COLOR)

# ─────────────────────────────────────────────────────────────────────────────
out_main = "/home/user/zahrproject/src/main/resources/assets/votingmod/textures/entity/screetch.png"
out_eyes = "/home/user/zahrproject/src/main/resources/assets/votingmod/textures/entity/screetch_eyes.png"

main.save(out_main)
eyes.save(out_eyes)
print("screetch.png  saved →", out_main)
print("screetch_eyes.png saved →", out_eyes)
