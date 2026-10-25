#version 150

// Shadered+'s fires: UV0 = (along the fire in the world, height in the opening; +10 = free flames with no back);
// Color = flame colour, alpha on/off.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uvIn;
out vec4 vColor;

void main() {
    uvIn = UV0;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
