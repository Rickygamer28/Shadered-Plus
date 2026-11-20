#version 150

// Shadered+'s fractal cube (client/FractalCubeRenderer): a box round the fractal, traced in the fragment shader.
// UV0.x: which corner (0 .. 7), UV0.y: which fractal. Color: r the music's beat, g how lit it is round it.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 lp;
flat out vec3 eye;
flat out vec3 centre;
flat out float kind;
flat out float pulse;
flat out float bright;

const float HALF = 0.36;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int corner = int(UV0.x + 0.5);
    lp = (vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)) * 2.0 - 1.0) * HALF;
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eyeCam = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
    centre = Position - lp;
    eye = eyeCam - centre;
    kind = floor(UV0.y + 0.5);
    pulse = Color.r;
    bright = Color.g;
}
