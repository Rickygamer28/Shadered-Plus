#version 150

// Shadered+'s solar systems (our own code): the Sun and its eight planets, traced in the block's own space.
//   kind 0 - a brass orrery: the planets on thin brass arms from the central shaft, the Sun a glowing ball on top;
//   kind 1 - a hologram planetarium: the system floating in a glass dome full of stars, glowing orbit lines.
// Every planet turns on its axis and goes round the Sun at its own pace (SimYears: years gone by in the model); their
// surfaces are made here - Earth's oceans, continents, ice caps and clouds (and its cities lit on the night side),
// Jupiter's bands and Great Red Spot, Saturn's rings (casting their shadow on it, and it on them), Mars red with its
// polar cap... The Moon goes round the Earth (its orbit tilted: an eclipse now and then), four moons round Jupiter,
// Titan round Saturn, an asteroid belt, and a comet on its long orbit, its tail streaming away from the Sun.
// The planets' places are worked out the same way in client/SolarSystemRenderer (for their names).

in vec3 lp;
in vec3 lray;
flat in float kind;
flat in vec3 boxLo;
flat in vec3 boxHi;
flat in float persp;
flat in vec3 settings;
flat in vec3 toView;

uniform float SimYears;  // years gone by in the model
uniform float SpinTime;  // seconds of turning (stops when it's paused)
// (this one's own settings, from its corners: set in main)
float Dome = 1.0;      // the planetarium: 1 in its glass dome full of dark starry space, 0 just the planets in the open
float SizeMode = 0.0;  // 0 sizes to see them by, 1 real sizes
float HoloScale = 1.0; // the planetarium's size: 1, 2 or 4 times
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ViewRot; // (1.20.1: the camera's turn, which the positions here are without)
uniform float Boost;     // music: the Sun pulsing
uniform float Glow;      // the hologram's brightness (dimmer by day)
uniform float ItemMode;
uniform float DepthOnly; // 1: only the solid things' depth (a second pass: what's drawn later is hidden behind the planets)

out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;

// ---- the system's place and size in the block ----
vec3 gC;   // the Sun's middle
float gR;  // the system's radius (Neptune's orbit)

// ---- noise ----
float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float hash11(float n) {
    return fract(sin(n * 12.9898 + 4.1414) * 43758.5453);
}

float vnoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = p * 2.07 + vec3(3.1, 7.7, 1.3);
        a *= 0.5;
    }
    return v;
}

float fbm3(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * vnoise(p);
        p = p * 2.07 + vec3(3.1, 7.7, 1.3);
        a *= 0.5;
    }
    return v;
}

// ---- the bodies: 0 the Sun, 1-8 the planets, 9 the Moon, 10-13 Jupiter's moons, 14 Titan, 15 the comet ----

// orbit radius (of R), radius shown, radius real (of R), period (years), where it starts (turns), axial tilt (radians),
// one turn on its axis (seconds, negative: backwards)
float orbitOf(int i) {
    // (spaced so that no two bodies - with their moons - ever touch: the moons of the big planets go round tilted)
    return i == 1 ? 0.15 : i == 2 ? 0.23 : i == 3 ? 0.32 : i == 4 ? 0.435 : i == 5 ? 0.61 : i == 6 ? 0.765 : i == 7 ? 0.885 : 0.985;
}

float sizeOf(int i) {
    bool real = SizeMode > 0.5;
    if (i == 0) return real ? 0.12 : 0.1;
    if (i == 1) return real ? 0.006 : 0.026;
    if (i == 2) return real ? 0.014 : 0.042;
    if (i == 3) return real ? 0.015 : 0.045;
    if (i == 4) return real ? 0.008 : 0.032;
    if (i == 5) return real ? 0.055 : 0.085;
    if (i == 6) return real ? 0.046 : 0.065;
    if (i == 7) return real ? 0.020 : 0.05;
    if (i == 8) return real ? 0.019 : 0.048;
    if (i == 9) return real ? 0.004 : 0.015;
    if (i >= 10 && i <= 13) return real ? 0.003 : (i == 12 ? 0.010 : 0.008);
    if (i == 14) return real ? 0.004 : 0.011;
    return 0.006;
}

float periodOf(int i) {
    return i == 1 ? 0.241 : i == 2 ? 0.615 : i == 3 ? 1.0 : i == 4 ? 1.881 : i == 5 ? 11.86 : i == 6 ? 29.46 : i == 7 ? 84.01 : 164.8;
}

