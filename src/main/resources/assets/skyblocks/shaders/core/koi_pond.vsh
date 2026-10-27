#version 150

// Shadered+'s koi pond: a box over the water. UV0 = the world position across it (x, z); Color alpha = how high (0 the
// water .. 1 the box's top); Color rgb = the pond's rectangle; UV2 = its shape's row in
// KoiPondData's texture and which blocks round this one are pond. Right in any view (world, mirror).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform mat4 ModelViewMat;
// 1.20.1: the camera's turn already in the positions (block entities' poses hold it): taken back out (client/ViewState)
uniform mat4 ViewRot;
uniform mat4 ProjMat;

out vec3 wray;
out vec2 uv;
out vec4 vColor;
out vec3 vpos;
flat out ivec2 vShape;

void main() {
    vec3 Pos = (inverse(ViewRot) * vec4(Position, 1.0)).xyz;
    mat4 MVm = ModelViewMat * ViewRot;
    uv = UV0;
    vColor = Color;
    vShape = UV2;
    vpos = Pos;
    gl_Position = ProjMat * MVm * vec4(Pos, 1.0);
    mat4 iv = inverse(MVm);
    wray = ProjMat[2][3] < -0.5 ? Pos - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
}
