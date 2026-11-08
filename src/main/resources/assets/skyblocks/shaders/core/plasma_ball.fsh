#version 150

// Shadered+'s plasma ball (our own code): a glass globe of thin gas with a glowing electrode in the middle and
// lightning-like filaments crackling from it to the glass - wandering, flickering, and reaching for the nearest
// player's hand. Louder bass (music nearby): brighter, thicker, more of them. See-through: premultiplied colour.

in vec3 lp;
in vec3 lray;
flat in vec4 vColor;
flat in vec4 target;
flat in float seedIn;

uniform float PlasmaTime;
uniform float Boost;

out vec4 fragColor;

const vec3 C = vec3(0.5, 9.5 / 16.0, 0.5); // (the globe's centre and radius, block units)
const float R = 5.5 / 16.0;
const float CORE = 0.055;

float hash11(float n) {
    return fract(sin(n * 12.9898 + 4.1414) * 43758.5453);
}

float vnoise1(float x) {
    float i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(hash11(i), hash11(i + 1.0), f);
}

// a direction on the globe from two numbers in 0..1
vec3 sphereDir(float u, float v) {
    float z = u * 2.0 - 1.0, a = v * 6.2831853;
    float r = sqrt(max(1.0 - z * z, 0.0));
    return vec3(r * cos(a), z, r * sin(a));
}

// how far the point q is from the ray (ro + rd * t, t in [t0, t1])
float rayPoint(vec3 ro, vec3 rd, float t0, float t1, vec3 q) {
    float t = clamp(dot(q - ro, rd), t0, t1);
    return length(ro + rd * t - q);
}

// how far the segment a..b comes to the ray (ro + rd * t, t in [t0, t1]); (nearly) along the ray: from points on it
vec2 raySeg(vec3 ro, vec3 rd, float t0, float t1, vec3 a, vec3 b) {
    vec3 ab = b - a, w = ro - a;
    float bb = dot(ab, ab), rb = dot(rd, ab), rw = dot(rd, w), bw = dot(ab, w);
    float den = bb - rb * rb; // (rd is unit length)
    if (den < 0.05 * bb || bb < 1e-8) {
        float d = min(min(rayPoint(ro, rd, t0, t1, a), rayPoint(ro, rd, t0, t1, b)),
                      min(rayPoint(ro, rd, t0, t1, a + ab * 0.33), rayPoint(ro, rd, t0, t1, a + ab * 0.67)));
        return vec2(d, 0.0);
    }
    float s = clamp((bw - rb * rw) / den, 0.0, 1.0);
    float t = clamp(dot(rd, a + ab * s - ro), t0, t1);
    s = clamp(dot(ro + rd * t - a, ab) / bb, 0.0, 1.0);
    t = clamp(dot(rd, a + ab * s - ro), t0, t1);
    return vec2(length(ro + rd * t - (a + ab * s)), t);
}

