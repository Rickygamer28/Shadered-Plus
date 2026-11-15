#version 150

// The room behind the glass, worked out for each pixel: the way you look, carried on through the glass into a room
// wider and taller than the window and two blocks deep - its walls, floor and ceiling, its furniture (boxes), flat
// things (a cat on the sill, plants, a screen) - lit by its lamp at night, by the daylight through the window by day;
// curtains or blinds just inside the glass; the glass reflecting the sky a little, more at a slant; the frame round it.

uniform float GameTime;

in vec3 viewDir;
in vec2 uv;
flat in float seed;
flat in int kind;
flat in float lights;
flat in float day;

out vec4 fragColor;

const vec3 RMIN = vec3(-0.7, -0.35, 0.0);
const vec3 RMAX = vec3(1.7, 1.25, 2.0);

float h1(float n) {
    return fract(sin(n * 12.9898 + 4.1) * 43758.5453);
}

float h2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// ---- what the ray meets ----
float bestT;
vec3 bestN;
vec3 bestCol;
float bestGlow; // (light of its own: a screen, a lamp shade)

void box(vec3 o, vec3 d, vec3 lo, vec3 hi, vec3 col, float glow) {
    vec3 inv = 1.0 / d;
    vec3 t0 = (lo - o) * inv, t1 = (hi - o) * inv;
    vec3 tmin = min(t0, t1), tmax = max(t0, t1);
    float tn = max(max(tmin.x, tmin.y), tmin.z), tf = min(min(tmax.x, tmax.y), tmax.z);
    if (tn > 0.0 && tn < tf && tn < bestT) {
        bestT = tn;
        bestN = tn == tmin.x ? vec3(-sign(d.x), 0.0, 0.0) : tn == tmin.y ? vec3(0.0, -sign(d.y), 0.0) : vec3(0.0, 0.0, -sign(d.z));
        bestCol = col;
        bestGlow = glow;
    }
}

// a flat thing standing in the room, facing the window: shape mask f(p.xy) at depth z
bool card(vec3 o, vec3 d, float z, out vec2 at, out float t) {
    t = (z - o.z) / d.z;
    at = (o + d * t).xy;
    return t > 0.0 && t < bestT;
}

void cat(vec3 o, vec3 d, vec2 base, float z, float s, vec3 col) {
    vec2 at;
    float t;
    if (!card(o, d, z, at, t)) {
        return;
    }
    vec2 q = (at - base) / s;
    // a cat sat looking out, seen from behind: body, head, ears, tail curled round
    float body = length((q - vec2(0.0, 0.42)) * vec2(1.0, 0.72)) - 0.36;
    float head = length(q - vec2(0.0, 0.98)) - 0.22;
    vec2 e1 = q - vec2(-0.13, 1.2), e2 = q - vec2(0.13, 1.2);
    float ears = min(max(abs(e1.x) * 1.6 + e1.y, -e1.y - 0.12) - 0.06, max(abs(e2.x) * 1.6 + e2.y, -e2.y - 0.12) - 0.06);
    float tail = abs(length((q - vec2(0.3, 0.12)) * vec2(1.0, 1.6)) - 0.22) - 0.05;
    tail = max(tail, -(q.y - 0.12));
    float shape = min(min(body, head), min(ears, tail));
    if (shape < 0.0 && q.y > 0.0) {
        bestT = t;
        bestN = vec3(0.0, 0.0, -1.0);
        bestCol = col;
        bestGlow = 0.0;
    }
}

void plant(vec3 o, vec3 d, vec2 base, float z, float s, float k) {
    vec2 at;
    float t;
    if (!card(o, d, z, at, t)) {
        return;
    }
    vec2 q = (at - base) / s;
    float pot = max(abs(q.x) - 0.18 + q.y * 0.08, abs(q.y - 0.15) - 0.15);
    float leaves = 1.0;
    for (int i = 0; i < 6; i++) {
        float a = float(i) * 1.1 + k * 3.0;
        vec2 c = vec2(sin(a) * 0.28, 0.5 + 0.25 * cos(a * 1.3) + float(i) * 0.06);
        leaves = min(leaves, length((q - c) * vec2(1.0, 1.7)) - 0.17);
    }
    if (pot < 0.0) {
        bestT = t;
        bestN = vec3(0.0, 0.0, -1.0);
        bestCol = vec3(0.62, 0.36, 0.24);
        bestGlow = 0.0;
    } else if (leaves < 0.0) {
        bestT = t;
        bestN = normalize(vec3(sin(q.x * 9.0), 0.4, -1.0));
        bestCol = mix(vec3(0.16, 0.42, 0.18), vec3(0.32, 0.6, 0.26), h2(floor(q * 6.0) + k));
        bestGlow = 0.0;
    }
}

