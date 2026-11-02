#version 150

// Shadered+'s black holes: a box round the hole's reach. UV0.x: which corner; UV0.y: 10 * kind (0 small, 1 big) +
// seed. Works out the position on the box in the block's own space, the view ray there, and (flat) where the block's
// corner is in the drawn space - so the fragment shader can find any point's place on the screen.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float ItemMode;
uniform mat4 LampInv;

out vec3 lp;
out vec3 lray;
flat out vec3 origin;
flat out float kind;
out float seedIn;
out vec4 vColor;

void main() {
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int corner = int(UV0.x + 0.5);
    kind = floor(UV0.y / 10.0 + 0.001);
    seedIn = UV0.y - 10.0 * kind;
    // (client/BlackHoleRenderer's boxes: the reach round the hole's middle)
    vec3 c = kind > 0.5 ? vec3(0.5) : vec3(0.5, 0.62, 0.5);
    float reach = kind > 0.5 ? 1.5 : 0.36;
    lp = c + reach * (vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)) * 2.0 - 1.0);
    origin = Position - lp;
    mat4 iv = inverse(ModelViewMat);
    bool persp = ProjMat[2][3] < -0.5;
    vec3 dir = persp ? Position - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    lray = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
}
