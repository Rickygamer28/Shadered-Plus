#version 150

// Shadered+'s glitch zone (client/GlitchRenderer): a box round the block, over a copy of the picture. UV0.y: its
// half-size (the zone's reach). Color: green the block's seed.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 at;
flat out vec3 eye;
flat out vec3 centre;
flat out float reach;
flat out float seed;
flat out mat4 unproject;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int corner = int(UV0.x + 0.5);
    reach = UV0.y;
    vec3 lp = (vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)) * 2.0 - 1.0) * reach;
    centre = Position - lp;
    at = Position;
    seed = Color.g * 255.0;
    unproject = inverse(ProjMat * ModelViewMat);
    vec4 e = unproject * vec4(0.0, 0.0, 1.0, 0.0);
    eye = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
}
