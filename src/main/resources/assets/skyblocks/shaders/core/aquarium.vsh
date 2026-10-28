#version 150

// Shadered+'s aquarium: positions relative to the camera; UV0 = where on the face (0..1); Color = which of the face's
// edges are edges of the tank (u = 0, u = 1, v = 0, v = 1).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 fragPos;
out vec2 faceUV;
out vec4 edges;

void main() {
    fragPos = Position;
    faceUV = UV0;
    edges = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
