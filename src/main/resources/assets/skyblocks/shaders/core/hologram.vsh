#version 150

// Shadered+'s hologram: an item's own model (any of its render types), in one see-through colour.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 vColor;
out vec3 vNormal;
out vec3 wray;
out float wy;

void main() {
    uv = UV0;
    vColor = Color;
    vNormal = Normal;
    wy = Position.y;
    mat4 iv = inverse(ModelViewMat);
    wray = Position - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
