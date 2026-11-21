#version 150

// Smooth train track (client/TrackRenderer): Color red its part (0 sleeper, 1 rail head, 2 glowing strip, 3 the strip
// dimmed - a switch's way not taken, 4 rail sides, 5 gravel, 6 plates and clips), green the block light there, blue the
// sky light; UV0.y how far along it; Normal its face.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wp;
out vec3 n;
out vec3 vdir;
out vec2 uv;
flat out int mat;
out float blockL;
out float skyL;

void main() {
    vec4 v = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * v;
    wp = Position;
    n = Normal;
    // (the way from the eye, in the world's axes: the view's rotation undone)
    vdir = (inverse(ModelViewMat) * vec4(v.xyz, 0.0)).xyz;
    uv = UV0;
    mat = int(Color.r * 255.0 + 0.5);
    blockL = Color.g;
    skyL = Color.b;
}
