#version 150

// Shadered+'s sky lantern light: a face of a block near the lantern, lit by it. Color: alpha = how strongly (it fades
// with distance from the lantern); the colour itself is the sky's (sky_glow.fsh).

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
}
