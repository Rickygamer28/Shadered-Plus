#version 150

// Shadered+'s glitch block (client/GlitchRenderer): its cube's faces. UV0: across each face (0 .. 1). Color: red which
// face, green its own seed.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 at;
flat out float face;
flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    at = UV0;
    face = Color.r * 255.0;
    seed = Color.g * 255.0;
}
