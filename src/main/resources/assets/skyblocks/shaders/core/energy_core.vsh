#version 150

// Shadered+'s power core (client/PowerCoreRenderer): a box round what it holds, traced through in the fragment shader
// in 3D. UV0.x: which corner of the box (0 .. 7); UV0.y: the box's half-size (blocks). Color: red the skin (0 star,
// 1 storm, 2 plasma, 3 atom), green how full it is, blue 1 in a core chamber, alpha how strongly it shows.
// Position (from the block entity renderer): from the camera, the world's way round - so the eye is at the origin.

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
    eye = lp - Position; // (the box's middle is at Position - lp; the eye at 0)
    skin = int(Color.r * 255.0 + 0.5);
    full = Color.g;
    big = Color.b;
    strength = Color.a;
}
