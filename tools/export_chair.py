"""Export the original Blender chair as NeoForge's baked OBJ model.

Usage: blender -b Vergil_Chair.blend --python tools/export_chair.py
"""

from pathlib import Path
from typing import NamedTuple

import bpy
from mathutils import Vector


PROJECT = Path(__file__).resolve().parents[1]
ASSETS = PROJECT / "src/main/resources/assets/vergil_chair"
MODELS = ASSETS / "models/block"
TEXTURE = ASSETS / "textures/block/warm_chalk.png"
BLOCK_SCALE = 1.58


class Corner(NamedTuple):
    position: Vector
    uv: Vector
    normal: Vector

    def at_seam(self, other):
        t = (1.0 - self.position.y) / (other.position.y - self.position.y)
        position = self.position.lerp(other.position, t)
        position.y = 1.0
        return Corner(position, self.uv.lerp(other.uv, t), self.normal.lerp(other.normal, t))


def clip_face(face, top):
    corners = []
    for a, b in zip(face[-1:] + face[:-1], face):
        a_inside = a.position.y >= 1.0 if top else a.position.y < 1.0
        b_inside = b.position.y >= 1.0 if top else b.position.y < 1.0
        if a_inside != b_inside:
            corners.append(a.at_seam(b) if a.position.y < b.position.y else b.at_seam(a))
        if b_inside:
            corners.append(b)
    return corners


def diffuse_color(normal):
    x, up, depth = normal
    key = max(0.0, -0.43 * x - 0.41 * depth + 0.80 * up)
    shade = min(1.0, 0.73 + 0.18 * (0.5 + 0.5 * up) + 0.13 * key)
    return shade, shade, shade, 1.0


def write_obj(stem, faces, y_offset=0):
    positions, uvs, normals, colors = {}, {}, {}, {}
    indexed_faces = []

    def index(table, values):
        key = tuple(round(value, 6) for value in values)
        return table.setdefault(key, len(table) + 1)

    for face in faces:
        if len(face) < 3:
            continue
        indexed = []
        for position, uv, normal in face:
            x, y, z = position
            u, v = uv
            indexed.append((
                index(positions, (x, y - y_offset, z)),
                index(uvs, (max(0, min(1, u)), max(0, min(1, v)))),
                index(normals, normal),
                index(colors, diffuse_color(normal)),
            ))
        if len({v[0] for v in indexed}) == len(indexed):
            indexed_faces.append(indexed)

    with (MODELS / f"{stem}.obj").open("w", encoding="ascii", newline="\n") as out:
        out.write("# Evaluated and decimated from Vergil_Chair.blend\n")
        out.write("mtllib vergil_chair.mtl\no vergil_chair\nusemtl warm_chalk\n")
        for kind, table in (("v", positions), ("vt", uvs), ("vn", normals), ("vc", colors)):
            for values in table:
                out.write(kind + " " + " ".join(f"{v:.6f}" for v in values) + "\n")
        for face in indexed_faces:
            out.write("f " + " ".join("/".join(map(str, corner)) for corner in face) + "\n")
    print(f"Exported {stem}: {len(positions)} vertices, {len(indexed_faces)} faces")

chair = bpy.data.objects["Vergil Chair"]
molded_edges = chair.modifiers["Molded edges"]
molded_edges.levels = molded_edges.render_levels = 1
decimate = chair.modifiers.new("Minecraft mesh", "DECIMATE")
decimate.ratio = 0.024
depsgraph = bpy.context.evaluated_depsgraph_get()
mesh = bpy.data.meshes.new_from_object(
    chair.evaluated_get(depsgraph), preserve_all_data_layers=True, depsgraph=depsgraph
)
mesh.calc_loop_triangles()
uv_layer = mesh.uv_layers.active.data

MODELS.mkdir(parents=True, exist_ok=True)
TEXTURE.parent.mkdir(parents=True, exist_ok=True)
TEXTURE.write_bytes(bpy.data.images["Warm_chalk.png"].packed_file.data)

corners = []
for loop in mesh.loops:
    x, depth, up = mesh.vertices[loop.vertex_index].co
    nx, nd, nu = mesh.corner_normals[loop.index].vector
    corners.append(Corner(
        Vector((0.5 + x * BLOCK_SCALE, up * BLOCK_SCALE, 0.5 + depth * BLOCK_SCALE)),
        uv_layer[loop.index].uv.copy(),
        Vector((nx, nu, nd)),
    ))

# Swapping depth and up reverses handedness. Cut the mesh at the block border
# so both halves keep their own lighting, culling and selection in the world.
faces = [[corners[index] for index in reversed(triangle.loops)] for triangle in mesh.loop_triangles]
write_obj("vergil_chair", faces)
write_obj("vergil_chair_lower", (clip_face(face, top=False) for face in faces))
write_obj("vergil_chair_upper", (clip_face(face, top=True) for face in faces), y_offset=1)
