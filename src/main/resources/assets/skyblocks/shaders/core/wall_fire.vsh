#version 150

// Shadered+'s fires: the surface the view enters the fire through (right in any view: the world, a mirror).
// UV0.y = 10 * kind code + (the hearth: height in the block); UV0.x = the hearth: across / the tornado: box corner.
// Color = flame colour, alpha = facing * 64 + on * 32 + 16.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
// 1.20.1: the camera's turn already in the positions (block entities' poses hold it): taken back out (client/ViewState)
uniform mat4 ViewRot;
uniform mat4 ProjMat;
uniform float ItemMode;
uniform mat4 LampInv; // (as an item: from the drawn position back into the block's own space)

out vec3 wray; // (the view ray, in the space the fire was drawn in: only moved and turned)
out vec2 uvIn;
out vec3 lpos; // (the tornado: where on its box, px)
out vec4 vColor;

void main() {
    vec3 Pos = (inverse(ViewRot) * vec4(Position, 1.0)).xyz;
    mat4 MVm = ModelViewMat * ViewRot;
    uvIn = UV0;
    vColor = Color;
    gl_Position = ProjMat * MVm * vec4(Pos, 1.0);
    mat4 iv = inverse(MVm);
    vec3 dir = ProjMat[2][3] < -0.5 ? Pos - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    wray = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
    int corner = int(UV0.x + 0.5) & 7;
    lpos = mix(vec3(2.0, 3.3, 2.0), vec3(14.0, 29.0, 14.0), vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)));
}
