#version 150

// Shadered+'s weather globe. Pass 0 (the box's far sides): the sky inside the ball for the weather and time of day out
// here, and - with no Sky Island in it - a little blocky world: grass (or snow), a house, a tree. Pass 1 (the near
// sides, see-through): the glass - its shine and reflections - and the rain or snow falling inside; shaken, a flurry.

in vec3 lp;
in vec3 eye;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 PoseMat;
uniform float Pass;    // 0 inside, 1 the glass
uniform float Time;    // seconds
uniform float SunY;    // the sun's height, -1 (midnight) to 1 (noon)
uniform float Rain;    // 0-1
uniform float Thunder; // 0-1
uniform float Snowy;   // 1: here it snows instead of raining
uniform float Shake;   // seconds since it was shaken
uniform float Flash;   // lightning: 0-1
uniform float HasIsland;

out vec4 fragColor;

const vec3 C = vec3(0.5, 0.615, 0.5);
const float R = 0.355;
const float FLOOR = 0.40;

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
float hash3(vec3 p) { return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453); }

vec2 sphere(vec3 ro, vec3 rd) {
    vec3 oc = ro - C;
    float b = dot(oc, rd), c = dot(oc, oc) - R * R, h = b * b - c;
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// a box: the near and far t, and the normal where it's entered
vec2 boxHit(vec3 ro, vec3 rd, vec3 lo, vec3 hi, out vec3 n) {
    vec3 inv = 1.0 / rd;
    vec3 t0 = (lo - ro) * inv, t1 = (hi - ro) * inv;
    vec3 tn = min(t0, t1), tf = max(t0, t1);
    float a = max(max(tn.x, tn.y), tn.z), b = min(min(tf.x, tf.y), tf.z);
    n = a == tn.x ? vec3(-sign(rd.x), 0.0, 0.0) : a == tn.y ? vec3(0.0, -sign(rd.y), 0.0) : vec3(0.0, 0.0, -sign(rd.z));
    return vec2(a, b);
}

float depthOf(vec3 p) {
    vec4 c = ProjMat * ModelViewMat * PoseMat * vec4(p, 1.0);
    float d = c.z / c.w * 0.5 + 0.5;
    return clamp(d, 0.0, 1.0);
}

float day() { return smoothstep(-0.15, 0.25, SunY); }

vec3 sunDir() { return normalize(vec3(0.45, max(SunY, 0.05) + 0.2, 0.35)); }

vec3 skyColour(vec3 rd) {
    float d = day();
    vec3 dayTop = vec3(0.32, 0.55, 0.95), dayLow = vec3(0.62, 0.78, 1.0);
    vec3 nightTop = vec3(0.01, 0.02, 0.06), nightLow = vec3(0.04, 0.06, 0.15);
    float up = clamp(rd.y * 1.5 + 0.3, 0.0, 1.0);
    vec3 c = mix(mix(nightLow, nightTop, up), mix(dayLow, dayTop, up), d);
    // sunrise and sunset: warm low down
    float dusk = exp(-SunY * SunY * 30.0);
    c = mix(c, vec3(1.0, 0.5, 0.25), dusk * (1.0 - up) * 0.7);
    // stars
    vec2 s = floor(vec2(atan(rd.z, rd.x) * 40.0, rd.y * 40.0));
    c += vec3(0.9) * step(0.985, hash(s)) * (1.0 - d) * (1.0 - Rain);
    // clouds over it in rain
    vec3 grey = mix(vec3(0.05, 0.055, 0.07), vec3(0.42, 0.45, 0.5), d) * (1.0 - 0.45 * Thunder);
    c = mix(c, grey, Rain * 0.85);
    // the sun, or the moon
    float s1 = max(dot(rd, sunDir()), 0.0);
    c += vec3(1.0, 0.9, 0.6) * pow(s1, 300.0) * 2.0 * d * (1.0 - Rain);
    vec3 moon = normalize(vec3(-0.45, max(-SunY, 0.05) + 0.2, -0.35));
    c += vec3(0.85, 0.9, 1.0) * smoothstep(0.995, 0.997, dot(rd, moon)) * (1.0 - d) * (1.0 - Rain);
    return c + vec3(0.8, 0.85, 1.0) * Flash * 0.6;
}

// the little world: id 1 ground, 2 walls, 3 roof, 4 trunk, 5 leaves, 6 the earth under the ground
float world(vec3 ro, vec3 rd, out float id, out vec3 n) {
    float best = 1e9;
    id = 0.0;
    vec3 nn;
    // the ground: a flat layer of blocks across the ball
    if (rd.y < 0.0) {
        float t = (FLOOR - ro.y) / rd.y;
        vec3 p = ro + rd * t;
        if (t > 0.0 && length(p - C) < R) { best = t; id = 1.0; n = vec3(0.0, 1.0, 0.0); }
    }
    vec2 h;
    h = boxHit(ro, rd, vec3(0.53, FLOOR, 0.48), vec3(0.63, FLOOR + 0.07, 0.57), nn);
    if (h.x > 0.0 && h.x < h.y && h.x < best) { best = h.x; id = 2.0; n = nn; }
    h = boxHit(ro, rd, vec3(0.52, FLOOR + 0.07, 0.47), vec3(0.64, FLOOR + 0.095, 0.58), nn);
    if (h.x > 0.0 && h.x < h.y && h.x < best) { best = h.x; id = 3.0; n = nn; }
    h = boxHit(ro, rd, vec3(0.545, FLOOR + 0.095, 0.48), vec3(0.625, FLOOR + 0.115, 0.57), nn);
    if (h.x > 0.0 && h.x < h.y && h.x < best) { best = h.x; id = 3.0; n = nn; }
    h = boxHit(ro, rd, vec3(0.395, FLOOR, 0.405), vec3(0.415, FLOOR + 0.07, 0.425), nn);
    if (h.x > 0.0 && h.x < h.y && h.x < best) { best = h.x; id = 4.0; n = nn; }
    h = boxHit(ro, rd, vec3(0.365, FLOOR + 0.055, 0.375), vec3(0.445, FLOOR + 0.12, 0.455), nn);
    if (h.x > 0.0 && h.x < h.y && h.x < best) { best = h.x; id = 5.0; n = nn; }
    return best;
}

vec3 shadeWorld(vec3 p, float id, vec3 n) {
    float d = day();
    bool snow = Snowy > 0.5;
    vec3 base;
    vec2 px = floor(p.xz * 160.0); // (block texture-ish speckle: 10 pixels a mini block)
    float speck = 0.88 + 0.12 * hash(px + floor(p.y * 160.0));
    if (id < 1.5) base = snow ? vec3(0.93, 0.96, 1.0) : vec3(0.36, 0.6, 0.24);
    else if (id < 2.5) base = vec3(0.72, 0.56, 0.36);      // planks
    else if (id < 3.5) base = snow ? vec3(0.95) : vec3(0.55, 0.22, 0.18); // roof
    else if (id < 4.5) base = vec3(0.4, 0.28, 0.16);       // trunk
    else base = snow ? vec3(0.85, 0.92, 0.95) : vec3(0.2, 0.48, 0.18); // leaves
    base *= speck;
    // the house's windows, lit at night
    if (id > 1.5 && id < 2.5 && abs(n.y) < 0.5) {
        float w = step(abs(p.y - (FLOOR + 0.04)), 0.012) * step(abs(fract((p.x + p.z) * 30.0) - 0.5), 0.2);
        base = mix(base, mix(vec3(1.0, 0.75, 0.35), vec3(0.35, 0.5, 0.65), d), w);
        if (w > 0.5) return base * (1.2 - 0.4 * d);
    }
    float sun = max(dot(n, sunDir()), 0.0);
    float light = mix(0.12, 1.0, d) * (1.0 - 0.45 * Rain);
    vec3 moonLight = vec3(0.35, 0.4, 0.6) * (1.0 - d) * 0.5;
    return base * (0.35 * light + 0.75 * sun * light + moonLight) + base * Flash * 0.5;
}

// rain or snow falling inside (and the flurry when shaken): what the ray picks up from t0 to t1
vec3 weather(vec3 ro, vec3 rd, float t0, float t1) {
    vec3 c = vec3(0.0);
    float flurry = clamp(1.0 - Shake / 6.0, 0.0, 1.0);
    float snowing = Rain * Snowy;
    float raining = Rain * (1.0 - Snowy);
    float flakes = max(snowing, flurry);
    if (flakes < 0.01 && raining < 0.01) return c;
    float d = day();
    vec3 tint = mix(vec3(0.45, 0.5, 0.65), vec3(1.0), d);
    for (int k = 0; k < 48; k++) {
        float fk = float(k);
        vec3 q = vec3(hash(vec2(fk, 1.0)), hash(vec2(fk, 2.0)), hash(vec2(fk, 3.0)));
        vec3 p = C + (q - 0.5) * R * 1.7;
        if (flakes > 0.01) {
            // falling slowly, swaying; shaken: whirled round the middle first
            float fall = fract(q.y + Time * 0.05 * (0.6 + q.x));
            p.y = C.y + R * 0.9 - fall * R * 1.7;
            float swirl = flurry * 6.0;
            float a = Time * (1.0 + swirl) * (0.5 + q.z) + fk;
            p.xz = C.xz + (p.xz - C.xz) * (1.0 - 0.3 * flurry) + vec2(cos(a), sin(a)) * (0.02 + 0.08 * flurry);
            if (length(p - C) > R * 0.95) continue;
            float along = dot(p - ro, rd);
            if (along < t0 || along > t1) continue;
            float dist = length(ro + rd * along - p);
            c += tint * smoothstep(0.006, 0.002, dist) * (k < int(48.0 * max(flakes, 0.2)) ? 1.0 : 0.0);
        } else {
            float fall = fract(q.y + Time * 0.9 * (0.8 + q.x * 0.4));
            p.y = C.y + R * 0.9 - fall * R * 1.7;
            if (length(p - C) > R * 0.95) continue;
            float along = dot(p - ro, rd);
            if (along < t0 || along > t1) continue;
            vec3 off = ro + rd * along - p;
            float streak = step(length(off.xz), 0.0025) * step(abs(off.y), 0.018);
            c += vec3(0.55, 0.65, 0.85) * streak * 0.7 * raining;
        }
    }
    return c;
}

void main() {
    vec3 rd = normalize(lp - eye);
    vec3 ro = eye;
    vec2 s = sphere(ro, rd);
    if (s.y < 0.0) discard;
    float t0 = max(s.x, 0.0);
    if (Pass < 0.5) {
        if (gl_FrontFacing) discard; // (the far sides only: once a pixel)
        // (the sky painted on the inside of the ball: where the look meets its far side, not the way it looks - so the
        // stars and the moon stay put on the globe as you walk round it)
        vec3 c = skyColour(normalize(ro + rd * s.y - C));
        float tHit = s.y;
        if (HasIsland < 0.5) {
            float id; vec3 n;
            float t = world(ro, rd, id, n);
            if (id > 0.0 && t < s.y && t > t0) {
                vec3 p = ro + rd * t;
                c = shadeWorld(p, id, n);
                tHit = t;
            } else if (rd.y < 0.0 && ro.y + rd.y * s.y < FLOOR) {
                c = vec3(0.32, 0.22, 0.14) * mix(0.15, 0.7, day()); // (the earth under the ground)
            }
        } else if (rd.y < 0.0 && ro.y + rd.y * s.y < 0.37) {
            c = vec3(0.32, 0.22, 0.14) * mix(0.15, 0.7, day()); // (under the island: earth, not sky)
        }
        fragColor = vec4(c, 1.0);
        gl_FragDepth = depthOf(ro + rd * tHit);
        return;
    }
    // the glass
    if (!gl_FrontFacing && s.x > 0.0) discard;
    vec3 p = ro + rd * max(s.x, 0.0);
    vec3 n = normalize(p - C);
    float fres = pow(1.0 - abs(dot(-rd, n)), 3.0);
    vec3 refl = reflect(rd, n);
    vec3 env = mix(vec3(0.15, 0.16, 0.2), vec3(0.75, 0.8, 0.9), clamp(refl.y * 0.5 + 0.5, 0.0, 1.0)) * mix(0.3, 1.0, day());
    float spec = pow(max(dot(refl, normalize(vec3(-0.4, 0.75, 0.5))), 0.0), 60.0);
    vec3 c = env * fres + vec3(1.0) * spec * 1.2;
    float a = clamp(0.06 + fres * 0.7 + spec, 0.0, 0.95);
    vec3 w = weather(ro, rd, t0, s.y);
    float wa = clamp(max(max(w.r, w.g), w.b), 0.0, 1.0);
    c = c + w;
    a = max(a, wa);
    fragColor = vec4(c / max(a, 0.001) * min(a, 1.0), a);
    gl_FragDepth = depthOf(p);
}
