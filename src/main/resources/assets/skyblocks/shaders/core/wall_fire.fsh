#version 150

// Shadered+'s fires (our own code): real 3D flames. The view ray enters through the glass (or the hearth's opening)
// and is marched through a volume of fire - a living sheet of flame over a burner (the glass box) or tongues of
// flame licking up from the logs (the hearth) - adding up its light and how much it hides what is behind.
// Everything in px (1/16 block) in the fire's own frame: x along it, y up, z from the front (0) to the back (16).

in vec3 wray;
in vec2 uvIn;
in vec4 vColor;

uniform float FireTime;
uniform float Boost;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    return 0.62 * noise3(p) + 0.38 * noise3(p * 2.13 + 5.7);
}

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

// how much fire at P (0..1), and how hot (0..1); kind 0 = the glass box's sheet, 1 = the hearth's tongues
vec2 fire(vec3 P, float t, int kind) {
    float base, H, zc, w, foot;
    if (kind == 0) {
        base = 6.5;
        H = 6.2 + 1.2 * Boost;
        zc = 11.8;
        w = 1.7;
        foot = 1.0;
    } else {
        base = (P.x > 11.0 && P.x < 21.0) ? 8.2 : 6.4;
        H = 10.5 + 2.5 * Boost;
        zc = 11.4;
        w = 3.2;
        foot = smoothstep(6.5, 9.0, P.x) * smoothstep(25.5, 23.0, P.x);
    }
    float y = P.y - base;
    float hn = y / H;
    if (y < -0.6 || hn > 1.25 || foot <= 0.0) {
        return vec2(0.0);
    }
    float n = fbm3(vec3(P.x * 0.32, P.y * 0.22 - t * 2.3, P.z * 0.35 + t * 0.3));
    float n2 = noise3(vec3(P.x * 0.9, P.y * 0.6 - t * 4.0, P.z * 0.9));
    float tongue = foot * (0.62 + 0.25 * sin(P.x * (kind == 0 ? 0.42 : 0.75) + n * 3.0 + t * 1.3)
                          + 0.13 * sin(P.x * 1.37 - t * 2.1 + n2 * 2.0));
    float dz = (P.z - zc - (n - 0.5) * (kind == 0 ? 1.6 : 2.8) - sin(P.y * 0.4 - t * 3.0 + P.x * 0.3) * 0.4) / (w * (1.0 - 0.55 * clamp(hn, 0.0, 1.0)));
    float d = tongue - hn - dz * dz * 0.55 + (n - 0.5) * 0.85 + (n2 - 0.5) * 0.3;
    d *= smoothstep(-0.6, 0.4, y); // (a soft start at the burner / logs)
    float dens = smoothstep(0.0, 0.45, d);
    float heat = clamp(d * 1.6 * (1.15 - hn), 0.0, 1.0);
    return vec2(dens, heat);
}

vec3 fireColour(float heat, vec3 fc) {
    vec3 deep = fc * vec3(0.55, 0.3, 0.25) + vec3(0.05, 0.0, 0.0);  // (the cool tips)
    vec3 mid = fc * 1.25;
    vec3 hot = mix(fc + 0.4, vec3(1.0, 0.97, 0.88), 0.7);
    return heat < 0.5 ? mix(deep, mid, heat * 2.0) : mix(mid, hot, heat * 2.0 - 1.0);
}

// a ray against a box: (enter, leave)
vec2 boxHit(vec3 ro, vec3 rd, vec3 lo, vec3 hi) {
    vec3 inv = 1.0 / rd;
    vec3 a = (lo - ro) * inv, b = (hi - ro) * inv;
    vec3 n = min(a, b), f = max(a, b);
    return vec2(max(max(n.x, n.y), n.z), min(min(f.x, f.y), f.z));
}

