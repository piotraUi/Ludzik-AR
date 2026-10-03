#!/usr/bin/env python3
"""
Buduje zasoby gry „Ludzik 3D”:
  * pobiera modele, tekstury PBR i HDRI z Poly Haven (licencja CC0),
  * pakuje modele glTF do pojedynczych plików .glb,
  * generuje geometrię pokoju (podłoga, ściany z oknem, sufit, listwy, dywan),
  * generuje billboard.glb (prostokąt z materiałem unlit do rysunkowych postaci),
  * zapisuje układ mebli i bryły kolizji do RoomLayout.kt (żeby fizyka i AI były testowalne na JVM).

Uruchomienie:  python3 tools/build_assets.py
Wymaga: Python 3.9+, Pillow (pip install pillow), dostęp do internetu.
"""
import io
import json
import math
import os
import struct
import sys
import urllib.request

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app/src/main/assets")
CACHE = os.path.join(ROOT, "tools/.cache")
KOTLIN_OUT = os.path.join(ROOT, "app/src/main/java/com/ludzik/game/scene/RoomLayout.kt")

API = "https://api.polyhaven.com"
UA = {"User-Agent": "Ludzik3D-asset-builder"}

# ------------------------------------------------------------------ wymiary pokoju (metry)
ROOM_HALF_X = 2.6
ROOM_HALF_Z = 2.2
ROOM_HEIGHT = 2.7
WINDOW = dict(x0=-0.85, x1=0.85, y0=0.85, y1=2.25)  # okno w ścianie z = -ROOM_HALF_Z

# ------------------------------------------------------------------ meble
# pos = środek podstawy (x, z), rot = obrót wokół Y w stopniach (0 = przód modelu w +Z)
# walk = gdzie mogą chodzić ludziki: "top" (cały blat), ("seat", wysokość_frac, głębokość_frac) albo None
FURNITURE = [
    dict(id="sofa_02", pos=(-2.05, 0.15), rot=90, walk=("seat", 0.47, 0.62)),
    dict(id="throw_pillows_01", pos=(-2.2, 0.15), rot=90, walk=None, collide=False, on="sofa_02"),
    dict(id="modern_coffee_table_01", pos=(-0.95, 0.15), rot=90, walk="top"),
    dict(id="modern_arm_chair_01", pos=(-0.9, 1.5), rot=180, walk=("seat", 0.45, 0.6)),
    dict(id="side_table_01", pos=(-2.25, -1.45), rot=90, walk="top"),
    dict(id="round_wooden_table_02", pos=(1.45, 0.75), rot=0, walk="top", obstacle=False),
    dict(id="dining_chair_02", pos=(1.45, 1.35), rot=180, walk=("seat", 0.5, 0.75)),
    dict(id="dining_chair_02", pos=(0.85, 0.75), rot=90, walk=("seat", 0.5, 0.75)),
    dict(id="wooden_bookshelf_worn", pos=(2.35, -1.05), rot=-90, walk="top"),
    dict(id="potted_plant_02", pos=(1.75, -1.85), rot=0, walk=None),
    dict(id="modern_ceiling_lamp_01", pos=(0.0, 0.0), rot=0, walk=None, collide=False, ceiling=True),
    dict(id="fancy_picture_frame_01", pos=(-2.58, 0.15), rot=90, walk=None, collide=False, y=1.35),
]

# ------------------------------------------------------------------ przedmioty do respienia
SPAWNABLES = [
    dict(id="dirty_football", name="Piłka", bounce=0.72, friction=0.35, rolls=True, mass=0.45),
    dict(id="rubber_duck_toy", name="Kaczka", bounce=0.45, friction=0.6, rolls=False, mass=0.1),
    dict(id="cardboard_box_01", name="Karton", bounce=0.15, friction=0.8, rolls=False, mass=0.6),
    dict(id="food_apple_01", name="Jabłko", bounce=0.35, friction=0.5, rolls=True, mass=0.2),
    dict(id="wooden_crate_01", name="Skrzynka", bounce=0.1, friction=0.9, rolls=False, mass=3.0),
]

