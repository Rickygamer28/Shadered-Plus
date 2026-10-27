#version 150

// Shadered+'s shader skies (chaos, synthwave): a cube around the camera; the direction to each corner goes to the fragment shader.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 dir;

void main() {
    dir = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
