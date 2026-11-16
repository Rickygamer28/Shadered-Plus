#version 150

// Shadered+'s power core (client/PowerCoreRenderer): what it holds, drawn on a square turned to face you. UV0: across
// the square (-1 .. 1); Color: red its skin (0 star, 1 storm, 2 plasma, 3 atom), green how full it is (0 .. 1), blue
// 1 in a core chamber (bigger: a little more of everything), alpha how strongly it shows.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 q;
flat out int skin;
flat out float full;
flat out float big;
flat out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    q = UV0;
    skin = int(Color.r * 255.0 + 0.5);
    full = Color.g;
    big = Color.b;
    strength = Color.a;
}
