#version 150

// Shadered+'s elevator cabin (client/ElevatorRenderer): a box round it (3 x 4 x 3 blocks, its floor's top 0.8 below
// its middle), traced through in the fragment shader. UV0.x: which corner (0 .. 7); UV0.y: how fast it's going (blocks
// a tick, up +). Color: red its look (0 glass capsule, 1 hard-light platform, 2 bubble pod, 3 warp cabin, 4 crystal
// cabin, 5 cloud), green how wide it is (blocks), blue how tall: the box scaled from 3 x 3 by those.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

const vec3 HALF = vec3(1.5, 2.0, 1.5);

out vec3 lp;
flat out vec3 SC;
flat out vec3 eye;
flat out vec3 centre;
flat out int skin;
flat out float vel;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int corner = int(UV0.x + 0.5);
    float wide = max(floor(Color.g * 255.0 + 0.5), 1.0), tall = max(floor(Color.b * 255.0 + 0.5), 1.0);
    SC = vec3(wide / 3.0, tall / 3.0, wide / 3.0);
    lp = (vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)) * 2.0 - 1.0) * HALF * SC;
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eyeCam = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
    centre = Position - lp;
    eye = eyeCam - centre;
    skin = int(Color.r * 255.0 + 0.5);
    vel = UV0.y;
}
