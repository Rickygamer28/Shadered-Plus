#version 150

// A portal whose other end is too far to draw live (our own code). Pictures were taken from just in front of the
// other end - straight out, left, right, up and down - each with how far away every pixel is. Here each pixel's view
// ray, carried through the portal, is followed out from the other end through those pictures until it meets what's
// there: walking past, you see round things, nearer things move against farther ones, as through a window. The edge
// glows in the portal's colour, as a live portal's (mirror_surface).

uniform sampler2D Sampler0; // straight out
uniform sampler2D Sampler1; // right
uniform sampler2D Sampler2; // left
uniform sampler2D Sampler3; // up
uniform sampler2D Sampler4; // down
uniform vec3 EyeQ;  // the eye, carried through the portal (relative to where the pictures were taken)
uniform vec3 Front; // the other end's facing
uniform float GameTime;

in vec3 q;
in vec2 faceUv;
flat in vec3 faceCode;

out vec4 fragColor;

// which picture looks the way u goes, and what it shows there (rgb, and its distance code); along: u's part along
// that picture's view
vec4 look(vec3 u, out float along) {
    vec3 f = Front, y = vec3(0.0, 1.0, 0.0);
    vec3 r = normalize(cross(f, y));
    float df = dot(u, f), dr = dot(u, r), dy = u.y;
    vec3 a, upv;
    int k;
    if (df >= abs(dr) && df >= abs(dy)) {
        k = 0; a = f; upv = y;
    } else if (abs(dr) >= abs(dy)) {
        k = dr > 0.0 ? 1 : 2; a = dr > 0.0 ? r : -r; upv = y;
    } else {
        k = dy > 0.0 ? 3 : 4; a = dy > 0.0 ? y : -y; upv = dy > 0.0 ? -f : f;
    }
    vec3 b = normalize(cross(a, upv)), c = cross(-a, b); // (as the pictures' cameras: lookAt)
    along = max(dot(u, a), 1e-3);
    vec2 uv = clamp(vec2(dot(u, b), dot(u, c)) / along * 0.5 + 0.5, vec2(0.001), vec2(0.999));
    return k == 0 ? texture(Sampler0, uv) : k == 1 ? texture(Sampler1, uv) : k == 2 ? texture(Sampler2, uv)
         : k == 3 ? texture(Sampler3, uv) : texture(Sampler4, uv);
}

// how far what's there is along u (straight from where the pictures were taken)
float sceneDist(vec4 s, float along) {
    return s.a > 0.998 ? 1.0e6 : s.a * s.a * 256.0 / along;
}

const vec3 DYES[16] = vec3[16](vec3(0.976, 1.0, 0.996), vec3(0.976, 0.502, 0.114), vec3(0.78, 0.306, 0.741), vec3(0.227, 0.702, 0.855),
    vec3(0.996, 0.847, 0.239), vec3(0.502, 0.78, 0.122), vec3(0.953, 0.545, 0.667), vec3(0.278, 0.31, 0.322),
    vec3(0.616, 0.616, 0.592), vec3(0.086, 0.612, 0.612), vec3(0.537, 0.196, 0.722), vec3(0.235, 0.267, 0.667),
    vec3(0.514, 0.329, 0.196), vec3(0.369, 0.486, 0.086), vec3(0.69, 0.18, 0.149), vec3(0.114, 0.114, 0.129));

void main() {
    vec3 d = normalize(q - EyeQ);
    // out along the ray, in growing steps, until it's past what the pictures show there; then closer in on it
    float tPrev = 0.0, t = 0.03;
    bool hit = false;
    float along;
    for (int i = 0; i < 40; i++) {
        vec3 X = q + d * t;
        float L = length(X);
        vec4 s = look(X / L, along);
        if (L >= sceneDist(s, along)) {
            hit = true;
            break;
        }
        tPrev = t;
        t *= 1.2;
        if (t > 400.0) {
            break;
        }
    }
    vec3 col;
    if (hit) {
        float lo = tPrev, hi = t;
        for (int i = 0; i < 6; i++) {
            float m = 0.5 * (lo + hi);
            vec3 X = q + d * m;
            float L = length(X);
            vec4 s = look(X / L, along);
            if (L >= sceneDist(s, along)) hi = m; else lo = m;
        }
        vec3 X = q + d * hi;
        col = look(normalize(X), along).rgb;
    } else {
        col = look(d, along).rgb; // (far off: the sky, the distance)
    }

    // the edge glowing in the portal's colour (as mirror_surface's portals)
    vec3 tint = DYES[int(clamp(faceCode.y, 0.0, 15.0) + 0.5)];
    int edges = int(faceCode.z + 0.5);
    vec2 p = faceUv;
    float left = (edges & 1) != 0 ? 9.0 : p.x;
    float right = (edges & 2) != 0 ? 9.0 : 1.0 - p.x;
    float bottom = (edges & 4) != 0 ? 9.0 : p.y;
    float top = (edges & 8) != 0 ? 9.0 : 1.0 - p.y;
    float m = min(min(left, right), min(bottom, top));
    float tt = GameTime * 1200.0;
    float rim = exp(-m * 9.0);
    float spark = pow(max(0.0, sin(p.x * 40.0 + tt * 3.0) * sin(p.y * 37.0 - tt * 2.3)), 12.0);
    float glow = rim * (0.75 + 0.25 * sin(tt * 2.0 + (p.x + p.y) * 9.0));
    col = mix(col, tint * 1.2, clamp(glow * 0.8, 0.0, 1.0)) + tint * (pow(rim, 4.0) * 0.8 + spark * rim * 0.6);
    col += tint * 0.04;
    fragColor = vec4(min(col, vec3(1.0)), 1.0);
}
