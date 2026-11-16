#version 150

// Shadered+'s time crystal's dome (client/TimeCrystalRenderer): a half ball over what it hurries. UV0: where on it this
// is (x round it, y up it: radians). Color: red its speed (0 .. 3), green which side is drawn (0 its inside - the sky -,
// 1 its outside - a glassy rim), alpha how strongly it shows (fading in and out).
// Position: from the camera, the world's way round.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 at;
out vec3 fromEye;
flat out float speed;
flat out float side;
flat out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    at = UV0;
    fromEye = Position;
    speed = Color.r * 255.0;
    side = Color.g;
    strength = Color.a;
}
