#version 150

// Shadered+'s small fish tanks: a box round the tank, in the tank's own space (block units from its corner; for the
// slim tank, x along the wall, z out of it). UV0.x: which corner + 8 * shape + 32 * facing; UV0.y: the tank's seed.
// Color: width, height (blocks), light (0..255), variant.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float ItemMode;
uniform mat4 LampInv;

out vec3 lp;
out vec3 lray;
flat out vec4 info;
flat out vec2 extra;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int code = int(UV0.x + 0.5);
    int corner = code & 7, shape = (code >> 3) & 3, face = (code >> 5) & 3;
    float w = floor(Color.r * 255.0 + 0.5), h = floor(Color.g * 255.0 + 0.5);
    info = vec4(float(shape), w, h, floor(Color.a * 255.0 + 0.5));
    extra = vec2(UV0.y, Color.b);
    // (client/SmallTankRenderer's boxes)
    vec3 lo = shape == 0 ? vec3(0.1, 0.0, 0.1) : shape == 1 ? vec3(0.07, 0.125, 0.07) : vec3(0.0);
    vec3 hi = shape == 0 ? vec3(0.9, 0.72, 0.9) : shape == 1 ? vec3(0.93, h - 0.125, 0.93) : vec3(w, h, 0.3125);
    lp = mix(lo, hi, vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)));
    mat4 iv = inverse(ModelViewMat);
    bool persp = ProjMat[2][3] < -0.5;
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eye = abs(e.w) > 1e-6 ? e.xyz / e.w : (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    vec3 dir = persp ? Position - eye : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    vec3 d = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
    // (the tank turned to its wall: x along it, z out of it)
    vec3 ex = face == 0 ? vec3(1.0, 0.0, 0.0) : face == 1 ? vec3(0.0, 0.0, 1.0) : face == 2 ? vec3(-1.0, 0.0, 0.0) : vec3(0.0, 0.0, -1.0);
    vec3 ez = cross(ex, vec3(0.0, 1.0, 0.0));
    lray = vec3(dot(d, ex), d.y, dot(d, ez));
}
