#version 150

// Shadered+'s galaxy projector: the box round its reach (the shader works from the depth at each pixel, not from here).

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
