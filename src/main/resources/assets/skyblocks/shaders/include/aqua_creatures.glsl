// Shadered+'s sea creatures (our own code), shared by the aquariums (core/aquarium.fsh) and the underwater sky
// (core/underwater_sky.fsh): every fish and creature's shape (signed distance, in its own frame: x forward, y up, z
// sideways, in units of its size) and colours, and how they swim (tail beat, fins, a jellyfish's squeeze).
// Needs, before it: hash22(vec2), and Deep (1 in the deep sea aquarium).

// A species: body radii (along, up, sideways), tail radii, fin radii (a dorsal fin; the turtle's flippers), how fast.
// 0-9 the normal tank (clownfish, blue tang, yellow tang, neon tetra, angelfish, goldfish, betta, pufferfish, guppy,
// discus); 10-19 the ocean tank (shark, tuna, manta ray, sea turtle, grouper, jellyfish, dolphin, sardine, ocean
// sunfish, basking shark).
struct Species {
    vec3 body;
    vec3 tail;
    vec3 fin;
    float speed;
};

Species species(int k) {
    if (k == 0) return Species(vec3(0.16, 0.075, 0.035), vec3(0.06, 0.065, 0.01), vec3(0.07, 0.03, 0.008), 1.0);
    if (k == 1) return Species(vec3(0.16, 0.09, 0.03), vec3(0.06, 0.07, 0.01), vec3(0.12, 0.03, 0.008), 1.0);
    if (k == 2) return Species(vec3(0.13, 0.11, 0.03), vec3(0.05, 0.06, 0.01), vec3(0.11, 0.08, 0.008), 0.9);
    if (k == 3) return Species(vec3(0.11, 0.04, 0.025), vec3(0.04, 0.04, 0.008), vec3(0.0), 1.4);
    if (k == 4) return Species(vec3(0.11, 0.13, 0.025), vec3(0.05, 0.08, 0.008), vec3(0.06, 0.15, 0.008), 0.6);
    if (k == 5) return Species(vec3(0.13, 0.09, 0.065), vec3(0.09, 0.09, 0.012), vec3(0.06, 0.04, 0.008), 0.7);
    if (k == 6) return Species(vec3(0.12, 0.05, 0.03), vec3(0.13, 0.17, 0.008), vec3(0.11, 0.11, 0.008), 0.6);
    if (k == 7) return Species(vec3(0.14, 0.085, 0.08), vec3(0.045, 0.05, 0.006), vec3(0.0), 0.5);
    if (k == 8) return Species(vec3(0.08, 0.03, 0.02), vec3(0.085, 0.075, 0.006), vec3(0.04, 0.035, 0.005), 1.3);
    if (k == 9) return Species(vec3(0.12, 0.13, 0.025), vec3(0.04, 0.05, 0.008), vec3(0.11, 0.035, 0.008), 0.6);
    if (k == 10) return Species(vec3(0.9, 0.18, 0.16), vec3(0.25, 0.3, 0.03), vec3(0.18, 0.22, 0.03), 0.8);
    if (k == 11) return Species(vec3(0.45, 0.13, 0.11), vec3(0.12, 0.18, 0.02), vec3(0.08, 0.1, 0.02), 1.3);
    if (k == 12) return Species(vec3(0.35, 0.05, 0.75), vec3(0.35, 0.015, 0.015), vec3(0.0), 0.6);
    if (k == 13) return Species(vec3(0.4, 0.16, 0.33), vec3(0.12, 0.08, 0.09), vec3(0.18, 0.03, 0.55), 0.5);
    if (k == 14) return Species(vec3(0.55, 0.25, 0.2), vec3(0.15, 0.2, 0.03), vec3(0.3, 0.08, 0.02), 0.5);
    if (k == 15) return Species(vec3(0.25, 0.18, 0.25), vec3(0.06, 0.4, 0.06), vec3(0.0), 0.25);
    if (k == 16) return Species(vec3(0.8, 0.17, 0.17), vec3(0.12, 0.04, 0.28), vec3(0.15, 0.18, 0.03), 1.1);
    if (k == 18) return Species(vec3(0.5, 0.42, 0.12), vec3(0.05, 0.4, 0.02), vec3(0.14, 0.6, 0.03), 0.35); // ocean sunfish
    if (k == 19) return Species(vec3(1.25, 0.3, 0.27), vec3(0.22, 0.4, 0.04), vec3(0.2, 0.38, 0.045), 0.45); // basking shark
    // the bottom creatures: how far they reach (forward/back, up, sideways), for the quick tests
    if (k == 20) return Species(vec3(0.15, 0.13, 0.11), vec3(0.0), vec3(0.0), 0.5);  // hermit crab
    if (k == 21) return Species(vec3(0.16, 0.09, 0.17), vec3(0.0), vec3(0.0), 1.0);  // crab
    if (k == 22) return Species(vec3(0.63, 0.08, 0.31), vec3(0.0), vec3(0.0), 0.8);  // stingray
    if (k == 23) return Species(vec3(0.17, 0.13, 0.17), vec3(0.0), vec3(0.0), 0.5);  // octopus
    if (k == 24) return Species(vec3(0.38, 0.1, 0.17), vec3(0.0), vec3(0.0), 0.4);   // lobster
    if (k == 25) return Species(vec3(0.13, 0.08, 0.06), vec3(0.0), vec3(0.0), 0.8);  // shrimp
    if (k == 35) return Species(vec3(0.16, 0.1, 0.07), vec3(0.0), vec3(0.0), 0.8);   // cleaner shrimp (its feelers longer)
    // the game's own water mobs
    if (k == 26) return Species(vec3(0.26, 0.07, 0.05), vec3(0.07, 0.08, 0.008), vec3(0.15, 0.05, 0.008), 0.8);   // cod
    if (k == 27) return Species(vec3(0.25, 0.075, 0.05), vec3(0.08, 0.1, 0.009), vec3(0.08, 0.07, 0.008), 1.1);   // salmon
    if (k == 28) return Species(vec3(0.12, 0.085, 0.03), vec3(0.05, 0.065, 0.008), vec3(0.1, 0.06, 0.008), 0.9);  // tropical fish
    if (k == 29) return Species(vec3(0.05, 0.04, 0.036), vec3(0.095, 0.036, 0.004), vec3(0.0), 1.0);             // tadpole
    if (k == 30 || k == 31) return Species(vec3(0.3, 0.07, 0.07), vec3(0.13, 0.0, 0.0), vec3(0.0), 0.7);         // squid, glow squid
    if (k == 32) return Species(vec3(0.25, 0.1, 0.1), vec3(0.0), vec3(0.0), 0.6);                                 // axolotl (on the bottom)
    // the deep sea's
    if (k == 33) return Species(vec3(0.13, 0.1, 0.09), vec3(0.045, 0.05, 0.006), vec3(0.04, 0.03, 0.006), 0.3);    // anglerfish
    if (k == 34) return Species(vec3(0.09, 0.03, 0.02), vec3(0.035, 0.035, 0.005), vec3(0.04, 0.02, 0.005), 1.3);  // lanternfish
    return Species(vec3(0.12, 0.035, 0.025), vec3(0.04, 0.035, 0.006), vec3(0.0), 1.5);
}

// the creatures that walk on the bottom (20-25, the axolotl), and the squid (jetting mantle first, arms trailing)
bool isCrawler(int k) {
    return (k >= 20 && k <= 25) || k == 32 || k == 35;
}

bool isSquid(int k) {
    return k == 30 || k == 31;
}

// a fish with a fish's body (fins, scales, eyes): not a bottom creature, a squid, the manta, turtle or jellyfish
bool isFish(int k) {
    return !isCrawler(k) && !isSquid(k) && k != 12 && k != 13 && k != 15;
}

// the game's sixteen dye colours (a tropical fish's two colours are two of these)
vec3 dyeColour(int i) {
    if (i == 0) return vec3(0.976, 1.0, 0.996);
    if (i == 1) return vec3(0.976, 0.502, 0.114);
    if (i == 2) return vec3(0.78, 0.306, 0.741);
    if (i == 3) return vec3(0.227, 0.702, 0.855);
    if (i == 4) return vec3(0.996, 0.847, 0.239);
    if (i == 5) return vec3(0.502, 0.78, 0.122);
    if (i == 6) return vec3(0.953, 0.545, 0.667);
    if (i == 7) return vec3(0.278, 0.31, 0.322);
    if (i == 8) return vec3(0.616, 0.616, 0.592);
    if (i == 9) return vec3(0.086, 0.612, 0.612);
    if (i == 10) return vec3(0.537, 0.196, 0.722);
    if (i == 11) return vec3(0.235, 0.267, 0.667);
    if (i == 12) return vec3(0.514, 0.329, 0.196);
    if (i == 13) return vec3(0.369, 0.486, 0.086);
    if (i == 14) return vec3(0.69, 0.18, 0.149);
    return vec3(0.114, 0.114, 0.129);
}

vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// how close to the edge of a cell of a random net (small on the lines): the discus's and the turtle's patterns
float cellEdge(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float f1 = 8.0, f2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            float d = length(g + hash22(i + g) - f);
            if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) { f2 = d; }
        }
    }
    return f2 - f1;
}

// smooth random variation (0..1), and a few layers of it (finer and finer): blotches, mottling, uneven colour
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash22(i).x, hash22(i + vec2(1.0, 0.0)).x, f.x), mix(hash22(i + vec2(0.0, 1.0)).x, hash22(i + vec2(1.0, 1.0)).x, f.x), f.y);
}

float fbm(vec2 p) {
    return 0.5 * vnoise(p) + 0.3 * vnoise(p * 2.03 + 7.1) + 0.2 * vnoise(p * 4.1 + 3.3);
}

// 1 inside a round spot (one per cell, placed at random), 0 elsewhere
float spot(vec2 p, float r) {
    vec2 o = hash22(floor(p)) * 0.5 + 0.25;
    return 1.0 - smoothstep(r * 0.8, r, length(fract(p) - o));
}

// a fish's colour at a point on it (q: that part's own coordinates, x along it nose-first, -1..1; part 0 body,
// 1 tail (the turtle's head and flippers; the jellyfish's arms), 2 fin (the jellyfish's tentacles); on the fins
// q.y is the height out from the body and q.z says which: 0 the back's, -1 underneath, 2 a side fin, 3 a filament);
// rnd: this fish's own random number (colour variants). Each matched to a photo of the real fish.
vec3 critterColour(int kind, vec3 p, int part, float rnd);

// a lanternfish's lights: small round glowing spots in two rows along its belly and lower side (0..1)
float photophores(vec3 q) {
    float m = 0.0;
    for (int r = 0; r < 2; r++) {
        float y0 = r == 0 ? -0.42 : -0.72;
        vec2 d = vec2((fract(q.x * 6.5 + float(r) * 0.5) - 0.5) / 6.5, q.y - y0);
        if (q.x > -0.8 && q.x < 0.75) m = max(m, smoothstep(0.075, 0.045, length(d * vec2(1.0, 0.85))));
    }
    return m;
}
vec3 squidColour(int kind, vec3 p, int part, float rnd);