float phaseOf(int i) {
    return i == 1 ? 0.1 : i == 2 ? 0.6 : i == 3 ? 0.3 : i == 4 ? 0.85 : i == 5 ? 0.2 : i == 6 ? 0.55 : i == 7 ? 0.05 : 0.7;
}

float tiltOf(int i) {
    return i == 3 ? 0.41 : i == 4 ? 0.44 : i == 5 ? 0.05 : i == 6 ? 0.47 : i == 7 ? 1.71 : i == 8 ? 0.49 : i == 2 ? 3.1 : 0.0;
}

float spinOf(int i) {
    return i == 0 ? 25.0 : i == 1 ? 30.0 : i == 2 ? -60.0 : i == 3 ? 6.0 : i == 4 ? 6.2 : i == 5 ? 2.6 : i == 6 ? 2.8 : i == 7 ? -4.0 : i == 9 ? 40.0 : 4.2;
}

// the comet: on a long ellipse (Kepler's equation, a few steps)
vec3 cometAt(float years) {
    float a = 0.55, e = 0.8, period = 9.0;
    float M = TAU * fract(years / period + 0.35);
    float E = M;
    for (int k = 0; k < 6; k++) E = M + e * sin(E);
    vec2 q = vec2(a * (cos(E) - e), a * sqrt(1.0 - e * e) * sin(E));
    float w = 2.2, inc = 0.17; // (its ellipse turned, and tilted)
    vec3 p = vec3(q.x * cos(w) - q.y * sin(w), 0.0, q.x * sin(w) + q.y * cos(w));
    p.y = p.z * sin(inc);
    return p;
}

// where the Sun (0) or a planet (1-8) is (block units)
vec3 planetAt(int i) {
    if (i == 0) return gC;
    float a = TAU * (SimYears / periodOf(i) + phaseOf(i));
    float r = orbitOf(i) * gR;
    return gC + vec3(cos(a) * r, sin(a) * r * 0.012 * float(i), sin(a) * r);
}

// where body i is (block units)
vec3 bodyAt(int i) {
    if (i <= 8) return planetAt(i);
    if (i == 9) { // the Moon: its orbit tilted 5 degrees, the tilt turning round every 18.6 years (eclipses only now and then)
        vec3 e = planetAt(3);
        float a = TAU * SimYears / 0.0748;
        float node = TAU * SimYears / 18.6;
        float r = (SizeMode > 0.5 ? 0.05 : 0.062) * gR; // (close round the Earth: clear of Mars's orbit)
        vec3 p = vec3(cos(a), 0.0, sin(a)) * r;
        p.y = sin(a - node) * sin(0.09) * r;
        return e + p;
    }
    if (i >= 10 && i <= 13) {
        vec3 j = planetAt(5);
        float per = i == 10 ? 0.0048 : i == 11 ? 0.0097 : i == 12 ? 0.0196 : 0.0457;
        float r = (i == 10 ? 0.1 : i == 11 ? 0.11 : i == 12 ? 0.122 : 0.135) * gR;
        float a = TAU * (SimYears / per + float(i) * 0.37);
        // (their orbits tilted up on the Sun's side and the far side: never out as far as Mars's or Saturn's orbits)
        vec3 u = normalize(vec3(j.x - gC.x, 0.0, j.z - gC.z));
        vec3 ax = u * cos(0.6) + vec3(0.0, sin(0.6), 0.0);
        return j + (ax * cos(a) + vec3(-u.z, 0.0, u.x) * sin(a)) * r;
    }
    if (i == 14) {
        vec3 s = planetAt(6);
        float a = TAU * (SimYears / 0.0437 + 0.2);
        float r = 0.175 * gR;
        float t = tiltOf(6);
        return s + vec3(cos(a) * r, sin(a) * r * sin(t), sin(a) * r * cos(t));
    }
    return gC + cometAt(SimYears) * gR;
}

float radiusOf(int i) {
    return sizeOf(i) * gR;
}

float sphere(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd), q = dot(oc, oc) - r * r, h = b * b - q;
    if (h < 0.0) return -1.0;
    float t = -b - sqrt(h);
    return t > 0.0 ? t : -1.0;
}

// how near the ray passes a point (distance), and where along it
vec2 nearest(vec3 ro, vec3 rd, vec3 c) {
    float t = max(dot(c - ro, rd), 0.0);
    return vec2(length(ro + rd * t - c), t);
}

