// Shadered+'s Sky Studio sky (our own code): a sky made of layers a player sets up in the Sky Studio - a colour
// gradient with a glow at the horizon, nebula clouds drifting, square twinkling stars, aurora bands waving, a blocky
// sun or moon with craters, shooting stars now and then, a glowing grid floor scrolling below the horizon. Every number
// comes from the slot's settings (P0..P12: client/StudioSkiesClient, in the order StudioSkies.Params lists).

uniform vec4 P0;
uniform vec4 P1;
uniform vec4 P2;
uniform vec4 P3;
uniform vec4 P4;
uniform vec4 P5;
uniform vec4 P6;
uniform vec4 P7;
uniform vec4 P8;
uniform vec4 P9;
uniform vec4 P10;
uniform vec4 P11;
uniform vec4 P12;

float st_hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float st_hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float st_noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(st_hash13(i), st_hash13(i + vec3(1, 0, 0)), f.x), mix(st_hash13(i + vec3(0, 1, 0)), st_hash13(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(st_hash13(i + vec3(0, 0, 1)), st_hash13(i + vec3(1, 0, 1)), f.x), mix(st_hash13(i + vec3(0, 1, 1)), st_hash13(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

float st_fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * st_noise(p);
        p = p * 2.07 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v;
}

// a point on the sky (unit direction) in a frame round dir c: how far off it, sideways (u) and up (v)
vec2 st_local(vec3 d, vec3 c) {
    vec3 side = normalize(cross(vec3(0.0, 1.0, 0.0), c));
    vec3 up = cross(c, side);
    return vec2(dot(d, side), dot(d, up));
}

vec3 st_dirAt(float az, float el) {
    return vec3(cos(el) * sin(az), sin(el), cos(el) * cos(az));
}

vec3 studioSky(vec3 d, float t) {
    // ---- the settings ----
    vec3 topC = P0.xyz, midC = vec3(P0.w, P1.xy), horC = vec3(P1.zw, P2.x);
    float horizon = (P2.y - 0.5) * 0.6, glow = P2.z, gradOn = P2.w;
    float nebOn = P3.x; vec3 neb1 = P3.yzw, neb2 = P4.xyz; float nebAmt = P4.w, nebSpeed = P5.x, nebSoft = P5.y;
    float starOn = P5.z, starDen = P5.w, starTw = P6.x, starSize = P6.y;
    float aurOn = P6.z; vec3 aur1 = vec3(P6.w, P7.xy), aur2 = vec3(P7.zw, P8.x); float aurBright = P8.y, aurSpeed = P8.z, aurHeight = P8.w;
    float moonOn = P9.x; vec3 moonC = P9.yzw; float moonSize = P10.x, moonGlow = P10.y, moonPos = P10.z;
    float shootOn = P10.w, shootFreq = P11.x, shootLen = P11.y;
    float gridOn = P11.z; vec3 gridC = vec3(P11.w, P12.xy); float gridSpacing = P12.z, gridSpeed = P12.w;

    float above = d.y - horizon;
    vec3 col = vec3(0.0);

    // ---- the colour of the sky: horizon -> middle -> top; below the horizon, darker ----
    if (gradOn > 0.5) {
        float h = clamp(above / max(1.0 - horizon, 0.05), 0.0, 1.0);
        vec3 sky = mix(horC, midC, smoothstep(0.0, 0.35, h));
        sky = mix(sky, topC, smoothstep(0.3, 1.0, h));
        vec3 ground = mix(horC * 0.35, topC * 0.25, smoothstep(0.0, 0.6, -above));
        col = above >= 0.0 ? sky : ground;
        col += horC * glow * 0.8 * exp(-abs(above) * 9.0);
    }

    // ---- nebula clouds, drifting ----
    if (nebOn > 0.5 && above > -0.05) {
        vec3 q = d * 2.2 + vec3(t * 0.0006 * nebSpeed * 10.0, 0.0, t * 0.0004 * nebSpeed * 10.0);
        float n = st_fbm(q);
        float edge = mix(0.04, 0.35, nebSoft);
        float m = smoothstep(1.0 - nebAmt * 0.75 - 0.15, 1.0 - nebAmt * 0.75 - 0.15 + edge, n);
        vec3 c = mix(neb1, neb2, clamp(st_fbm(q * 1.7 + 5.0) * 1.6 - 0.3, 0.0, 1.0));
        col += c * m * 0.85 * smoothstep(-0.05, 0.12, above);
    }

    // ---- stars: square, twinkling ----
    if (starOn > 0.5 && above > 0.0) {
        float scale = 140.0;
        vec3 p = d * scale;
        vec3 cell = floor(p);
        float h = st_hash13(cell);
        if (h > 1.0 - starDen * 0.06) {
            vec3 f = fract(p) - 0.5;
            float r = mix(0.08, 0.3, starSize);
            float on = step(max(max(abs(f.x), abs(f.y)), abs(f.z)), r);
            float tw = mix(1.0, 0.45 + 0.55 * sin(t * 0.08 * (0.5 + starTw * 2.0) + h * 80.0), starTw);
            col += vec3(1.0, 0.97, 0.9) * on * tw * smoothstep(0.0, 0.08, above);
        }
    }

    // ---- aurora: ribbons of light waving across the sky ----
    if (aurOn > 0.5 && above > 0.0) {
        float az = atan(d.x, d.z);
        float el = asin(clamp(d.y, -1.0, 1.0));
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float centre = mix(0.2, 0.8, aurHeight) + 0.07 * fi + 0.09 * sin(az * 2.0 + fi * 2.1 + t * 0.004 * aurSpeed * 10.0);
            float k = (el - centre) / 0.07;
            float band = exp(-k * k) * (0.55 + 0.45 * st_noise(vec3(az * 6.0 + fi * 9.0, t * 0.01 * aurSpeed * 10.0, fi)));
            float rays = 0.88 + 0.12 * sin(az * 60.0 + fi * 7.0 + t * 0.02);
            col += mix(aur1, aur2, clamp(k * 0.5 + 0.5, 0.0, 1.0)) * band * rays * aurBright * 0.5;
        }
    }

    // ---- a blocky sun or moon ----
    if (moonOn > 0.5) {
        vec3 m = st_dirAt(0.7, mix(0.08, 1.2, moonPos));
        float facing = dot(d, m);
        if (facing > 0.0) {
            vec2 l = st_local(d, m);
            float hs = mix(0.03, 0.14, moonSize);
            vec2 a = abs(l);
            if (max(a.x, a.y) < hs) {
                vec2 g = floor((l / hs) * 4.0);
                float crater = step(0.72, st_hash12(g + 3.0));
                col = mix(moonC, moonC * 0.78, crater);
            } else {
                float dist = max(a.x, a.y) - hs;
                col += moonC * moonGlow * 0.5 * exp(-dist * 18.0);
            }
        }
    }

    // ---- a shooting star now and then ----
    if (shootOn > 0.5 && above > 0.0) {
        float period = mix(900.0, 120.0, shootFreq);
        float slot = floor(t / period), phase = fract(t / period);
        if (phase < 0.12) {
            float s = phase / 0.12;
            float az0 = st_hash12(vec2(slot, 1.0)) * 6.2832, el0 = 0.35 + st_hash12(vec2(slot, 2.0)) * 0.6;
            float dirA = st_hash12(vec2(slot, 3.0)) * 6.2832;
            vec2 step2 = vec2(cos(dirA), -abs(sin(dirA)) - 0.2) * 0.5;
            vec2 head = vec2(az0, el0) + step2 * s;
            float az = atan(d.x, d.z), el = asin(clamp(d.y, -1.0, 1.0));
            vec2 rel = vec2(az, el) - head;
            rel.x = mod(rel.x + 3.14159, 6.28318) - 3.14159;
            vec2 back = -normalize(step2);
            float along = dot(rel, back);
            float across = abs(dot(rel, vec2(-back.y, back.x)));
            float len = mix(0.05, 0.3, shootLen);
            if (along > 0.0 && along < len) {
                col += vec3(1.0) * (1.0 - along / len) * smoothstep(0.006, 0.0, across) * (1.0 - s * 0.5);
            }
        }
    }

    // ---- a grid floor below the horizon, scrolling towards you ----
    if (gridOn > 0.5 && above < 0.0) {
        float below = -above;
        vec2 p = d.xz / max(below, 0.004) * mix(0.25, 1.2, gridSpacing);
        p.y += t * 0.01 * gridSpeed * 4.0;
        vec2 f = abs(fract(p) - 0.5);
        float width = 0.03 + 0.4 * (1.0 - smoothstep(0.0, 0.25, below));
        float line = max(smoothstep(0.5 - width, 0.5, f.x), smoothstep(0.5 - width, 0.5, f.y));
        float fade = smoothstep(0.0, 0.04, below);
        col = mix(col, gridC, line * fade * 0.9);
        col += gridC * 0.25 * exp(-below * 30.0);
    }
    return col;
}
