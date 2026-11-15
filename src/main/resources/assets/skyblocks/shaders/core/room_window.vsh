#version 150

// Shadered+'s room window: a window with a room behind it that isn't there. Position: from the camera, the world's way
// round; UV0: where on the window (0 .. 1, across from the left as you look at it, up); Color: red the room's own
// number (its colours, its things), green which room (1 .. 8), blue the way the window looks out (0 south, 1 west,
// 2 north, 3 east) + 4 if its lights are on, alpha how light it is outside.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewDir;
out vec2 uv;
flat out float seed;
flat out int kind;
flat out float lights;
flat out float day;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int b = int(Color.b * 255.0 + 0.5);
    int facing = b & 3;
    // the window's out, its across (to the right as you look at it) and the way into the room, in the world
    vec3 n = facing == 0 ? vec3(0.0, 0.0, 1.0) : facing == 1 ? vec3(-1.0, 0.0, 0.0) : facing == 2 ? vec3(0.0, 0.0, -1.0) : vec3(1.0, 0.0, 0.0);
    vec3 across = cross(vec3(0.0, 1.0, 0.0), n);
    viewDir = vec3(dot(Position, across), Position.y, dot(Position, -n));
    uv = UV0;
    seed = Color.r * 255.0;
    kind = int(Color.g * 255.0 + 0.5);
    lights = (b & 4) != 0 ? 1.0 : 0.0;
    day = Color.a;
}
