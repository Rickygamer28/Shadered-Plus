#version 150

// Shadered+'s kaleidoscope: a window on each face of the block, looked into in the block's own space. UV0.x: which
// sides join the next block's window (1 before it across, 2 after, 4 below, 8 above) * 24 + face (0 down, 1 up,
// 2 north, 3 south, 4 west, 5 east) * 4 + corner; UV0.y: symmetry + 8 * palette + 64 * seed. Color: the joined window
// it's part of - its middle from this block's (128 + half blocks, across and up), its size in blocks (across, up).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// 1.20.1: the camera's turn already in the positions (block entities' poses hold it): taken back out (client/ViewState)
uniform mat4 ViewRot;
uniform float ItemMode;
uniform mat4 LampInv;

out vec3 lp;
out vec3 lray;
flat out vec3 normal;
flat out vec3 settings; // symmetry, palette, seed
flat out vec4 region;   // the joined window: its middle from this block's (blocks), its size (blocks)

const float W0 = 2.0 / 16.0, W1 = 14.0 / 16.0; // (the window, inside the brass frame)

void main() {
    vec3 Pos = (inverse(ViewRot) * vec4(Position, 1.0)).xyz;
    mat4 MVm = ModelViewMat * ViewRot;
    gl_Position = ProjMat * MVm * vec4(Pos, 1.0);
    int code = int(UV0.x + 0.5);
    int joined = code / 24, rest = code - joined * 24;
    int face = rest >> 2, corner = rest & 3;
    vec4 rc = floor(Color * 255.0 + 0.5);
    region = vec4((rc.x - 128.0) * 0.5, (rc.y - 128.0) * 0.5, max(rc.z, 1.0), max(rc.w, 1.0));
    int s = int(UV0.y + 0.5);
    settings = vec3(float(s & 7), float((s >> 3) & 7), float(s >> 6));
    bool hiU = corner == 1 || corner == 2, hiV = corner >= 2;
    vec2 c = vec2(hiU ? ((joined & 2) != 0 ? 1.0 : W1) : ((joined & 1) != 0 ? 0.0 : W0),
                  hiV ? ((joined & 8) != 0 ? 1.0 : W1) : ((joined & 4) != 0 ? 0.0 : W0));
    vec3 n;
    if (face == 0) { lp = vec3(c.x, 0.0, c.y); n = vec3(0, -1, 0); }
    else if (face == 1) { lp = vec3(c.x, 1.0, c.y); n = vec3(0, 1, 0); }
    else if (face == 2) { lp = vec3(c.x, c.y, 0.0); n = vec3(0, 0, -1); }
    else if (face == 3) { lp = vec3(c.x, c.y, 1.0); n = vec3(0, 0, 1); }
    else if (face == 4) { lp = vec3(0.0, c.y, c.x); n = vec3(-1, 0, 0); }
    else { lp = vec3(1.0, c.y, c.x); n = vec3(1, 0, 0); }
    normal = n;
    mat4 iv = inverse(MVm);
    bool persp = ProjMat[2][3] < -0.5;
    vec4 e = inverse(ProjMat * MVm) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eye = abs(e.w) > 1e-6 ? e.xyz / e.w : (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = persp ? Pos - eye : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    lray = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
}
