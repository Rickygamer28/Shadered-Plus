#version 150

// Shadered+'s hourglass (client/HourglassRenderer): a box round its glass, traced through in the fragment shader.
// UV0.x: which corner (0 .. 7); UV0.y: the box's half-depth (z). Color: red how far it's turned over (0 .. 1: half a
// turn), green and blue how much sand has fallen (0 .. 65535), alpha 1 while it runs (the stream falling).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 lp;
flat out vec3 eye;
flat out vec3 centre;
flat out vec3 half_;
flat out float turned;
flat out float fallen;
flat out float running;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int corner = int(UV0.x + 0.5);
    half_ = vec3(0.32, 0.4, UV0.y);
    lp = (vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)) * 2.0 - 1.0) * half_;
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eyeCam = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
    centre = Position - lp;
    eye = eyeCam - centre;
    turned = Color.r;
    fallen = (floor(Color.g * 255.0 + 0.5) * 256.0 + floor(Color.b * 255.0 + 0.5)) / 65535.0;
    running = Color.a;
}
