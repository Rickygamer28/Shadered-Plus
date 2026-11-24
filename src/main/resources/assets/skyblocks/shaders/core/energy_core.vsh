#version 150

// Shadered+'s power core (client/PowerCoreRenderer): a box round what it holds, traced through in the fragment shader
// in 3D. UV0.x: which corner of the box (0 .. 7); UV0.y: the box's half-size (blocks). Color: red the skin (0 star,
// 1 storm, 2 plasma, 3 atom), green how full it is, blue 1 in a core chamber, alpha how strongly it shows.
// Position (from the block entity renderer): from the camera, the world's way round.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 lp;          // this point of the box, from its middle (blocks)
flat out vec3 eye;    // the eye, from the box's middle (blocks)
flat out float half_;
flat out int skin;
flat out float full;
flat out float big;
flat out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int corner = int(UV0.x + 0.5);
    half_ = UV0.y;
    lp = (vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)) * 2.0 - 1.0) * half_;
    // the true eye: the point the whole projection sees from (view bobbing is in the projection - taking the eye as the
    // origin, what's traced slid about against the box as you walked); the box's middle is at Position - lp
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eyeCam = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
    eye = lp - Position + eyeCam;
    skin = int(Color.r * 255.0 + 0.5);
    full = Color.g;
    big = Color.b;
    strength = Color.a;
}
