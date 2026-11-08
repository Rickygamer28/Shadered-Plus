#version 150

// Shadered+'s plasma ball: a box round the glass globe. As the lava lamp: works out the position on the box in the
// ball's own space (block units from the block's corner; UV0.x = which corner) and the view ray there, so it is right
// in the world, in a mirror and as an item. UV0.y: the ball's seed. Normal: towards the nearest player, its length how
// near (0 = nobody close). Color: the plasma's colour, alpha on (255) / off.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// 1.20.1: the camera's turn already in the positions (block entities' poses hold it): taken back out (client/ViewState)
uniform mat4 ViewRot;
uniform float ItemMode;
uniform mat4 LampInv;

out vec3 lp;
out vec3 lray;
flat out vec4 vColor;
flat out vec4 target; // (xyz: towards the player, w: how near)
flat out float seedIn;

void main() {
    vec3 Pos = (inverse(ViewRot) * vec4(Position, 1.0)).xyz;
    mat4 MVm = ModelViewMat * ViewRot;
    vColor = Color;
    gl_Position = ProjMat * MVm * vec4(Pos, 1.0);
    int corner = int(UV0.x + 0.5);
    vec3 lo = vec3(2.5, 4.0, 2.5) / 16.0, hi = vec3(13.5, 15.0, 13.5) / 16.0; // (client/PlasmaBallRenderer's box)
    lp = mix(lo, hi, vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)));
    seedIn = UV0.y;
    float near = length(Normal);
    target = vec4(near > 0.02 ? Normal / near : vec3(0.0, 1.0, 0.0), clamp(near, 0.0, 1.0));
    mat4 iv = inverse(MVm);
    bool persp = ProjMat[2][3] < -0.5;
    vec3 dir = persp ? Pos - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    lray = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
}