vec3 fishColour(int kind, vec3 q, int part, float rnd) {
    if (isCrawler(kind)) {
        return critterColour(kind, q, part, rnd);
    }
    if (isSquid(kind)) {
        return squidColour(kind, q, part, rnd);
    }
    bool tail = part == 1, fin = part == 2;
    bool anal = fin && q.z < -0.5, pect = fin && q.z > 1.5 && q.z < 2.5, fil = fin && q.z > 2.5;
    bool dorsal = fin && !anal && !pect && !fil;
    float tk = 0.5 - 0.5 * q.x; // along the tail: 0 at its root, 1 at its tip
    if (kind == 0) { // clownfish: orange, three white bands edged black, black-rimmed fins
        vec3 o = mix(vec3(1.0, 0.56, 0.12), vec3(0.95, 0.35, 0.02), smoothstep(-0.5, 0.6, q.y)) * (0.93 + 0.12 * fbm(q.xy * 7.0));
        if (part == 0) {
            float wob = (fbm(q.xy * vec2(5.0, 7.0) + rnd * 3.0) - 0.5) * 0.05;
            float b1 = abs(q.x - 0.4 + 0.1 * q.y * q.y) - 0.08;  // behind the eye, curving
            float b2 = abs(q.x + 0.1 - 0.12 * q.y) - 0.11;        // the middle, leaning forward at the top
            float b3 = abs(q.x + 0.86) - 0.06;                     // at the tail root
            float b = min(b1, min(b2, b3)) + wob;
            return b < 0.0 ? vec3(0.97) * (0.95 + 0.05 * fbm(q.xy * 12.0)) : b < 0.035 ? vec3(0.03) : o;
        }
        float edge = tail ? smoothstep(0.8, 0.88, tk) : pect ? smoothstep(0.62, 0.78, abs(q.x)) : smoothstep(0.72, 0.84, abs(q.y));
        return mix(o, vec3(0.03), edge);
    }
    if (kind == 1) { // blue tang: royal blue, the black "palette" mark with a blue window, yellow tail
        vec3 blue = vec3(0.08, 0.3, 0.95) * (0.85 + 0.25 * fbm(q.xy * 8.0 + rnd * 5.0)), black = vec3(0.02, 0.03, 0.08), yellow = vec3(1.0, 0.85, 0.08);
        if (tail) return abs(q.y) > 0.78 ? black : yellow;
        if (pect) return yellow;
        if (dorsal) return q.y > 0.72 ? blue : black;
        if (anal) return abs(q.y) > 0.72 ? black : blue;
        float lower = mix(0.18, -0.22, smoothstep(0.6, -0.1, q.x)) + 0.24 * smoothstep(-0.4, -0.95, q.x);
        bool mark = q.x < 0.58 && q.y > lower && q.y < 0.8;
        bool window = length((q.xy - vec2(-0.12, 0.42)) / vec2(0.36, 0.15)) < 1.0;
        return mark && !window ? black : blue;
    }
    if (kind == 2) { // yellow tang: all yellow, a little green on the belly, a white spine at the tail root
        vec3 y = vec3(1.0, 0.86, 0.05);
        if (part > 0) return y * 0.95;
        if (q.x < -0.84 && abs(q.y) < 0.08) return vec3(0.95);
        y *= 0.95 + 0.05 * sin(q.y * 45.0 + sin(q.x * 8.0) * 2.0);
        return mix(y, vec3(0.78, 0.9, 0.15), smoothstep(-0.1, -0.6, q.y) * 0.6) * (0.94 + 0.1 * fbm(q.xy * 6.0));
    }
    if (kind == 3) { // neon tetra: glowing blue line, red rear belly, silvery, see-through fins
        if (part > 0) return tail && tk < 0.2 && abs(q.y) < 0.3 ? vec3(0.9, 0.2, 0.2) : vec3(0.55, 0.65, 0.85);
        if (abs(q.y - 0.12) < 0.13 && q.x < 0.6 && q.x > -0.6) return vec3(0.15, 0.85, 1.0) * 1.35;
        if (q.y < -0.01 && q.y > -0.8 && q.x < 0.15) return vec3(1.0, 0.15, 0.12) * 1.1;
        return q.y > 0.0 ? vec3(0.55, 0.58, 0.62) : vec3(0.88, 0.88, 0.92);
    }
    if (kind == 4) { // angelfish: silver, black bands (one through the eye), orange shoulders, long fins and filaments
        vec3 silver = vec3(0.86, 0.86, 0.9), black = vec3(0.06), orange = vec3(0.95, 0.42, 0.12);
        if (fil) return vec3(0.7, 0.82, 1.0);
        if (tail) return mix(silver, vec3(1.0), 0.3);
        if (pect) return silver;
        if (fin) return q.x > 0.6 ? black : abs(fract(q.x * 1.6 + 0.3) - 0.5) < 0.1 ? black * 2.0 : silver * 0.95;
        float b = min(abs(q.x - 0.62), min(abs(q.x - 0.18), abs(q.x + 0.3)));
        if (b < 0.07) return black;
        if (q.x > 0.7) return vec3(0.78, 0.62, 0.45);
        if (q.y > 0.1 && q.x > -0.3) return mix(silver, orange, smoothstep(0.1, 0.45, q.y));
        return silver;
    }
    if (kind == 5) { // goldfish: deep orange back, golden sides, pale belly
        if (part > 0) return vec3(1.0, 0.52, 0.1);
        vec3 c = mix(vec3(1.0, 0.68, 0.15), vec3(0.95, 0.42, 0.04), smoothstep(0.1, 0.7, q.y));
        c = mix(c, vec3(1.0, 0.82, 0.45), smoothstep(-0.1, -0.7, q.y));
        return mix(c, vec3(1.0, 0.38, 0.08), smoothstep(0.55, 0.9, q.x) * 0.6) * (0.88 + 0.2 * fbm(q.xy * 5.0 + rnd * 4.0));
    }
    if (kind == 6) { // betta: blue body, reddish head, huge fins blue at the base going red, pale tips
        vec3 blue = vec3(0.2, 0.25, 0.95), red = vec3(0.9, 0.12, 0.08);
        if (rnd > 0.6) { blue = rnd > 0.8 ? vec3(0.1, 0.15, 0.85) : vec3(0.55, 0.1, 0.75); red = blue * 1.3 + 0.05; }
        if (part == 0) return mix(blue * (0.8 + 0.35 * fbm(q.xy * 9.0)), vec3(0.55, 0.15, 0.25), smoothstep(0.45, 0.7, q.x));
        float away = tail ? tk : pect ? 0.3 : abs(q.y);
        vec3 c = mix(blue, red, smoothstep(0.1, 0.45, away));
        c *= 0.8 + 0.2 * sin((tail ? q.y : q.x) * 25.0 + fbm(q.xy * 4.0) * 3.0); // (streaks)
        c = mix(c, c * 1.3, smoothstep(0.55, 0.8, fbm(q.xy * 6.0 + rnd * 7.0)) * 0.5);
        return mix(c, vec3(0.95, 0.85, 0.85), smoothstep(0.85, 1.0, away) * 0.7);
    }
    if (kind == 7) { // porcupine pufferfish: pale cream, dense small black spots, a greyer band low on its side,
                     // a white belly, pink lips, see-through yellowish fins with black spots
        if (part > 0) {
            vec3 f = vec3(0.95, 0.82, 0.35) * (1.0 - 0.75 * spot(q.xy * vec2(6.0, 5.0) + rnd * 3.0, 0.16));
            return mix(vec3(1.0, 0.88, 0.45), f, smoothstep(0.0, 0.35, abs(q.y) + (1.0 - abs(q.x)) * 0.1)); // (paler at the base)
        }
        if (q.x > 1.1) return vec3(0.92, 0.58, 0.56) * (0.9 + 0.1 * fbm(vec2(q.z * 9.0, 1.0))); // (lips)
        float ang = atan(q.y, q.z);
        vec2 uv = vec2(q.x * 0.14, ang * 0.08) / 0.0105;
        float dots = spot(uv + rnd * 7.0, 0.13 + 0.12 * vnoise(uv * 0.3)) + spot(uv * 1.6 + 3.3, 0.1) * 0.8;
        vec3 c = mix(vec3(0.82, 0.66, 0.2), vec3(1.0, 0.84, 0.28), smoothstep(0.75, 0.2, q.y)); // (deeper yellow on top)
        c = mix(c, vec3(0.66, 0.52, 0.18), smoothstep(0.12, 0.0, abs(q.y + 0.38)) * 0.7);        // (the band low on its side)
        float belly = smoothstep(-0.42, -0.55, q.y);
        c *= 1.0 - 0.88 * min(dots, 1.0) * (1.0 - belly) * smoothstep(1.02, 0.92, q.x);
        c = mix(c, vec3(1.0, 0.93, 0.62), belly);
        return c * (0.95 + 0.08 * fbm(q.xy * 5.0));
    }
    if (kind == 8) { // guppy: silver-green front, deep blue back half, a big orange tail with black blotches
        if (part > 0) {
            vec3 c = rnd < 0.5 ? vec3(1.0, 0.45, 0.1) : hue(rnd) * 1.05;
            float bl = fbm(q.xy * vec2(5.0, 7.0) + rnd * 9.0);
            c = mix(c, vec3(0.04), smoothstep(0.55, 0.6, bl));
            c = mix(c, c * 1.25 + 0.05, smoothstep(0.3, 0.2, bl) * 0.6);
            return tail ? mix(c, vec3(0.8, 0.78, 0.75), smoothstep(0.85, 1.0, tk)) : c;
        }
        vec3 c = mix(vec3(0.12, 0.2, 0.55), vec3(0.62, 0.75, 0.78), smoothstep(-0.2, 0.4, q.x));
        if (q.x < -0.7) c = mix(c, vec3(0.3, 0.75, 0.85), 0.5);
        return q.y < -0.4 ? mix(c, vec3(0.85, 0.85, 0.88), 0.5) : c;
    }
    if (kind == 9) { // discus: cream with a red net pattern, orange face, red-striped fins, clear tail
        vec3 cream = vec3(0.98, 0.92, 0.85), red = vec3(0.9, 0.22, 0.08);
        if (tail) return vec3(0.85, 0.86, 0.82);
        if (fin) return fract(q.x * 9.0) < 0.35 || abs(q.y) > 0.8 ? red : cream;
        float ce = cellEdge(q.xy * vec2(4.0, 5.0) + rnd * 7.0 + (vec2(fbm(q.xy * 6.0), fbm(q.yx * 6.0 + 3.0)) - 0.5) * 0.5);
        vec3 c = mix(red * (0.85 + 0.2 * fbm(q.xy * 9.0)), cream, smoothstep(0.09, 0.14, ce));
        return q.x > 0.62 ? mix(c, vec3(1.0, 0.7, 0.45), 0.6) : c;
    }
    if (kind == 10) { // mako shark: metallic blue-grey above, a sharp white belly, gill slits
        vec3 top = vec3(0.3, 0.4, 0.56), belly = vec3(0.93, 0.94, 0.96);
        if (part > 0) return top * (dorsal ? 0.8 : 0.9);
        top *= 0.9 + 0.14 * fbm(q.xy * vec2(10.0, 6.0) + rnd * 4.0);
        float line = -0.15 + 0.1 * q.x + (fbm(q.xy * vec2(6.0, 3.0)) - 0.5) * 0.1;
        vec3 c = mix(belly, top, smoothstep(line - 0.06, line + 0.06, q.y));
        if (q.x > 0.25 && q.x < 0.5 && fract((q.x - 0.25) * 20.0) < 0.2 && abs(q.y + 0.05) < 0.35) c *= 0.6;
        if (q.x > 0.72 && abs(q.y + 0.38 - (q.x - 0.72) * 0.4) < 0.035) c = vec3(0.15, 0.1, 0.1); // (mouth)
        return c;
    }
    if (kind == 11) { // bluefin tuna: navy back, steel-blue band, silver belly, golden first fin, yellow finlets
        if (tail) return vec3(0.14, 0.15, 0.2);
        if (pect) return vec3(0.12, 0.13, 0.18);
        if (dorsal) return q.x > -0.2 ? vec3(0.7, 0.55, 0.25) : vec3(0.15, 0.16, 0.22);
        if (anal) return vec3(0.6, 0.6, 0.62);
        vec3 c = mix(vec3(0.86, 0.89, 0.92), vec3(0.35, 0.45, 0.62), smoothstep(-0.05, 0.15, q.y));
        c = mix(c, vec3(0.04, 0.07, 0.16), smoothstep(0.3, 0.5, q.y));
        if (q.y < 0.0) c *= 0.94 + 0.06 * step(0.5, fract(q.x * 16.0 + q.y * 3.0 + fbm(q.xy * 5.0)));
        return c;
    }
    if (kind == 12) { // manta ray: speckled dark brown on top, white underneath, pale-tipped head fins
        vec3 top = vec3(0.17, 0.14, 0.11);
        if (part == 2) return mix(top, vec3(0.88, 0.85, 0.72), smoothstep(0.0, 0.6, q.x));
        if (part == 0 && q.y < 0.0) return vec3(0.9, 0.9, 0.92) * (1.0 - 0.75 * spot(q.xz * 6.0 + rnd * 5.0, 0.1 + 0.08 * vnoise(q.xz * 3.0)));
        vec3 c = mix(top, vec3(0.6), step(0.9, hash22(floor(q.xz * 40.0)).x) * 0.6);
        if (part == 0) c = mix(c, vec3(0.78, 0.78, 0.76), smoothstep(0.12, 0.04, abs(q.x - 0.35 + abs(q.z) * 0.7) - 0.05 * fbm(q.xz * 8.0)) * step(0.12, abs(q.z)) * step(abs(q.z), 0.6)); // (shoulder marks)
        return c * (0.9 + 0.15 * fbm(q.xz * 7.0));
    }
    if (kind == 13) { // green sea turtle: red-brown shell with streaked plates, cream-netted scaly skin
        if (part == 0) {
            if (q.y < -0.25) return vec3(0.9, 0.84, 0.66);
            float e = cellEdge(q.xz * vec2(2.0, 2.4) + rnd * 3.0);
            float streak = 0.5 + 0.5 * sin(q.x * 31.0 + sin(q.z * 9.0) * 3.0);
            vec3 c = mix(vec3(0.48, 0.21, 0.09), vec3(0.7, 0.42, 0.2), streak * 0.5 + smoothstep(0.0, 0.5, e) * 0.3);
            return mix(vec3(0.82, 0.68, 0.48), c * (0.85 + 0.25 * fbm(q.xz * 9.0 + rnd * 3.0)), smoothstep(0.03, 0.08, e));
        }
        if (q.y < -0.4) return vec3(0.9, 0.85, 0.7);
        return cellEdge(vec2(q.x * 4.0, q.z * 4.0 + q.y * 3.0)) < 0.15 ? vec3(0.9, 0.84, 0.68) : vec3(0.45, 0.22, 0.12);
    }
    if (kind == 14) { // giant grouper: blue-grey with pale spots on the front, dark fins with yellowish spotted edges
        if (part > 0) {
            float edge = tail ? smoothstep(0.6, 0.9, tk) : pect ? smoothstep(0.3, 0.8, abs(q.x)) : smoothstep(0.5, 0.9, abs(q.y));
            return mix(vec3(0.24, 0.26, 0.3), vec3(0.7, 0.62, 0.3), spot(q.xy * 10.0, 0.18) * edge);
        }
        vec3 base = mix(vec3(0.42, 0.47, 0.54), vec3(0.32, 0.36, 0.42), smoothstep(-0.2, 0.6, q.y));
        base = mix(base, base * 0.7, smoothstep(0.5, 0.65, fbm(q.xy * vec2(5.0, 4.0) + rnd * 5.0)));
        return mix(base, vec3(0.86, 0.88, 0.9), spot(q.xy * vec2(9.0, 7.0) + rnd * 3.0, 0.13) * smoothstep(-0.3, 0.3, q.x) * 0.85);
    }
    if (kind == 15 && Deep > 0.5) { // the deep sea's: a deep red crown jellyfish, a ring of blue lights round its rim
        if (part > 0) return vec3(0.6, 0.12, 0.15);
        float rim = smoothstep(0.25, 0.0, abs(q.y + 0.15)) * step(0.5, fract(atan(q.z, q.x) * 2.546));
        return mix(vec3(0.5, 0.04, 0.08) * (0.8 + 0.4 * fbm(q.xz * 5.0)), vec3(0.3, 0.6, 1.0), rim);
    }
    if (kind == 15) { // jellyfish: mostly sea nettles (orange, dark stripes), some pink-purple ones
        bool nettle = rnd < 0.6;
        if (part == 1) return nettle ? vec3(0.98, 0.9, 0.86) : vec3(0.9, 0.75, 1.0);
        if (part == 2) return nettle ? vec3(1.0, 0.55, 0.45) : vec3(0.95, 0.6, 0.95);
        vec3 bell = nettle ? vec3(1.0, 0.55, 0.22) : mix(vec3(1.0, 0.45, 0.85), vec3(0.6, 0.4, 1.0), rnd);
        if (nettle && q.y > -0.3 && abs(fract(atan(q.z, q.x) * 2.546) - 0.5) < 0.1) bell = vec3(0.5, 0.1, 0.07);
        bell *= 0.9 + 0.2 * fbm(vec2(atan(q.z, q.x) * 3.0, q.y * 4.0));
        return mix(bell, vec3(1.0, 0.9, 0.85), smoothstep(-0.2, -0.7, q.y)) * 1.15;
    }
    if (kind == 16) { // dolphin: dark grey back, lighter sides, pale belly, a smile
        vec3 dark = vec3(0.36, 0.39, 0.44);
        if (part > 0) return dark;
        vec3 c = mix(vec3(0.86, 0.86, 0.88), vec3(0.55, 0.58, 0.63), smoothstep(-0.55, -0.25, q.y));
        c = mix(c, dark, smoothstep(0.1, 0.5, q.y));
        if (q.x > 0.78 && abs(q.y + 0.12 + (q.x - 0.78) * 0.3) < 0.025) c *= 0.55;
        c *= 0.95 + 0.08 * fbm(q.xy * vec2(3.0, 9.0) + rnd * 6.0);
        if (abs(fract(q.x * 6.0 + q.y * 9.0 + rnd * 3.0) - 0.5) < 0.012 && vnoise(q.xy * 4.0 + rnd) > 0.7) c *= 1.12; // (old scratches)
        return c;
    }
    if (kind == 18) { // ocean sunfish: brownish grey, brown mottling round the head, white specks behind, pale below
        if (pect) return vec3(0.6, 0.45, 0.25);
        if (part > 0) return mix(vec3(0.33, 0.31, 0.3), vec3(0.85), spot(q.xy * 12.0, 0.1));
        if (q.x > 0.9 && abs(q.y + 0.12) < 0.1) return vec3(0.85, 0.65, 0.62);
        float m = sin(q.x * 17.0 + sin(q.y * 13.0) * 2.0) * sin(q.y * 15.0 + q.x * 7.0);
        vec3 upper = mix(vec3(0.5, 0.47, 0.44), vec3(0.34, 0.24, 0.16), step(0.2, m) * smoothstep(-0.2, 0.3, q.x));
        upper = mix(upper, vec3(0.88, 0.87, 0.84), spot(q.xy * vec2(11.0, 13.0), 0.12) * smoothstep(0.2, -0.3, q.x));
        return mix(vec3(0.9, 0.88, 0.83), upper, smoothstep(-0.2, 0.15, q.y));
    }
    if (kind == 19) { // basking shark: dark grey-brown, faintly blotched, paler underneath; inside its mouth, white gill rakers
        if (q.z > 6.5) { // inside the mouth: rows of white gill rakers deep in the throat, a dark pink lining, pale lips
            float z = (q.z - 7.0) / 0.4;
            if (q.x < 0.62) return fract(z * 5.0 + 0.5) < 0.55 ? mix(vec3(0.92, 0.9, 0.86), vec3(0.6, 0.55, 0.52), smoothstep(0.62, 0.4, q.x)) : vec3(0.08, 0.03, 0.03);
            if (q.x > 1.02) return vec3(0.82, 0.68, 0.66);
            return mix(vec3(0.3, 0.15, 0.15), vec3(0.55, 0.38, 0.38), smoothstep(0.62, 1.0, q.x)) * (0.85 + 0.15 * step(0.5, fract(z * 5.0 + 0.5)));
        }
        vec3 c = mix(vec3(0.55, 0.52, 0.49), vec3(0.32, 0.3, 0.28), smoothstep(-0.5, 0.3, q.y));
        if (part > 0) return c * 0.92;
        c *= 0.94 + 0.08 * sin(q.x * 9.0 + sin(q.y * 7.0 + rnd * 6.0) * 2.0) * sin(q.y * 6.0 + q.x * 4.0); // (soft mottling)
        float gx = q.x + 0.1 * q.y * q.y;
        if (gx > -0.01 && gx < 0.27 && fract(gx / 0.065 + 0.08) < 0.16 && abs(q.y) < 0.95) c *= 0.6; // (gill slits)
        return mix(c, vec3(0.62, 0.6, 0.57), smoothstep(0.85, 1.0, q.x) * 0.5); // (a paler snout)
    }
    if (kind == 26) { // Atlantic cod: an olive-brown back thick with darker freckles, a pale arching lateral line, a creamy belly
        vec3 back = mix(vec3(0.46, 0.42, 0.27), vec3(0.62, 0.56, 0.36), fbm(q.xy * 6.0 + rnd * 4.0));
        if (part > 0) {
            vec3 f = mix(back * 0.85, vec3(0.78, 0.74, 0.6), anal || pect ? 0.35 : 0.0);
            return f * (1.0 - 0.3 * spot(q.xy * 9.0 + rnd * 3.0, 0.2) * (anal ? 0.0 : 1.0));
        }
        vec3 c = mix(vec3(0.94, 0.91, 0.82), back, smoothstep(-0.38, 0.05, q.y));
        c *= 1.0 - 0.38 * spot(q.xy * vec2(15.0, 10.0) + rnd * 5.0, 0.2 + 0.08 * vnoise(q.xy * 4.0)) * smoothstep(-0.32, 0.0, q.y);
        float lineY = 0.12 + 0.2 * smoothstep(-0.7, 0.3, q.x) - 0.1 * smoothstep(0.3, 0.8, q.x);
        if (abs(q.y - lineY) < 0.035 && q.x < 0.55) c = mix(c, vec3(0.93, 0.9, 0.8), 0.75);
        return q.x > 0.86 ? mix(c, vec3(0.7, 0.62, 0.45), 0.3) : c;
    }
    if (kind == 27) { // salmon: most steel-blue above with black spots, silver sides flushed rosy, a white belly; some in
                      // their red spawning colours with a green head (as the game's salmon)
        if (rnd > 0.6) {
            if (part > 0) return mix(vec3(0.32, 0.33, 0.22), vec3(0.6, 0.2, 0.15), tail ? 0.0 : 0.3);
            vec3 c = vec3(0.82, 0.17, 0.12) * (0.88 + 0.2 * fbm(q.xy * 7.0 + rnd * 3.0));
            c = mix(c, vec3(0.3, 0.38, 0.2), smoothstep(0.45, 0.62, q.x));
            if (q.x > 0.6 && q.y < -0.25) c = vec3(0.86, 0.83, 0.74);
            return c;
        }
        vec3 back = vec3(0.24, 0.33, 0.38);
        if (part > 0) {
            vec3 f = mix(back * 0.9, vec3(0.62, 0.62, 0.64), anal || pect ? 0.55 : 0.0);
            return f * (1.0 - 0.6 * spot(q.xy * vec2(7.0, 6.0) + rnd * 2.0, 0.15) * (tail || dorsal ? 1.0 : 0.0));
        }
        vec3 c = mix(vec3(0.93, 0.93, 0.95), vec3(0.74, 0.76, 0.82), smoothstep(-0.45, 0.05, q.y));
        c = mix(c, vec3(0.92, 0.56, 0.56), smoothstep(0.3, 0.0, abs(q.y - 0.02)) * 0.45);
        c = mix(c, back * (0.9 + 0.2 * fbm(q.xy * 6.0)), smoothstep(0.2, 0.45, q.y));
        float xs = spot(q.xy * vec2(13.0, 9.0) + rnd * 6.0, 0.15) * smoothstep(0.05, 0.3, q.y);
        return c * (1.0 - 0.8 * xs);
    }
    if (kind == 28) { // tropical fish, as the game's: a body colour and a pattern colour (two of the sixteen dyes), in one of
                      // six patterns (bands, a stripe along it, two halves, spots, a dark back, thin stripes)
        int bi = int(rnd * 16.0), pi = int(fract(rnd * 7.31) * 16.0);
        if (pi == bi) pi = (pi + 5) - (pi + 5) / 16 * 16;
        vec3 base = dyeColour(bi), patt = dyeColour(pi);
        int pat = int(fract(rnd * 13.7) * 6.0);
        float m = 0.0;
        if (part == 0) {
            if (pat == 0) m = step(abs(fract(q.x * 1.6 + 0.2) - 0.5), 0.17);
            else if (pat == 1) m = step(abs(q.y - 0.05 + 0.1 * q.x), 0.18);
            else if (pat == 2) m = step(q.x, -0.05 + 0.15 * q.y);
            else if (pat == 3) m = spot(q.xy * vec2(4.5, 5.0) + rnd * 9.0, 0.3);
            else if (pat == 4) m = step(0.2 + 0.08 * sin(q.x * 9.0), q.y);
            else m = step(abs(fract(q.x * 3.2) - 0.5), 0.12) * step(-0.5, q.x);
        } else {
            m = pat == 2 || pat == 4 ? 1.0 : pat == 3 ? spot(q.xy * 5.0, 0.3) : tail ? step(0.7, 0.5 - 0.5 * q.x) : 0.0;
        }
        vec3 c = mix(base, patt, m) * (0.92 + 0.12 * fbm(q.xy * 6.0 + rnd * 3.0));
        return part == 0 ? mix(c, c * 1.12 + 0.05, smoothstep(-0.3, -0.75, q.y)) : c;
    }
    if (kind == 29) { // tadpole: dark brown, freckled gold, its long tail fin clear grey round a dark muscle
        if (tail) {
            float core = smoothstep(0.4, 0.15, abs(q.y));
            return mix(vec3(0.45, 0.43, 0.38), vec3(0.17, 0.14, 0.11), core) * (1.0 - 0.35 * spot(q.xy * vec2(9.0, 4.0) + rnd, 0.15));
        }
        vec3 c = mix(vec3(0.45, 0.38, 0.27), vec3(0.17, 0.14, 0.1), smoothstep(-0.55, 0.2, q.y));
        return mix(c, vec3(0.78, 0.64, 0.3), spot(q.xy * 8.0 + q.z * 4.0 + rnd * 4.0, 0.12) * 0.7);
    }
    if (kind == 33) { // anglerfish: near black, rough mottled skin, a dark mouth, glassy teeth, its lure's bulb glowing
        if (part == 3) return vec3(0.75, 1.0, 1.0);
        if (part == 4) return vec3(0.82, 0.84, 0.8);
        if (part == 0 && q.z > 6.5) return vec3(0.16, 0.03, 0.05); // (inside its mouth: dark red)
        vec3 c = mix(vec3(0.07, 0.05, 0.05), vec3(0.2, 0.15, 0.12), fbm(q.xy * 6.0 + q.z * 3.0 + rnd * 5.0));
        c *= 0.85 + 0.3 * vnoise(q.xy * 22.0 + q.z * 9.0); // (warty)
        if (part > 0) return c * 1.2;
        return mix(c, c * 1.5 + 0.03, smoothstep(-0.3, -0.75, q.y));
    }
    if (kind == 34) { // lanternfish: dark blue-black back, silvery sides, rows of glowing blue lights along the belly
        if (part > 0) return vec3(0.25, 0.28, 0.34);
        vec3 c = mix(vec3(0.62, 0.66, 0.72), vec3(0.08, 0.1, 0.18), smoothstep(-0.1, 0.35, q.y));
        return mix(c, vec3(0.5, 0.85, 1.0), photophores(q));
    }
    // sardine: dark blue-green back, bright silver sides, golden tail and back fin
    if (tail || dorsal) return vec3(0.85, 0.78, 0.5);
    if (fin) return vec3(0.85, 0.85, 0.82);
    vec3 c = mix(vec3(0.86, 0.88, 0.92), vec3(0.16, 0.27, 0.38), smoothstep(0.1, 0.4, q.y));
    if (q.x > -0.5 && q.x < 0.4) c *= 1.0 - 0.55 * spot(vec2(q.x * 6.0 + rnd * 3.0, (q.y - 0.12) * 5.0 + 0.5), 0.2);
    return q.x > 0.45 && q.x < 0.62 && abs(q.y) < 0.5 ? mix(c, vec3(0.8, 0.7, 0.45), 0.35) : c;
}

