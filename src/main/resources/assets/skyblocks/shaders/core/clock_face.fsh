#version 150

// The time crystal's clock face, laid on the ground round it, as wide as its dome: rings, twelve hour marks (the
// quarters bigger) and sixty minute ticks, a rune at each hour, a band of runes turning backwards, and two hands - the
// hour hand going round once a dome day (it points where the dome's sun is in its day: noon at the top), a little sun at
// its tip; the minute hand twelve times as fast. Light, added (as shaders/core/spin_aura: alpha next to nothing).

uniform float GameTime;

in vec2 at;
flat in float speed;
flat in float radius;
flat in float strength;

out vec4 fragColor;

const float TAU = 6.2831853;

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

float daysPerDay(float s) {
    return s < 0.5 ? 10.0 : s < 1.5 ? 20.0 : s < 2.5 ? 40.0 : 80.0;
}

// a line of width w (blocks), at distance d (blocks): its light, softened by a pixel
float line(float d, float w) {
    float px = fwidth(d) + 1e-4;
    return 1.0 - smoothstep(w * 0.5, w * 0.5 + px, abs(d));
}

// a hand: from the middle along angle a (0 = north, clockwise), length l and width w (blocks)
float hand(vec2 p, float a, float l, float w) {
    vec2 dir = vec2(sin(a), -cos(a));
    float s = clamp(dot(p, dir), 0.0, l);
    float d = length(p - dir * s);
    return line(d, w * (1.0 - 0.5 * s / l));
}

// a rune: a little 3 x 3 pattern of strokes (seed), in a cell of size 1 (q: -0.5 .. 0.5)
float rune(vec2 q, float seed) {
    float v = 0.0;
    for (int i = 0; i < 6; i++) {
        float h = hash1(seed * 13.1 + float(i) * 7.7);
        if (h < 0.35) continue;
        vec2 a = (vec2(floor(hash1(seed + float(i)) * 3.0), floor(hash1(seed * 2.0 + float(i)) * 3.0)) - 1.0) * 0.35;
        vec2 b = (vec2(floor(hash1(seed + float(i) * 3.0) * 3.0), floor(hash1(seed * 5.0 + float(i)) * 3.0)) - 1.0) * 0.35;
        vec2 ab = b - a;
        float s = clamp(dot(q - a, ab) / max(dot(ab, ab), 1e-4), 0.0, 1.0);
        v = max(v, 1.0 - smoothstep(0.05, 0.1, length(q - a - ab * s)));
    }
    return v;
}

void main() {
    vec2 p = at * radius;                     // blocks from the middle
    float r = length(p);
    if (r > radius + 0.2) {
        discard;
    }
    float a = atan(p.x, -p.y);                // 0 north, clockwise
    float u = fract(a / TAU);
    float R = radius;
    vec3 teal = vec3(0.3, 1.0, 0.88), violet = vec3(0.78, 0.45, 1.0), gold = vec3(1.0, 0.72, 0.3);
    vec3 col = vec3(0.0);
    // rings
    col += teal * line(r - (R - 0.12), 0.12);
    col += teal * 0.8 * line(r - (R - 0.95), 0.06);
    col += violet * 0.7 * line(r - R * 0.42, 0.06);
    col += teal * 0.6 * line(r - 0.9, 0.05);
    // hour marks (quarters bigger) and minute ticks, between the outer rings
    float hourCell = u * 12.0;
    float hf = abs(fract(hourCell + 0.5) - 0.5) / 12.0 * TAU * r;
    bool quarter = mod(floor(hourCell + 0.5), 3.0) < 0.5;
    float inHourBand = step(R - 0.9, r) * step(r, R - 0.2);
    col += teal * line(hf, quarter ? 0.2 : 0.09) * inHourBand * (quarter ? 1.2 : 0.9);
    float mf = abs(fract(u * 60.0 + 0.5) - 0.5) / 60.0 * TAU * r;
    col += teal * 0.5 * line(mf, 0.04) * step(R - 0.45, r) * step(r, R - 0.2);
    // a rune at each hour, inside the marks
    float runeR = R - 1.45;
    vec2 hourDir = vec2(sin(floor(hourCell + 0.5) / 12.0 * TAU), -cos(floor(hourCell + 0.5) / 12.0 * TAU));
    vec2 c = hourDir * runeR;
    vec2 side = vec2(-hourDir.y, hourDir.x);
    vec2 q = vec2(dot(p - c, side), dot(p - c, -hourDir)) / 0.8;
    if (max(abs(q.x), abs(q.y)) < 0.5 && runeR > 1.6) {
        col += violet * rune(q, floor(hourCell + 0.5) + 1.0) * 1.1;
    }
    // a band of runes turning backwards, inside the violet ring
    float bandR = R * 0.42 - 0.45;
    if (bandR > 0.9 && abs(r - bandR) < 0.22) {
        float n = floor(TAU * bandR / 0.5);
        float turn = fract(GameTime * 12.0 * (speed + 1.0));
        float bu = fract(u + turn) * n;
        vec2 bq = vec2(fract(bu) - 0.5, (r - bandR) / 0.44);
        col += teal * 0.75 * rune(bq * vec2(1.0, 1.0), floor(bu) + 40.0);
    }
    // the hands: the hour hand a turn each dome day (noon at the top), the minute hand twelve turns
    float phase = fract(GameTime * daysPerDay(speed));
    float hourA = (phase - 0.25) * TAU;
    float minA = fract(phase * 12.0) * TAU;
    col += gold * hand(p, hourA, R * 0.55, 0.22) * 1.1;
    col += teal * hand(p, minA, R * 0.8, 0.12);
    vec2 tip = vec2(sin(hourA), -cos(hourA)) * (R * 0.55 + 0.35);
    float sunD = length(p - tip);
    col += gold * (1.0 - smoothstep(0.2, 0.26, sunD)) * 1.4;
    float rays = step(0.3, sunD) * step(sunD, 0.48) * step(0.6, abs(sin(atan(p.y - tip.y, p.x - tip.x) * 4.0)));
    col += gold * rays;
    // its hub: glowing under the crystal
    col += violet * exp(-r * 2.5) * 0.6;
    col *= strength * (0.85 + 0.15 * sin(GameTime * 1200.0 * 0.15));
    if (max(col.r, max(col.g, col.b)) < 0.003) {
        discard;
    }
    fragColor = vec4(col, 1.0 / 255.0);
}
