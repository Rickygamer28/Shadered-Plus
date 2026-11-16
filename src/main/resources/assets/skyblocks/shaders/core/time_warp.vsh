#version 150

// Shadered+'s time crystal's warp (client/TimeCrystalRenderer): the dome closed off - its half ball and the disc of its
// floor - drawn over a copy of the picture, bending what's seen through the space inside it. UV0: this point across
// (x, z, over the dome's radius). Color: red its speed (0 .. 3), green its radius x 10, blue 1 on the floor (0 on the
// half ball), alpha how strongly it shows. Position: from the camera, the world's way round.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 at;
flat out vec3 eye;
flat out vec3 centre;
flat out float radius;
flat out float speed;
flat out float strength;
flat out mat4 unproject;
flat out vec2 crystalAt;   // the crystal on the screen (0 .. 1)

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    at = Position;
    radius = Color.g * 255.0 / 10.0;
    float floor_ = Color.b;
    vec3 local = vec3(UV0.x, floor_ > 0.5 ? 0.0 : sqrt(max(0.0, 1.0 - dot(UV0, UV0))), UV0.y) * radius;
    centre = Position - local;
    speed = Color.r * 255.0;
    strength = Color.a;
    unproject = inverse(ProjMat * ModelViewMat);
    vec4 cc = ProjMat * ModelViewMat * vec4(centre + vec3(0.0, 1.2, 0.0), 1.0);
    crystalAt = cc.w > 1e-4 ? (cc.xy / cc.w) * 0.5 + 0.5 : vec2(-10.0);
    vec4 e = unproject * vec4(0.0, 0.0, 1.0, 0.0);
    eye = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
}
