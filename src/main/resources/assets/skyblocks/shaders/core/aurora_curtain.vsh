#version 150

// Shadered+'s Aurora Curtain. UV0: x along the curtain (the world's position, so curtains side by side are one),
// y down from its top (blocks). Color: its colour there (the top's at the top, the bottom's at the bottom); alpha how
// long it hangs (/ 16).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec3 viewPos;
out vec4 data;

void main() {
    vec4 v = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * v;
    viewPos = v.xyz;
    uv = UV0;
    data = Color;
}
