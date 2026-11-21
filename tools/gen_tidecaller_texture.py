from PIL import Image

# 16x16 water sword: diamond-like blade with a cyan tint, darker hilt.
W = H = 16
img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()

def put(x, y, color):
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = color

# Palette
blade_hi   = (200, 250, 255, 255)
blade      = (110, 220, 245, 255)
blade_mid  = (60, 180, 220, 255)
blade_dark = (30, 120, 170, 255)
edge       = (225, 255, 255, 255)
hilt       = (40, 60, 80, 255)
hilt_hi    = (70, 100, 130, 255)
gem        = (90, 230, 255, 255)

# Diagonal blade from bottom-left (hilt) to top-right (tip)
for i in range(0, 12):
    x = 3 + i
    y = 12 - i
    # main blade width
    put(x, y, blade)
    put(x + 1, y, blade_mid)
    put(x, y - 1, blade_hi)
    if i > 0:
        put(x - 1, y + 1, blade_dark)

# bright edge along the top-left side
for i in range(1, 12):
    x = 3 + i
    y = 12 - i
    put(x, y - 1, edge)

# tip highlight
put(14, 1, edge)
put(13, 2, blade_hi)
put(15, 1, blade_hi)

# crossguard around lower area
for dx in range(-2, 3):
    put(3 + dx, 13, hilt)
    put(3 + dx, 12, hilt_hi if dx == 0 else hilt)
put(1, 12, hilt)
put(5, 12, hilt)

# grip going down-left
for i in range(0, 4):
    put(2 - i // 2, 13 + i // 2, hilt)
    put(3 - i // 2, 13 + i // 2, hilt_hi)

# pommel
put(0, 15, hilt_hi)
put(1, 15, hilt)

# gem on the guard
put(3, 13, gem)

img.save("src/main/resources/assets/wildbound/textures/item/tidecaller.png")
print("written")