#version 150

// Shadered+'s jellyfish tank: the water's box. UV0.x = which corner (the fragment shader works out where it is),
// UV0.y = how lit the tank is; Color = the jellyfish's colour. Right in any view (world, mirror, item).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
// 1.20.1: the camera's turn already in the positions (block entities' poses hold it): taken back out (client/ViewState)
uniform mat4 ViewRot;
uniform mat4 ProjMat;
uniform float ItemMode;
uniform mat4 LampInv;

out vec3 wray;
out vec3 lpos;
out float lit;
out vec4 vColor;

void main() {
    vec3 Pos = (inverse(ViewRot) * vec4(Position, 1.0)).xyz;
    mat4 MVm = ModelViewMat * ViewRot;
    vColor = Color;
    lit = UV0.y;
    gl_Position = ProjMat * MVm * vec4(Pos, 1.0);
    mat4 iv = inverse(MVm);
    vec3 dir = ProjMat[2][3] < -0.5 ? Pos - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    wray = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
    int corner = int(UV0.x + 0.5) & 7;
    lpos = mix(vec3(2.0, 3.3, 2.0), vec3(14.0, 29.0, 14.0), vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)));
}
