#version 150

// Shadered+'s dance floor (our own code): LED tiles, 2 x 2 per block, showing one of 9 animations across the whole
// floor; AnimA fades into AnimB as Blend goes 0 -> 1. Bass from music nearby makes the lights pulse.

uniform float Time;   // seconds
uniform float AnimA;
uniform float AnimB;
uniform float Blend;
uniform vec2 Size;    // the floor's size in blocks (x, z)
uniform float Music;
uniform float Bass;
uniform vec4 Bars0;
uniform vec4 Bars1;
uniform vec4 Bars2;
uniform vec4 Bars3;

in vec2 floorPos;

out vec4 fragColor;

const float TAU = 6.2831853;

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

vec3 hsv(float h, float s, float v) {
    vec3 k = clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(vec3(1.0), k, s);
}

float bar(int i) {
    vec4 v = i < 4 ? Bars0 : i < 8 ? Bars1 : i < 12 ? Bars2 : Bars3;
    return v[i - (i / 4) * 4];
}

// the colour of the tile at q (its middle, in blocks from the floor's corner), cell = which tile
vec3 animation(int kind, vec2 q, vec2 cell, float t) {
    vec2 c = Size * 0.5;
    if (kind == 0) { // checkerboard, swapping on the beat, colours turning
        float beat = floor(t * 2.0);
        float k = mod(cell.x + cell.y, 2.0);
        float on = mod(k + beat, 2.0);
        return hsv(fract(beat * 0.13 + k * 0.5), 0.9, mix(0.12, 1.0, on));
    }
    if (kind == 1) { // rainbow wave
        return hsv(fract((q.x + q.y) * 0.07 - t * 0.3), 0.85, 1.0);
    }
    if (kind == 2) { // rings rippling out from the middle
        float d = length(q - c);
        float v = 0.5 + 0.5 * sin(d * 2.5 - t * 5.0);
        return hsv(fract(d * 0.04 - t * 0.1), 0.8, 0.08 + 0.92 * v * v);
    }
    if (kind == 3) { // a radar sweep going round
        vec2 r = q - c;
        float s = fract(atan(r.y, r.x) / TAU - t * 0.3);
        return hsv(fract(length(r) * 0.05 + t * 0.05), 0.85, 0.06 + 0.94 * pow(s, 3.0));
    }
    if (kind == 4) { // tiles lighting up and fading at random, random colours
        float r = hash12(cell);
        float phase = t * 0.55 + r;
        float f = fract(phase);
        float v = smoothstep(0.0, 0.12, f) * (1.0 - smoothstep(0.35, 0.9, f));
        return hsv(hash12(cell + floor(phase) * 7.31), 0.9, 0.05 + 0.95 * v);
    }
    if (kind == 5) { // two light bars sweeping back and forth, across and along
        float lenX = max(Size.x, 1.0), lenZ = max(Size.y, 1.0);
        float px = lenX * (0.5 + 0.5 * sin(t * 2.2));
        float pz = lenZ * (0.5 + 0.5 * sin(t * 1.7 + 1.3));
        float v = exp(-abs(q.x - px) * 1.3) + exp(-abs(q.y - pz) * 1.3);
        return hsv(fract(t * 0.07), 0.8, 0.05) + hsv(fract(t * 0.07 + 0.15), 0.85, 1.0) * min(v, 1.2);
    }
    if (kind == 6) { // squares growing out from the middle
        float d = max(abs(q.x - c.x), abs(q.y - c.y)) * 0.6 - t * 1.4;
        return hsv(fract(floor(d) * 0.17), 0.9, step(0.45, fract(d)) * 0.92 + 0.08);
    }
    if (kind == 7) { // plasma
        float v = sin(q.x * 0.55 + t) + sin(q.y * 0.65 - t * 1.3) + sin((q.x + q.y) * 0.35 + t * 0.7) + sin(length(q - c) * 0.8 - t);
        return hsv(fract(v * 0.12 + t * 0.05), 0.9, 0.75 + 0.25 * sin(v * 2.0));
    }
    // 8: an equaliser across the floor (the music playing nearby; without music it dances on its own)
    float across = clamp(q.x / max(Size.x, 1.0), 0.0, 0.999);
    int band = int(across * 16.0);
    float level = Music > 0.05 ? bar(band) : 0.45 + 0.35 * sin(t * 3.1 + float(band) * 0.8) * sin(t * 1.3 + float(band) * 0.31);
    float up = q.y / max(Size.y, 1.0);
    float lit = step(up, level);
    return hsv(mix(0.33, 0.0, up), 0.9, mix(0.06, 1.0, lit));
}

void main() {
    vec2 tiles = floorPos * 2.0;
    vec2 cell = floor(tiles);
    vec2 inTile = fract(tiles);
    vec2 q = (cell + 0.5) / 2.0;
    vec3 a = animation(int(AnimA + 0.5), q, cell, Time);
    vec3 b = animation(int(AnimB + 0.5), q, cell, Time);
    vec3 c = mix(a, b, Blend) * (1.0 + 0.7 * Bass);

    // a glowing square with a darker frame, brightest in the middle
    float edge = min(min(inTile.x, 1.0 - inTile.x), min(inTile.y, 1.0 - inTile.y));
    float aa = fwidth(tiles.x) + fwidth(tiles.y);
    float tile = smoothstep(0.05, 0.05 + aa + 0.03, edge);
    float glow = 0.8 + 0.3 * (1.0 - smoothstep(0.0, 0.7, length(inTile - 0.5)));
    vec3 col = mix(vec3(0.03, 0.03, 0.04) + c * 0.12, c * glow, tile);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
