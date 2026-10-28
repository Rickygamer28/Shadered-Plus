#version 150

// Shadered+'s music visualizer: UV0 = where in the whole joined screen (0..1 across, 0..1 up); Color = its colour
// (black: rainbow), alpha = look * 64 + how many blocks wide.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
// 1.20.1: the camera's turn already in the positions (block entities' poses hold it): taken back out (client/ViewState)
uniform mat4 ViewRot;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 vColor;

void main() {
    vec3 Pos = (inverse(ViewRot) * vec4(Position, 1.0)).xyz;
    mat4 MVm = ModelViewMat * ViewRot;
    uv = UV0;
    vColor = Color;
    gl_Position = ProjMat * MVm * vec4(Pos, 1.0);
}
