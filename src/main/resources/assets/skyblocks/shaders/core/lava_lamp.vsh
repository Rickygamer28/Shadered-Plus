#version 150

// Shadered+'s lava lamp: a box round the glass; positions relative to the camera; Color = the wax colour, alpha on/off.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wpos;
out vec4 vColor;
out vec2 uv;

void main() {
    wpos = Position;
    vColor = Color;
    uv = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