TEXTURES = dict(
    floor=("herringbone_parquet", 1.6),   # (asset, rozmiar kafla w metrach)
    wall=("beige_wall_001", 2.0),
    rug=("fabric_pattern_07", 0.9),
)
HDRI = "white_home_studio"


# ================================================================== pobieranie

def fetch(url, cache_name=None):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, cache_name or url.replace("/", "_").replace(":", ""))
    if os.path.exists(path):
        with open(path, "rb") as f:
            return f.read()
    print("  pobieram", url)
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    with open(path, "wb") as f:
        f.write(data)
    return data


def api(path):
    return json.loads(fetch(API + path, "api" + path.replace("/", "_") + ".json"))


# ================================================================== glTF -> GLB

def pad4(b, fill=b"\x00"):
    return b + fill * ((4 - len(b) % 4) % 4)


def write_glb(gltf, bin_data, path):
    gltf["buffers"] = [{"byteLength": len(bin_data)}]
    js = pad4(json.dumps(gltf, separators=(",", ":")).encode(), b" ")
    bd = pad4(bin_data)
    total = 12 + 8 + len(js) + 8 + len(bd)
    with open(path, "wb") as f:
        f.write(struct.pack("<4sII", b"glTF", 2, total))
        f.write(struct.pack("<I4s", len(js), b"JSON"))
        f.write(js)
        f.write(struct.pack("<I4s", len(bd), b"BIN\x00"))
        f.write(bd)


def shrink_image(data, max_size, is_normal=False):
    img = Image.open(io.BytesIO(data))
    if max(img.size) <= max_size:
        return data, "image/jpeg" if data[:2] == b"\xff\xd8" else "image/png"
    img.thumbnail((max_size, max_size), Image.LANCZOS)
    out = io.BytesIO()
    img.convert("RGB").save(out, "JPEG", quality=88)
    return out.getvalue(), "image/jpeg"


def pack_polyhaven_model(asset_id, out_path, max_tex=1024):
    files = api(f"/files/{asset_id}")
    g = files["gltf"]["1k"]["gltf"]
    gltf = json.loads(fetch(g["url"], f"{asset_id}.gltf"))
    includes = {k: v["url"] for k, v in g.get("include", {}).items()}

    def load_uri(uri):
        key = urllib.request.unquote(uri)
        for k, u in includes.items():
            if k == key or k.endswith("/" + key) or key.endswith(k):
                return fetch(u, f"{asset_id}__{os.path.basename(k)}")
        raise KeyError(f"{asset_id}: brak pliku {uri} w {list(includes)}")

    blob = bytearray()
    # bufory -> jeden BIN; przesuwamy offsety bufferView
    buffer_offsets = []
    for b in gltf.get("buffers", []):
        data = load_uri(b["uri"])
        while len(blob) % 4:
            blob.append(0)
        buffer_offsets.append(len(blob))
        blob += data
    for bv in gltf.get("bufferViews", []):
        bv["byteOffset"] = bv.get("byteOffset", 0) + buffer_offsets[bv["buffer"]]
        bv["buffer"] = 0
    # obrazy -> bufferView
    for im in gltf.get("images", []):
        if "uri" not in im:
            continue
        data, mime = shrink_image(load_uri(im["uri"]), max_tex)
        while len(blob) % 4:
            blob.append(0)
        gltf.setdefault("bufferViews", []).append({"buffer": 0, "byteOffset": len(blob), "byteLength": len(data)})
        blob += data
        im["bufferView"] = len(gltf["bufferViews"]) - 1
        im["mimeType"] = mime
        del im["uri"]
    write_glb(gltf, bytes(blob), out_path)
    return gltf, bytes(blob)


# ================================================================== bryły brzegowe modeli

def quat_to_mat(q):
    x, y, z, w = q
    return [
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ]


