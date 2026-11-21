#version 150

// Shadered+'s train cars (client/TrainRenderer): a box round each car, turned along the rails, traced through in the
// fragment shader. Normal: the way its nose points (its length). UV0.x: which corner (0 .. 7) + 8 x how open its doors
// are (0 .. 15) + 128 x its gangways (1 at its +x end, 2 its -x end); UV0.y: how far it's come (wheels). Color: red its
// look (0 maglev, 1 steam), green 0 a locomotive / 1 a car / 2 a cab car, blue how fast (40 a block a tick), alpha how
// lit it is where it is.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

const vec3 LO = vec3(-7.0, -0.15, -1.85), HI = vec3(7.3, 5.2, 1.85);

out vec3 lp;
flat out vec3 eye;
flat out vec3 centre;
flat out vec3 F;
flat out vec3 U;
flat out vec3 S;
flat out int style;
flat out int kind;
flat out int flags;
flat out float door;
flat out float speed;
flat out float travel;
flat out float lit;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    int c = int(UV0.x + 0.5);
    int corner = c & 7;
    door = float((c >> 3) & 15) / 15.0;
    flags = (c >> 7) & 3;
    F = normalize(Normal);
    S = normalize(cross(F, vec3(0.0, 1.0, 0.0)));
    U = cross(S, F);
    lp = mix(LO, HI, vec3(float(corner & 1), float((corner >> 1) & 1), float((corner >> 2) & 1)));
    centre = Position - (F * lp.x + U * lp.y + S * lp.z);
    vec4 e = inverse(ProjMat * ModelViewMat) * vec4(0.0, 0.0, 1.0, 0.0);
    vec3 eyeCam = abs(e.w) > 1e-6 ? e.xyz / e.w : vec3(0.0);
    vec3 w = eyeCam - centre;
    eye = vec3(dot(w, F), dot(w, U), dot(w, S));
    style = int(Color.r * 255.0 + 0.5);
    kind = int(Color.g * 255.0 + 0.5);
    speed = Color.b * 255.0 / 40.0;
    lit = Color.a;
    travel = UV0.y;
}
