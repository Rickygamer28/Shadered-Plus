#version 150

// Shadered+'s phase block (client/PhaseBlockRenderer). UV0: across its face (0 .. 1). Color: r how lit, g which block
// (a number from its place), b which face, a how far gone (0 whole .. 1 gone).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 info;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    info = Color;
}
