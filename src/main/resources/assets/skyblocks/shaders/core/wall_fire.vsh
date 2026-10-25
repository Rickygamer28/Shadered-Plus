#version 150

// Shadered+'s wall fire: UV0 = (along the strip in the world, height in the block); Color = flame colour, alpha on/off.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 vColor;

void main() {
    uv = UV0;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
