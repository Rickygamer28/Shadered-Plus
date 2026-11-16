#version 150

// Shadered+'s time crystal (client/TimeCrystalRenderer): a box round the crystal and its rune rings, traced through in
// the fragment shader in 3D (as the power core's, shaders/core/energy_core). UV0.x: which corner (0 .. 7); UV0.y: the
// box's half-size (blocks). Color: red and alpha how far its rings have turned (a turn: 65536, red the high byte), green
// how strongly it's running, blue its speed (0 .. 3).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 lp;
flat out vec3 eye;
flat out vec3 centre;   // the box's middle, from the camera (blocks)
flat out float half_;
flat out float turn;
flat out float running;
flat out float speed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int corner = int(UV0.x + 0.5);
    half_ = UV0.y;
    lp = (vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)) * 2.0 - 1.0) * half_;
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eyeCam = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
    centre = Position - lp;
    eye = eyeCam - centre;
    turn = (floor(Color.r * 255.0 + 0.5) * 256.0 + floor(Color.a * 255.0 + 0.5)) / 65536.0;
    running = Color.g;
    speed = Color.b * 255.0;
}