// a capsule (iq): the brass arms
float capsule(vec3 ro, vec3 rd, vec3 pa, vec3 pb, float r) {
    vec3 ba = pb - pa, oa = ro - pa;
    float baba = dot(ba, ba), bard = dot(ba, rd), baoa = dot(ba, oa), rdoa = dot(rd, oa), oaoa = dot(oa, oa);
    float a = baba - bard * bard, b = baba * rdoa - baoa * bard, c = baba * oaoa - baoa * baoa - r * r * baba;
    float h = b * b - a * c;
    if (h >= 0.0) {
        float t = (-b - sqrt(h)) / a;
        float y = baoa + t * bard;
        if (y > 0.0 && y < baba) return t;
        vec3 oc = (y <= 0.0) ? oa : ro - pb;
        b = dot(rd, oc);
        c = dot(oc, oc) - r * r;
        h = b * b - c;
        if (h > 0.0) return -b - sqrt(h);
    }
    return -1.0;
}

// a body's own frame: its axis tilted, turned on it
vec3 toBody(int i, vec3 n) {
    float t = tiltOf(i);
    // tilt about x
    n = vec3(n.x, n.y * cos(t) + n.z * sin(t), -n.y * sin(t) + n.z * cos(t));
    float s = SpinTime * TAU / spinOf(i);
    return vec3(n.x * cos(s) - n.z * sin(s), n.y, n.x * sin(s) + n.z * cos(s));
}

// ---- the surfaces ----

vec3 earth(vec3 q, float lit, out float night) {
    float h = fbm(q * 2.3 + 11.0);
    float land = smoothstep(0.5, 0.53, h);
    float lat = abs(q.y);
    vec3 ocean = mix(vec3(0.02, 0.08, 0.28), vec3(0.04, 0.2, 0.45), smoothstep(0.4, 0.5, h));
    vec3 green = mix(vec3(0.12, 0.32, 0.08), vec3(0.3, 0.38, 0.12), fbm3(q * 6.0));
    vec3 desert = vec3(0.72, 0.6, 0.38);
    vec3 ground = mix(green, desert, smoothstep(0.15, 0.35, 0.35 - abs(lat - 0.33) + 0.3 * (fbm3(q * 4.0) - 0.5)));
    vec3 c = mix(ocean, ground, land);
    c = mix(c, vec3(0.92, 0.95, 1.0), smoothstep(0.76, 0.82, lat + 0.08 * fbm3(q * 5.0))); // (the ice caps)
    // the cities: lights on the land, on the night side
    float cities = land * smoothstep(0.62, 0.72, vnoise(q * 40.0)) * smoothstep(0.75, 0.3, lat);
    night = cities;
    return c;
}

vec3 clouds(vec3 q, float t) {
    float c = fbm(q * 3.5 + vec3(t * 0.02, 0.0, 0.0));
    return vec3(smoothstep(0.52, 0.72, c));
}

vec3 jupiter(vec3 q) {
    float y = q.y + 0.04 * (fbm3(q * 6.0) - 0.5);
    float band = sin(y * 22.0 + 1.5 * fbm3(vec3(q.x, y * 8.0, q.z) * 3.0));
    vec3 c = mix(vec3(0.85, 0.75, 0.6), vec3(0.62, 0.42, 0.28), smoothstep(-0.3, 0.6, band));
    c = mix(c, vec3(0.95, 0.9, 0.82), smoothstep(0.85, 1.0, sin(y * 11.0 + 0.7)) * 0.4);
    // the Great Red Spot
    float lon = atan(q.z, q.x);
    vec2 d = vec2((lon - 0.8) * 1.4, (q.y + 0.37) * 4.6);
    float spot = smoothstep(0.35, 0.15, length(d));
    c = mix(c, vec3(0.75, 0.32, 0.2), spot * 0.9);
    return c;
}

vec3 saturn(vec3 q) {
    float y = q.y + 0.02 * (fbm3(q * 5.0) - 0.5);
    float band = sin(y * 16.0 + 0.6 * fbm3(q * 3.0));
    vec3 c = mix(vec3(0.9, 0.82, 0.6), vec3(0.75, 0.62, 0.42), smoothstep(-0.4, 0.8, band));
    c = mix(c, vec3(0.55, 0.62, 0.68), smoothstep(0.85, 0.95, abs(q.y)) * 0.5); // (the bluish poles)
    return c;
}

vec3 mars(vec3 q) {
    float n = fbm(q * 3.0 + 5.0);
    vec3 c = mix(vec3(0.75, 0.32, 0.14), vec3(0.45, 0.2, 0.1), smoothstep(0.45, 0.62, n));
    c = mix(c, vec3(0.85, 0.5, 0.3), smoothstep(0.6, 0.75, fbm3(q * 8.0)) * 0.3);
    c = mix(c, vec3(0.95, 0.93, 0.9), smoothstep(0.86, 0.9, q.y)); // (the polar cap)
    return c;
}

