#version 150

// Shadered+'s time crystal's clock face on the ground (client/TimeCrystalRenderer). UV0: where on it (-1 .. 1 across).
// Color: red its speed (0 .. 3), green its radius (blocks), alpha how
// strongly it shows.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 at;
flat out float speed;
flat out float radius;
flat out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    at = UV0;
    speed = Color.r * 255.0;
    radius = Color.g * 255.0;
    strength = Color.a;
}
