#version 150

// The faces of Shadered+'s Draconic effect blocks (positions relative to the camera, per-face texture coordinates,
// the block's colour in Color).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec3 fPos;
out vec4 vertexColor;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    fPos = view.xyz;
    gl_Position = ProjMat * view;
    texCoord0 = UV0;
    vertexColor = Color;
}