vec3 venus(vec3 q, float t) {
    float n = fbm(vec3(q.x * 2.0 + t * 0.01, q.y * 5.0, q.z * 2.0));
    return mix(vec3(0.85, 0.72, 0.45), vec3(0.97, 0.9, 0.7), n);
}

vec3 craters(vec3 q, vec3 base, float k) {
    vec3 g = q * k;
    vec3 i = floor(g);
    float d = 9.0;
    for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec3 c = i + vec3(x, y, z);
        vec3 p = c + vec3(hash13(c), hash13(c + 3.1), hash13(c + 7.7));
        d = min(d, length(g - p) / (0.3 + 0.5 * hash13(c + 9.0)));
    }
    float rim = smoothstep(0.55, 0.75, d) - smoothstep(0.75, 1.0, d);
    return base * (0.75 + 0.25 * fbm3(q * 6.0)) * (0.8 + 0.2 * smoothstep(0.3, 0.8, d)) + vec3(0.12) * rim;
}

vec3 surface(int i, vec3 n, float t, out float nightGlow) {
    vec3 q = toBody(i, n);
    nightGlow = 0.0;
    if (i == 1) return craters(q, vec3(0.55, 0.53, 0.5), 3.0);
    if (i == 2) return venus(q, t);
    if (i == 3) {
        vec3 c = earth(q, 1.0, nightGlow);
        float cl = clouds(q, t).r;
        nightGlow *= 1.0 - cl;
        return mix(c, vec3(1.0), cl * 0.85);
    }
    if (i == 4) return mars(q);
    if (i == 5) return jupiter(q);
    if (i == 6) return saturn(q);
    if (i == 7) return mix(vec3(0.62, 0.85, 0.88), vec3(0.72, 0.9, 0.92), smoothstep(-0.5, 0.5, sin(q.y * 8.0)) * 0.4);
    if (i == 8) {
        vec3 c = mix(vec3(0.15, 0.3, 0.75), vec3(0.25, 0.45, 0.9), fbm3(q * 3.0));
        c = mix(c, vec3(0.05, 0.12, 0.4), smoothstep(0.2, 0.1, length(vec2(atan(q.z, q.x) - 1.0, (q.y + 0.3) * 3.0)))); // (its dark spot)
        c = mix(c, vec3(0.9), smoothstep(0.9, 0.97, sin(q.y * 14.0 + fbm3(q * 4.0) * 3.0)) * 0.6);
        return c;
    }
    if (i == 9) return craters(q, mix(vec3(0.62), vec3(0.4), smoothstep(0.45, 0.6, fbm3(q * 2.0 + 1.0))), 2.5);
    if (i == 10) return mix(vec3(0.95, 0.85, 0.35), vec3(0.75, 0.45, 0.15), smoothstep(0.5, 0.7, fbm3(q * 5.0)));
    if (i == 11) return mix(vec3(0.9, 0.88, 0.8), vec3(0.6, 0.4, 0.25), smoothstep(0.47, 0.5, abs(fbm3(q * 4.0) - 0.5) + 0.47) * 0.6);
    if (i == 12) return craters(q, vec3(0.55, 0.5, 0.45), 2.0);
    if (i == 13) return craters(q, vec3(0.35, 0.32, 0.3), 3.0);
    if (i == 14) return vec3(0.85, 0.6, 0.25);
    return vec3(0.7, 0.75, 0.8);
}

// the Sun's face: boiling granules, darker at its edge, hot spots
vec3 sunFace(vec3 n, float t) {
    vec3 q = toBody(0, n);
    float g = fbm(q * 7.0 + vec3(0.0, t * 0.05, 0.0));
    float spots = smoothstep(0.72, 0.8, fbm3(q * 3.0 + 20.0));
    vec3 c = mix(vec3(1.0, 0.55, 0.12), vec3(1.0, 0.9, 0.55), g);
    c *= 1.0 - spots * 0.6;
    return c * (1.6 + 0.6 * Boost);
}

// Saturn's rings: how dense at this distance (of Saturn's radius); gaps
float ringDensity(float r) {
    if (r < 1.24 || r > 2.28) return 0.0;
    float d;
    if (r < 1.52) d = 0.22 + 0.08 * sin(r * 90.0);                   // the faint C ring
    else if (r < 1.95) d = 0.85 + 0.1 * sin(r * 70.0) * sin(r * 23.0); // the bright B ring
    else if (r < 2.03) d = 0.06;                                       // the Cassini division
    else d = 0.6 + 0.1 * sin(r * 80.0);                                // the A ring
    d *= 1.0 - (1.0 - smoothstep(0.0, 0.012, abs(r - 2.2))) * 0.8;     // (the Encke gap)
    d *= smoothstep(1.24, 1.28, r) * (1.0 - smoothstep(2.24, 2.28, r));
    return clamp(d, 0.0, 1.0);
}