void screen(vec3 o, vec3 d, vec2 lo, vec2 hi, float z, vec3 col) {
    vec2 at;
    float t;
    if (!card(o, d, z, at, t)) {
        return;
    }
    if (all(greaterThan(at, lo)) && all(lessThan(at, hi))) {
        vec2 inner = (at - lo) / (hi - lo);
        bool edge = any(lessThan(inner, vec2(0.05))) || any(greaterThan(inner, vec2(0.95)));
        bestT = t;
        bestN = vec3(0.0, 0.0, -1.0);
        bestCol = edge ? vec3(0.05) : col;
        bestGlow = edge ? 0.0 : 1.0;
    }
}

void main() {
    float ticks = GameTime * 24000.0;
    vec3 d = normalize(viewDir);
    float k = seed;
    // ---- the frame round the glass, and the bars across it ----
    float fw = 0.065;
    vec3 frameCol = h1(k * 1.7) < 0.5 ? vec3(0.92, 0.91, 0.88) : h1(k * 2.3) < 0.5 ? vec3(0.36, 0.24, 0.15) : vec3(0.13, 0.13, 0.14);
    bool frame = uv.x < fw || uv.x > 1.0 - fw || uv.y < fw * 1.5 || uv.y > 1.0 - fw;
    bool bars = abs(uv.x - 0.5) < 0.018 || abs(uv.y - 0.62) < 0.018;
    float outside = day;
    vec3 sky = mix(vec3(0.02, 0.03, 0.07), vec3(0.55, 0.68, 0.86), outside);
    if (frame || bars) {
        float bevel = frame ? 0.85 + 0.15 * smoothstep(0.0, fw, min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y))) : 0.92;
        fragColor = vec4(frameCol * bevel * (0.18 + 0.82 * max(outside, 0.15)), 1.0);
        return;
    }
    if (d.z <= 1.0e-3) {
        fragColor = vec4(sky, 1.0);
        return;
    }
    vec3 o = vec3(uv, 0.0);

    // ---- the room ----
    vec3 inv = 1.0 / d;
    vec3 tWall = max((RMIN - o) * inv, (RMAX - o) * inv);
    bestT = min(min(tWall.x, tWall.y), tWall.z);
    vec3 p = o + d * bestT;
    bool floorHit = bestT == tWall.y && d.y < 0.0, ceilingHit = bestT == tWall.y && d.y > 0.0;
    bool sideHit = bestT == tWall.x;
    bestN = bestT == tWall.x ? vec3(-sign(d.x), 0.0, 0.0) : bestT == tWall.y ? vec3(0.0, -sign(d.y), 0.0) : vec3(0.0, 0.0, -1.0);
    bestGlow = 0.0;
    // its colours
    vec3 paper = vec3(0.78, 0.74, 0.68);
    float pick = h1(k * 3.1);
    if (pick < 0.2) paper = vec3(0.72, 0.8, 0.84);
    else if (pick < 0.4) paper = vec3(0.86, 0.78, 0.62);
    else if (pick < 0.55) paper = vec3(0.7, 0.78, 0.66);
    else if (pick < 0.7) paper = vec3(0.84, 0.7, 0.72);
    else if (pick < 0.8) paper = vec3(0.55, 0.6, 0.72);
    if (kind == 7) paper = vec3(0.5, 0.48, 0.45);
    if (floorHit) {
        // floorboards (or a rug in the middle)
        float plank = floor(p.x * 5.0);
        float joint = step(0.92, fract(p.x * 5.0)) + step(0.97, fract(p.z * 1.3 + h1(plank) * 0.7));
        bestCol = mix(vec3(0.55, 0.38, 0.24), vec3(0.42, 0.28, 0.17), h1(plank + k)) * (1.0 - 0.35 * min(joint, 1.0));
        vec2 rug = abs(p.xz - vec2(0.5, 1.15)) - vec2(0.8, 0.5);
        if (h1(k * 5.3) < 0.6 && max(rug.x, rug.y) < 0.0 && kind != 3 && kind != 7) {
            bestCol = mix(vec3(0.6, 0.18, 0.2), vec3(0.2, 0.3, 0.55), h1(k * 7.1)) * (0.85 + 0.15 * step(0.5, fract((p.x + p.z) * 4.0)));
        }
        if (kind == 3) {
            bestCol = mix(vec3(0.85), vec3(0.25), mod(floor(p.x * 4.0) + floor(p.z * 4.0), 2.0)); // (tiles)
        }
    } else if (ceilingHit) {
        bestCol = vec3(0.86, 0.85, 0.82);
    } else {
        float stripes = h1(k * 9.7) < 0.5 ? 0.93 + 0.07 * step(0.5, fract((sideHit ? p.z : p.x) * 6.0)) : 1.0;
        bestCol = paper * stripes;
        if (p.y < -0.27) bestCol = vec3(0.85, 0.83, 0.8) * 0.8; // (skirting)
        // a picture on the back wall
        vec2 pic = abs(p.xy - vec2(0.2 + h1(k) * 0.6, 0.62)) - vec2(0.22, 0.16);
        if (!sideHit && max(pic.x, pic.y) < 0.0 && kind != 3 && kind != 7) {
            bool rim = max(pic.x, pic.y) > -0.03;
            bestCol = rim ? vec3(0.3, 0.2, 0.12) : mix(vec3(0.25, 0.45, 0.65), vec3(0.85, 0.6, 0.3), smoothstep(-0.16, 0.16, p.y - 0.62 + sin(p.x * 9.0) * 0.04));
        }
    }

    // ---- its things ----
    vec3 lamp = vec3(0.5, 1.16, 1.0);
    vec3 lampCol = vec3(1.0, 0.82, 0.58);
    vec3 extraPos = vec3(0.0);
    vec3 extraCol = vec3(0.0);
    float flick = 0.75 + 0.25 * sin(ticks * 0.9 + sin(ticks * 0.37) * 3.0) * sin(ticks * 0.23);
    if (kind == 1) {
        vec3 blanket = mix(vec3(0.3, 0.42, 0.7), vec3(0.75, 0.35, 0.4), h1(k * 4.4));
        box(o, d, vec3(-0.65, -0.35, 0.95), vec3(0.45, -0.08, 1.98), blanket, 0.0);
        box(o, d, vec3(-0.6, -0.08, 1.65), vec3(0.4, 0.02, 1.9), vec3(0.92, 0.92, 0.9), 0.0);
        box(o, d, vec3(-0.68, -0.35, 1.9), vec3(0.48, 0.38, 1.99), vec3(0.4, 0.27, 0.18), 0.0);
        box(o, d, vec3(0.62, -0.35, 1.55), vec3(0.95, 0.02, 1.95), vec3(0.45, 0.3, 0.2), 0.0);
        box(o, d, vec3(0.7, 0.02, 1.68), vec3(0.86, 0.22, 1.84), vec3(1.0, 0.85, 0.6), lights);
        lamp = vec3(0.78, 0.15, 1.76);
        lampCol = vec3(1.0, 0.72, 0.42);
    } else if (kind == 2) {
        vec3 sofa = mix(vec3(0.3, 0.5, 0.42), vec3(0.55, 0.42, 0.3), h1(k * 6.6));
        box(o, d, vec3(-0.5, -0.35, 1.5), vec3(1.15, -0.02, 1.98), sofa, 0.0);
        box(o, d, vec3(-0.5, -0.02, 1.8), vec3(1.15, 0.32, 1.98), sofa * 0.9, 0.0);
        box(o, d, vec3(-0.6, -0.35, 1.5), vec3(-0.45, 0.12, 1.98), sofa * 0.8, 0.0);
        box(o, d, vec3(1.1, -0.35, 1.5), vec3(1.25, 0.12, 1.98), sofa * 0.8, 0.0);
        // the television, on, by the side wall
        vec3 tv = vec3(0.35, 0.55, 0.95) * flick + vec3(0.2) * step(0.8, h1(floor(ticks / 30.0)));
        box(o, d, vec3(1.55, -0.35, 0.75), vec3(1.7, 0.05, 1.45), vec3(0.15, 0.12, 0.1), 0.0);
        box(o, d, vec3(1.6, 0.08, 0.8), vec3(1.66, 0.55, 1.4), tv, 1.0);
        extraPos = vec3(1.5, 0.3, 1.1);
        extraCol = tv * 0.9 * (1.0 - 0.5 * lights);
    } else if (kind == 3) {
        vec3 wood = mix(vec3(0.85, 0.85, 0.82), vec3(0.45, 0.6, 0.55), h1(k * 2.2));
        box(o, d, vec3(-0.7, -0.35, 1.6), vec3(1.15, 0.12, 2.0), wood, 0.0);
        box(o, d, vec3(-0.7, 0.12, 1.58), vec3(1.15, 0.15, 2.0), vec3(0.3, 0.3, 0.32), 0.0);
        box(o, d, vec3(-0.7, 0.62, 1.75), vec3(1.15, 1.0, 2.0), wood * 0.95, 0.0);
        box(o, d, vec3(1.2, -0.35, 1.45), vec3(1.7, 0.95, 2.0), vec3(0.88, 0.9, 0.92), 0.0);
        box(o, d, vec3(0.38, 0.15, 1.7), vec3(0.62, 0.3, 1.9), vec3(0.7, 0.7, 0.72), 0.0);
        lampCol = vec3(1.0, 0.95, 0.85);
    } else if (kind == 4) {
        // a cat tree, a cat up on it, and one on the sill looking out
        box(o, d, vec3(1.0, -0.35, 1.4), vec3(1.08, 0.9, 1.48), vec3(0.75, 0.62, 0.42), 0.0);
        box(o, d, vec3(0.8, 0.2, 1.25), vec3(1.4, 0.26, 1.7), vec3(0.6, 0.45, 0.35), 0.0);
        box(o, d, vec3(0.85, 0.8, 1.2), vec3(1.35, 0.86, 1.65), vec3(0.6, 0.45, 0.35), 0.0);
        box(o, d, vec3(-0.6, -0.35, 1.6), vec3(0.1, -0.25, 1.98), vec3(0.75, 0.55, 0.65), 0.0);
        cat(o, d, vec2(1.1, 0.86), 1.42, 0.22, vec3(0.92, 0.6, 0.3));
        float bob = sin(ticks * 0.02) * 0.004;
        cat(o, d, vec2(0.66, -0.02 + bob), 0.22, 0.26, h1(k * 8.8) < 0.5 ? vec3(0.12, 0.11, 0.12) : vec3(0.55, 0.55, 0.58));
    } else if (kind == 5) {
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            plant(o, d, vec2(-0.5 + fi * 0.5, -0.35), 1.5 + 0.1 * sin(fi * 3.0), 0.55 + 0.2 * h1(fi + k), fi + k);
        }
        plant(o, d, vec2(0.25, -0.02), 0.3, 0.3, k + 9.0);
        lampCol = vec3(1.0, 0.55, 0.95); // (a grow light)
    } else if (kind == 6) {
        box(o, d, vec3(-0.2, -0.05, 1.45), vec3(1.0, 0.0, 1.98), vec3(0.45, 0.32, 0.22), 0.0);
        box(o, d, vec3(-0.15, -0.35, 1.5), vec3(-0.1, -0.05, 1.95), vec3(0.3, 0.22, 0.15), 0.0);
        box(o, d, vec3(0.9, -0.35, 1.5), vec3(0.95, -0.05, 1.95), vec3(0.3, 0.22, 0.15), 0.0);
        vec3 glowCol = vec3(0.7, 0.85, 1.0) * (0.85 + 0.15 * sin(ticks * 0.05));
        screen(o, d, vec2(0.15, 0.05), vec2(0.65, 0.38), 1.82, glowCol);
        // a bookshelf by the wall, full of books
        box(o, d, vec3(-0.7, -0.35, 0.6), vec3(-0.45, 1.1, 1.6), vec3(0.4, 0.28, 0.18), 0.0);
        if (bestT < 1.0e8 && bestN.x > 0.5 && bestCol == vec3(0.4, 0.28, 0.18)) {
            vec3 hp = o + d * bestT;
            float shelf = fract((hp.y + 0.35) * 3.4);
            float book = floor(hp.z * 22.0);
            bestCol = shelf < 0.12 ? vec3(0.4, 0.28, 0.18) : mix(vec3(0.6, 0.15, 0.15), vec3(0.2, 0.35, 0.6), h1(book)) * (0.6 + 0.4 * h1(book + 3.0));
        }
        extraPos = vec3(0.4, 0.2, 1.75);
        extraCol = glowCol * 0.5;
        lampCol = vec3(1.0, 0.9, 0.75);
    } else if (kind == 7) {
        for (int i = 0; i < 6; i++) {
            float fi = float(i);
            vec3 lo = vec3(-0.6 + mod(fi, 3.0) * 0.55 + h1(fi + k) * 0.1, -0.35 + floor(fi / 3.0) * 0.38, 1.3 + h1(fi * 2.0 + k) * 0.4);
            box(o, d, lo, lo + vec3(0.48, 0.36, 0.5), vec3(0.62, 0.46, 0.3) * (0.85 + 0.15 * h1(fi)), 0.0);
        }
        lamp = vec3(0.5, 1.0, 1.1);
        lampCol = vec3(1.0, 0.9, 0.6) * 0.6;
    } else if (kind == 8) {
        box(o, d, vec3(0.42, 0.95, 1.05), vec3(0.58, 1.11, 1.21), vec3(0.85, 0.88, 0.95), 1.0);
        lampCol = vec3(0.6, 0.3, 1.0);
    }
    p = o + d * bestT;

    // ---- its light ----
    float lit = lights;
    vec3 ambient = mix(vec3(0.02, 0.022, 0.035), vec3(0.42, 0.45, 0.5), outside) * (0.6 + 0.4 * exp(-p.z * 0.8));
    vec3 toLamp = lamp - p;
    float dl = length(toLamp);
    float diffuse = max(dot(bestN, toLamp / dl), 0.0) * 0.75 + 0.25;
    vec3 light = ambient + lit * lampCol * diffuse * 1.35 / (1.0 + dl * dl * 0.7);
    if (dot(extraCol, extraCol) > 0.0) {
        vec3 te = extraPos - p;
        float de = length(te);
        light += extraCol * (max(dot(bestN, te / de), 0.0) * 0.7 + 0.3) / (1.0 + de * de * 2.0);
    }
    if (kind == 8 && lit > 0.5) {
        // party lights sweeping round the walls
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            vec2 spot = vec2(0.5 + 1.1 * sin(ticks * 0.031 + fi * 2.1), 0.45 + 0.6 * sin(ticks * 0.043 + fi * 1.3));
            vec3 c = 0.5 + 0.5 * cos(vec3(0.0, 2.1, 4.2) + fi * 2.0 + ticks * 0.01);
            light += c * smoothstep(0.35, 0.0, length((sideHit ? p.zy : p.xy) - spot)) * 1.2;
        }
    }
    vec3 col = bestCol * light + bestCol * bestGlow * (0.6 + 0.4 * lit) * 1.2;
    // (further in is a little darker, and hazier)
    col *= 1.0 - 0.15 * clamp(p.z - 1.0, 0.0, 1.0);

    // ---- curtains, or blinds, just inside the glass ----
    float style = h1(k * 11.3);
    if (style < 0.55) {
        float cl = 0.12 + 0.2 * h1(k * 12.7), cr = 0.12 + 0.2 * h1(k * 13.9);
        vec2 at = o.xy + d.xy * (0.05 / d.z);
        if (at.x < cl || at.x > 1.0 - cr) {
            vec3 fabric = mix(vec3(0.75, 0.3, 0.3), vec3(0.85, 0.8, 0.6), h1(k * 14.1));
            float folds = 0.78 + 0.22 * sin(at.x * 70.0 + sin(at.y * 3.0 + ticks * 0.01) * 0.4);
            // (lit from the room behind them, the light glowing through)
            col = fabric * folds * (ambient * 1.2 + lit * lampCol * 0.55 + 0.25 * outside);
        }
    } else if (style < 0.75) {
        float down = 0.25 + 0.45 * h1(k * 15.5);
        vec2 at = o.xy + d.xy * (0.04 / d.z);
        if (at.y > 1.0 - down) {
            float slat = fract(at.y * 18.0);
            vec3 c = vec3(0.86, 0.84, 0.78) * (0.75 + 0.25 * slat) * (ambient * 1.4 + lit * lampCol * 0.5 + 0.3 * outside);
            col = slat < 0.85 ? c : col;
        }
    }

    // ---- the glass: the sky a little in it, more as you look along it, and a smear of shine ----
    float fresnel = 0.05 + 0.6 * pow(1.0 - d.z, 5.0);
    vec3 reflection = sky * (0.7 + 0.3 * smoothstep(-0.3, 0.6, -d.y));
    col = mix(col, reflection, fresnel);
    col += vec3(0.06) * outside * smoothstep(0.08, 0.0, abs(uv.x + uv.y * 0.6 - 0.75 - 0.25 * h1(k)));
    fragColor = vec4(col, 1.0);
}
