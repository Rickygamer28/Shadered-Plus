#version 150

// Shadered+'s kaleidoscope (our own code): looking into each face of the block, a turning N-fold pattern of coloured
// glass. Inside, three layers of loose glass - big shards at the back, smaller ones, round beads at the front - lit
// from behind by a frosted glass plate, each layer turning its own way (so they slide over each other) and seen at its
// own depth (moving your head, the layers shift); folded round the middle into mirrored wedges, and the box's walls
// mirrors too, the pattern going on in them. The light pulses with music.

in vec3 lp;
in vec3 lray;
flat in vec3 normal;
flat in vec3 settings;
flat in vec4 region;

uniform float Turn;  // seconds of turning (this one's own: stops when it's powered)
uniform float Boost; // music

out vec4 fragColor;

const float TAU = 6.2831853;

const vec3 PAL[30] = vec3[30](
    // jewels
    vec3(0.92, 0.10, 0.20), vec3(0.10, 0.30, 0.95), vec3(0.10, 0.80, 0.35), vec3(1.00, 0.75, 0.10), vec3(0.65, 0.20, 0.90),
    // cathedral glass
    vec3(0.78, 0.08, 0.10), vec3(0.08, 0.18, 0.72), vec3(0.95, 0.70, 0.15), vec3(0.15, 0.55, 0.20), vec3(0.95, 0.92, 0.80),
    // ocean
    vec3(0.00, 0.45, 0.80), vec3(0.00, 0.75, 0.75), vec3(0.25, 0.92, 0.85), vec3(0.05, 0.20, 0.58), vec3(0.72, 0.95, 1.00),
    // sunset
    vec3(1.00, 0.45, 0.10), vec3(1.00, 0.22, 0.40), vec3(0.70, 0.15, 0.62), vec3(1.00, 0.80, 0.30), vec3(0.38, 0.10, 0.52),
    // autumn
    vec3(0.88, 0.35, 0.05), vec3(0.95, 0.70, 0.10), vec3(0.32, 0.60, 0.15), vec3(0.62, 0.15, 0.05), vec3(0.92, 0.86, 0.40),
    // frost
    vec3(0.75, 0.90, 1.00), vec3(0.88, 0.75, 1.00), vec3(1.00, 0.80, 0.90), vec3(0.70, 1.00, 0.90), vec3(1.00, 1.00, 0.86)
);

vec2 hash22(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    q += dot(q, q.yzx + 33.33);
    return fract((q.xx + q.yz) * q.zy);
}

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, s, -s, c);
}

// the pattern's wedge: q folded round the middle into 2N mirrored slices
vec2 fold(vec2 q, float n) {
    float r = length(q);
    float seg = TAU / n;
    float a = atan(q.y, q.x);
    a = mod(a, seg);
    a = abs(a - seg * 0.5);
    return r * vec2(cos(a), sin(a));
}