// how big a fish's eye is, against the usual
// where a fish's eye is (along it, nose = 1; up it, in body heights)
vec2 eyeAt(int kind) {
    return kind == 7 ? vec2(0.5, 0.32) : kind == 29 ? vec2(0.45, 0.36) : vec2(0.62, 0.22);
}

float eyeScale(int kind) {
    return kind == 7 ? 1.3 : kind == 33 ? 0.55 : kind == 34 ? 1.45 : kind == 29 ? 1.45 : kind == 28 ? 1.1 : kind == 18 ? 1.25 : kind == 3 || kind == 17 ? 1.2 : kind == 19 ? 0.6 : kind == 10 ? 0.8 : 1.0;
}

// the ring round a fish's pupil
vec3 eyeRing(int kind) {
    if (kind == 10 || kind == 16 || kind == 19 || kind == 1) return vec3(0.05);
    if (kind == 9 || kind == 4) return vec3(0.75, 0.2, 0.08);
    if (kind == 3) return vec3(0.3, 0.8, 0.7);
    if (kind == 18 || kind == 17 || kind == 8 || kind == 27) return vec3(0.9, 0.9, 0.88);
    if (kind == 29) return vec3(0.8, 0.62, 0.25);
    if (kind == 7) return vec3(0.35, 0.45, 0.5);
    if (kind == 0) return vec3(0.9, 0.4, 0.05);
    return vec3(0.95, 0.9, 0.6);
}

// One part of a fish, in the fish's own frame (x forward, y up, z sideways): where, its radii, how it's turned
// (yaw about up, then pitch about sideways), and what it is (0 body, 1 tail, 2 fin).
struct Part {
    vec3 at;
    vec3 r;
    float yaw;
    float pitch;
    int type;
};

float gPulse = 0.0; // (a jellyfish's squeeze, 0..1)

const int PARTS = 13;