def node_matrix(n):
    if "matrix" in n:
        m = n["matrix"]
        return [[m[0], m[4], m[8], m[12]], [m[1], m[5], m[9], m[13]], [m[2], m[6], m[10], m[14]], [0, 0, 0, 1]]
    t = n.get("translation", [0, 0, 0])
    r = quat_to_mat(n.get("rotation", [0, 0, 0, 1]))
    s = n.get("scale", [1, 1, 1])
    return [
        [r[0][0] * s[0], r[0][1] * s[1], r[0][2] * s[2], t[0]],
        [r[1][0] * s[0], r[1][1] * s[1], r[1][2] * s[2], t[1]],
        [r[2][0] * s[0], r[2][1] * s[1], r[2][2] * s[2], t[2]],
        [0, 0, 0, 1],
    ]


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def gltf_aabb(gltf):
    lo = [1e9] * 3
    hi = [-1e9] * 3
    scene = gltf.get("scenes", [{}])[gltf.get("scene", 0)]

    def visit(idx, parent):
        n = gltf["nodes"][idx]
        m = matmul(parent, node_matrix(n))
        if "mesh" in n:
            for prim in gltf["meshes"][n["mesh"]]["primitives"]:
                acc = gltf["accessors"][prim["attributes"]["POSITION"]]
                mn, mx = acc["min"], acc["max"]
                for cx in (mn[0], mx[0]):
                    for cy in (mn[1], mx[1]):
                        for cz in (mn[2], mx[2]):
                            for i in range(3):
                                v = m[i][0] * cx + m[i][1] * cy + m[i][2] * cz + m[i][3]
                                lo[i] = min(lo[i], v)
                                hi[i] = max(hi[i], v)
        for c in n.get("children", []):
            visit(c, m)

    ident = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    for r in scene.get("nodes", []):
        visit(r, ident)
    return lo, hi


# ================================================================== generator geometrii

class MeshBuilder:
    """Zbiera prymitywy (pozycje, normalne, UV, opcjonalnie kolory) i zapisuje GLB."""

    def __init__(self):
        self.gltf = {"asset": {"version": "2.0", "generator": "Ludzik3D build_assets.py"},
                     "scene": 0, "scenes": [{"nodes": []}], "nodes": [], "meshes": [],
                     "materials": [], "textures": [], "images": [], "samplers": [
                         {"magFilter": 9729, "minFilter": 9987, "wrapS": 10497, "wrapT": 10497}],
                     "accessors": [], "bufferViews": [], "extensionsUsed": []}
        self.blob = bytearray()

    def _view(self, data, target=None):
        while len(self.blob) % 4:
            self.blob.append(0)
        v = {"buffer": 0, "byteOffset": len(self.blob), "byteLength": len(data)}
        if target:
            v["target"] = target
        self.blob += data
        self.gltf["bufferViews"].append(v)
        return len(self.gltf["bufferViews"]) - 1

    def _accessor(self, values, comps, kind, minmax=False):
        flat = [c for v in values for c in v]
        data = struct.pack(f"<{len(flat)}f", *flat)
        acc = {"bufferView": self._view(data, 34962), "componentType": 5126, "count": len(values), "type": kind}
        if minmax:
            acc["min"] = [min(v[i] for v in values) for i in range(comps)]
            acc["max"] = [max(v[i] for v in values) for i in range(comps)]
        self.gltf["accessors"].append(acc)
        return len(self.gltf["accessors"]) - 1

    def _indices(self, idx):
        data = struct.pack(f"<{len(idx)}I", *idx)
        self.gltf["accessors"].append({"bufferView": self._view(data, 34963), "componentType": 5125,
                                       "count": len(idx), "type": "SCALAR"})
        return len(self.gltf["accessors"]) - 1

    def image(self, data, mime="image/jpeg"):
        self.gltf["images"].append({"bufferView": self._view(data), "mimeType": mime})
        self.gltf["textures"].append({"source": len(self.gltf["images"]) - 1, "sampler": 0})
        return len(self.gltf["textures"]) - 1

    def material(self, **m):
        self.gltf["materials"].append(m)
        return len(self.gltf["materials"]) - 1

    def add_mesh(self, name, quads_or_tris, material, colors=False):
        """quads_or_tris: lista wierzchołków (pos, normal, uv[, color]) w trójkątach."""
        pos = [v[0] for v in quads_or_tris]
        nor = [v[1] for v in quads_or_tris]
        uv = [v[2] for v in quads_or_tris]
        attrs = {"POSITION": self._accessor(pos, 3, "VEC3", True),
                 "NORMAL": self._accessor(nor, 3, "VEC3"),
                 "TEXCOORD_0": self._accessor(uv, 2, "VEC2")}
        if colors:
            attrs["COLOR_0"] = self._accessor([v[3] for v in quads_or_tris], 4, "VEC4")
        prim = {"attributes": attrs, "indices": self._indices(list(range(len(pos)))), "material": material}
        self.gltf["meshes"].append({"name": name, "primitives": [prim]})
        self.gltf["nodes"].append({"name": name, "mesh": len(self.gltf["meshes"]) - 1})
        self.gltf["scenes"][0]["nodes"].append(len(self.gltf["nodes"]) - 1)

    def save(self, path):
        if not self.gltf["extensionsUsed"]:
            del self.gltf["extensionsUsed"]
        for k in ("textures", "images"):
            if not self.gltf[k]:
                del self.gltf[k]
        write_glb(self.gltf, bytes(self.blob), path)


