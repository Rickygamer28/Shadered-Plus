#version 150

// Shadered+'s phase block (client/PhaseBlockRenderer). UV0: its picture in the block atlas. Color: rgb its tint times
// how lit, a how far gone (0 whole .. 1 gone). Normal: xy where on the face (-1 .. 1), z one number: how it goes
// (x40), whether it's its own panel (+20), which block and face (0 .. 19).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec2 face;
out vec4 info;
flat out int mode;
flat out int own;
flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    face = Normal.xy * 0.5 + 0.5;
    info = Color;
    int code = int(floor(Normal.z * 127.0 + 127.5));
    mode = code / 40;
    own = (code % 40) / 20;
    seed = float(code % 20);
}
