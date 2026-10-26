#version 150

// Shadered+'s fires (our own code): real 3D flames. The view ray enters through the hearth's opening or the fire
// tornado's glass case and is marched through a volume of fire - tongues of flame licking up from the logs, or a
// whirling column of flame - adding up its light and how much it hides what is behind.
// Everything in px (1/16 block) in the fire's own frame: x along it, y up, z from the front (0) to the back (16).

in vec3 wray;
in vec2 uvIn;
in vec3 lpos;
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

// how much fire at P (0..1), and how hot (0..1); kind 1 = the hearth's tongues, 2 = the tornado's whirl
vec2 fire(vec3 P, float t, int kind, float g) { // (g: 0..1 lighting up / dying down)
    if (kind == 2) {
        float y = P.y - 3.4, hn = y / ((23.0 + 3.0 * Boost) * max(g, 0.04));
        if (y < -0.6 || hn > 1.2) {
            return vec2(0.0);
        }
        vec2 q = P.xz - vec2(8.0);
        float a = -P.y * 0.35 + t * 6.0 * (0.4 + 0.6 * g); // (spinning up as it lights)
        q = mat2(cos(a), -sin(a), sin(a), cos(a)) * q;
        q += vec2(sin(P.y * 0.2 - t * 1.7), cos(P.y * 0.17 - t * 1.3)) * hn * 1.8;
        float n = fbm3(vec3(q * 0.5, P.y * 0.18 - t * 3.0));
        float n2 = noise3(vec3(q * 1.2, P.y * 0.5 - t * 5.0));
        float R0 = 4.2 * (0.35 + 1.0 * hn);
        float r = length(q);
        float arms = 0.5 + 0.5 * sin(atan(q.y, q.x) * 3.0 + r * 0.8);
        float d = (1.0 - hn * 0.95) - pow(abs(r - R0 * 0.45) / (R0 * 0.7), 2.0) + (n - 0.5) * 1.0 + (n2 - 0.5) * 0.4 + arms * 0.25 - 0.1;
        d *= smoothstep(-0.6, 0.6, y);
        d -= (1.0 - g) * 0.35;
        return vec2(smoothstep(0.0, 0.45, d), clamp(d * 1.5 * (1.15 - hn), 0.0, 1.0));
    }
    float base = (P.x > 11.0 && P.x < 21.0) ? 8.2 : 6.4;
    float H = (10.5 + 2.5 * Boost) * max(g, 0.04);
    float y = P.y - base;
    float hn = y / H;
    float foot = smoothstep(6.5, 9.0, P.x) * smoothstep(25.5, 23.0, P.x);
    if (y < -0.6 || hn > 1.25 || foot <= 0.0) {
        return vec2(0.0);
    }
    float n = fbm3(vec3(P.x * 0.32, P.y * 0.22 - t * 2.3, P.z * 0.35 + t * 0.3));
    float n2 = noise3(vec3(P.x * 0.9, P.y * 0.6 - t * 4.0, P.z * 0.9));
    float tongue = foot * (0.62 + 0.25 * sin(P.x * 0.75 + n * 3.0 + t * 1.3) + 0.13 * sin(P.x * 1.37 - t * 2.1 + n2 * 2.0));
    float dz = (P.z - 11.4 - (n - 0.5) * 2.8 - sin(P.y * 0.4 - t * 3.0 + P.x * 0.3) * 0.4) / (3.2 * (1.0 - 0.55 * clamp(hn, 0.0, 1.0)));
    float d = tongue - hn - dz * dz * 0.55 + (n - 0.5) * 0.85 + (n2 - 0.5) * 0.3;
    d *= smoothstep(-0.6, 0.4, y); // (a soft start at the logs)
    d -= (1.0 - g) * 0.35;
    return vec2(smoothstep(0.0, 0.45, d), clamp(d * 1.6 * (1.15 - hn), 0.0, 1.0));
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
    int kind = (code & 32) != 0 ? 2 : 1;
    float ly = uvIn.y - 10.0 * float(code);
    int ia = int(vColor.a * 255.0 + 0.5);
    int facing = ia / 64;
    vec3 fc = vColor.rgb;
    float t = FireTime;
    // how lit (0..1, eased by client/WallFireRenderer): the tornado's in uv.y, the hearth's in uv.x (4 * 63ths)
    float g, ux = uvIn.x;
    if (kind == 2) {
        g = clamp(ly, 0.0, 1.0);
    } else {
        float k = floor(uvIn.x / 4.0);
        g = k / 63.0;
        ux = uvIn.x - 4.0 * k;
    }
    g = g * g * (3.0 - 2.0 * g);
    if (kind == 1 && g <= 0.001) {
        discard;
    }

    // into the fire's frame
    vec3 rw = normalize(wray);
    vec3 rd;
    if (facing == 1) rd = vec3(rw.z, rw.y, -rw.x);
    else if (facing == 2) rd = vec3(-rw.x, rw.y, -rw.z);
    else if (facing == 3) rd = vec3(-rw.z, rw.y, rw.x);
    else rd = rw;
    vec3 ro, lo, hi;
    if (kind == 2) {
        ro = lpos;
        lo = vec3(2.0, 3.3, 2.0);
        hi = vec3(14.0, 29.0, 14.0);
    } else {
        ro = vec3(ux, ly, 4.6 / 16.0) * 16.0;
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
    tEnd = min(tEnd, 32.0);

    // march
    vec3 emit = vec3(0.0);
    float trans = 1.0;
    if (g > 0.001) {
        int N = kind == 2 ? 56 : 44;
        float dt = tEnd / float(N);
        float jitter = hash3(vec3(gl_FragCoord.xy, fract(t) * 7.0));
        for (int i = 0; i < N; i++) {
            vec3 P = ro + rd * (dt * (float(i) + jitter));
            vec2 f = fire(P, t, kind, g);
            if (f.x > 0.001) {
                emit += trans * fireColour(f.y, fc) * f.x * 0.55 * dt;
                trans *= exp(-f.x * 0.38 * dt);
                if (trans < 0.02) {
                    break;
                }
            }
        }
    }
    // only the flames (what is behind is the world): premultiplied, the bright parts also brighten what's behind
    vec4 col = vec4(emit, 1.0 - trans + dot(emit, vec3(0.1)));
    if (kind == 2) {
        // the case's glass (on the sides, not the top or bottom): clear, a soft sky reflection at grazing angles,
        // a faint smooth sheen across it, a touch brighter at its edges
        vec3 n = abs(ro.x - 2.0) < 0.05 ? vec3(-1, 0, 0) : abs(ro.x - 14.0) < 0.05 ? vec3(1, 0, 0)
               : abs(ro.z - 2.0) < 0.05 ? vec3(0, 0, -1) : abs(ro.z - 14.0) < 0.05 ? vec3(0, 0, 1) : vec3(0.0);
        if (dot(n, n) > 0.5) {
            float c = abs(dot(rd, n));
            float fres = 0.04 + 0.5 * pow(1.0 - c, 4.0);
            vec3 rf = reflect(rd, n);
            vec3 sky = mix(vec3(0.35, 0.38, 0.34), vec3(0.85, 0.92, 1.0), smoothstep(-0.2, 0.5, rf.y));
            float u = n.x != 0.0 ? ro.z : ro.x;
            float sheen = smoothstep(2.5, 0.0, abs(u * 0.9 + ro.y * 0.45 - 14.0)) * 0.06
                        + smoothstep(1.0, 0.0, abs(u * 0.9 + ro.y * 0.45 - 18.0)) * 0.035;
            float edge = smoothstep(1.2, 0.0, min(min(u - 2.6, 13.4 - u), min(ro.y - 3.3, 29.0 - ro.y))) * 0.05;
            float ga = clamp(fres + sheen + edge, 0.0, 0.6);
            col = vec4(sky * ga + col.rgb * (1.0 - ga), ga + col.a * (1.0 - ga));
        }
    }
    fragColor = col;
}
