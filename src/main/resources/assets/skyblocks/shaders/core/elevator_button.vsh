#version 150

// Shadered+'s elevator floor buttons (client/ElevatorButtons): flat colour.

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 colour;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    colour = Color;
}