vec3 ringAxis() {
    float t = tiltOf(6);
    return vec3(0.0, cos(t), -sin(t));
}

// a point's shadow: lit by the Sun, or something in the way (a body, or for Saturn its rings)
float shadow(vec3 p, int self) {
    vec3 sun = bodyAt(0);
    vec3 L = normalize(sun - p);
    float dist = length(sun - p);
    float s = 1.0;
    for (int i = 1; i <= 14; i++) {
        if (i == self) continue;
        // (only the bodies near this one can shadow it: the Moon and Earth each other, Jupiter's moons, Saturn's)
        bool nearby = (self == 3 && i == 9) || (self == 9 && i == 3) || (self == 5 && i >= 10 && i <= 13) || (self >= 10 && self <= 13 && i == 5)
                      || (self == 6 && i == 14) || (self == 14 && i == 6) || (self == -6 && i == 6);
        if (!nearby) continue;
        vec3 c = bodyAt(i);
        vec2 hn = nearest(p, L, c);
        if (hn.y > 0.0 && hn.y < dist) {
            float r = radiusOf(i);
            s = min(s, smoothstep(r * 0.75, r * 1.25, hn.x));
        }
    }
    if (self == 6) { // the rings' shadow on Saturn
        vec3 sc = bodyAt(6), ax = ringAxis();
        float denom = dot(L, ax);
        if (abs(denom) > 1e-4) {
            float tr = dot(sc - p, ax) / denom;
            if (tr > 0.0) {
                float rr = length(p + L * tr - sc) / radiusOf(6);
                s *= 1.0 - ringDensity(rr) * 0.75;
            }
        }
    }
    return s;
}

// a lit body's colour
vec3 shadeBody(int i, vec3 p, vec3 n, vec3 rd, float t) {
    vec3 sun = bodyAt(0);
    vec3 L = normalize(sun - p);
    float nightGlow;
    vec3 alb = surface(i, n, t, nightGlow);
    float sh = shadow(p, i);
    float diff = max(dot(n, L), 0.0) * sh;
    // (the day side lit by the Sun; a little of the sky's light on the night side so it isn't black)
    float wrap = max(dot(n, L) * 0.8 + 0.2, 0.0) * sh;
    vec3 c = alb * (wrap * 1.1 + 0.13);
    if (i == 3) {
        c += vec3(1.0, 0.75, 0.35) * nightGlow * smoothstep(0.1, -0.15, dot(n, L)) * 0.9; // (the cities at night)
        c += vec3(0.35, 0.55, 1.0) * pow(1.0 - max(dot(n, -rd), 0.0), 3.0) * (0.15 + 0.5 * diff); // (the air's blue edge)
        vec3 h = normalize(L - rd); // (the Sun glinting on the sea)
        c += vec3(1.0, 0.9, 0.7) * pow(max(dot(n, h), 0.0), 60.0) * 0.4 * diff;
    }
    if (i == 9 && sh < 0.9) c = mix(c, alb * vec3(0.5, 0.12, 0.05) * 0.4, 1.0 - sh); // (a lunar eclipse: the Moon gone red)
    if (i == 2 || i == 7 || i == 8 || i == 14) {
        c += alb * pow(1.0 - max(dot(n, -rd), 0.0), 3.0) * 0.3 * (0.3 + diff); // (thick air: a soft glowing edge)
    }
    return c;
}

// ---- the stars in the hologram's dome ----
vec3 stars(vec3 d) {
    vec3 g = d * 40.0;
    vec3 i = floor(g);
    vec3 col = vec3(0.0);
    for (int z = 0; z <= 1; z++) for (int y = 0; y <= 1; y++) for (int x = 0; x <= 1; x++) {
        vec3 c = floor(g - 0.5) + vec3(x, y, z);
        float h = hash13(c);
        if (h > 0.35) continue;
        vec3 sp = normalize(c + vec3(hash13(c + 7.1), hash13(c + 3.3), hash13(c + 5.9)));
        float a = length(cross(d, sp)) * 40.0;
        float b = hash13(c + 11.0);
        col += mix(vec3(0.7, 0.8, 1.0), vec3(1.0, 0.85, 0.7), b) * smoothstep(0.08, 0.0, a) * (0.4 + b) * (0.7 + 0.3 * sin(SpinTime * 2.0 + h * 50.0));
    }
    return col;
}

