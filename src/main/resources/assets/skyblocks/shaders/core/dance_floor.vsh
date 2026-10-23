#version 150

// Shadered+'s dance floor: positions relative to the camera, UV0 = where on the floor (blocks from its corner).

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 floorPos;

void main() {
    floorPos = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