// part j of a fish of this kind (radii scaled by the species, not yet by the fish's size); swing: tail beat (-1..1)
Part fishPart(int kind, Species sp, int j, float swing, float flap) {
    vec3 b = sp.body, tl = sp.tail, fn = sp.fin;
    bool forked = kind == 0 || kind == 1 || kind == 2 || kind == 3 || kind == 4 || kind == 9 || kind == 10 || kind == 11 || kind == 14 || kind == 17;
    if (kind == 13) { // sea turtle: shell, head, four flippers
        if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
        if (j == 1) return Part(vec3(b.x + tl.x * 0.4, 0.0, 0.0), tl, 0.0, 0.0, 1);
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            return Part(vec3(b.x * 0.35, -b.y * 0.15 + b.y * 0.5 * swing, s * b.z * 0.95), vec3(fn.x * 0.7, fn.y, fn.z * 0.62), s * (0.5 + 0.3 * swing), -0.25 * swing, 1); }
        if (j == 4 || j == 5) { float s = j == 4 ? 1.0 : -1.0;
            return Part(vec3(-b.x * 0.7, -b.y * 0.2, s * b.z * 0.7), vec3(fn.x * 0.6, fn.y, fn.z * 0.25), -s * (0.7 + 0.15 * swing), 0.0, 1); }
        return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    }
    if (kind == 15) { // jellyfish: a hollow dome, four frilly arms from its middle, eight thin tentacles from its rim
        float sq = 1.0 - 0.15 * gPulse, st = 1.0 + 0.12 * gPulse; // (following the bell as it squeezes)
        if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
        if (j <= 4) { float a = float(j) * 1.5708 + 0.4;
            return Part(vec3(b.x * 0.16 * cos(a) * sq, 0.05 * b.y * st - tl.y, b.z * 0.16 * sin(a) * sq), tl, 0.0, 0.0, 1); }
        { float a = float(j - 5) * 0.7854 + 0.39;
            vec3 r = vec3(0.02, tl.y * 1.15, 0.02);
            return Part(vec3(b.x * 0.84 * cos(a) * sq, -0.5 * b.y * st - r.y, b.z * 0.84 * sin(a) * sq), r, 0.0, 0.0, 2); }
    }
    if (kind == 12) { // manta ray: wide body, long thin tail, two head fins
        if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
        if (j == 1) return Part(vec3(-b.x - tl.x * 0.8, 0.0, 0.0), tl, swing * 0.15, 0.0, 1);
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            return Part(vec3(b.x * 0.95, -b.y * 0.2, s * b.z * 0.18), vec3(b.x * 0.25, b.y * 0.6, b.z * 0.06), 0.0, 0.0, 2); }
        return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    }
    if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
    if (j == 1) { // the narrow part before the tail
        if (kind == 7) return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
        return Part(vec3(-b.x * 0.82, 0.0, 0.0), vec3(b.x * 0.38, b.y * 0.42, b.z * 0.62), swing * 0.25, 0.0, 0);
    }
    vec3 tailRoot = vec3(-b.x * 1.12, 0.0, 0.0);
    if (kind == 16) { // dolphin: flat flukes, sideways
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            return Part(tailRoot + vec3(-tl.x * 0.6, 0.0, s * tl.z * 0.45), vec3(tl.x, tl.y, tl.z * 0.55), swing * 0.4 - s * 0.45, swing * 0.3, 1); }
    } else if (forked) { // forked tail: two lobes (the shark's top lobe bigger)
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            float big = kind == 10 ? (s > 0.0 ? 1.25 : 0.75) : 1.0;
            return Part(tailRoot + vec3(-tl.x * 0.55, s * tl.y * 0.42 * big, 0.0), vec3(tl.x * big, tl.y * 0.5 * big, tl.z), swing * 0.5, -s * 0.55, 1); }
    } else if (j == 2) { // a round, flowing tail
        return Part(tailRoot + vec3(-tl.x * 0.6, 0.0, 0.0), tl, swing * 0.5, 0.0, 1);
    }
    if (j == 4 && fn.x > 0.0) { // dorsal fin, swept back
        return Part(vec3(-b.x * 0.12, b.y * 0.86, 0.0), fn, 0.0, 0.35, 2);
    }
    if (j == 5 || j == 6) { // side fins, gently paddling
        if (kind == 7 || kind == 8) return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
        float s = j == 5 ? 1.0 : -1.0;
        float big = kind == 10 || kind == 16 ? 1.6 : 1.0;
        return Part(vec3(b.x * 0.25, -b.y * 0.35, s * b.z * 0.95), vec3(b.x * 0.22 * big, b.y * 0.07, b.z * 0.55 * big), s * (0.7 + 0.25 * flap), -0.35, 2);
    }
    if (j == 7) { // a fin underneath (long on the angelfish and betta)
        if (kind == 4 || kind == 6) return Part(vec3(-b.x * 0.2, -b.y * 0.9, 0.0), vec3(fn.x * 0.9, fn.y * 0.9, fn.z), 0.0, -0.3, 2);
        if (kind == 0 || kind == 1 || kind == 2 || kind == 9 || kind == 14) return Part(vec3(-b.x * 0.4, -b.y * 0.8, 0.0), vec3(b.x * 0.25, b.y * 0.25, b.z * 0.2), 0.0, -0.4, 2);
    }
    if (j == 8 && kind != 7) { // (the back half of the body, narrower: a teardrop)
        return Part(vec3(-b.x * 0.42, -b.y * 0.04, 0.0), vec3(b.x * 0.68, b.y * 0.8, b.z * 0.78), 0.0, 0.0, 0);
    }
    if (j == 3 && kind == 16) return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    if (j == 7 && kind == 16) return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
}

// ---- smooth shapes (signed distances) ----

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / max(k1, 1.0e-6);
}

float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}

float sdStar5(vec2 p, float r, float rf) {
    const vec2 k1 = vec2(0.809016994375, -0.587785252292);
    const vec2 k2 = vec2(-0.809016994375, -0.587785252292);
    p.x = abs(p.x);
    p -= 2.0 * max(dot(k1, p), 0.0) * k1;
    p -= 2.0 * max(dot(k2, p), 0.0) * k2;
    p.x = abs(p.x);
    p.y -= r;
    vec2 ba = rf * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
    float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
    return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

/**
 * A fish as one smooth shape: its parts blended together, the back half swinging in a travelling wave as it swims.
 * p: in the fish's own frame (x forward, y up, z sideways), in species units. ptype / q: the nearest part and the
 * point in that part's coordinates (for its colours).
 */
// how each fish is shaped: where it is deepest (0 tail root .. 1 nose), how blunt, its tail (0 round / fan,
// 1 forked, 2 the shark's (forked, top lobe longer), 3 flat flukes sideways (dolphin)), how big its belly fin is
vec4 fishShape(int kind) {
    if (kind == 0) return vec4(0.55, 0.5, 0.0, 0.6);
    if (kind == 1) return vec4(0.5, 0.45, 1.0, 0.8);
    if (kind == 2) return vec4(0.5, 0.4, 0.0, 1.8);
    if (kind == 3) return vec4(0.55, 0.5, 1.0, 0.7);
    if (kind == 4) return vec4(0.5, 0.55, 1.0, 2.6);
    if (kind == 5) return vec4(0.55, 0.55, 1.0, 0.6);
    if (kind == 6) return vec4(0.62, 0.5, 0.0, 3.5);
    if (kind == 7) return vec4(0.58, 0.52, 0.0, 0.0);
    if (kind == 8) return vec4(0.62, 0.5, 0.0, 0.4);
    if (kind == 9) return vec4(0.5, 0.6, 0.0, 0.8);
    if (kind == 10) return vec4(0.6, 0.6, 2.0, 0.35);
    if (kind == 11) return vec4(0.55, 0.35, 1.0, 0.5);
    if (kind == 14) return vec4(0.55, 0.55, 0.0, 0.7);
    if (kind == 16) return vec4(0.6, 0.4, 3.0, 0.0);
    if (kind == 18) return vec4(0.45, 0.7, 0.0, 1.7);
    if (kind == 19) return vec4(0.58, 0.7, 2.0, 0.35);
    if (kind == 26) return vec4(0.6, 0.5, 0.0, 0.7);
    if (kind == 27) return vec4(0.55, 0.42, 1.0, 0.5);
    if (kind == 28) return vec4(0.5, 0.5, 0.0, 0.9);
    if (kind == 29) return vec4(0.72, 0.9, 4.0, 0.0); // (4: a tadpole's tail, a long leaf of fin round a muscle)
    if (kind == 33) return vec4(0.66, 0.95, 0.0, 0.35);
    if (kind == 34) return vec4(0.6, 0.55, 1.0, 0.5);
    return vec4(0.55, 0.4, 1.0, 0.5);
}

/**
 * A fish's real shape: a body whose height and width follow a fish outline along its length (blunt head, deepest
 * part, narrowing to the tail root), bulging eyes and a mouth, a thin tail fin (forked, fan, or a shark's), a thin
 * fin along the back and one underneath, paddling side fins; the back half swinging in a travelling wave.
 */
// (set for each fish before its shape is traced)
float gBend = 0.0;    // how sharply it's turning: its body curves round into the turn
float gBeat = 1.0;    // how hard it's swimming: bigger tail beats in a burst, small ones gliding
float gFinSway = 0.0; // the sunfish sweeping its tall fins side to side (it swims with them)
float gMouth = 0.5;   // breathing: the mouth opening and closing
float gTime = 0.0;

// the body alone (no fins), as in fishBody: for the basking shark's lower jaw, worked out a second time turned down
float trunkAt(int kind, Species sp, vec4 sh, vec3 p) {
    float L = sp.body.x, H = sp.body.y, W = sp.body.z;
    float s = clamp((p.x + L) / (2.0 * L), 0.0, 1.0);
    float u = (s - sh.x) / (s < sh.x ? sh.x : 1.0 - sh.x);
    float sb = sh.x - 0.06;
    float ub = (s - sb) / (s < sb ? sb : 1.0 - sb);
    float profT = pow(max(1.0 - u * u, 0.0), sh.y);
    float profB = pow(max(1.0 - ub * ub, 0.0), sh.y * 1.15);
    float root = smoothstep(0.3, 0.0, s) * 0.22;
    float hT = H * max(profT, root) + 0.002, hB = H * max(profB, root) + 0.002;
    float w = W * max(profT, root * 0.55) + 0.002;
    float py = p.y + 0.12 * H * smoothstep(0.55, 1.0, s);
    float h = py > 0.0 ? hT : hB;
    float yy = clamp(py / h, -1.0, 1.0);
    float wl = w * (1.0 - 0.28 * yy * yy);
    return max((length(vec2(py / h, p.z / wl)) - 1.0) * min(h, wl), abs(p.x) - L);
}

// where an anglerfish's lure bulb is (in its own frame), bobbing
vec3 anglerLure(float L, float H) {
    return vec3(L * 1.22 + 0.01 * sin(gTime * 1.3), H * 1.5 + 0.012 * sin(gTime * 1.9), 0.012 * sin(gTime * 1.1));
}

// a fin's height along it (kx: 0 its back end .. 1 its front), as a share of its full height
float finShape(float kx, float lean, float rag, float rk) {
    return pow(max(sin(kx * 3.1416), 0.0), 0.8) * (1.0 + lean * (0.5 - kx)) * (1.0 - rag * abs(sin(kx * rk)));
}

// as finShape, for this kind's fin along its back (which 0) or underneath (1): the cod has three on its back, two below
float finH(int kind, float kx, float lean, float rag, float rk, int which) {
    if (kind == 26) {
        return pow(abs(sin(kx * 3.1416 * (which == 0 ? 3.0 : 2.0))), 0.7) * (which == 0 ? 0.65 + 0.45 * kx : 1.0);
    }
    return finShape(kx, lean, rag, rk);
}

float sdTaper(vec3 p, vec3 a, vec3 b, float ra, float rb) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - mix(ra, rb, h);
}


