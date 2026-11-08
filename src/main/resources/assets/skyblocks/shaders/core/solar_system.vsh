#version 150

// Shadered+'s solar systems: a box round the model, worked out in the block's own space as the lava lamp's. UV0.x:
// which corner + 8 * kind (0 the brass orrery, 1 the hologram planetarium).

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
flat out float kind;
flat out vec3 boxLo;
flat out vec3 boxHi;
flat out float persp;
flat out vec3 toView; // (from the block's own space to the camera's: its corners come already moved there)
flat out vec3 settings; // (this one's own: dome, real sizes, size - carried by its corners, not uniforms)

void main() {
    vec3 Pos = (inverse(ViewRot) * vec4(Position, 1.0)).xyz;
    mat4 MVm = ModelViewMat * ViewRot;
    gl_Position = ProjMat * MVm * vec4(Pos, 1.0);
    int code = int(UV0.x + 0.5);
    int corner = code & 7;
    kind = float(code >> 3);
    // (client/SolarSystemRenderer's boxes)
    int extra = int(UV0.y + 0.5); // dome + 2 * real sizes + 4 * size (0, 1, 2)
    int size = extra >> 2;
    float s = size == 2 ? 4.0 : size == 1 ? 2.0 : 1.0;
    settings = vec3(float(extra & 1), float((extra >> 1) & 1), s);
    vec3 lo = kind < 0.5 ? vec3(-0.3, 0.3, -0.3) : vec3(0.5 - 1.55 * s, 0.12, 0.5 - 1.55 * s);
    vec3 hi = kind < 0.5 ? vec3(1.3, 1.1, 1.3) : vec3(0.5 + 1.55 * s, 0.3 + 2.65 * s, 0.5 + 1.55 * s);
    boxLo = lo;
    boxHi = hi;
    lp = mix(lo, hi, vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)));
    toView = Pos - lp;
    mat4 iv = inverse(MVm);
    persp = ProjMat[2][3] < -0.5 ? 1.0 : 0.0;
    vec4 e = inverse(ProjMat * MVm) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eye = abs(e.w) > 1e-6 ? e.xyz / e.w : (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = persp > 0.5 ? Pos - eye : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    lray = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
}
