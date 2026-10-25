#version 150

// Shadered+'s lava lamp: a box round the glass; positions relative to the camera; Color = the wax colour, alpha on/off.

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wpos;
out vec4 vColor;

void main() {
    wpos = Position;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
