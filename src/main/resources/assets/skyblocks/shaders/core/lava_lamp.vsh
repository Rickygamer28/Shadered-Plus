#version 150

// Shadered+'s lava lamp: a box round the glass. Works out, for the fragment shader, the lamp-space position (block
// units from the lamp block's corner) and the view ray in lamp space - from the box corner each vertex is (UV0.x:
// corner + 10 if big; UV0.y: the lamp's seed) and the eye, so it is right in any view: the world, a mirror, an item.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float ItemMode;
uniform mat4 LampInv;

out vec3 lp;
out vec3 lray;
out float seedIn;
out vec4 vColor;
out vec2 uv;

void main() {
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int code = int(UV0.x + 0.5);
    float big = code >= 10 ? 1.0 : 0.0;
    int corner = code - 10 * int(big);
    uv = vec2(big, 0.0);
    vec3 lo = vec3(0.325, 0.305, 0.325), hi = vec3(0.675, 0.805, 0.675);
    if (big > 0.5) { // (client/LavaLampRenderer's big box)
        lo = vec3(0.5 - 0.175 * 1.55, 0.305 * 2.2 - 0.143, 0.5 - 0.175 * 1.55);
        hi = vec3(0.5 + 0.175 * 1.55, 0.805 * 2.2 - 0.143, 0.5 + 0.175 * 1.55);
    }
    lp = mix(lo, hi, vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)));
    seedIn = UV0.y;
    mat4 iv = inverse(ModelViewMat);
    bool persp = ProjMat[2][3] < -0.5;
    vec3 dir = persp ? Position - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    // in the world the box is only moved, not turned: the ray is the same in the lamp's space; an item can be turned
    lray = ItemMode < 0.5 ? dir : (LampInv * vec4(dir, 0.0)).xyz;
}
