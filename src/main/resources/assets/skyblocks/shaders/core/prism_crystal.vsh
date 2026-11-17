#version 150

// Shadered+'s prism crystal (client/PrismRenderer): a box round it, traced through in the fragment shader.
// UV0.x: which corner (0 .. 7) plus how bright it is round it (the fraction, 0 .. 0.9); UV0.y: how far it's turned
// (radians). Color: rgb the way to the light shining on it (0.5 + 0.5 * dir), alpha how strong that light is.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 lp;
flat out vec3 eye;
flat out vec3 centre;
flat out vec3 toLight;
flat out float strength;
flat out float bright;
flat out float turn;

const vec3 HALF = vec3(0.22, 0.29, 0.22);

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    float c = floor(UV0.x + 0.001);
    int corner = int(c);
    bright = clamp((UV0.x - c) / 0.9, 0.0, 1.0);
    lp = (vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)) * 2.0 - 1.0) * HALF;
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eyeCam = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
    centre = Position - lp;
    eye = eyeCam - centre;
    vec3 l = Color.rgb * 2.0 - 1.0;
    toLight = length(l) > 0.01 ? normalize(l) : vec3(0.0, 1.0, 0.0);
    strength = Color.a;
    turn = UV0.y;
}