float fishBody(int kind, Species sp, vec3 p, float wave, float flap, out int ptype, out vec3 q) {
    float L = sp.body.x, H = sp.body.y, W = sp.body.z;
    vec4 sh = fishShape(kind);
    bool bony = kind != 10 && kind != 16 && kind != 19 && kind != 18 && kind != 29;
    // swimming: a wave running back along the body, growing towards the tail (tuna and sharks: only the back end
    // swings; the dolphin beats up and down; the sunfish hardly bends at all), the tail fin flexing behind it
    bool stiff = kind == 10 || kind == 11 || kind == 19;
    float env = stiff ? smoothstep(-0.05 * L, -1.9 * L, p.x) * 1.15 : kind == 29 ? smoothstep(0.8 * L, -1.0 * L, p.x) : smoothstep(0.4 * L, -1.9 * L, p.x);
    float amp = 0.2 * L * gBeat * (kind == 18 ? 0.12 : kind == 7 ? 0.6 : kind == 29 ? 2.0 : 1.0);
    float ph = wave - p.x / L * (kind == 29 ? 1.1 : 2.2); // (a tadpole: its whole tail one long wave, wriggling)
    float sw = amp * sin(ph) * env;
    float flex = clamp((-p.x - L * 0.9) / max(sp.tail.x * 2.0, 0.001), 0.0, 1.0);
    sw += 0.35 * amp * flex * flex * sin(ph - 1.3);
    if (kind == 16) p.y += sw * 0.9; else p.z += sw;
    p.z -= 0.5 * gBend * p.x * p.x; // (turning)
    // the body's outline at this point along it: back and belly curved differently, the head sloping down to the mouth
    float s = clamp((p.x + L) / (2.0 * L), 0.0, 1.0);
    float u = (s - sh.x) / (s < sh.x ? sh.x : 1.0 - sh.x);
    float sb = sh.x - 0.06;
    float ub = (s - sb) / (s < sb ? sb : 1.0 - sb);
    float profT = pow(max(1.0 - u * u, 0.0), sh.y);
    float profB = pow(max(1.0 - ub * ub, 0.0), sh.y * 1.15);
    float root = smoothstep(0.3, 0.0, s) * (kind == 18 ? 0.75 : 0.22); // (the sunfish ends as if cut off)
    float hT = H * max(profT, root) + 0.002, hB = H * max(profB, root) + 0.002;
    float w = W * max(profT, root * (kind == 18 ? 0.8 : 0.55)) + 0.002; // (a thin tail stalk)
    float yc = kind == 7 || kind == 18 ? 0.0 : -0.12 * H * smoothstep(0.55, 1.0, s);
    float py = p.y - yc;
    float h = py > 0.0 ? hT : hB;
    float yy = clamp(py / h, -1.0, 1.0);
    float wl = kind == 7 ? w : w * (1.0 - 0.28 * yy * yy); // (narrowing to a ridge along the back and belly)
    float body = max((length(vec2(py / h, p.z / wl)) - 1.0) * min(h, wl), abs(p.x) - L);
    if (kind == 29) { // the tadpole's tail muscle, down the middle of its fin, thinning to the tip
        float mus = sdTaper(p, vec3(-L * 0.5, -0.05 * H, 0.0), vec3(-L * 0.92 - sp.tail.x * 1.85, 0.0, 0.0), H * 0.42, H * 0.04);
        body = smin(body, mus, 0.35 * H);
    }
    float mouthFace = 0.0; // (the basking shark: 1 inside its open mouth, 2 its gill rakers at the back)
    if (kind == 19) { // feeding: the pointed snout on top, under it the huge round mouth wide open, the throat ballooning
        float snout = sdEllipsoid(p - vec3(L * 0.86, -0.02 * H, 0.0), vec3(L * 0.2, H * 0.36, W * 0.4));
        float bag = sdEllipsoid(p - vec3(L * 0.7, -0.35 * H, 0.0), vec3(L * 0.45, H * 0.62, W * 0.95));
        body = smin(smin(body, snout, 0.06 * H), bag, 0.12 * H);
        // the inside: a deep cave, its round opening at the front
        float cave = sdEllipsoid(p - vec3(L * 0.8, -0.36 * H, 0.0), vec3(L * 0.47, H * 0.5, W * 0.8));
        if (-cave > body) mouthFace = 1.0;
        body = max(body, -cave);
    }
    if (kind == 19 || kind == 10) { // gill slits, cut into the skin
        for (int k = 0; k < 5; k++) {
            float xk = kind == 19 ? L * (0.0 + 0.065 * float(k)) : L * (0.27 + 0.05 * float(k));
            float slab = max(abs(p.x - xk + (kind == 19 ? 0.1 : 0.06) * L * (p.y / H) * (p.y / H)) - 0.005 * L, abs(p.y) - (kind == 19 ? 0.95 : 0.35) * H);
            body = max(body, min(-slab, body + 0.025 * H));
        }
    }
    // eyes, bulging a little, and a mouth (breathing)
    vec2 ea = eyeAt(kind);
    float se = (ea.x + 1.0) * 0.5;
    float ue = (se - sh.x) / (1.0 - sh.x);
    float we = W * pow(max(1.0 - ue * ue, 0.0), sh.y);
    float eyeR = H * 0.13 * eyeScale(kind);
    float eye = length(vec3(p.x - L * ea.x, p.y - H * ea.y, abs(p.z) - we * (kind == 7 ? 0.72 : 0.8))) - eyeR;
    if (kind != 10 && kind != 16 && kind != 19) {
        body = smin(body, eye, eyeR * 0.7);
    }
    vec3 mc = vec3(L * 1.0, -H * 0.12, 0.0), mr = vec3(L * 0.08, H * 0.05 * (0.5 + gMouth), W * 0.7);
    if (kind == 14) mr = vec3(L * 0.12, H * 0.1 * (0.6 + 0.8 * gMouth), W * 0.8); // (the grouper's big mouth)
    if (kind == 10 || kind == 16) mr.y = H * 0.04;
    if (kind == 7) { mc = vec3(L * 1.05, -H * 0.16, 0.0); mr = vec3(L * 0.05, H * 0.035 * (0.5 + gMouth), W * 0.14); } // (a small round mouth)
    if (kind == 33) { // the anglerfish: a huge mouth, gaping slowly, its lower jaw jutting out
        body = smin(body, sdEllipsoid(p - vec3(L * 0.8, -H * 0.45, 0.0), vec3(L * 0.36, H * 0.32, W * 0.78)), 0.25 * H);
        mc = vec3(L * 0.92, -H * 0.16, 0.0); // (a wide gape, cut back along both sides: seen from the side too)
        mr = vec3(L * 0.5, H * 0.2 * (0.8 + 0.4 * gMouth), W * 0.85);
    }
    if (kind == 33 && -sdEllipsoid(p - mc, mr) > body) mouthFace = 1.0; // (inside its mouth)
    if (kind != 19) body = max(body, -sdEllipsoid(p - mc, mr));
    float res = body, best = body;
    ptype = 0;
    q = p / sp.body;
    if (mouthFace > 0.5) q.z = (mouthFace > 1.5 ? 9.0 : 7.0) + 0.4 * clamp(p.z / W, -1.0, 1.0);
    // tail fin, behind the tail root
    float tl = sp.tail.x * 2.0, th = sp.tail.y, thick = max(sp.tail.z, 0.004);
    float xt = -L * 0.92 - p.x;
    float kt = clamp(xt / tl, 0.0, 1.0);
    bool flukes = sh.z > 2.5 && sh.z < 3.5;
    float across = flukes ? abs(p.z) : abs(p.y), sideways = flukes ? abs(p.y) : abs(p.z);
    float span = flukes ? sp.tail.z : th;
    if (flukes) thick = max(sp.tail.y * 0.5, 0.004);
    float outer, inner = -1.0;
    if (sh.z > 3.5) { // a tadpole's: a leaf of fin, deepest a third of the way along, tapering to a point
        outer = span * sin(3.1416 * mix(0.15, 1.0, pow(kt, 0.7)));
    } else if (sh.z > 0.5) {
        outer = span * (0.2 + 0.8 * kt);
        inner = span * 0.85 * kt * kt;
        if (sh.z > 1.5 && sh.z < 2.5) { // the shark's: the top lobe longer than the bottom one
            outer = p.y > 0.0 ? span * (0.2 + 1.0 * kt) : span * (0.2 + (kind == 19 ? 0.85 : 0.55) * kt);
        }
    } else {
        outer = span * (0.25 + 0.75 * sqrt(max(sin(kt * 1.5708), 0.0))); // a rounded fan
    }
    float tlr = tl * (bony ? 1.0 - 0.05 * abs(sin(across / max(span, 0.001) * 14.0)) : 1.0);
    float tail = max(max(max(across - outer, inner - across), max(-xt, xt - tlr)) * 0.7, sideways - thick * (1.0 - 0.6 * kt));
    res = smin(res, tail, 0.05 * L);
    if (tail < best) {
        best = tail; ptype = 1;
        q = vec3(1.0 - 2.0 * kt, p.y / span, p.z / thick);
    }
    // the fin along the back and one underneath: swept back (sharks' and the dolphin's strongly), rippling as it
    // swims (the sunfish's sweeping side to side)
    float sweep = bony ? 0.35 : kind == 18 ? 0.1 : kind == 19 ? 0.45 : 0.9;
    if (sp.fin.x > 0.0) {
        float fx1 = L * 0.3, fx0 = fx1 - sp.fin.x * 2.2;
        float lift = max(py - hT * 0.92, 0.0);
        float xs = p.x + lift * sweep;
        float kx = clamp((xs - fx0) / (fx1 - fx0), 0.0, 1.0);
        float rag = bony ? 0.07 : 0.0;
        float dh = sp.fin.y * 1.0 * finH(kind, kx, 0.5, rag, 22.0, 0);
        float slope = sp.fin.y * (finH(kind, min(kx + 0.02, 1.0), 0.5, rag, 22.0, 0) - finH(kind, max(kx - 0.02, 0.0), 0.5, rag, 22.0, 0)) / (0.04 * (fx1 - fx0));
        float up = clamp(lift / max(dh, 0.001), 0.0, 1.0);
        float upS = clamp(lift / sp.fin.y, 0.0, 1.0); // (smooth: over the fin's full height)
        float zf = p.z - (kind == 18 ? gFinSway * sp.fin.y * 0.3 : sp.fin.y * 0.06 * gBeat * sin(wave * 1.3 - kx * 6.0)) * upS * upS;
        float dorsal = max(max(max((py - (hT * 0.92 + dh)) / sqrt(1.0 + slope * slope), hT * 0.55 - py), max(fx0 - xs, xs - fx1)) * 0.55, (abs(zf) - sp.fin.z * (1.0 - 0.6 * upS)) * 0.8);
        res = smin(res, dorsal, 0.04 * L);
        if (dorsal < best) {
            best = dorsal; ptype = 2;
            q = vec3(kx * 2.0 - 1.0, up, 0.0);
        }
    }
    if (sh.w > 0.0) {
        float ax1 = kind == 26 ? L * 0.12 : -L * 0.05, ax0 = kind == 26 ? -L * 0.72 : -L * 0.65;
        float lift = max(-py - hB * 0.92, 0.0);
        float xs = p.x + lift * sweep;
        float kx = clamp((xs - ax0) / (ax1 - ax0), 0.0, 1.0);
        float rag = bony ? 0.07 : 0.0;
        float dh = H * 0.35 * sh.w * finH(kind, kx, 0.4, rag, 18.0, 1);
        float slope = H * 0.35 * sh.w * (finH(kind, min(kx + 0.02, 1.0), 0.4, rag, 18.0, 1) - finH(kind, max(kx - 0.02, 0.0), 0.4, rag, 18.0, 1)) / (0.04 * (ax1 - ax0));
        float down = clamp(lift / max(dh, 0.001), 0.0, 1.0);
        float downS = clamp(lift / (H * 0.35 * sh.w), 0.0, 1.0);
        float zf = p.z - (kind == 18 ? -gFinSway * H * 0.35 * sh.w * 0.3 : H * 0.02 * gBeat * sin(wave * 1.3 - kx * 6.0 + 1.0)) * downS * downS;
        float anal = max(max(max((-py - (hB * 0.92 + dh)) / sqrt(1.0 + slope * slope), hB * 0.55 + py), max(ax0 - xs, xs - ax1)) * 0.55, (abs(zf) - max(sp.fin.z, 0.006) * (1.0 - 0.6 * downS)) * 0.8);
        res = smin(res, anal, 0.04 * L);
        if (anal < best) {
            best = anal; ptype = 2;
            q = vec3(kx * 2.0 - 1.0, -down, -1.0);
        }
    }
    if (kind == 10 || kind == 11 || kind == 19) { // a small second fin on the back and underneath, near the tail
        float x1 = -L * 0.45, x0 = -L * 0.66;
        float kx = clamp((p.x - x0) / (x1 - x0), 0.0, 1.0);
        float dh = sp.fin.y * 0.32 * sin(kx * 3.1416);
        float d2 = max(max(max(py - (hT * 0.92 + dh), hT * 0.55 - py), max(x0 - p.x, p.x - x1)) * 0.7, abs(p.z) - sp.fin.z * 0.6);
        float d3 = max(max(max(-py - (hB * 0.92 + dh), hB * 0.55 + py), max(x0 - p.x, p.x - x1)) * 0.7, abs(p.z) - sp.fin.z * 0.6);
        float d23 = min(d2, d3);
        res = smin(res, d23, 0.03 * L);
        if (d23 < best) {
            best = d23; ptype = 2;
            q = vec3(kx * 2.0 - 1.0, d2 < d3 ? 0.5 : -0.5, d2 < d3 ? 0.0 : -1.0);
        }
    }
    // side fins, paddling (sharks' stiff and swept back)
    if (kind == 7) { // pufferfish
        // thick pouting lips round the mouth
        float lips = sdEllipsoid(p - vec3(L * 0.97, -H * 0.16, 0.0), vec3(L * 0.07, H * 0.11, W * 0.24));
        res = smin(res, max(lips, -sdEllipsoid(p - mc, mr)), 0.04 * H);
        if (lips < best) { best = lips; ptype = 0; q = vec3(1.2, -0.16, p.z / W); } // (q.x 1.2: the lips)
        // short spines lying flat all over it (the porcupine pufferfish)
        float ang = atan(py, p.z);
        vec2 sg = vec2(p.x / L * 7.0, ang * 2.6);
        sg.y += 0.5 * floor(sg.x);
        float spike = max(1.0 - length(fract(sg) - 0.5) * 3.2, 0.0);
        res -= 0.035 * H * spike * spike * smoothstep(0.98, 0.7, abs(p.x / L));
        // its big side fins: upright fans just behind the eyes, whirring
        vec3 pp = vec3(p.x - L * 0.18, py + H * 0.05, abs(p.z) - W * 0.8);
        float a = 1.0 + 0.3 * sin(gTime * 9.0 + wave * 0.3); // (standing well out from its side, never flat against it)
        vec3 lq = vec3(pp.x * cos(a) - pp.z * sin(a), pp.y, pp.x * sin(a) + pp.z * cos(a));
        lq.x += L * 0.12;
        float pect = sdEllipsoid(lq, vec3(L * 0.15, H * 0.34, W * 0.06));
        res = smin(res, pect, 0.02 * L);
        if (pect < best) { best = pect; ptype = 2; q = vec3(-lq.x / (L * 0.15), lq.y / (H * 0.34), 2.0); }
        // small rounded fins on its back and underneath, far back near the tail
        for (int k = 0; k < 2; k++) {
            float sg2 = k == 0 ? 1.0 : -1.0;
            vec3 fp = vec3(p.x + L * 0.62, py * sg2 - H * 0.5, p.z);
            float tilt = 0.6 + 0.12 * sin(wave * 1.3 + float(k));
            vec3 fq = vec3(fp.x * cos(tilt) - fp.y * sin(tilt), fp.x * sin(tilt) + fp.y * cos(tilt), fp.z);
            fq.y -= H * 0.18;
            float fin = sdEllipsoid(fq, vec3(L * 0.13, H * 0.3, W * 0.05));
            res = smin(res, fin, 0.02 * L);
            if (fin < best) { best = fin; ptype = 2; q = vec3(fq.x / (L * 0.13), sg2 * clamp(fq.y / (H * 0.3) * 0.5 + 0.5, 0.0, 1.0), k == 0 ? 0.0 : -1.0); }
        }
    }
    if (kind == 27) { // the salmon's little fleshy fin on its back, near the tail
        float ad = sdEllipsoid(vec3(p.x + L * 0.62, py - H * 0.78, p.z), vec3(L * 0.065, H * 0.2, max(sp.fin.z, 0.006)));
        res = smin(res, ad, 0.02 * L);
        if (ad < best) { best = ad; ptype = 2; q = vec3(0.0, 0.5, 0.0); }
    }
    if (kind != 8 && kind != 7 && kind != 29) {
        float big = kind == 10 || kind == 16 || kind == 19 ? 1.7 : 1.0;
        vec3 pp = vec3(p.x - L * 0.3, py + H * 0.35, abs(p.z) - W * 0.85);
        float a = big > 1.5 ? 1.0 + 0.06 * flap : 0.7 + 0.3 * flap;
        vec3 lq = vec3(pp.x * cos(a) - pp.z * sin(a), pp.y, pp.x * sin(a) + pp.z * cos(a));
        lq.y -= lq.z * (big > 1.5 ? 0.35 : 0.15 * flap); // (angled down, or rowing)
        float pect = sdEllipsoid(lq, vec3(L * 0.2 * big, H * 0.05, W * 0.5 * big));
        res = smin(res, pect, 0.02 * L);
        if (pect < best) {
            best = pect; ptype = 2;
            q = vec3(lq.x / (L * 0.2 * big), 0.0, 2.0);
        }
    }
    // the pair of small fins under the belly
    if (bony && kind != 7 && kind != 8) {
        vec3 pp = vec3(p.x - L * 0.02, py + hB * 0.82, abs(p.z) - W * 0.3);
        float a = 0.65 + 0.1 * flap;
        vec3 lq = vec3(pp.x * cos(a) + pp.y * sin(a), -pp.x * sin(a) + pp.y * cos(a), pp.z);
        float pelv = sdEllipsoid(lq, vec3(L * 0.13, H * 0.12, W * 0.07));
        res = smin(res, pelv, 0.02 * L);
        if (pelv < best) {
            best = pelv; ptype = 2;
            q = vec3(lq.x / (L * 0.13), 0.0, 2.0);
        }
    }
    if (kind == 33) {
        // needle teeth round its mouth: the lower ones pointing up, the upper ones down
        float teeth = 1.0e9;
        if (p.x > L * 0.4) { // (both sides at once: the teeth are the same each side)
            vec3 pt = vec3(p.x, p.y, abs(p.z));
            float open = 0.8 + 0.4 * gMouth;
            for (int k = 0; k < 5; k++) {
                float u = float(k) / 4.0;
                float xt = L * (0.55 + 0.45 * u);
                float zt = W * 0.62 * (1.0 - 0.55 * u * u) + 0.002;
                vec3 lo1 = vec3(xt, -H * (0.16 + 0.2 * open), zt), up1 = vec3(xt - L * 0.03, -H * (0.16 - 0.2 * open), zt);
                float len = H * (0.18 + 0.12 * u);
                teeth = min(teeth, sdTaper(pt, lo1, lo1 + vec3(L * 0.02, len, -0.004), 0.0042, 0.0006));
                teeth = min(teeth, sdTaper(pt, up1, up1 - vec3(-L * 0.02, len * 0.8, 0.004), 0.0038, 0.0006));
            }
        }
        res = min(res, teeth);
        if (teeth < best) { best = teeth; ptype = 4; q = vec3(0.0); }
        // its lure: a stalk from the top of its head arching forward, a glowing bulb at the end, bobbing
        vec3 s0 = vec3(L * 0.45, H * 0.85, 0.0), s1 = vec3(L * 0.85, H * 1.7, 0.0), s2 = anglerLure(L, H);
        float stalk = min(sdTaper(p, s0, s1, 0.005, 0.0035), sdTaper(p, s1, s2, 0.0035, 0.0025));
        res = smin(res, stalk, 0.01);
        if (stalk < best) { best = stalk; ptype = 2; q = vec3(0.0, 0.5, 0.0); }
        float bulb = length(p - s2) - 0.017;
        res = min(res, bulb);
        if (bulb < best) { best = bulb; ptype = 3; q = vec3(0.0); }
    }
    if (kind == 26) { // the cod's barbel, hanging from its chin
        float bar = sdCapsule(p, vec3(L * 0.86, -H * 0.4, 0.0), vec3(L * 0.8 + 0.008 * sin(gTime * 2.0 + wave * 0.1), -H * 0.78, 0.0), 0.0035);
        res = min(res, bar);
        if (bar < best) { best = bar; ptype = 2; q = vec3(0.0, 0.0, 3.0); }
    }
    if (kind == 4) { // the angelfish's two long filaments, hanging down from its chest
        vec3 fp = vec3(p.x, p.y, abs(p.z));
        float fil = sdCapsule(fp, vec3(L * 0.3, -H * 0.55, W * 0.25), vec3(-L * 0.1 + 0.05 * L * sin(wave * 0.5), -H * 2.0, W * 0.4), 0.006);
        res = min(res, fil);
        if (fil < best) {
            best = fil; ptype = 2;
            q = vec3(0.0, 0.0, 3.0);
        }
    }
    return res;
}

