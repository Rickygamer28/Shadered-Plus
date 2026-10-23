#version 150

// Shadered+'s aquarium: positions relative to the camera.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 fragPos;

void main() {
    fragPos = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
