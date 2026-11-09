#version 150

// Shadered+'s effect block: a side of the cube. UV0: where it is on its group's picture (blocks from the group's anchor,
// along the side's own two axes). Color: the side's shade (rgb) and which side it is (alpha: side * 40).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out float shade;
flat out float side;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    shade = Color.r;
    side = floor(Color.a * 255.0 / 40.0 + 0.5);
}
