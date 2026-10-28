#version 150

// Shadered+'s hologram: an item's own model (any of its render types), in one see-through colour.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
// 1.20.1: the camera's turn already in the positions (block entities' poses hold it): taken back out (client/ViewState)
uniform mat4 ViewRot;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 vColor;
out vec3 vNormal;
out vec3 wray;
out float wy;

void main() {
    vec3 Pos = (inverse(ViewRot) * vec4(Position, 1.0)).xyz;
    mat4 MVm = ModelViewMat * ViewRot;
    uv = UV0;
    vColor = Color;
    vNormal = (mat3(inverse(ViewRot)) * Normal);
    wy = Pos.y;
    mat4 iv = inverse(MVm);
    wray = Pos - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
    gl_Position = ProjMat * MVm * vec4(Pos, 1.0);
}