// ---- the creatures on the bottom: 20 hermit crab, 21 crab, 22 stingray, 23 (blue-ringed) octopus, 24 lobster,
// 25 (fire) shrimp. In their own frame: origin on the sand under them, x forward, y up, z sideways. Parts (for their
// colours): 0 body, 1 legs / claws / arms / tail, 2 eyes, 3 pale bits (eye stalks, a shrimp's legs and feelers),
// 4 the hermit crab's shell, 5 feelers ----

float gWalk = 0.0; // (how far it has walked: its legs step with it)

void addPart(inout float res, inout float best, inout int ptype, float d, int id, float k) {
    res = k > 0.0 ? smin(res, d, k) : min(res, d);
    if (d < best) {
        best = d;
        ptype = id;
    }
}

// a jointed walking leg (hip, knee, foot), stepping: lifted and swung forward in turn
void addLeg(inout float res, inout float best, inout int ptype, vec3 p, vec3 hip, vec3 knee, vec3 foot, float r, float phase,
            vec3 swingDir, float stride, int id) {
    float lift = max(sin(phase), 0.0);
    vec3 f = foot + vec3(0.0, lift * stride * 0.9, 0.0) + swingDir * cos(phase) * stride;
    vec3 k = knee + vec3(0.0, lift * stride * 0.6, 0.0) + swingDir * cos(phase) * stride * 0.5;
    addPart(res, best, ptype, sdTaper(p, hip, k, r, r * 0.85), id, 0.0);
    addPart(res, best, ptype, sdTaper(p, k, f, r * 0.85, r * 0.4), id, 0.0);
}