def quad(p0, p1, p2, p3, n, tile, uv_axes):
    """Prostokąt p0..p3 (przeciwnie do zegara patrząc od strony normalnej) z UV w metrach/tile."""
    a, b = uv_axes
    # Kolejność wierzchołków musi być przeciwna do zegara patrząc od strony normalnej
    # (inaczej ściana jest „odwrócona” i znika albo źle się oświetla) — poprawiamy automatycznie.
    e1 = [p1[i] - p0[i] for i in range(3)]
    e2 = [p2[i] - p0[i] for i in range(3)]
    cr = (e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
    if cr[0] * n[0] + cr[1] * n[1] + cr[2] * n[2] < 0:
        p1, p3 = p3, p1

    def uv(p):
        return (p[a] / tile, -p[b] / tile)

    return [(p0, n, uv(p0)), (p1, n, uv(p1)), (p2, n, uv(p2)), (p0, n, uv(p0)), (p2, n, uv(p2)), (p3, n, uv(p3))]


def box(x0, y0, z0, x1, y1, z1, tile=1.0):
    t = []
    t += quad((x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), (0, 1, 0), tile, (0, 2))
    t += quad((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1), tile, (0, 1))
    t += quad((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (0, 0, -1), tile, (0, 1))
    t += quad((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (1, 0, 0), tile, (2, 1))
    t += quad((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (-1, 0, 0), tile, (2, 1))
    return t


def pbr_material(mb, tex_id, name, tile_tint=None):
    """Materiał z tekstur Poly Haven (diff / nor_gl / arm)."""
    files = api(f"/files/{tex_id}")
    g = files["gltf"]["1k"]["gltf"]
    inc = g["include"]

    def pick(*keys):
        for k, v in inc.items():
            if any(key in k for key in keys):
                return fetch(v["url"], f"{tex_id}__{os.path.basename(k)}")
        raise KeyError(f"{tex_id}: brak {keys} w {list(inc)}")

    diff = mb.image(shrink_image(pick("diff", "col_1", "col"), 1024)[0])
    nor = mb.image(shrink_image(pick("nor_gl"), 1024)[0])
    try:
        arm_data = shrink_image(pick("arm"), 1024)[0]
    except KeyError:
        # brak mapy ARM: składamy ją z mapy chropowatości (R = AO 1.0, G = roughness, B = metal 0)
        rough = Image.open(io.BytesIO(pick("rough"))).convert("L")
        rough.thumbnail((1024, 1024), Image.LANCZOS)
        full = Image.new("L", rough.size, 255)
        zero = Image.new("L", rough.size, 0)
        out = io.BytesIO()
        Image.merge("RGB", (full, rough, zero)).save(out, "JPEG", quality=90)
        arm_data = out.getvalue()
    arm = mb.image(arm_data)
    pbr = {"baseColorTexture": {"index": diff}, "metallicRoughnessTexture": {"index": arm}, "metallicFactor": 1.0,
           "roughnessFactor": 1.0}
    if tile_tint:
        pbr["baseColorFactor"] = tile_tint
    return mb.material(name=name, pbrMetallicRoughness=pbr, normalTexture={"index": nor},
                       occlusionTexture={"index": arm})


def build_room(path):
    mb = MeshBuilder()
    hx, hz, h = ROOM_HALF_X, ROOM_HALF_Z, ROOM_HEIGHT
    floor_m = pbr_material(mb, TEXTURES["floor"][0], "floor")
    wall_m = pbr_material(mb, TEXTURES["wall"][0], "wall", [1.0, 0.97, 0.92, 1.0])
    # sufit: gładka biała farba (tekstura tynku wyglądała na brudną)
    ceil_m = mb.material(name="ceiling", pbrMetallicRoughness={"baseColorFactor": [0.95, 0.95, 0.94, 1], "metallicFactor": 0,
                                                               "roughnessFactor": 0.92})
    rug_m = pbr_material(mb, TEXTURES["rug"][0], "rug")
    trim_m = mb.material(name="trim", pbrMetallicRoughness={"baseColorFactor": [0.93, 0.92, 0.9, 1], "metallicFactor": 0,
                                                            "roughnessFactor": 0.45})
    # „Widok za oknem”: jasna tafla bez oświetlenia (unlit) — wygląda jak prześwietlone niebo/ogród
    # i nie zależy od ekspozycji kamery.
    mb.gltf["extensionsUsed"].append("KHR_materials_unlit")
    glass_m = mb.material(name="window_light", pbrMetallicRoughness={"baseColorFactor": [0.93, 0.97, 1.0, 1]},
                          extensions={"KHR_materials_unlit": {}})

    ft = TEXTURES["floor"][1]
    mb.add_mesh("floor", quad((-hx, 0, hz), (hx, 0, hz), (hx, 0, -hz), (-hx, 0, -hz), (0, 1, 0), ft, (0, 2)), floor_m)
    ct = 2.0
    mb.add_mesh("ceiling", quad((-hx, h, -hz), (hx, h, -hz), (hx, h, hz), (-hx, h, hz), (0, -1, 0), ct, (0, 2)), ceil_m)

    wt = TEXTURES["wall"][1]
    walls = []
    # przednia (z=+hz, normalna -z) i boczne
    walls += quad((hx, 0, hz), (-hx, 0, hz), (-hx, h, hz), (hx, h, hz), (0, 0, -1), wt, (0, 1))
    walls += quad((-hx, 0, hz), (-hx, 0, -hz), (-hx, h, -hz), (-hx, h, hz), (1, 0, 0), wt, (2, 1))
    walls += quad((hx, 0, -hz), (hx, 0, hz), (hx, h, hz), (hx, h, -hz), (-1, 0, 0), wt, (2, 1))
    # tylna z oknem (z=-hz, normalna +z): cztery prostokąty wokół otworu
    w = WINDOW
    z = -hz
    for (x0, y0, x1, y1) in [(-hx, 0, w["x0"], h), (w["x1"], 0, hx, h), (w["x0"], 0, w["x1"], w["y0"]),
                             (w["x0"], w["y1"], w["x1"], h)]:
        walls += quad((x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z), (0, 0, 1), wt, (0, 1))
    mb.add_mesh("walls", walls, wall_m)

    # ościeże okna (głębokość muru) + rama i szprosy
    d = 0.18
    reveal = []
    reveal += box(w["x0"] - 0.0, w["y0"] - 0.02, z - d, w["x1"], w["y0"], z + 0.04)  # parapet
    reveal += box(w["x0"] - 0.05, w["y0"] - 0.06, z, w["x1"] + 0.05, w["y0"] - 0.02, z + 0.08)  # parapet wewn.
    reveal += quad((w["x0"], w["y0"], z - d), (w["x0"], w["y0"], z), (w["x0"], w["y1"], z), (w["x0"], w["y1"], z - d), (1, 0, 0), 1, (2, 1))
    reveal += quad((w["x1"], w["y0"], z), (w["x1"], w["y0"], z - d), (w["x1"], w["y1"], z - d), (w["x1"], w["y1"], z), (-1, 0, 0), 1, (2, 1))
    reveal += quad((w["x0"], w["y1"], z), (w["x1"], w["y1"], z), (w["x1"], w["y1"], z - d), (w["x0"], w["y1"], z - d), (0, -1, 0), 1, (0, 2))
    fz = z - d + 0.03
    mid = (w["x0"] + w["x1"]) / 2
    for (x0, y0, x1, y1) in [(w["x0"], w["y0"], w["x1"], w["y0"] + 0.06), (w["x0"], w["y1"] - 0.06, w["x1"], w["y1"]),
                             (w["x0"], w["y0"], w["x0"] + 0.06, w["y1"]), (w["x1"] - 0.06, w["y0"], w["x1"], w["y1"]),
                             (mid - 0.03, w["y0"], mid + 0.03, w["y1"]),
                             (w["x0"], (w["y0"] + w["y1"]) * 0.58, w["x1"], (w["y0"] + w["y1"]) * 0.58 + 0.05)]:
        reveal += box(x0, y0, fz - 0.03, x1, y1, fz + 0.03)
    # listwy przypodłogowe
    bh, bd = 0.08, 0.015
    reveal += box(-hx, 0, hz - bd, hx, bh, hz)
    reveal += box(-hx, 0, -hz, -hx + bd, bh, hz)
    reveal += box(hx - bd, 0, -hz, hx, bh, hz)
    reveal += box(-hx, 0, -hz, hx, bh, -hz + bd)
    mb.add_mesh("trim", reveal, trim_m)
    mb.add_mesh("window_light", quad((w["x0"], w["y0"], z - d), (w["x1"], w["y0"], z - d), (w["x1"], w["y1"], z - d),
                                     (w["x0"], w["y1"], z - d), (0, 0, 1), 1, (0, 1)), glass_m)
    # dywan pod stolikiem
    rt = TEXTURES["rug"][1]
    rx0, rx1, rz0, rz1, ry = -1.75, -0.15, -0.95, 1.25, 0.006
    rug = quad((rx0, ry, rz1), (rx1, ry, rz1), (rx1, ry, rz0), (rx0, ry, rz0), (0, 1, 0), rt, (0, 2))
    rug += box(rx0, 0, rz0, rx1, ry, rz1, rt)[6:]  # boki dywanu (bez górnej ściany)
    mb.add_mesh("rug", rug, rug_m)
    mb.save(path)


def build_billboard(path):
    """Prostokąt 1x1 z materiałem unlit + blend + kolorami wierzchołków.
    W grze podmieniamy mu geometrię (dynamiczne bufory) i teksturę (atlas klatek)."""
    mb = MeshBuilder()
    png = io.BytesIO()
    Image.new("RGBA", (4, 4), (255, 255, 255, 255)).save(png, "PNG")
    tex = mb.image(png.getvalue(), "image/png")
    mb.gltf["extensionsUsed"].append("KHR_materials_unlit")
    m = mb.material(name="billboard", alphaMode="BLEND", doubleSided=True,
                    pbrMetallicRoughness={"baseColorTexture": {"index": tex}, "baseColorFactor": [1, 1, 1, 1]},
                    extensions={"KHR_materials_unlit": {}})
    v = [((-0.5, 0, 0), (0, 0, 1), (0, 1), (1, 1, 1, 1)), ((0.5, 0, 0), (0, 0, 1), (1, 1), (1, 1, 1, 1)),
         ((0.5, 1, 0), (0, 0, 1), (1, 0), (1, 1, 1, 1)), ((-0.5, 0, 0), (0, 0, 1), (0, 1), (1, 1, 1, 1)),
         ((0.5, 1, 0), (0, 0, 1), (1, 0), (1, 1, 1, 1)), ((-0.5, 1, 0), (0, 0, 1), (0, 0), (1, 1, 1, 1))]
    mb.add_mesh("billboard", v, m, colors=True)
    mb.save(path)


# ================================================================== układ i kolizje -> Kotlin

def rotated_aabb(lo, hi, rot_deg):
    """AABB po obrocie o wielokrotność 90° wokół Y."""
    r = int(round(rot_deg / 90.0)) % 4
    xs, zs = (lo[0], hi[0]), (lo[2], hi[2])
    pts = []
    for x in xs:
        for z in zs:
            if r == 0:
                pts.append((x, z))
            elif r == 1:
                pts.append((z, -x))
            elif r == 2:
                pts.append((-x, -z))
            else:
                pts.append((-z, x))
    return (min(p[0] for p in pts), lo[1], min(p[1] for p in pts)), (max(p[0] for p in pts), hi[1], max(p[1] for p in pts))


def kt_float(v):
    if abs(v) < 5e-5:
        v = 0.0
    return f"{v:.4f}f"


def main():
    os.makedirs(os.path.join(ASSETS, "models"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "envs"), exist_ok=True)
    print("== modele mebli")
    aabbs = {}
    for f in FURNITURE + SPAWNABLES:
        mid = f["id"]
        if mid in aabbs:
            continue
        out = os.path.join(ASSETS, "models", f"{mid}.glb")
        gltf, _ = pack_polyhaven_model(mid, out, 1024 if f in FURNITURE else 512)
        aabbs[mid] = gltf_aabb(gltf)
        print(f"  {mid}: {os.path.getsize(out) / 1e6:.1f} MB, aabb {[round(x, 2) for x in aabbs[mid][0]]} .. {[round(x, 2) for x in aabbs[mid][1]]}")

    print("== pokój")
    build_room(os.path.join(ASSETS, "models", "room.glb"))
    build_billboard(os.path.join(ASSETS, "models", "billboard.glb"))

    print("== HDRI")
    files = api(f"/files/{HDRI}")
    hdr = fetch(files["hdri"]["1k"]["hdr"]["url"], f"{HDRI}_1k.hdr")
    with open(os.path.join(ASSETS, "envs", "room.hdr"), "wb") as f:
        f.write(hdr)

    # ------------------------------------------------------------------ Kotlin
    lines = []
    lines.append("// PLIK WYGENEROWANY przez tools/build_assets.py — nie edytuj ręcznie.")
    lines.append("package com.ludzik.game.scene\n")
    lines.append("import com.ludzik.game.characters.Vec3")
    lines.append("import com.ludzik.game.math.Aabb\n")
    lines.append("/** Układ pokoju: wymiary, meble (model, pozycja, obrót) i przedmioty do respienia. */")
    lines.append("object RoomLayout {")
    lines.append(f"    const val HALF_X = {kt_float(ROOM_HALF_X)}")
    lines.append(f"    const val HALF_Z = {kt_float(ROOM_HALF_Z)}")
    lines.append(f"    const val HEIGHT = {kt_float(ROOM_HEIGHT)}")
    lines.append(f"    val window = Aabb(Vec3({kt_float(WINDOW['x0'])}, {kt_float(WINDOW['y0'])}, {kt_float(-ROOM_HALF_Z - 0.2)}), "
                 f"Vec3({kt_float(WINDOW['x1'])}, {kt_float(WINDOW['y1'])}, {kt_float(-ROOM_HALF_Z)}))\n")
    lines.append("    val furniture: List<FurnitureSpec> = listOf(")
    placed = {}
    for f in FURNITURE:
        lo, hi = aabbs[f["id"]]
        wlo, whi = rotated_aabb(lo, hi, f["rot"])
        x, z = f["pos"]
        # podstawa modelu na podłodze (albo pod sufitem / na ścianie)
        if f.get("ceiling"):
            y = ROOM_HEIGHT - hi[1]
        elif "y" in f:
            y = f["y"] - (lo[1] + hi[1]) / 2
        elif f.get("on"):
            base = placed[f["on"]]
            y = base["walk_y"] - lo[1] - 0.06
        else:
            y = -lo[1]
        # środek AABB w XZ ustawiamy w pos
        cx = (wlo[0] + whi[0]) / 2
        cz = (wlo[2] + whi[2]) / 2
        tx, tz = x - cx, z - cz
        world_lo = (wlo[0] + tx, wlo[1] + y, wlo[2] + tz)
        world_hi = (whi[0] + tx, whi[1] + y, whi[2] + tz)
        walk = f.get("walk")
        if walk == "top":
            walk_y = world_hi[1]
            wl = (world_lo[0], world_lo[2], world_hi[0], world_hi[2])
        elif isinstance(walk, tuple):
            # siedzisko: wysokość = frac * wysokość, przednia część głębokości
            _, hf, df = walk
            walk_y = world_lo[1] + (world_hi[1] - world_lo[1]) * hf
            # przód modelu (+Z lokalnie) po obrocie
            r = int(round(f["rot"] / 90.0)) % 4
            x0, z0, x1, z1 = world_lo[0], world_lo[2], world_hi[0], world_hi[2]
            if r == 0:
                z0 = z1 - (z1 - z0) * df
            elif r == 1:
                x0 = x1 - (x1 - x0) * df
            elif r == 2:
                z1 = z0 + (z1 - z0) * df
            else:
                x1 = x0 + (x1 - x0) * df
            wl = (x0, z0, x1, z1)
        else:
            walk_y = None
            wl = None
        placed[f["id"]] = dict(walk_y=walk_y if walk_y is not None else world_hi[1])
        walk_kt = "null" if wl is None else (
            f"WalkTop({kt_float(walk_y)}, {kt_float(wl[0])}, {kt_float(wl[1])}, {kt_float(wl[2])}, {kt_float(wl[3])})")
        lines.append(f"        FurnitureSpec(\"models/{f['id']}.glb\", Vec3({kt_float(tx)}, {kt_float(y)}, {kt_float(tz)}), "
                     f"{kt_float(float(f['rot']))}, Aabb(Vec3({kt_float(world_lo[0])}, {kt_float(world_lo[1])}, {kt_float(world_lo[2])}), "
                     f"Vec3({kt_float(world_hi[0])}, {kt_float(world_hi[1])}, {kt_float(world_hi[2])})), "
                     f"collides = {'true' if f.get('collide', True) else 'false'}, floorObstacle = {'true' if f.get('obstacle', True) else 'false'}, walk = {walk_kt}),")
    lines.append("    )\n")
    lines.append("    val spawnables: List<SpawnableSpec> = listOf(")
    for s in SPAWNABLES:
        lo, hi = aabbs[s["id"]]
        ext = [hi[i] - lo[i] for i in range(3)]
        radius = max(ext) / 2 if s["rolls"] else (ext[0] + ext[1] + ext[2]) / 6
        center = [(lo[i] + hi[i]) / 2 for i in range(3)]
        lines.append(f"        SpawnableSpec(\"{s['id']}\", \"{s['name']}\", \"models/{s['id']}.glb\", "
                     f"radius = {kt_float(radius)}, halfHeight = {kt_float(ext[1] / 2)}, "
                     f"modelCenter = Vec3({kt_float(center[0])}, {kt_float(center[1])}, {kt_float(center[2])}), "
                     f"bounce = {kt_float(s['bounce'])}, friction = {kt_float(s['friction'])}, rolls = {'true' if s['rolls'] else 'false'}, "
                     f"mass = {kt_float(s['mass'])}),")
    lines.append("    )")
    lines.append("}")
    os.makedirs(os.path.dirname(KOTLIN_OUT), exist_ok=True)
    with open(KOTLIN_OUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    total = sum(os.path.getsize(os.path.join(dp, fn)) for dp, _, fns in os.walk(ASSETS) for fn in fns)
    print(f"== gotowe, zasoby: {total / 1e6:.1f} MB")


if __name__ == "__main__":
    sys.exit(main())