void main() {
    vec3 rd = normalize(lray);
    vec3 oc = lp - C;
    float b = dot(oc, rd), c = dot(oc, oc) - R * R;
    float h = b * b - c;
    if (h < 0.0) {
        discard;
    }
    h = sqrt(h);
    float t0 = max(-b - h, 0.0), t1 = -b + h;
    if (t1 <= 0.0) {
        discard;
    }
    float on = vColor.a > 0.5 ? 1.0 : 0.0;
    vec3 pc = vColor.rgb;
    float t = PlasmaTime + seedIn * 97.0;
    float boost = clamp(Boost, 0.0, 1.0);

    // the electrode: a small glowing ball in the middle (it hides what is behind it)
    float coreT = 1e9;
    {
        vec3 oc2 = lp - C;
        float b2 = dot(oc2, rd), c2 = dot(oc2, oc2) - CORE * CORE, h2 = b2 * b2 - c2;
        if (h2 > 0.0) {
            coreT = -b2 - sqrt(h2);
        }
    }
    float tEnd = min(t1, coreT);

    vec3 emit = vec3(0.0);
    // the thin gas: a faint glow, stronger near the middle
    vec3 mid = lp + rd * clamp(dot(C - lp, rd), t0, tEnd);
    float gas = smoothstep(R, 0.0, length(mid - C));
    emit += pc * gas * gas * (0.12 + (0.4 + 0.35 * boost) * on) * (tEnd - t0) / R;

    // the filaments
    if (on > 0.5) {
        int n = 7 + int(boost * 5.0 + 0.5);
        for (int i = 0; i < 12; i++) {
            if (i >= n) {
                break;
            }
            float fi = float(i);
            // where it touches the glass: wandering slowly over the globe, the first two reaching for the player
            float u = fract(hash11(fi * 3.1 + seedIn * 7.0) + 0.08 * sin(t * (0.21 + 0.05 * fi) + fi));
            float v = fract(hash11(fi * 5.7 + seedIn * 3.0) + t * (0.015 + 0.012 * hash11(fi)) * (hash11(fi * 9.1) > 0.5 ? 1.0 : -1.0));
            vec3 e = sphereDir(u, v);
            // a player near: the first one jumps to the glass right by them (a thick bright bolt, as a real plasma
            // ball with a hand on it), the second follows it closely, the rest lean their way
            float near = clamp(target.w, 0.0, 1.0);
            float grab = i == 0 ? smoothstep(0.0, 0.35, near) : i == 1 ? 0.85 * smoothstep(0.1, 0.6, near) : 0.3 * near;
            vec3 toward = near > 0.01 ? normalize(target.xyz + (i == 1 ? 0.18 * vec3(sin(t * 1.7), cos(t * 1.3), sin(t * 2.1)) : vec3(0.0))) : e;
            e = normalize(mix(e, toward, grab));
            float hand = i < 2 ? grab : 0.0;
            // across the filament, two directions to bend it in
            vec3 s1 = normalize(cross(e, abs(e.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
            vec3 s2 = cross(e, s1);
            float flick = 0.55 + 0.45 * vnoise1(t * 9.0 + fi * 13.0); // (each one flickers)
            float wid = (0.0055 + 0.0025 * boost) * (0.8 + 0.4 * flick) * (1.0 + 0.9 * hand);
            vec3 prev = C + e * CORE;
            const int K = 7;
            float glow = 0.0;
            for (int k = 1; k <= K; k++) {
                float s = float(k) / float(K);
                float bend = sin(3.14159 * s) * (0.035 + 0.035 * boost) * (1.0 - 0.4 * hand);
                // a slow sway plus a fast crackle, more of it away from both ends
                vec2 j = vec2(vnoise1(s * 3.0 + t * 1.3 + fi * 7.0) - 0.5, vnoise1(s * 3.0 - t * 1.1 + fi * 3.0 + 40.0) - 0.5) * 2.0
                       + vec2(vnoise1(s * 11.0 + t * 17.0 + fi) - 0.5, vnoise1(s * 11.0 - t * 15.0 + fi * 2.0 + 9.0) - 0.5) * 0.7;
                vec3 p = C + e * mix(CORE, R * 0.985, s) + (s1 * j.x + s2 * j.y) * bend;
                vec2 dt = raySeg(lp, rd, t0, tEnd, prev, p);
                float d = dt.x;
                glow += wid * wid / (d * d + wid * wid) * (0.6 + 0.4 * s) + 0.22 * exp(-d * d / (0.028 * 0.028));
                prev = p;
            }
            // the bright spot where it touches the glass
            vec2 ds = raySeg(lp, rd, t0, t1, prev, prev);
            float spot = 0.02 * (1.0 + 1.2 * hand);
            glow += (0.9 + 1.5 * hand) * exp(-ds.x * ds.x / (spot * spot));
            emit += mix(pc, vec3(1.0), clamp(glow * 0.35, 0.0, 0.85)) * glow * mix(flick, 1.0, hand * 0.6) * (1.1 + 0.4 * boost) * (1.0 + 0.8 * hand);
        }
    }

    // the electrode itself
    float alpha = 0.0;
    if (coreT < t1) {
        vec3 pn = normalize(lp + rd * coreT - C);
        float lit = 0.35 + 0.65 * max(dot(pn, -rd), 0.0);
        emit += mix(pc, vec3(1.0), 0.5 + 0.3 * on) * lit * (0.25 + 1.1 * on);
        alpha = 1.0;
    }

    // the glass: a dark tint (it's a smoky globe), a sheen at the edges, a soft highlight
    vec3 n = normalize(lp + rd * t0 - C);
    float cosv = abs(dot(n, rd));
    float fres = 0.04 + 0.55 * pow(1.0 - cosv, 4.0);
    vec3 rf = reflect(rd, n);
    vec3 sky = mix(vec3(0.25, 0.27, 0.3), vec3(0.8, 0.88, 1.0), smoothstep(-0.2, 0.6, rf.y));
    float spec = pow(max(dot(rf, normalize(vec3(0.4, 1.0, 0.3))), 0.0), 60.0);
    float tint = 0.18 + 0.12 * (1.0 - cosv);
    vec4 col;
    col.a = clamp(max(alpha, tint + fres + dot(emit, vec3(0.08))), 0.0, 1.0);
    col.rgb = emit + sky * fres * 0.6 + vec3(spec) * 0.8;
    fragColor = clamp(col, 0.0, 1.0);
}
