#version 150

// Smooth train track (client/TrackRenderer): Color red its part (0 sleeper, 1 rail, 2 glowing strip, 3 a strip dimmed -
// a switch's way not taken), green the block light there, blue the sky light; UV0.y how far along it; Normal its face.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wp;
out vec3 n;
out vec2 uv;
flat out int mat;
out float blockL;
out float skyL;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    wp = Position;
    n = Normal;
    uv = UV0;
    mat = int(Color.r * 255.0 + 0.5);
    blockL = Color.g;
    skyL = Color.b;
}