// the depth of a point (block units): what's in front of it in the world hides it
float depthOf(vec3 p) {
    vec4 c = ProjMat * ModelViewMat * ViewRot * vec4(p + toView, 1.0);
    return clamp(c.z / c.w * 0.5 + 0.5, 0.0, 1.0);
}

void main() {
    gl_FragDepth = gl_FragCoord.z;
    Dome = settings.x;
    SizeMode = settings.y;
    HoloScale = settings.z;
    bool holo = kind > 0.5;
    float S = holo ? HoloScale : 1.0;
    gC = holo ? vec3(0.5, 0.3 + 1.15 * S, 0.5) : vec3(0.5, 0.74, 0.5);
    gR = holo ? 1.38 * S : 0.74;
    float t = SpinTime;
    vec3 ro = lp;
    vec3 rd = normalize(lray);
    // standing inside its box, or close to it (in the planetarium): traced from the eye, on the box's far side
    // (its near side, right by the eye, is partly cut away by the screen's near plane); further out, on its near side
    // only (both sides are drawn)
    vec3 eyeL = lp - lray;
    const float NEAR = 0.6;
    bool inside = persp > 0.5 && ItemMode < 0.5 && all(greaterThan(eyeL, boxLo - NEAR)) && all(lessThan(eyeL, boxHi + NEAR));
    if (inside) {
        if (gl_FrontFacing) discard;
        ro = eyeL;
    } else if (!gl_FrontFacing) {
        discard;
    }

    // the hologram's dome: a glass bubble round it (a flat floor at the base)
    float domeR = gR * 1.08;
    float tIn = 0.0, tOut = 1e9;
    if (holo) {
        vec3 oc = ro - gC;
        float b = dot(oc, rd), c = dot(oc, oc) - domeR * domeR, h = b * b - c;
        if (h < 0.0) discard;
        h = sqrt(h);
        tIn = max(-b - h, 0.0);
        tOut = -b + h;
        if (tOut <= 0.0) discard;
        float floorY = 0.3;
        if (abs(rd.y) > 1e-5) {
            float tf = (floorY - ro.y) / rd.y;
            if (rd.y > 0.0) tIn = max(tIn, tf); else tOut = min(tOut, tf);
        }
        if (tOut <= tIn) discard;
    }

    // ---- the nearest solid thing ----
    float hitT = 1e9;
    int hit = -1; // body, or 20 an arm, 21 the ring plate
    vec3 hitN = vec3(0.0);
    for (int i = 0; i <= 15; i++) {
        float r = i == 15 ? radiusOf(15) * 0.6 : radiusOf(i);
        float ts = sphere(ro, rd, bodyAt(i), r);
        if (ts > 0.0 && ts < hitT && ts > tIn) {
            hitT = ts;
            hit = i;
        }
    }
    if (!holo) {
        // the brass arms: from the shaft, each at its own height, out under its planet, a pin up to it
        for (int i = 1; i <= 8; i++) {
            vec3 p = bodyAt(i);
            float y = gC.y - 0.035 - float(i) * 0.012;
            vec3 a0 = vec3(gC.x, y, gC.z), a1 = vec3(p.x, y, p.z);
            float ta = capsule(ro, rd, a0, a1, 0.0035);
            if (ta > 0.0 && ta < hitT) { hitT = ta; hit = 20; }
            float tb = capsule(ro, rd, a1, p - vec3(0.0, radiusOf(i) * 0.6, 0.0), 0.0025);
            if (tb > 0.0 && tb < hitT) { hitT = tb; hit = 20; }
        }
        // the shaft from the base up to the Sun
        float ts = capsule(ro, rd, vec3(gC.x, 0.3, gC.z), gC, 0.012);
        if (ts > 0.0 && ts < hitT) { hitT = ts; hit = 20; }
    }
    if (DepthOnly > 0.5) { // (the depth pass: the solid things only)
        if (hit < 0 || hit == 15 || ItemMode > 0.5) discard;
        gl_FragDepth = depthOf(ro + rd * hitT);
        fragColor = vec4(0.0);
        return;
    }

    vec3 col = vec3(0.0);
    float alpha = 0.0;
    if (hit >= 0) {
        vec3 p = ro + rd * hitT;
        if (hit == 0) {
            vec3 n = normalize(p - bodyAt(0));
            float limb = pow(max(dot(n, -rd), 0.0), 0.45);
            col = sunFace(n, t) * (0.55 + 0.45 * limb);
        } else if (hit == 20) {
            // brass: polished, the Sun shining in it
            vec3 sunP = bodyAt(0);
            float e = 0.001;
            vec3 n;
            {
                // (the arm's surface direction: towards the ray from its line - good enough for a thin rod)
                n = normalize(cross(cross(rd, vec3(0.0, 1.0, 0.0)), rd) + 0.3 * (-rd));
            }
            vec3 L = normalize(sunP - p);
            float diff = 0.35 + 0.65 * max(dot(n, L), 0.0);
            vec3 h = normalize(L - rd);
            col = vec3(0.78, 0.56, 0.24) * diff + vec3(1.0, 0.85, 0.5) * pow(max(dot(n, h), 0.0), 30.0) * 0.6;
        } else if (hit == 15) {
            col = vec3(0.85, 0.9, 1.0) * 1.2; // (the comet's icy nucleus, bright)
        } else {
            vec3 c = bodyAt(hit);
            vec3 n = normalize(p - c);
            col = shadeBody(hit, p, n, rd, t);
        }
        alpha = 1.0;
    }

    // ---- Saturn's rings (in front of what's behind them) ----
    {
        vec3 sc = bodyAt(6), ax = ringAxis();
        float denom = dot(rd, ax);
        if (abs(denom) > 1e-5) {
            float tr = dot(sc - ro, ax) / denom;
            if (tr > tIn && tr < hitT) {
                vec3 p = ro + rd * tr;
                float rr = length(p - sc) / radiusOf(6);
                float d = ringDensity(rr);
                if (d > 0.0) {
                    vec3 rc = mix(vec3(0.78, 0.7, 0.55), vec3(0.95, 0.9, 0.78), 0.5 + 0.5 * sin(rr * 37.0)) * (rr < 1.52 ? 0.75 : 1.0);
                    vec3 L = normalize(bodyAt(0) - p);
                    float lit = 0.45 + 0.75 * abs(dot(ax, L));
                    vec2 hn = nearest(p, L, sc); // (Saturn's shadow across them)
                    if (hn.y > 0.0 && hn.x < radiusOf(6)) lit *= 0.15;
                    rc *= lit;
                    float a = d * 0.85;
                    col = rc * a + col * (1.0 - a); // (premultiplied: col is already times its alpha)
                    alpha = a + alpha * (1.0 - a);
                }
            }
        }
    }

    // ---- glows and lines, added in front ----
    vec3 add = vec3(0.0);
    float addA = 0.0;
    // the Sun's corona
    {
        vec2 hn = nearest(ro, rd, bodyAt(0));
        float r0 = radiusOf(0);
        if (hn.y < hitT || hit == 0) {
            float d = max(hn.x - r0, 0.0) / r0;
            float g = exp(-d * 2.8) * (hit == 0 ? 0.35 : 1.0);
            add += vec3(1.0, 0.6, 0.2) * g * (0.7 + 0.5 * Boost);
            addA += g * 0.6;
        }
    }
    // the comet's tail: straight away from the Sun, longer and brighter close to it
    {
        vec3 cp = bodyAt(15);
        vec3 away = normalize(cp - gC);
        float near = clamp(0.35 * gR / length(cp - gC), 0.2, 2.5);
        float len = gR * 0.14 * min(near, 2.0);
        vec3 pb = cp + away * len;
        vec3 ba = pb - cp;
        // (the nearest the ray passes the tail: closest points of the ray and the tail's segment)
        vec3 r0 = ro - cp;
        float bb = dot(ba, ba), rb = dot(rd, ba), r0b = dot(r0, ba), r0d = dot(r0, rd);
        float den = bb - rb * rb;
        if (den > 1e-8) {
            float s = clamp((r0b - rb * r0d) / den * 1.0, 0.0, 1.0);
            float tt = max(dot(cp + ba * s - ro, rd), 0.0);
            s = clamp(dot(ro + rd * tt - cp, ba) / bb, 0.0, 1.0);
            tt = max(dot(cp + ba * s - ro, rd), 0.0);
            float dist = length(ro + rd * tt - (cp + ba * s));
            float width = mix(radiusOf(15) * 1.0, len * 0.12, s);
            float g = exp(-pow(dist / width, 2.0)) * pow(1.0 - s, 1.6) * min(near, 1.5) * 0.8;
            if (tt > tIn && tt < hitT) {
                add += vec3(0.6, 0.85, 1.0) * g * 0.9;
                addA += g * 0.5;
            }
        }
        vec2 hn = nearest(ro, rd, cp); // (the coma)
        if (hn.y < hitT) {
            float g = exp(-pow(hn.x / (radiusOf(15) * 3.0), 2.0)) * min(near, 1.5);
            add += vec3(0.75, 0.9, 1.0) * g;
            addA += g * 0.4;
        }
    }
    // the ecliptic: the asteroid belt, and (the hologram) the planets' orbits drawn in light
    if (abs(rd.y) > 1e-5) {
        float tp = (gC.y - ro.y) / rd.y;
        if (tp > tIn && tp < hitT) {
            vec3 p = ro + rd * tp;
            vec2 d = p.xz - gC.xz;
            float rr = length(d) / gR;
            float ang = atan(d.y, d.x);
            // the belt: specks of rock going round
            if (rr > 0.48 && rr < 0.52) {
                float a2 = ang - TAU * SimYears / 4.6;
                vec2 cell = vec2(floor(a2 / TAU * 260.0), floor(rr * 90.0));
                float hh = hash13(vec3(cell, 3.0));
                if (hh < 0.35) {
                    vec2 f = vec2(fract(a2 / TAU * 260.0), fract(rr * 90.0)) - 0.5;
                    float dot0 = smoothstep(0.2, 0.05, length(f - (vec2(hash13(vec3(cell, 5.0)), hash13(vec3(cell, 9.0))) - 0.5) * 0.5));
                    vec3 rock = vec3(0.55, 0.5, 0.45) * (0.6 + 0.6 * hash13(vec3(cell, 1.0)));
                    add += rock * dot0 * (holo ? 0.9 : 0.7);
                    addA += dot0 * 0.8;
                }
            }
            if (holo) {
                for (int i = 1; i <= 8; i++) {
                    float w = abs(rr - orbitOf(i)) * gR;
                    float line = exp(-pow(w / (0.004 * S), 2.0));
                    add += vec3(0.3, 0.75, 1.0) * line * 0.35;
                    addA += line * 0.2;
                }
            }
        }
    }

    if (holo) {
        // the space inside the dome: dark, full of stars
        bool dome = Dome > 0.5 || ItemMode > 0.5;
        if (hit < 0 && dome) {
            vec3 bg = vec3(0.01, 0.015, 0.04) + stars(rd) * 0.9;
            float a = 0.72; // (dark space inside the dome)
            col += bg * a * (1.0 - alpha);
            alpha += a * (1.0 - alpha);
        }
        col += add;
        alpha = clamp(alpha + addA, 0.0, 1.0);
        // the hologram's shimmer: faint lines going up, a cool rim of light round the planets
        // (the planets themselves solid: not dimmed or see-through by day, only the light round them)
        float solid = hit >= 0 ? 1.0 : 0.0;
        col *= (0.92 + 0.08 * sin(gl_FragCoord.y * 1.5 + t * 6.0)) * mix(Glow, 1.0, solid);
        alpha = mix(alpha * Glow, 1.0, solid);
        // the glass: a sheen at its edge, a highlight
        vec3 pe = ro + rd * tIn;
        vec3 n = normalize(pe - gC);
        if (dome && pe.y > 0.31 && length(ro - gC) > domeR) { // (the glass, seen from outside)
            float fres = 0.04 + 0.5 * pow(1.0 - abs(dot(n, rd)), 4.0);
            vec3 rf = reflect(rd, n);
            float spec = pow(max(dot(rf, normalize(vec3(0.4, 1.0, 0.3))), 0.0), 80.0);
            col = col * (1.0 - fres) + vec3(0.55, 0.7, 0.85) * fres + vec3(spec) * 0.6;
            alpha = clamp(alpha + fres + spec * 0.5, 0.0, 1.0);
        }
        if (alpha < 0.004) discard; // (no dome: nothing here)
        // (the depth of a planet hit, else of the dome's near side: the world in front of it hides it, not the box's)
        if (ItemMode < 0.5) gl_FragDepth = depthOf(ro + rd * (hit >= 0 ? hitT : max(tIn, 0.1)));
        fragColor = vec4(clamp(col, 0.0, 1.0), clamp(alpha, 0.0, 1.0));
        return;
    }

    // the orrery: solid things opaque, glows over them
    col = col + add;
    alpha = clamp(alpha + addA, 0.0, 1.0);
    if (alpha < 0.004) discard;
    if (ItemMode < 0.5) gl_FragDepth = depthOf(ro + rd * (hit >= 0 ? hitT : max(tIn, 0.1)));
    fragColor = vec4(clamp(col, 0.0, 1.0), alpha);
}
