"""Mascota de racha + cosméticos al estilo «Datapack Icons».

Estilo: bloques gruesos, contorno oscuro de 1 px, luz arriba-izquierda,
sombra abajo y acentos saturados. Nada de degradados ni curvas.

Frames de 16x16 en tiras horizontales dentro de
`app/src/main/res/drawable-nodpi/`.

ESTADOS (llama sola)
    flame_idle.png      4 frames
    flame_blink.png     1 frame
    dormida_anim.png    2 frames
    guino_anim.png      2 frames
    feliz_anim.png      2 frames
    apagada_anim.png    2 frames

COSMÉTICOS (capas sueltas con contorno)
    cos_camiseta.png     2 frames
    cos_bufanda.png      2 frames
    cos_gafas.png        2 frames
    cos_gorro_fiesta.png 2 frames
    cos_gorro_dormir.png 2 frames
"""

from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"

PAL = {
    # llama
    "d": (143, 45, 0),
    "o": (224, 78, 0),
    "y": (255, 138, 0),
    "w": (255, 201, 60),
    "k": (20, 12, 8),
    # ceniza
    "D": (58, 58, 64),
    "O": (94, 94, 102),
    "Y": (130, 130, 140),
    "W": (172, 172, 182),
    "T": (86, 150, 220),
    # cosméticos
    "n": (24, 16, 14),      # contorno
    "B": (232, 64, 120),    # rosa base
    "H": (255, 134, 174),   # rosa iluminado
    "P": (255, 214, 64),    # pompón amarillo
    "v": (110, 75, 201),    # morado base
    "V": (156, 123, 232),   # morado iluminado
    "Z": (242, 242, 242),   # pompón blanco
    "c": (84, 196, 232),    # bufanda
    "s": (63, 169, 201),    # camiseta
    "S": (180, 235, 250),   # cuello
    "G": (176, 176, 192),   # montura
    "g": (14, 14, 22),      # cristal
    "l": (255, 255, 255),   # reflejo
    "L": (255, 255, 255),   # reflejo dentro del cristal
}

# Llama de siempre: no se toca.
BASE = [
    "................",
    ".......dd.......",
    "......dod.......",
    "......dod.......",
    ".....ddoood.....",
    "....ddoooodd....",
    "..ddoooooooodd..",
    "..ddoooooooood..",
    ".ddoooyyyyooood.",
    ".dooyyyyyyyyood.",
    ".dookkyyyykkoood",
    ".dookkyyyykkoood",
    ".dooyyyyyyyyood.",
    ".dooyywwwyyyood.",
    "..dooyyyyyyood..",
    "...dddddddddd...",
]


def pad(rows):
    return [(row + "." * 16)[:16] for row in rows]


def shifted(frame, dx, upto=8):
    rows = list(frame)
    for i in range(upto):
        row = rows[i]
        rows[i] = ("." * dx + row)[:16] if dx > 0 else (row[-dx:] + "." * (-dx))[:16]
    return rows


A = pad(BASE)
B = shifted(A, 1)

GRAY = str.maketrans({"d": "D", "o": "O", "y": "Y", "w": "W"})


def closed_eyes(frame):
    rows = list(frame)
    rows[10] = rows[10].replace("k", "y")
    return rows


def happy_eyes(frame):
    rows = list(frame)
    rows[10] = frame[10].replace("kk", "ww")
    rows[11] = frame[11].replace("kk", "yy")
    return rows


def wink(frame):
    rows = list(frame)
    for y in (10, 11):
        rows[y] = rows[y][:4] + "yy" + rows[y][6:]
    return rows


def extinguished(frame, tear_row):
    rows = [r.translate(GRAY) for r in frame]
    line = list(rows[tear_row])
    line[5] = "T"
    rows[tear_row] = "".join(line)
    return rows


# --- Cosméticos: contorno + luz arriba-izquierda ---------------------------

CAMISETA = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "...SSSSSSSSSS...",
    "..nssssssssssn..",
    ".nssssssssssssn.",
    ".nssssssssssssn.",
    ".nnnnnnnnnnnnnn.",
]

BUFANDA = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..nnnnnnnnnnnn..",
    "..nccccccccccn..",
    "....ncc..ccn....",
    "....ncc..ccn....",
]

# Gafas de sol: banda clara con dos ventanas negras, como el pack.
GAFAS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..nnnnnnnnnnnn..",
    "..nGGkkGGkkGGn..",
    "..nGGkkGGkkGGn..",
    "..nnnnnnnnnnnn..",
    "................",
    "................",
    "................",
]

