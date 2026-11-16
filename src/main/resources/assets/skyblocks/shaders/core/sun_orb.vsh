#version 150

// Shadered+'s sun in a jar: a little sun (a ball, drawn on a square turned to face you), boiling, with flares
// licking out round it. UV0: across the square (-1 .. 1); Color: alpha how bright (red unused).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 q;
flat out float phase;
flat out float bright;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    q = UV0;
    phase = Color.r * 255.0;
    bright = Color.a;
}
