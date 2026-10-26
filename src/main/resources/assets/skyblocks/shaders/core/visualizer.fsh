#version 150

// Shadered+'s music visualizer (our own code): the music's 16 bands (bass to treble) as glowing LED bars with
// falling peak caps, as bars mirrored from the middle, as a scrolling spectrum, as a ring of bars round a pulsing
// circle, or as glowing waves; gently breathing when it's quiet.

in vec2 uv;
in vec4 vColor;

uniform sampler2D Sampler0; // the bands' last seconds (x: how long ago, y: band)
uniform vec4 Bands0;
uniform vec4 Bands1;
uniform vec4 Bands2;
uniform vec4 Bands3;
uniform float Music;
uniform float VisTime;

out vec4 fragColor;

float bandAt(int k) {
    vec4 b = k < 4 ? Bands0 : k < 8 ? Bands1 : k < 12 ? Bands2 : Bands3;
    int i = k - (k / 4) * 4;
    return i == 0 ? b.x : i == 1 ? b.y : i == 2 ? b.z : b.w;
}

// a band's level at f (0 = bass .. 1 = treble), smooth between bands; breathing when quiet
float level(float f, float t) {
    float x = clamp(f, 0.0, 1.0) * 15.0;
    int k = int(floor(x));
    float v = mix(bandAt(k), bandAt(min(k + 1, 15)), fract(x));
    v = pow(clamp(v, 0.0, 1.0), 0.75) * 0.95;
    float idle = 0.06 + 0.05 * sin(t * 1.7 + f * 9.0) + 0.03 * sin(t * 2.9 - f * 23.0);
    return max(v, idle * (1.0 - clamp(Music * 2.0, 0.0, 1.0)));
}

// the highest it's been in the last half second (the peak caps)
float peak(float f) {
    float y = (clamp(f, 0.0, 1.0) * 15.0 + 0.5) / 16.0;
    float p = 0.0;
    for (int i = 0; i < 6; i++) {
        p = max(p, texture(Sampler0, vec2((float(i) * 2.0 + 0.5) / 128.0, y)).r);
    }
    return pow(p, 0.75) * 0.95;
}

vec3 hue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    int ia = int(vColor.a * 255.0 + 0.5);
    int mode = ia / 32;
    float w = floor(uv.x / 2.0), hgt = floor(uv.y / 2.0);
    vec2 p = vec2(uv.x - 2.0 * w, uv.y - 2.0 * hgt);
    w = max(w, 1.0);
    hgt = max(hgt, 1.0);
    bool rainbow = dot(vColor.rgb, vec3(1.0)) < 0.004;
    float t = VisTime;
    // the screen: nearly black glass with a faint grid
    vec3 col = vec3(0.012, 0.014, 0.02);
    vec2 grid = abs(fract(p * vec2(w * 8.0, 24.0)) - 0.5);
    col += vec3(0.02, 0.025, 0.035) * smoothstep(0.47, 0.5, max(grid.x, grid.y));

    if (mode == 2) {
        // the spectrum: time runs to the left, bass at the bottom; hot colours where it's loud
        float v = texture(Sampler0, vec2(clamp(1.0 - p.x, 0.0, 1.0) * 0.98 + 0.01, (clamp(p.y, 0.0, 1.0) * 15.0 + 0.5) / 16.0)).r;
        v = pow(v, 0.8);
        vec3 base = rainbow ? hue(p.y * 0.8 + t * 0.03) : vColor.rgb;
        vec3 heat = v < 0.5 ? base * v * 2.0 : mix(base, vec3(1.0), (v - 0.5) * 1.4);
        col += heat * 1.2;
    } else if (mode == 3) {
        // a ring of bars round a circle that swells with the bass (bass at the top, treble at the bottom, both sides)
        vec2 q = (p - 0.5) * vec2(w, hgt);
        float R = 0.5 * min(w, hgt);
        float r = length(q) / R;
        float ang = atan(q.x, q.y);                       // (0 at the top)
        float f = abs(ang) / 3.14159;
        float n = 40.0;
        float i = floor(f * n);
        float within = fract(f * n);
        float fb = (i + 0.5) / n;
        float v = level(fb, t);
        float bass = level(0.03, t);
        float r0 = 0.34 + 0.06 * bass;
        vec3 base = rainbow ? hue(fb * 0.85 + t * 0.05) : vColor.rgb;
        float bar = step(0.18, within) * step(within, 0.82);
        if (r > r0 + 0.02 && r < r0 + 0.02 + v * 0.6) {
            col = mix(col, mix(base, vec3(1.0), smoothstep(0.3, 0.6, r - r0) * 0.5) * 1.3, bar);
        }
        float ring = 1.0 - smoothstep(0.0, 0.025, abs(r - r0));
        vec3 ringCol = rainbow ? hue(t * 0.05) : vColor.rgb;
        col += mix(ringCol, vec3(1.0), 0.4) * ring * (0.7 + 0.8 * bass);
        col += ringCol * 0.18 * exp(-max(r0 - r, 0.0) * 12.0) * step(r, r0) * bass; // (a glow inside the circle)
    } else if (mode == 4) {
        // glowing waves: bass, middle and treble as three lines rippling across the screen
        vec2 q = p * vec2(w, hgt);
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float amp = level(fk * 0.4 + 0.05, t) * hgt * 0.32;
            float freq = (1.2 + fk * 1.3) * 2.094; // (waves per block)
            float yk = hgt * (0.5 + (fk - 1.0) * 0.12)
                     + amp * sin(q.x * freq - t * (1.5 + fk)) * (0.6 + 0.4 * sin(q.x * freq * 0.37 + t * 0.7 + fk));
            float d = abs(q.y - yk);
            vec3 base = rainbow ? hue(fk * 0.33 + q.x * 0.05 / w + t * 0.05) : mix(vColor.rgb, vec3(1.0), fk * 0.2);
            col += base * (1.0 - smoothstep(0.0, 0.03, d)) * 1.2 + base * 0.35 * exp(-d * 9.0);
        }
    } else {
        float n = min(w * 6.0, 96.0);
        float fx = p.x;
        bool middle = mode == 1;
        if (middle) {
            fx = abs(p.x - 0.5) * 2.0; // (bass in the middle, mirrored to both sides)
            n = n * 0.5;
        }
        float i = floor(fx * n);
        float within = fract(fx * n);
        float f = (i + 0.5) / n;
        float v = level(f, t);
        float pk = max(peak(f), v);
        float y = middle ? abs(p.y - 0.5) * 2.0 : p.y;
        float bar = step(0.14, within) * step(within, 0.86);
        float seg = step(fract(y * (middle ? 12.0 : 24.0)), 0.8); // (LED segments)
        vec3 base = rainbow ? hue(f * 0.85 + t * 0.05) : vColor.rgb;
        vec3 lit = mix(base, vec3(1.0), smoothstep(0.55, 1.0, y) * 0.45) * (0.6 + 0.6 * y); // (brighter towards the top)
        if (y < v) {
            col = mix(col, lit * 1.25, bar * seg);
        }
        // the peak cap, and a soft glow above the bar
        float cap = bar * (1.0 - smoothstep(0.0, 0.018, abs(y - pk - 0.02)));
        col += mix(base, vec3(1.0), 0.5) * cap * 1.2;
        col += base * bar * 0.35 * exp(-max(y - v, 0.0) * 22.0) * step(v, y);
        col += base * 0.05 * exp(-y * 6.0) * v;
    }
    fragColor = vec4(col, 1.0);
}
