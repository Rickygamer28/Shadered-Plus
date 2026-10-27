#version 150

// Shadered+'s underwater sky (our own code): down in clear blue sea. Overhead the bright rippling surface, the sun a
// blur of light through it; shafts of light slanting down and swaying; all round, blue water fading into the
// distance, kelp swaying far off, schools of little fish wheeling past, now and then a turtle or a shark gliding by,
// bubbles rising; below, a sandy floor with light rippling on it, rocks and seagrass. Walls of these blocks: the sea
// right outside your windows.

uniform float Time; // ticks

in vec3 dir;

out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;
const vec3 SUN = vec3(0.28, 0.93, 0.24);

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash11(float n) {
    return hash12(vec2(n, n * 0.37 + 1.3));
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// rippling light: a moving web of bright lines (as the surface's waves focus the sunlight)
float caustic(vec2 p, float t) {
    float c = 0.0;
    vec2 q = p;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        q = vec2(q.x + 0.6 * sin(q.y * 1.3 + t * (0.6 + 0.2 * fi)), q.y + 0.6 * sin(q.x * 1.1 - t * (0.5 + 0.15 * fi)));
        c += 1.0 / (1.0 + 30.0 * abs(sin(q.x * 1.7) * sin(q.y * 1.9)));
    }
    return c / 3.0;
}

// the water's own colour looking this way: bright above, deep blue all round, dark far below
vec3 water(vec3 d) {
    vec3 c = mix(vec3(0.02, 0.2, 0.34), vec3(0.18, 0.6, 0.74), smoothstep(-0.1, 0.9, d.y));
    c = mix(c, vec3(0.0, 0.06, 0.15), smoothstep(0.0, -0.9, d.y));
    return c + vec3(0.3, 0.45, 0.4) * pow(max(dot(d, normalize(SUN)), 0.0), 6.0) * 0.35;
}

float wrapA(float a) {
    return mod(a + PI, TAU) - PI;
}

// a little fish seen side on (x along it, head forward, y up; in its own size): body, tail, how far in it is
float fishShape(vec2 p, float wig) {
    p.y -= 0.12 * wig * p.x * p.x; // (wiggling)
    float body = length(p * vec2(1.0, 2.6)) - 0.5;
    vec2 tp = p - vec2(-0.62, 0.0);
    float tail = max(abs(tp.y) - (0.05 - tp.x * 0.85), max(tp.x - 0.06, -tp.x - 0.24));
    return min(body, tail);
}

