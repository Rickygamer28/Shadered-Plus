#version 150

// Shadered+'s elevator shaft (client/ElevatorRenderer). UV0: where on it (walls: across and up, blocks - sliding as the
// cabin goes; rings: -1 .. 1 across; the rail: across -1 .. 1, up). Color: red the elevator's look, green what this
// is (0 a wall, 1 a ring, 2 the rail), blue how fast the cabin's going (128 still, 40 a block a tick, up +), alpha how
// near the cabin (rings).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 at;
flat out int skin;
flat out int mode;
flat out float vel;
flat out float near;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    at = UV0;
    skin = int(Color.r * 255.0 + 0.5);
    mode = int(Color.g * 255.0 + 0.5);
    vel = (Color.b * 255.0 - 128.0) / 40.0;
    near = Color.a;
}