void main() {
    int code = int(floor(uvIn.y / 10.0 + 0.001));
    int kind = code & 1;
    bool leftEnd = (code & 2) != 0, rightEnd = (code & 4) != 0;
    bool side = (code & 8) != 0, leftSide = (code & 16) != 0; // (the glass at an end: uv.x = the block + how deep)
    float ly = uvIn.y - 10.0 * float(code);
    int ia = int(vColor.a * 255.0 + 0.5);
    int facing = ia / 64;
    float on = float((ia / 32) & 1);
    vec3 fc = vColor.rgb;
    float t = FireTime;

    // into the fire's frame
    vec3 rw = normalize(wray);
    vec3 rd;
    if (facing == 1) rd = vec3(rw.z, rw.y, -rw.x);
    else if (facing == 2) rd = vec3(-rw.x, rw.y, -rw.z);
    else if (facing == 3) rd = vec3(-rw.z, rw.y, rw.x);
    else rd = rw;
    float lz, s0;
    if (kind == 1) { lz = 4.6 / 16.0; s0 = uvIn.x; }
    else if (side) { lz = fract(uvIn.x); s0 = floor(uvIn.x) + (leftSide ? 15.85 : 0.15) / 16.0; }
    else { lz = 8.15 / 16.0; s0 = uvIn.x; }
    float sx = kind == 0 ? mod(s0, 256.0) : s0;
    vec3 ro = vec3(sx, ly, lz) * 16.0;

    // where the ray leaves the volume
    vec3 lo, hi;
    if (kind == 0) {
        float b = floor(s0 + 0.0001);
        lo = vec3(rightEnd ? (mod(b, 256.0) * 16.0 + 0.15) : -1e5, 5.6, 8.0);
        hi = vec3(leftEnd ? (mod(b, 256.0) * 16.0 + 15.85) : 1e5, 13.4, 15.4);
    } else {
        lo = vec3(6.3, 4.2, 4.0);
        hi = vec3(25.7, 21.7, 14.0);
    }
    float tEnd = max(boxHit(ro, rd, lo, hi).y, 0.0);
    if (kind == 1) { // (the logs hide what is behind them)
        vec3 L0[3] = vec3[](vec3(8.0, 4.2, 9.0), vec3(10.0, 4.2, 11.6), vec3(11.0, 6.4, 10.0));
        vec3 L1[3] = vec3[](vec3(24.0, 6.6, 11.4), vec3(22.0, 6.4, 13.8), vec3(21.0, 8.4, 12.4));
        for (int i = 0; i < 3; i++) {
            vec2 h = boxHit(ro, rd, L0[i], L1[i]);
            if (h.x < h.y && h.y > 0.0) {
                tEnd = min(tEnd, max(h.x, 0.0));
            }
        }
    }
    tEnd = min(tEnd, kind == 0 ? 24.0 : 30.0);

    // march
    int N = kind == 0 ? 36 : 44;
    float dt = tEnd / float(N);
    float jitter = hash3(vec3(gl_FragCoord.xy, fract(t) * 7.0));
    vec3 emit = vec3(0.0);
    float trans = 1.0;
    bool bed = false;
    vec3 bp = vec3(0.0);
    for (int i = 0; i < N; i++) {
        vec3 P = ro + rd * (dt * (float(i) + jitter));
        if (kind == 0) {
            // the bed of fire glass on the tray
            float top = 6.3 + 0.7 * noise3(vec3(P.x * 0.7, 0.0, P.z * 0.7));
            if (P.y < top) { // (found the bed between the last step and this one: home in on its surface, so it's smooth)
                float ta = max(dt * (float(i) - 1.0 + jitter), 0.0), tb = dt * (float(i) + jitter);
                for (int k = 0; k < 6; k++) {
                    float tm = 0.5 * (ta + tb);
                    vec3 Q = ro + rd * tm;
                    if (Q.y < 6.3 + 0.7 * noise3(vec3(Q.x * 0.7, 0.0, Q.z * 0.7))) tb = tm; else ta = tm;
                }
                bed = true;
                bp = ro + rd * tb;
                break;
            }
        }
        if (on > 0.5) {
            vec2 f = fire(P, t, kind);
            if (f.x > 0.001) {
                emit += trans * fireColour(f.y, fc) * f.x * 0.55 * dt;
                trans *= exp(-f.x * 0.38 * dt);
                if (trans < 0.02) {
                    break;
                }
            }
        }
    }

    float flick = 0.85 + 0.15 * sin(t * 7.0 + ro.x * 0.2);
    if (kind == 1) {
        // the hearth: only the flames (the bricks, logs and embers are the block's model behind)
        float a = 1.0 - trans;
        fragColor = vec4(emit, a + dot(emit, vec3(0.1))); // (premultiplied; the bright parts also brighten what's behind)
        return;
    }
    // the glass box: what is behind the flames
    vec3 back;
    if (bed) {
        vec2 q = bp.xz * 0.55;
        vec2 id = floor(q), fq = fract(q);
        float d1 = 9.0, d2 = 9.0;
        vec2 cell = vec2(0.0);
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 o = vec2(i, j);
                vec2 pt = o + hash22(id + o) * 0.8 + 0.1;
                float d = length(fq - pt);
                if (d < d1) { d2 = d1; d1 = d; cell = id + o; } else if (d < d2) { d2 = d; }
            }
        }
        vec2 rh = hash22(cell + 5.0);
        float glow = on * flick * (0.4 + 0.6 * exp(-abs(bp.z - 11.8) * 0.35));
        vec3 glassCol = mix(vec3(0.05, 0.055, 0.07), mix(fc, vec3(1.0), 0.15), 0.1 + 0.35 * rh.y * rh.y);
        back = glassCol * (0.4 + 0.6 * rh.x) * (0.35 + 0.65 * glow) + fc * glow * 0.25 * rh.x * rh.x;
        back += (mix(fc, vec3(1.0), 0.5) * on + 0.08) * smoothstep(0.08, 0.0, d2 - d1) * (0.15 + 0.5 * rh.y) * flick;
    } else {
        vec3 pe = ro + rd * tEnd;
        // the back: black glass with the fire's glow on it
        back = vec3(0.014, 0.014, 0.018) + fc * on * 0.3 * flick * exp(-max(pe.y - 6.5, 0.0) * 0.28) * smoothstep(8.0, 15.4, pe.z);
    }
    vec3 col = back * trans + emit;
    // the front glass: a faint slanted reflection
    col += vec3(0.05 + 0.03 * (ly * 16.0 - 5.6) / 7.8) * smoothstep(0.06, 0.0, abs(fract(s0 * 0.35 + ly * 0.8) - 0.5) - 0.2);
    fragColor = vec4(col, 1.0);
}