void main() {
    vec3 d = normalize(dir);
    float t = Time / 20.0;
    float az = atan(d.z, d.x);
    float el = asin(clamp(d.y, -1.0, 1.0));
    vec3 sun = normalize(SUN);
    vec3 col = water(d);

    if (d.y > 0.05) { // the surface overhead: bright through its window straight up, mirror-dark beyond it, rippling
        vec2 p = d.xz * (6.0 / d.y);
        float far = length(p);
        float win = smoothstep(0.6, 0.72, d.y); // (the window of sky seen straight up)
        float rip = caustic(p * 0.35, t * 1.1);
        vec3 s = mix(vec3(0.15, 0.45, 0.6), vec3(0.75, 0.95, 1.0), win) * (0.75 + 0.5 * rip);
        s += vec3(1.0, 0.95, 0.8) * pow(max(dot(d, sun), 0.0), 60.0) * 2.0 * (0.8 + 0.4 * rip); // (the sun through it)
        col = mix(col, s, exp(-far * 0.045) * smoothstep(0.05, 0.25, d.y));
    }
    if (d.y < -0.04) { // the sea floor: sand in soft ripples, light dancing on it, rocks and tufts of seagrass
        vec2 p = d.xz * (4.0 / -d.y);
        float far = length(p);
        float rip = 0.5 + 0.5 * sin(p.x * 3.0 + 1.5 * fbm(p * 0.6));
        vec3 sand = vec3(0.78, 0.72, 0.52) * (0.8 + 0.2 * rip) * (0.85 + 0.25 * fbm(p * 4.0));
        sand += vec3(0.5, 0.75, 0.8) * caustic(p * 0.9, t * 1.3) * 0.5;
        float rf = fbm(p * 0.35 + 3.0);
        float rock = smoothstep(0.64, 0.67, rf);
        float lit = clamp((fbm(p * 0.35 + 3.0 + vec2(0.15, 0.15)) - rf) * -14.0 + 0.6, 0.2, 1.2); // (lit on the sun's side)
        vec3 stone = vec3(0.42, 0.44, 0.4) * (0.75 + 0.4 * fbm(p * 3.0)) * lit + vec3(0.4, 0.6, 0.6) * caustic(p * 0.9, t * 1.3) * 0.3;
        stone = mix(stone, vec3(0.25, 0.4, 0.2), smoothstep(0.5, 0.8, fbm(p * 2.0 + 5.0)) * 0.5); // (weedy)
        sand = mix(sand, stone, rock);
        float grass = smoothstep(0.6, 0.7, fbm(p * 0.5 + 9.0)) * step(0.45, noise(p * 14.0 + vec2(sin(t + p.y), 0.0) * 0.3));
        sand = mix(sand, vec3(0.15, 0.45, 0.2), grass * (1.0 - rock));
        col = mix(col, sand * vec3(0.7, 0.9, 1.0), exp(-far * 0.07) * smoothstep(-0.04, -0.2, d.y));
    }

    // kelp far off all round: tall swaying strands rising from the floor into the haze
    {
        float cells = 46.0;
        float x = az / TAU * cells;
        for (int k = -1; k <= 1; k++) {
            float ci = floor(x) + float(k);
            float h = hash11(ci * 1.37);
            if (h < 0.45) continue;
            float top = 0.05 + 0.45 * hash11(ci * 2.11);
            float k01 = clamp((el + 0.12) / (top + 0.12), 0.0, 1.0);
            float sway = 0.18 * sin(t * 0.5 + ci * 1.7 + el * 6.0) * k01;
            float cx = ci + 0.5 + (hash11(ci * 3.3) - 0.5) * 0.5 + sway;
            float w = (0.08 + 0.09 * smoothstep(0.3, 0.7, abs(fract(el * 16.0 + ci) - 0.5) * 2.0)) * (1.0 - 0.5 * k01); // (leaves along it)
            if (abs(x - cx) < w && el > -0.12 && el < top) {
                float depth = 0.45 + 0.4 * hash11(ci * 5.1); // (how far off: hazier)
                vec3 kc = mix(vec3(0.12, 0.25, 0.08), vec3(0.3, 0.42, 0.12), k01);
                col = mix(col, kc, 1.0 - depth);
            }
        }
    }

    // light shafts slanting down through the water, swaying
    {
        vec3 u = normalize(cross(sun, vec3(0.0, 0.0, 1.0))), v = cross(sun, u);
        float a = atan(dot(d, v), dot(d, u));
        float shafts = pow(noise(vec2(a * 9.0 + 0.4 * sin(t * 0.3), t * 0.15)), 3.0) + 0.6 * pow(noise(vec2(a * 23.0 - t * 0.1, 3.0)), 4.0);
        float toward = smoothstep(-0.3, 0.9, dot(d, sun));
        col += vec3(0.4, 0.75, 0.8) * shafts * toward * 0.35 * smoothstep(-0.5, 0.2, d.y);
    }

    // schools of little fish wheeling round, at different heights and distances
    for (int s = 0; s < 3; s++) {
        float fs = float(s);
        float dirn = s == 1 ? -1.0 : 1.0;
        float speed = (0.07 + 0.03 * fs) * dirn;
        float sAz = fs * 2.1 + t * speed;
        float sEl = -0.05 + 0.18 * fs * 0.6 + 0.06 * sin(t * 0.13 + fs * 2.0);
        float scale = 0.05 - 0.01 * fs; // (the nearer school bigger)
        float haze = 0.15 + 0.22 * fs;
        float dAz = wrapA(az - sAz) * cos(el);
        if (abs(dAz) > 0.55 || abs(el - sEl) > 0.35) continue;
        for (int i = 0; i < 16; i++) {
            float fi = float(i);
            vec2 off = (vec2(hash11(fi * 3.1 + fs * 9.0), hash11(fi * 5.3 + fs * 2.0)) - 0.5) * vec2(0.8, 0.4);
            off += 0.025 * vec2(sin(t * 1.3 + fi), cos(t * 1.1 + fi * 1.7)); // (each weaving a little)
            vec2 fp = (vec2(dAz, el - sEl) - off) / scale;
            fp.x *= dirn; // (facing the way it's swimming)
            if (dot(fp, fp) > 1.5) continue;
            float f = fishShape(fp, sin(t * 9.0 + fi * 2.0));
            if (f < 0.0) {
                vec3 fc = mix(vec3(0.15, 0.25, 0.35), vec3(0.7, 0.8, 0.85), smoothstep(-0.2, 0.15, fp.y)); // (dark back, silver belly)
                fc = mix(fc, vec3(0.95, 0.85, 0.3), step(0.85, hash11(fi + fs * 17.0)) * 0.6);             // (a few yellow ones)
                if (length(fp - vec2(0.32, 0.04)) < 0.05) fc = vec3(0.02);
                col = mix(fc, col, haze);
            }
        }
    }

    // now and then something bigger gliding by far off: a sea turtle, or a shark
    {
        float period = 70.0;
        float n = floor(t / period), u = fract(t / period);
        float kind = step(0.5, hash11(n * 3.7));
        float bAz = hash11(n * 1.3) * TAU + (u - 0.5) * 1.6;
        float bEl = 0.05 + 0.2 * hash11(n * 2.9) + 0.03 * sin(t * 0.3);
        vec2 bp = vec2(wrapA(az - bAz) * cos(el), el - bEl) / 0.09;
        bp.x = -bp.x;
        float seen = smoothstep(0.0, 0.12, u) * smoothstep(1.0, 0.88, u);
        float shape;
        if (kind < 0.5) { // a turtle: a domed shell, flippers sweeping, a little head
            float fl = sin(t * 1.4);
            shape = length(bp * vec2(1.0, 1.7)) - 0.55;
            shape = min(shape, length((bp - vec2(0.72, 0.02)) * vec2(1.0, 1.3)) - 0.17);
            vec2 f1 = bp - vec2(0.2, -0.25 - 0.12 * fl);
            shape = min(shape, length(f1 * vec2(0.8, 2.6)) - 0.22);
            vec2 f2 = bp - vec2(-0.35, -0.18);
            shape = min(shape, length(f2 * vec2(1.2, 2.4)) - 0.14);
        } else { // a shark: long body, tall fin on its back, a crescent tail swishing
            float sw = 0.15 * sin(t * 3.0);
            vec2 q = bp;
            q.y -= sw * 0.3 * q.x * q.x * step(q.x, 0.0);
            shape = length(q * vec2(0.55, 3.2)) - 0.55;
            vec2 df = q - vec2(0.05, 0.12);
            shape = min(shape, max(abs(df.x + df.y * 0.5) - 0.18 * (1.0 - df.y / 0.35), max(-df.y, df.y - 0.35)));
            vec2 tf = q - vec2(-1.05, 0.0);
            shape = min(shape, max(abs(tf.y) - (0.03 - tf.x * 1.4), max(tf.x - 0.05, -tf.x - 0.3)));
        }
        if (shape < 0.0 && seen > 0.0) {
            col = mix(col, vec3(0.04, 0.12, 0.2) + (kind < 0.5 ? vec3(0.05, 0.07, 0.0) : vec3(0.05)), 0.6 * seen);
        }
    }

    // bubbles rising past, and bits drifting in the water
    for (int b = 0; b < 14; b++) {
        float fb = float(b);
        float bAz = hash11(fb * 7.7) * TAU;
        float rise = mod(t * (0.05 + 0.04 * hash11(fb * 2.3)) + hash11(fb * 4.1), 1.4) - 0.6;
        float bEl = rise;
        vec2 bp = vec2(wrapA(az - bAz - 0.02 * sin(t * 2.0 + fb)) * cos(el), el - bEl);
        float r = 0.004 + 0.004 * hash11(fb * 9.3);
        float dd = length(bp);
        if (dd < r) col += vec3(0.6, 0.85, 0.9) * (0.25 + 0.75 * smoothstep(r * 0.4, r, dd)) * 0.5;
    }
    vec2 sp = vec2(az * 30.0, el * 30.0 + t * 0.15);
    vec2 sc = floor(sp);
    if (hash12(sc) > 0.93) {
        vec2 so = sc + 0.5 + (vec2(hash12(sc + 3.1), hash12(sc + 7.7)) - 0.5) * 0.6;
        col += vec3(0.5, 0.7, 0.7) * smoothstep(0.08, 0.0, length(sp - so)) * 0.35;
    }
    fragColor = vec4(col, 1.0);
}
