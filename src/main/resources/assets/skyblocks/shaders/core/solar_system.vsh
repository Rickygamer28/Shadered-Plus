#version 150

// Shadered+'s solar systems: a box round the model, worked out in the block's own space as the lava lamp's. UV0.x:
// which corner + 8 * kind (0 the brass orrery, 1 the hologram planetarium).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float ItemMode;
uniform mat4 LampInv;
uniform float HoloScale; // the planetarium's size: 1, 2 or 4 times

out vec3 lp;
out vec3 lray;
flat out float kind;
flat out vec3 boxLo;
flat out vec3 boxHi;
flat out float persp;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int code = int(UV0.x + 0.5);
    int corner = code & 7;
    kind = float(code >> 3);
    // (client/SolarSystemRenderer's boxes)
    float s = HoloScale;
    vec3 lo = kind < 0.5 ? vec3(-0.18, 0.3, -0.18) : vec3(0.5 - 1.55 * s, 0.12, 0.5 - 1.55 * s);
    vec3 hi = kind < 0.5 ? vec3(1.18, 1.08, 1.18) : vec3(0.5 + 1.55 * s, 0.3 + 2.65 * s, 0.5 + 1.55 * s);
    boxLo = lo;
    boxHi = hi;
    lp = mix(lo, hi, vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)));
    mat4 iv = inverse(ModelViewMat);
    persp = ProjMat[2][3] < -0.5 ? 1.0 : 0.0;
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eye = abs(e.w) > 1e-6 ? e.xyz / e.w : (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = persp > 0.5 ? Position - eye : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    lray = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
}
