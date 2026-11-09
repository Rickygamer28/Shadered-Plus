#version 150

// Shadered+'s waterfall: the falling sheet (UV0.x 0-1 across, UV0.y blocks down from the top) or the foam where it lands
// (Color alpha 1: UV0 0-1 across the pool). Color.rgb: how lit it is.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 col;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    col = Color;
}