float critterSDF(int kind, vec3 p, float wave, out int ptype, out vec3 q) {
    q = p;
    ptype = 0;
    float res = 1.0e9, best = 1.0e9;
    float stepPh = gWalk * 6.2831;
    vec3 sp = vec3(p.x, p.y, abs(p.z));
    float sgn = p.z < 0.0 ? 1.0 : -1.0; // (the two sides step in turn)
    if (kind == 21) { // crab: wide dark shell, red legs, two red claws, eyes on short stalks; walks sideways
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.0, 0.05, 0.0), vec3(0.058, 0.026, 0.084)), 0, 0.0);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.0, 0.04, 0.0), vec3(0.05, 0.022, 0.07)), 1, 0.01); // (underside)
        addPart(res, best, ptype, sdTaper(sp, vec3(0.045, 0.055, 0.02), vec3(0.056, 0.07, 0.023), 0.004, 0.0035), 1, 0.003);
        addPart(res, best, ptype, length(sp - vec3(0.057, 0.074, 0.023)) - 0.0075, 2, 0.0);
        float open = 0.5 + 0.5 * sin(wave * 0.5 + (p.z > 0.0 ? 0.0 : 2.0));
        addPart(res, best, ptype, sdTaper(sp, vec3(0.035, 0.042, 0.06), vec3(0.075, 0.038, 0.085), 0.011, 0.009), 1, 0.004);
        addPart(res, best, ptype, sdEllipsoid(sp - vec3(0.098, 0.034, 0.078), vec3(0.03, 0.017, 0.017)), 1, 0.006);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.12, 0.04, 0.074), vec3(0.152, 0.038, 0.066), 0.007, 0.0015), 1, 0.002);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.12, 0.027, 0.074), vec3(0.15, 0.027 - 0.018 * open, 0.067), 0.006, 0.0015), 1, 0.002);
        for (int k = 0; k < 4; k++) {
            float x = 0.028 - float(k) * 0.022;
            addLeg(res, best, ptype, sp, vec3(x, 0.04, 0.066), vec3(x - 0.006 * float(k), 0.072, 0.112),
                   vec3(x - 0.014 * float(k), 0.0, 0.148), 0.0065, stepPh + float(k) * 1.57 + sgn * 1.57, vec3(0.0, 0.0, 1.0), 0.014, 1);
        }
    } else if (kind == 20) { // hermit crab: a ridged cream shell, orange body, big black eyes on white stalks, a big claw
        vec3 sc = p - vec3(-0.035, 0.068, 0.0);
        float rock = 0.06 * sin(stepPh * 0.5);
        sc.xy = mat2(cos(rock), -sin(rock), sin(rock), cos(rock)) * sc.xy;
        float shell = sdEllipsoid(sc, vec3(0.066, 0.06, 0.055));
        shell -= 0.0035 * sin(atan(sc.y, sc.x) * 4.0 + sc.z * 60.0); // (spiral ridges)
        addPart(res, best, ptype, shell, 4, 0.0);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.028, 0.045, 0.0), vec3(0.036, 0.03, 0.034)), 0, 0.012);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.05, 0.058, 0.01), vec3(0.068, 0.09, 0.015), 0.0055, 0.005), 3, 0.004);
        addPart(res, best, ptype, sdEllipsoid(sp - vec3(0.071, 0.1, 0.016), vec3(0.009, 0.013, 0.008)), 2, 0.002);
        float aw = 0.15 * sin(wave * 0.8 + p.z * 40.0);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.062, 0.06, 0.006), vec3(0.1, 0.07 + aw * 0.1, 0.02), 0.0028, 0.002), 5, 0.0);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.1, 0.07 + aw * 0.1, 0.02), vec3(0.14, 0.02, 0.045 + aw * 0.05), 0.002, 0.0012), 5, 0.0);
        // the big claw (left) and the small one
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.078, 0.034, 0.03), vec3(0.034, 0.03, 0.017)), 1, 0.008);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.074, 0.03, -0.03), vec3(0.024, 0.02, 0.012)), 1, 0.008);
        for (int k = 0; k < 2; k++) {
            float x = 0.04 - float(k) * 0.022;
            addLeg(res, best, ptype, sp, vec3(x, 0.04, 0.03), vec3(x + 0.025, 0.062, 0.075),
                   vec3(x + 0.03, 0.0, 0.1), 0.0085, stepPh + float(k) * 3.14 + sgn * 1.57, vec3(1.0, 0.0, 0.0), 0.012, 1);
        }
    } else if (kind == 22) { // stingray: a flat rounded disc, its edges rippling, eyes on top, a long thin tail
        vec3 d = p - vec3(0.0, 0.03, 0.0);
        float zr = clamp(abs(d.z) / 0.27, 0.0, 1.2);
        d.y -= 0.028 * zr * zr * sin(wave - d.x * 14.0);
        d.x += 0.07 * zr * zr;
        addPart(res, best, ptype, sdEllipsoid(d, vec3(0.25, 0.032, 0.27)), 0, 0.0);
        addPart(res, best, ptype, length(sp - vec3(0.085, 0.056, 0.045)) - 0.013, 2, 0.01);
        float sway = 0.04 * sin(wave * 0.4);
        vec3 t1 = vec3(-0.43, 0.034, sway * 0.5), t2 = vec3(-0.62, 0.03, sway);
        addPart(res, best, ptype, sdTaper(p, vec3(-0.22, 0.035, 0.0), t1, 0.016, 0.008), 1, 0.02);
        addPart(res, best, ptype, sdTaper(p, t1, t2, 0.008, 0.002), 1, 0.0);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(-0.31, 0.05, sway * 0.2), vec3(0.03, 0.014, 0.004)), 1, 0.006);
    } else if (kind == 23) { // blue-ringed octopus: a round head and body, eight arms crawling, curling at the tips
        vec3 m = p - vec3(-0.03, 0.08, 0.0);
        m.xy = mat2(0.82, -0.57, 0.57, 0.82) * m.xy;
        addPart(res, best, ptype, sdEllipsoid(m, vec3(0.042, 0.05, 0.036)), 0, 0.0);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.006, 0.045, 0.0), vec3(0.03, 0.028, 0.032)), 0, 0.02);
        addPart(res, best, ptype, length(sp - vec3(0.02, 0.062, 0.025)) - 0.0095, 2, 0.006);
        for (int k = 0; k < 8; k++) {
            float th = (float(k) + 0.5) * 0.7854;
            vec3 dir = vec3(cos(th), 0.0, sin(th)), perp = vec3(-sin(th), 0.0, cos(th));
            vec3 prev = vec3(0.005, 0.035, 0.0) + dir * 0.015;
            float rPrev = 0.012;
            for (int j = 1; j <= 4; j++) {
                float s = float(j) / 4.0;
                float curl = 0.03 * s * s * sin(wave * 0.8 + float(k) * 1.3 + s * 4.0 + stepPh * 0.5);
                vec3 pt = vec3(0.005, 0.0, 0.0) + dir * (0.015 + s * 0.13) + perp * curl;
                pt.y = 0.035 * (1.0 - s) + 0.004 + 0.02 * s * s * max(sin(wave * 0.6 + float(k) * 2.1), 0.0);
                float r = mix(0.012, 0.0022, s);
                addPart(res, best, ptype, sdTaper(p, prev, pt, rPrev, r), 1, j == 1 ? 0.012 : 0.0);
                prev = pt;
                rPrev = r;
            }
        }
    } else if (kind == 24) { // lobster: a long shell, a segmented tail ending in a fan, two big claws, long feelers
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.04, 0.055, 0.0), vec3(0.085, 0.034, 0.037)), 0, 0.0);
        addPart(res, best, ptype, sdTaper(p, vec3(0.115, 0.06, 0.0), vec3(0.15, 0.064, 0.0), 0.008, 0.0015), 0, 0.004);
        for (int k = 0; k < 6; k++) {
            float fk = float(k);
            addPart(res, best, ptype, sdEllipsoid(p - vec3(-0.055 - fk * 0.033, 0.05 - fk * 0.003, 0.0),
                                                  vec3(0.021, 0.031 - fk * 0.0025, 0.034 - fk * 0.003)), 0, 0.003);
        }
        for (int j = 0; j < 5; j++) { // the tail fan
            float a = (float(j) - 2.0) * 0.38 + 0.08 * sin(wave * 0.6);
            vec3 c = vec3(-0.25 - 0.03 * cos(a), 0.034, 0.03 * sin(a));
            vec3 l = p - c;
            l.xz = mat2(cos(a), sin(a), -sin(a), cos(a)) * l.xz;
            addPart(res, best, ptype, sdEllipsoid(l, vec3(0.034, 0.005, 0.015)), 0, 0.004);
        }
        float big = p.z > 0.0 ? 1.12 : 0.9;
        float open = 0.5 + 0.5 * sin(wave * 0.4 + (p.z > 0.0 ? 0.0 : 1.7));
        addPart(res, best, ptype, sdTaper(sp, vec3(0.1, 0.045, 0.03), vec3(0.15, 0.045, 0.075), 0.012, 0.011), 1, 0.005);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.15, 0.045, 0.075), vec3(0.19, 0.04, 0.077), 0.011, 0.012), 1, 0.005);
        addPart(res, best, ptype, sdEllipsoid(sp - vec3(0.235, 0.04, 0.077), vec3(0.055, 0.02, 0.03) * big), 1, 0.008);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.28, 0.046, 0.072), vec3(0.335, 0.046, 0.062), 0.011 * big, 0.003), 1, 0.003);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.28, 0.034, 0.072), vec3(0.33, 0.034 - 0.022 * open, 0.064), 0.009 * big, 0.003), 1, 0.003);
        for (int k = 0; k < 4; k++) {
            float x = 0.07 - float(k) * 0.024;
            addLeg(res, best, ptype, sp, vec3(x, 0.035, 0.03), vec3(x - 0.01, 0.062, 0.078),
                   vec3(x - 0.02 - 0.01 * float(k), 0.0, 0.104), 0.0055, stepPh + float(k) * 1.57 + sgn * 1.57, vec3(1.0, 0.0, 0.0), 0.012, 1);
        }
        addPart(res, best, ptype, length(sp - vec3(0.11, 0.07, 0.016)) - 0.006, 2, 0.0);
        float aw = 0.02 * sin(wave * 0.7 + p.z * 30.0);
        vec3 a1 = vec3(0.25, 0.09, 0.07 + aw), a2 = vec3(0.37, 0.06 + aw, 0.13 + aw);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.13, 0.06, 0.012), a1, 0.0035, 0.0025), 5, 0.0);
        addPart(res, best, ptype, sdTaper(sp, a1, a2, 0.0025, 0.0012), 5, 0.0);
    } else if (kind == 32) { // axolotl: a wide flat head with a smile, three feathery gills each side, a soft body on four short
                             // legs, a long tail flattened into a fin; it waddles, body swaying, gills waving
        vec3 b = p;
        b.z -= 0.012 * sin(stepPh - p.x * 18.0) * smoothstep(0.1, -0.05, p.x); // (the head steadier)
        float s = clamp((-b.x - 0.07) / 0.18, 0.0, 1.0);
        vec3 tp = b;
        tp.z -= 0.03 * s * s * sin(wave * 0.9 - s * 3.0);
        addPart(res, best, ptype, sdEllipsoid(tp - vec3(-0.16, 0.048, 0.0), vec3(0.11, 0.032 * (1.0 - 0.55 * s), 0.011 * (1.0 - 0.5 * s))) * 0.7, 0, 0.0);
        addPart(res, best, ptype, sdEllipsoid(b - vec3(-0.015, 0.046, 0.0), vec3(0.08, 0.03, 0.035)), 0, 0.02);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.085, 0.05, 0.0), vec3(0.042, 0.026, 0.047)), 0, 0.018);
        addPart(res, best, ptype, length(sp - vec3(0.104, 0.064, 0.031)) - 0.0065, 2, 0.0);
        for (int k = 0; k < 3; k++) { // the gills: three stalks each side behind the head, fringed, waving
            float fk = float(k);
            vec3 root = vec3(0.062, 0.06 - fk * 0.011, 0.036);
            float wv = 0.18 * sin(wave * 0.7 + fk * 1.3 + (p.z > 0.0 ? 0.0 : 1.0));
            vec3 tip = root + normalize(vec3(-0.55 - fk * 0.1 + wv, 0.85 - fk * 0.45, 0.45 + fk * 0.25)) * (0.044 - fk * 0.004);
            vec3 pa = sp - root, ba = tip - root;
            float len = length(ba);
            vec3 ax = ba / len;
            float hh = clamp(dot(pa, ax) / len, 0.0, 1.0);
            float stalk = length(pa - ax * hh * len) - mix(0.0034, 0.0018, hh);
            // and its fringe: a thin feathery comb along it, facing sideways, its edge cut into filaments
            vec3 nz = normalize(vec3(0.0, 0.0, 1.0) - ax * ax.z);
            vec3 wu = cross(nz, ax);
            float along = dot(pa, ax);
            float wdt = 0.0085 * sqrt(max(sin(3.1416 * hh), 0.0)) * (0.45 + 0.55 * abs(sin(hh * 30.0)));
            float comb = max(max(abs(dot(pa, wu)) - wdt, abs(dot(pa, nz)) - 0.0011), max(-along, along - len)) * 0.7;
            addPart(res, best, ptype, min(stalk, comb), 6, 0.003);
        }
        for (int k = 0; k < 2; k++) { // short stubby legs, splayed, stepping in turn, a little splayed foot on each
            float x = k == 0 ? 0.045 : -0.065;
            float ph = stepPh + float(k) * 3.14 + sgn * 1.57;
            addLeg(res, best, ptype, sp, vec3(x, 0.038, 0.024), vec3(x + 0.008, 0.026, 0.05), vec3(x + 0.014, 0.004, 0.056), 0.0095,
                   ph, vec3(1.0, 0.0, 0.0), 0.012, 1);
            vec3 foot = vec3(x + 0.014 + cos(ph) * 0.012 + 0.006, 0.004 + max(sin(ph), 0.0) * 0.011, 0.058);
            addPart(res, best, ptype, sdEllipsoid(sp - foot, vec3(0.011, 0.0035, 0.009)), 1, 0.004);
        }
    } else { // fire shrimp: bright red, white spots, a curved tail, long white legs and feelers (the cleaner shrimp: the
             // same, its feelers longer; cleaning a fish, it rears up, front legs and feelers reaching and picking)
        float clean = kind == 35 ? gPulse : 0.0;
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.015, 0.042, 0.0), vec3(0.028, 0.018, 0.014)), 0, 0.0);
        addPart(res, best, ptype, sdTaper(p, vec3(0.04, 0.047, 0.0), vec3(0.056, 0.05, 0.0), 0.003, 0.001), 0, 0.002);
        for (int k = 0; k < 5; k++) {
            float fk = float(k);
            addPart(res, best, ptype, sdEllipsoid(p - vec3(-0.012 - fk * 0.011, 0.042 - fk * 0.004 - fk * fk * 0.0009, 0.0),
                                                  vec3(0.009, 0.016 - fk * 0.0018, 0.012 - fk * 0.0015)), 0, 0.003);
        }
        vec3 tf = p - vec3(-0.072, 0.02, 0.0);
        tf.xy = mat2(0.8, 0.6, -0.6, 0.8) * tf.xy;
        addPart(res, best, ptype, sdEllipsoid(tf, vec3(0.012, 0.0035, 0.011)), 0, 0.003);
        addPart(res, best, ptype, length(sp - vec3(0.038, 0.052, 0.007)) - 0.0042, 2, 0.0);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.03, 0.032, 0.008), vec3(0.058, 0.026, 0.013), 0.0032, 0.0018), 0, 0.0);
        for (int k = 0; k < 5; k++) {
            float x = 0.024 - float(k) * 0.009;
            vec3 foot = vec3(x - 0.004, 0.0, 0.026);
            if (k < 2 && clean > 0.0) { // (reaching up and forward, picking: each its own little rhythm)
                float pick = sin(gTime * 9.0 + float(k) * 2.1 + sgn * 1.3);
                foot = mix(foot, vec3(0.06 + 0.01 * pick, 0.07 + 0.012 * pick + 0.01 * float(k), 0.012), clean);
            }
            addLeg(res, best, ptype, sp, vec3(x, 0.03, 0.007), vec3(x + 0.002, 0.037, 0.02),
                   foot, 0.0018, stepPh * 1.5 + float(k) * 1.26 + sgn * 1.57, vec3(1.0, 0.0, 0.0), 0.005, 3);
        }
        float aw = 0.01 * sin(wave * 1.1 + p.z * 50.0) + clean * 0.012 * sin(gTime * 6.0 + sgn);
        float al = kind == 35 ? 1.7 : 1.0;
        vec3 a1 = vec3(0.08, 0.07 + 0.03 * clean, 0.02 + aw), a2 = vec3(0.04 + 0.08 * al, 0.052 + aw + 0.07 * clean, 0.036 * al);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.042, 0.05, 0.004), a1, 0.0013, 0.001), 3, 0.0);
        addPart(res, best, ptype, sdTaper(sp, a1, a2, 0.001, 0.0008), 3, 0.0);
    }
    return res;
}

vec3 critterColour(int kind, vec3 p, int part, float rnd) {
    if (part == 2) return vec3(0.02); // (eyes)
    if (kind == 21) { // crab
        if (part == 0) {
            float rim = length(vec2(p.x / 0.058, p.z / 0.084));
            vec3 c = mix(vec3(0.13, 0.09, 0.07), vec3(0.75, 0.15, 0.08), smoothstep(0.88, 0.97, rim));
            return mix(c, vec3(0.7, 0.62, 0.5), spot(p.xz * 140.0 + rnd * 9.0, 0.08) * 0.6);
        }
        float tip = smoothstep(0.13, 0.15, p.x);
        return mix(vec3(0.88, 0.16, 0.07), vec3(1.0, 0.55, 0.15), tip * 0.8 + 0.25 * step(0.5, fract(length(p.xz) * 60.0)) * 0.3);
    }
    if (kind == 20) { // hermit crab
        if (part == 4) {
            float ridge = 0.5 + 0.5 * sin(atan(p.y - 0.068, p.x + 0.035) * 4.0 + p.z * 60.0);
            vec3 c = mix(vec3(0.82, 0.8, 0.7), vec3(0.95, 0.93, 0.86), ridge);
            c = mix(c, vec3(0.6, 0.66, 0.45), smoothstep(0.55, 0.75, fbm(p.xz * 40.0)) * 0.5);
            return c * (1.0 - 0.6 * spot(p.xy * 260.0 + p.z * 90.0, 0.07));
        }
        if (part == 3) return vec3(0.95, 0.92, 0.85);
        if (part == 5) return vec3(1.0, 0.42, 0.12);
        vec3 c = mix(vec3(1.0, 0.85, 0.65), vec3(1.0, 0.42, 0.15), smoothstep(0.02, 0.05, p.y));
        return mix(c, vec3(1.0, 0.95, 0.88), spot(vec2(p.x, p.y + p.z) * 220.0, 0.12) * 0.8);
    }
    if (kind == 22) { // stingray
        if (p.y < 0.026 && part == 0) return vec3(0.9, 0.88, 0.85);
        vec3 c = mix(vec3(0.58, 0.56, 0.52), vec3(0.68, 0.66, 0.62), fbm(p.xz * 12.0 + rnd * 5.0));
        return part == 1 ? c * 0.85 : mix(c, c * 1.1, smoothstep(0.8, 1.0, length(vec2(p.x / 0.25, p.z / 0.27))));
    }
    if (kind == 23) { // blue-ringed octopus: cream-yellow, ochre patches each ringed in electric blue edged black
        vec2 uv = part == 1 ? vec2(length(p.xz) * 45.0, atan(p.z, p.x) * 3.0) : vec2(p.x * 70.0 + p.y * 30.0, p.z * 70.0 + p.y * 50.0);
        vec2 cell = floor(uv);
        vec2 o = hash22(cell) * 0.4 + 0.3;
        float d = length(fract(uv) - o);
        vec3 c = mix(vec3(0.85, 0.75, 0.5), vec3(0.95, 0.9, 0.75), fbm(uv * 0.3));
        if (d < 0.22) c = vec3(0.6, 0.45, 0.25);                       // (the patch)
        else if (d < 0.27) c = vec3(0.05, 0.04, 0.06);                 // (black edge)
        else if (d < 0.35) c = vec3(0.15, 0.45, 1.0) * 1.5;            // (blue ring)
        else if (d < 0.39) c = vec3(0.05, 0.04, 0.06);
        return c;
    }
    if (kind == 24) { // lobster: red, speckled darker, claw tips dark
        vec3 c = vec3(0.78, 0.2, 0.1) * (0.85 + 0.25 * fbm(p.xz * 60.0));
        c *= 1.0 - 0.35 * spot(p.xz * 180.0 + p.y * 40.0, 0.12);
        if (part == 1) c = mix(c, vec3(0.2, 0.06, 0.05), smoothstep(0.22, 0.3, p.x) * 0.7);
        if (part == 5) c = vec3(0.6, 0.15, 0.1);
        return mix(c, c * 1.2 + 0.05, smoothstep(0.03, 0.0, p.y)); // (paler underneath)
    }
    if (kind == 32) { // axolotl: the game's five colours (pink, wild brown, gold, cyan, the rare blue), deeper-coloured gills
        int v = rnd < 0.3 ? 0 : rnd < 0.55 ? 1 : rnd < 0.75 ? 2 : rnd < 0.95 ? 3 : 4;
        vec3 body = v == 0 ? vec3(1.0, 0.7, 0.76) : v == 1 ? vec3(0.42, 0.31, 0.22) : v == 2 ? vec3(1.0, 0.8, 0.32) : v == 3 ? vec3(0.8, 0.92, 0.96) : vec3(0.28, 0.38, 0.86);
        vec3 gill = v == 0 ? vec3(0.95, 0.3, 0.47) : v == 1 ? vec3(0.52, 0.26, 0.34) : v == 2 ? vec3(0.95, 0.55, 0.12) : v == 3 ? vec3(0.9, 0.45, 0.72) : vec3(0.95, 0.8, 0.25);
        if (part == 6) return gill * (0.85 + 0.25 * fbm(p.xy * 200.0));
        vec3 c = v == 1 ? body * (1.0 - 0.45 * spot(p.xz * 150.0 + p.y * 60.0, 0.18)) : body * (0.92 + 0.12 * fbm(p.xz * 60.0));
        c = mix(c, c * 1.12 + 0.06, smoothstep(0.034, 0.022, p.y)); // (a paler belly)
        if (p.x > 0.095 && abs(p.y - 0.042 - 6.0 * p.z * p.z) < 0.0018) c *= 0.5; // (its smile)
        return p.x < -0.1 ? mix(c, c * 1.1 + 0.08, smoothstep(0.012, 0.03, abs(p.y - 0.048))) : c; // (the tail fin's edges paler)
    }
    if (kind == 35) { // cleaner shrimp: see-through amber sides, a white stripe down its back between two red ones, white feelers
        if (part == 3) return vec3(0.97);
        float back = smoothstep(0.036, 0.046, p.y + p.x * 0.12);
        vec3 c = vec3(1.0, 0.72, 0.36) * (0.9 + 0.15 * fbm(p.xy * 200.0));
        float az = abs(p.z);
        vec3 stripe = az < 0.0032 ? vec3(1.0) : az < 0.009 ? vec3(0.9, 0.1, 0.08) : c;
        c = mix(c, stripe, back);
        if (p.x < -0.06) c = mix(c, vec3(1.0), spot(p.xz * 300.0, 0.18)); // (white spots on its tail fan)
        return c;
    }
    // fire shrimp
    if (part == 3) return vec3(0.95, 0.94, 0.92);
    vec3 c = vec3(1.0, 0.3, 0.08);
    if (part == 0 && p.x > -0.012) c = mix(c, vec3(1.0), spot(vec2(p.x, p.y + p.z) * 160.0 + rnd * 4.0, 0.12));
    return c;
}