GORRO_FIESTA = [
    ".......PP.......",
    "......PPPP......",
    "......nBBn......",
    ".....nHHBBn.....",
    "....nHHBBBBn....",
    "...nHHBBBBBBn...",
    "..nnnnnnnnnnnn..",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

GORRO_DORMIR = [
    "..........ZZ....",
    ".........ZZZZ...",
    ".........nVVn...",
    ".......nnVVVn...",
    "...nnnnVVVVVn...",
    "...nVVVVVVVVn...",
    "...nnnnnnnnnn...",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]

STATES = {
    "flame_idle.png": [A, B, shifted(A, -1), B],
    "flame_blink.png": [closed_eyes(A)],
    "dormida_anim.png": [closed_eyes(B), closed_eyes(shifted(A, -1))],
    "guino_anim.png": [wink(A), wink(B)],
    "feliz_anim.png": [happy_eyes(A), happy_eyes(B)],
    "apagada_anim.png": [extinguished(A, 13), extinguished(A, 14)],
}

# Segundos frames: un bloque de desplazamiento, sin suavizados.
CAMISETA_B = list(CAMISETA)
CAMISETA_B[15] = "..nnnnnnnnnnnn.."

BUFANDA_B = list(BUFANDA)
BUFANDA_B[14] = ".....ncc..ccn..."
BUFANDA_B[15] = ".....ncc..ccn..."

GAFAS_B = list(GAFAS)
GAFAS_B[11] = "..nGGkkGGkLGGn.."

GORRO_FIESTA_B = list(GORRO_FIESTA)
GORRO_FIESTA_B[0] = "........PP......"
GORRO_FIESTA_B[1] = ".......PPPP....."

GORRO_DORMIR_B = list(GORRO_DORMIR)
GORRO_DORMIR_B[0] = "...........ZZ..."
GORRO_DORMIR_B[1] = "..........ZZZZ.."

COSMETICS = {
    "cos_camiseta.png": [CAMISETA, CAMISETA_B],
    "cos_bufanda.png": [BUFANDA, BUFANDA_B],
    "cos_gafas.png": [GAFAS, GAFAS_B],
    "cos_gorro_fiesta.png": [GORRO_FIESTA, GORRO_FIESTA_B],
    "cos_gorro_dormir.png": [GORRO_DORMIR, GORRO_DORMIR_B],
}


def check(rows, name):
    if len(rows) != 16:
        raise SystemExit(f"{name}: {len(rows)} filas")
    for i, row in enumerate(rows):
        if len(row) != 16:
            raise SystemExit(f"{name}: fila {i} mide {len(row)}: {row!r}")


def render(frames, path):
    sheet = Image.new("RGBA", (16 * len(frames), 16), (0, 0, 0, 0))
    for idx, rows in enumerate(frames):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in PAL:
                    sheet.putpixel((idx * 16 + x, y), PAL[ch] + (255,))
    sheet.save(path)
    return sheet


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, frames in {**STATES, **COSMETICS}.items():
        for rows in frames:
            check(rows, name)
        render(frames, OUT / name)
        print(f"{name}: {len(frames)} frame(s)")

    llama = Image.open(OUT / "flame_idle.png").convert("RGBA").crop((0, 0, 16, 16))
    names = list(COSMETICS)

    sheet = Image.new("RGBA", (len(names) * 20, 16), (28, 28, 34, 255))
    x = 0
    for name in names:
        combo = llama.copy()
        combo.alpha_composite(Image.open(OUT / name).convert("RGBA").crop((0, 0, 16, 16)))
        sheet.alpha_composite(combo, (x, 0))
        x += 20
    sheet.resize((sheet.width * 14, sheet.height * 14), Image.NEAREST).save(
        Path(__file__).resolve().parent / "dpi_cosmeticos.png"
    )

    alone = Image.new("RGBA", (len(names) * 20, 16), (28, 28, 34, 255))
    x = 0
    for name in names:
        alone.alpha_composite(Image.open(OUT / name).convert("RGBA").crop((0, 0, 16, 16)), (x, 0))
        x += 20
    alone.resize((alone.width * 14, alone.height * 14), Image.NEAREST).save(
        Path(__file__).resolve().parent / "dpi_cosmeticos_solos.png"
    )
    print("previews listos")


if __name__ == "__main__":
    main()