// one layer of glass at f: rgb what it lets through, a how much of it is glass
vec4 glassLayer(vec2 f, float layer, float palette, float seed) {
    float scale = layer < 0.5 ? 2.3 : layer < 1.5 ? 3.6 : 4.8;
    vec2 g = f * scale + vec2(seed * 7.13 + layer * 3.1, seed * 3.71 - layer * 5.3);
    vec2 i = floor(g), fr = fract(g);
    float d1 = 8.0, d2 = 8.0;
    vec2 id = vec2(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(float(x), float(y));
            vec2 h = hash22(i + o);
            vec2 pt = o + 0.5 + 0.38 * sin(Turn * (0.25 + 0.2 * layer) + TAU * h); // (the pieces tumbling a little)
            float d = length(pt - fr);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                id = i + o;
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    float h = hash12(id + layer * 17.0 + seed);
    float which = floor(hash12(id * 1.7 + 3.0 + seed) * 4.999);
    vec3 col = PAL[int(palette) * 5 + int(which)];
    if (layer > 1.5) {
        // beads: round, a highlight on each
        float cover = (h < 0.45 ? 1.0 : 0.0) * (1.0 - smoothstep(0.24, 0.29, d1));
        vec3 through = mix(vec3(1.0), col, 0.9) * (0.75 + 0.5 * smoothstep(0.29, 0.0, d1));
        return vec4(through, cover);
    }
    float cover = h < (layer < 0.5 ? 0.7 : 0.55) ? 1.0 : 0.0;
    float edge = smoothstep(0.0, 0.07, d2 - d1);           // (the shards' edges: dark lines between them)
    vec3 through = mix(vec3(1.0), col, 0.85) * mix(0.25, 1.0, edge);
    through *= 0.85 + 0.3 * smoothstep(0.1, 0.5, d2 - d1);  // (thicker in the middle of each)
    return vec4(through, cover);
}

// the box's walls are mirrors too: points past them (at +-L) seen in the mirrors (folded back), a little darker each time
vec2 mirrorWalls(vec2 q, vec2 L, out float dim) {
    vec2 k = floor((abs(q) + L) / (2.0 * L));
    dim = pow(0.78, k.x + k.y);
    return abs(mod(q - L, 4.0 * L) - 2.0 * L) - L;
}

// the window's middle and half its size (block units, on the side's own axes), its scale (the shorter half)
vec2 gCentre, gHalf;
float gS;

// where p is on the side, across the (joined) window: -1..1 across its shorter way
vec2 faceUv(vec3 p, vec3 n) {
    return abs(n.y) > 0.5 ? p.xz : abs(n.z) > 0.5 ? p.xy : p.zy;
}

vec2 faceCoords(vec3 p, vec3 n) {
    return (faceUv(p, n) - gCentre) / gS;
}

void main() {
    vec3 rd = normalize(lray);
    vec3 n = normalize(normal);
    float cosd = dot(rd, -n);
    if (cosd <= 0.0) {
        discard; // (its back)
    }
    cosd = max(cosd, 0.06);
    float sym = settings.x;
    float n2 = sym < 0.5 ? 3.0 : sym < 1.5 ? 4.0 : sym < 2.5 ? 5.0 : sym < 3.5 ? 6.0 : sym < 4.5 ? 8.0 : 12.0;
    float palette = min(settings.y, 5.0);
    float seed = settings.z;
    float zoom = 1.0 - 0.05 * Boost;
    // the window (joined with the ones beside it: one big one)
    gCentre = vec2(0.5) + region.xy;
    gHalf = region.zw * 0.5 - 0.125;
    gS = min(gHalf.x, gHalf.y);
    vec2 L = gHalf / gS;
    float deep = gS / 0.375;                // (a bigger window: deeper inside)
    float detail = sqrt(deep);              // (and more pieces of glass)

    vec2 q0 = faceCoords(lp, n);
    // the frosted plate at the back, lit from behind
    vec2 qb = faceCoords(lp + rd * (0.42 * deep / cosd), n);
    vec3 col = vec3(1.0, 0.96, 0.88) * (1.0 + 0.6 * Boost) * (1.0 - 0.1 * min(dot(qb, qb), 2.0));
    // the layers, back to front
    for (int i = 0; i < 3; i++) {
        float layer = float(i);
        float depth = (0.33 - 0.1 * layer) * deep;
        vec2 q = faceCoords(lp + rd * (depth / cosd), n);
        float wd;
        q = mirrorWalls(q, L, wd);
        float dir = i == 1 ? -1.0 : 1.0;
        q = rot(dir * Turn * (0.1 + 0.045 * layer) + seed * 0.7) * q * zoom * detail;
        vec4 g = glassLayer(fold(q, n2), layer, palette, seed);
        col *= mix(vec3(1.0), g.rgb, g.a) * mix(1.0, wd, 0.5);
    }
    // the window: a little shadow from the frame round it, the glass in front catching the light
    vec2 toEdge = gHalf - abs(faceUv(lp, n) - gCentre); // (blocks)
    col *= mix(0.5, 1.0, smoothstep(0.0, 0.05, min(toEdge.x, toEdge.y)));
    float glint = pow(max(0.0, 1.0 - abs(q0.x * 0.8 + q0.y - 0.35 + rd.y * 0.4) * 2.5), 6.0);
    col += vec3(0.06) * glint;
    col = mix(col, vec3(0.04, 0.035, 0.03), pow(1.0 - cosd, 5.0) * 0.6);
    col = col / (1.0 + 0.12 * col);
    fragColor = vec4(col, 1.0);
}