// ---- the squid and glow squid (30, 31): a long mantle with a fin either side at its tip, a head with big eyes, eight
// arms and two long tentacles trailing behind. It swims mantle first: squeezing its mantle to surge forward, arms drawn
// together, then gliding, the mantle filling and the arms spreading. Parts: 0 mantle and head, 1 arms, 2 fins, 3 eyes ----
float squidSDF(vec3 p, float wave, float pulse, out int ptype, out vec3 q) {
    q = p;
    ptype = 0;
    float res = 1.0e9, best = 1.0e9;
    float sq = 1.0 - 0.16 * pulse;
    addPart(res, best, ptype, sdEllipsoid(p - vec3(0.13, 0.0, 0.0), vec3(0.22, 0.058 * sq, 0.058 * sq)), 0, 0.0);
    addPart(res, best, ptype, sdTaper(p, vec3(0.37, 0.0, 0.0), vec3(0.17, 0.0, 0.0), 0.006, 0.05 * sq), 0, 0.03);
    // the fins: a flat diamond either side at the tip, rippling
    vec3 fp = p - vec3(0.28, 0.0, 0.0);
    float az = abs(fp.z);
    fp.y -= 0.012 * sin(gTime * 5.0 + wave * 0.3 - az * 45.0) * smoothstep(0.02, 0.09, az);
    float dia = (abs(fp.x) * 0.095 + az * 0.085 - 0.085 * 0.095) / 0.1275;
    addPart(res, best, ptype, max(dia, abs(fp.y) - 0.0045 * (1.0 - 0.6 * clamp(az / 0.095, 0.0, 1.0))), 2, 0.012);
    // the head and its eyes
    addPart(res, best, ptype, sdEllipsoid(p - vec3(-0.1, 0.0, 0.0), vec3(0.055, 0.044, 0.048)), 0, 0.02);
    addPart(res, best, ptype, length(vec3(p.x, p.y, abs(p.z)) - vec3(-0.095, 0.008, 0.041)) - 0.018, 3, 0.004);
    // the arms (all behind the head: only worked out when this point may be nearer them than the rest)
    if (p.x + 0.13 < res) {
        float spread = 0.15 + 0.85 * (1.0 - pulse);
        for (int k = 0; k < 8; k++) {
            float th = (float(k) + 0.5) * 0.7854;
            vec2 rr = vec2(cos(th), sin(th));
            vec3 prev = vec3(-0.135, 0.02 * rr.x, 0.02 * rr.y);
            float rp = 0.0095;
            for (int j = 1; j <= 3; j++) {
                float s = float(j) / 3.0;
                float sway = 0.016 * s * s * sin(gTime * 2.3 + wave * 0.15 + float(k) * 0.9 - s * 3.0);
                float out_ = 0.02 + spread * 0.05 * s;
                vec3 pt = vec3(-0.135 - 0.2 * s, out_ * rr.x - sway * rr.y, out_ * rr.y + sway * rr.x);
                float r = mix(0.0095, 0.0016, s);
                addPart(res, best, ptype, sdTaper(p, prev, pt, rp, r), 1, j == 1 ? 0.012 : 0.0);
                prev = pt;
                rp = r;
            }
        }
        for (int k = 0; k < 2; k++) { // the two long tentacles, a club at each end
            float th = k == 0 ? 3.6 : 5.8;
            vec2 rr = vec2(cos(th), sin(th));
            vec3 prev = vec3(-0.14, 0.015 * rr.x, 0.015 * rr.y);
            for (int j = 1; j <= 3; j++) {
                float s = float(j) / 3.0;
                float sway = 0.03 * s * s * sin(gTime * 1.7 + float(k) * 2.0 - s * 3.0);
                float out_ = 0.015 + 0.03 * s * spread;
                vec3 pt = vec3(-0.14 - 0.31 * s, out_ * rr.x - sway * rr.y, out_ * rr.y + sway * rr.x);
                addPart(res, best, ptype, sdTaper(p, prev, pt, 0.004, 0.0028), 1, 0.0);
                prev = pt;
            }
            addPart(res, best, ptype, sdEllipsoid(p - prev - vec3(-0.015, 0.0, 0.0), vec3(0.022, 0.007, 0.009)), 1, 0.006);
        }
    }
    return res;
}

// where a glow squid glows (0..1): rows of spots down its mantle, rings round its eyes, its fins' edges, its arm tips
float squidGlow(vec3 p, int part) {
    if (part == 3) return 0.0;
    if (part == 2) return smoothstep(0.55, 0.85, abs(p.z) / 0.095 + abs(p.x - 0.28) / 0.085) * 0.9;
    if (part == 1) return smoothstep(-0.25, -0.32, p.x) * 0.8 + spot(vec2(p.x * 120.0, atan(p.z, p.y) * 2.0), 0.18) * 0.5;
    float ang = atan(p.z, p.y);
    float rows = spot(vec2(p.x * 38.0, ang * 2.55), 0.22 + 0.06 * sin(p.x * 30.0));
    float eyeRing = smoothstep(0.006, 0.0, abs(length(vec3(p.x, p.y, abs(p.z)) - vec3(-0.095, 0.008, 0.041)) - 0.022));
    float lines = smoothstep(0.02, 0.0, abs(abs(ang) - 1.5708)) * step(-0.05, p.x); // (a glowing line down each side)
    return clamp(rows + eyeRing + lines * 0.8, 0.0, 1.0);
}

vec3 squidColour(int kind, vec3 p, int part, float rnd) {
    bool gl = kind == 31;
    if (part == 3) { // its big eye: a black pupil in a golden (the glow squid's: teal) ring
        float r = length(vec2(p.x + 0.095, p.y - 0.008)) / 0.018;
        return r < 0.5 ? vec3(0.01) : gl ? vec3(0.2, 0.85, 0.75) : vec3(0.78, 0.6, 0.25);
    }
    float under = smoothstep(0.015, -0.04, p.y);
    vec3 top = gl ? vec3(0.03, 0.17, 0.22) : vec3(0.16, 0.22, 0.38);
    vec3 low = gl ? vec3(0.08, 0.3, 0.34) : vec3(0.5, 0.53, 0.68);
    vec3 c = mix(top, low, under);
    // chromatophores: small dark spots, flickering slowly as squid skin does
    vec2 uv = vec2(p.x * 95.0, atan(p.z, p.y) * 6.0);
    float flick = 0.5 + 0.5 * sin(gTime * 1.3 + hash22(floor(uv)).x * 30.0 + rnd * 9.0);
    c *= 1.0 - (gl ? 0.25 : 0.45) * spot(uv + rnd * 7.0, 0.2 + 0.12 * flick);
    if (part == 1) c = mix(c * 1.15, low, 0.3) * (1.0 - 0.2 * step(0.6, fract(p.x * 140.0)) * under);   // (suckers along the arms)
    if (part == 2) c = mix(c, low * 1.1, 0.35);
    if (gl) c = mix(c, vec3(0.3, 1.0, 0.88), squidGlow(p, part) * 0.7);
    return c;
}

float fishSDF(int kind, Species sp, vec3 p, float wave, float flap, float pulse, out int ptype, out vec3 q) {
    if (isCrawler(kind)) {
        return critterSDF(kind, p, wave, ptype, q);
    }
    if (isSquid(kind)) {
        return squidSDF(p, wave, pulse, ptype, q);
    }
    if (kind != 12 && kind != 13 && kind != 15) {
        return fishBody(kind, sp, p, wave, flap, ptype, q);
    }
    float L = sp.body.x;
    if (kind == 12) { // the manta: wings swept back, beating slowly up and down in a wave out to the tips
        float zr = clamp(abs(p.z) / sp.body.z, 0.0, 1.2);
        p.y -= sp.body.z * 0.2 * zr * zr * sin(wave * 0.6 - zr * 1.2);
        p.x += 0.4 * sp.body.x * zr * zr;
    }
    if (kind == 13) {
        p.z -= 0.5 * gBend * p.x * p.x;
    }
    if (kind == 15) { // the jellyfish's arms and tentacles trailing, swaying in slow waves running down them
        float below = max(-p.y - sp.body.y * 0.5, 0.0);
        float bend = below * below * 0.6 + below * 0.04;
        p.x += bend * sin(gTime * 1.3 + p.y * 4.5 + wave * 0.2);
        p.z += bend * 0.8 * cos(gTime * 1.05 + p.y * 3.8 + wave * 0.15);
    }
    float swing = kind == 12 || kind == 15 ? sin(wave) : kind == 13 ? flap : 0.0;
    float res = 1.0e9, best = 1.0e9;
    ptype = 0;
    q = p / sp.body;
    for (int j = 0; j < PARTS; j++) {
        Part pt = fishPart(kind, sp, j, swing, flap);
        if (pt.r.x <= 0.0) {
            continue;
        }
        vec3 r = pt.r;
        vec3 d0 = p - pt.at;
        vec3 fx = vec3(cos(pt.yaw), 0.0, sin(pt.yaw));
        vec3 fz = vec3(-sin(pt.yaw), 0.0, cos(pt.yaw));
        vec3 ax = fx * cos(pt.pitch) + vec3(0.0, sin(pt.pitch), 0.0);
        vec3 ay = vec3(0.0, cos(pt.pitch), 0.0) - fx * sin(pt.pitch);
        vec3 lq = vec3(dot(d0, ax), dot(d0, ay), dot(d0, fz));
        float d;
        if (kind == 15 && j == 0) { // the bell: a hollow dome, squeezing in quickly then slowly relaxing
            vec3 rb = r * vec3(1.0 - 0.15 * pulse, 1.0 + 0.12 * pulse, 1.0 - 0.15 * pulse);
            d = max(sdEllipsoid(lq, rb), -sdEllipsoid(lq + vec3(0.0, rb.y * 0.6, 0.0), rb * vec3(0.85, 0.75, 0.85)));
        } else if (kind == 15) { // an arm or tentacle: a strand from its top, thinning to its end
            float hy = clamp(lq.y, -r.y, r.y);
            float rad = r.x * (0.35 + 0.65 * (hy + r.y) / (2.0 * r.y));
            d = length(vec2(length(vec2(lq.x, lq.z * (j <= 4 ? 2.0 : 1.0))), lq.y - hy)) - rad;
        } else if (kind == 13 && j >= 2) { // a flipper: long, narrowing to a point at its tip (not a round paddle)
            float along = clamp(abs(lq.z) / r.z, 0.0, 1.0);
            d = sdEllipsoid(vec3(lq.x / (1.0 - 0.6 * along), lq.y, lq.z), r) * 0.55;
        } else if (kind == 13 && j == 0) { // the shell: domed on top, flatter underneath
            d = sdEllipsoid(lq, vec3(r.x, lq.y > 0.0 ? r.y : r.y * 0.55, r.z));
        } else {
            d = sdEllipsoid(lq, r);
        }
        float k = pt.type == 0 ? 0.35 * min(sp.body.y, sp.body.z) : 0.2 * min(r.y, r.z) + 0.012 * L;
        res = smin(res, d, k);
        if (d < best) {
            best = d;
            ptype = pt.type;
            q = pt.type == 0 ? p / sp.body : lq / r;
        }
    }
    return res;
}
