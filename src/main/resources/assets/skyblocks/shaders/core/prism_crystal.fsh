#version 150

// A clear crystal - a six-sided column with a point at each end, leaning a little, turning slowly - traced as glass
// really is: light off its faces (more at a slant), and through it, bent going in and coming out, red bent least and
// violet most, bouncing inside off faces it meets too steeply. What it shows through it is the room's light and the
// light shining on it: where that comes through, split into its colours - the crystal's fire. Its edges catch light;
// glints where the light's reflected straight at the eye. Writes its depth.

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

in vec3 lp;
flat in vec3 eye;
flat in vec3 centre;
flat in vec3 toLight;
flat in float strength;
flat in float bright;
flat in float turn;

out vec4 fragColor;

// the crystal (blocks, from its middle): the column's inner radius, its half-height, its points
const float R = 0.105, H = 0.13, T = 0.275, TILT = 0.2;
const int NP = 18;
vec3 N[NP];
float D[NP];

void planes() {
    float k = R / (T - H);
    for (int i = 0; i < 6; i++) {
        float a = float(i) * 1.0471976;
        vec3 side = vec3(cos(a), 0.0, sin(a));
        N[i] = side;
        D[i] = R;
        // the points' faces: through the tip and the top of the side face below them
        vec3 up = vec3(side.x, k, side.z);
        float l = length(up);
        N[6 + i] = up / l;
        D[6 + i] = k * T / l;
        N[12 + i] = vec3(side.x, -k, side.z) / l;
        D[12 + i] = k * T / l;
    }
}

// where the ray leaves the crystal from inside it (and that face's outward normal)
float leave(vec3 p, vec3 d, out vec3 n) {
    float best = 1e3;
    n = vec3(0.0, 1.0, 0.0);
    for (int i = 0; i < NP; i++) {
        float dn = dot(N[i], d);
        if (dn > 1e-5) {
            float t = (D[i] - dot(N[i], p)) / dn;
            if (t < best) { best = t; n = N[i]; }
        }
    }
    return best;
}

// what's round it: the room's light (lighter overhead), and the light that shines on it, bright and small
vec3 env(vec3 d, vec3 L) {
    float up = d.y * 0.5 + 0.5;
    vec3 room = mix(vec3(0.42, 0.44, 0.48), vec3(0.88, 0.93, 1.0), smoothstep(0.1, 0.9, up)) * (0.3 + 0.7 * bright);
    float c = max(dot(d, L), 0.0);
    vec3 light = vec3(1.0) * strength * (pow(c, 220.0) * 9.0 + pow(c, 28.0) * 1.2 + pow(c, 5.0) * 0.12);
    return room + light;
}

float hash1(float n) {
    return fract(sin(n) * 43758.5453);
}

void main() {
    planes();
    // into the crystal's own frame: turned about up, then leaning
    float ct = cos(turn), st = sin(turn), cl = cos(TILT), sl = sin(TILT);
    mat3 Ry = mat3(ct, 0.0, -st, 0.0, 1.0, 0.0, st, 0.0, ct);
    mat3 Rx = mat3(1.0, 0.0, 0.0, 0.0, cl, sl, 0.0, -sl, cl);
    mat3 M = Rx * Ry; // world -> crystal
    vec3 rdW = normalize(lp - eye);
    vec3 ro = M * eye, rd = M * rdW;
    vec3 L = normalize(M * toLight);

    // into it: the last face the ray crosses going in, the first going out
    float tin = -1e3, tout = 1e3;
    vec3 nin = vec3(0.0, 1.0, 0.0);
    for (int i = 0; i < NP; i++) {
        float dn = dot(N[i], rd), dist = D[i] - dot(N[i], ro);
        if (abs(dn) < 1e-6) {
            if (dist < 0.0) discard;
            continue;
        }
        float t = dist / dn;
        if (dn < 0.0) {
            if (t > tin) { tin = t; nin = N[i]; }
        } else {
            tout = min(tout, t);
        }
    }
    if (tin > tout || tout < 0.0) discard;
    tin = max(tin, 0.0);
    vec3 P = ro + rd * tin;
    float cosi = clamp(-dot(rd, nin), 0.0, 1.0);
    float F = 0.045 + 0.955 * pow(1.0 - cosi, 5.0);

    // off its face
    vec3 col = env(reflect(rd, nin), L) * F;

    // through it, each colour bent its own way
    vec3 ior = vec3(1.50, 1.535, 1.575);
    vec3 through = vec3(0.0);
    for (int c = 0; c < 3; c++) {
        float eta = ior[c];
        vec3 d = refract(rd, nin, 1.0 / eta);
        vec3 p = P;
        vec3 got = vec3(0.0);
        float carry = 1.0;
        for (int b = 0; b < 5; b++) {
            vec3 n;
            float t = leave(p + d * 1e-4, d, n);
            p = p + d * (t + 1e-4);
            vec3 o = refract(d, -n, eta);
            if (dot(o, o) > 0.0) {
                float co = clamp(dot(o, n), 0.0, 1.0);
                float Fo = 0.045 + 0.955 * pow(1.0 - co, 5.0);
                got = env(o, L) * (1.0 - Fo) * carry;
                break;
            }
            d = reflect(d, -n); // (too steep to get out: all of it back in)
            carry *= 0.92;
            if (b == 4) got = env(d, L) * 0.3 * carry;
        }
        through[c] = got[c];
    }
    // (clear, the faintest cool tint)
    col += through * (1.0 - F) * vec3(0.96, 0.98, 1.0);
    // its fire: each face throwing back its own colours, changing as it turns - stronger with light on it
    float facet = dot(nin, vec3(3.1, 2.7, 1.9)) + dot(refract(rd, nin, 1.0 / 1.535), vec3(1.7, 2.3, 2.9)) + turn * 0.5;
    vec3 fire = clamp(abs(fract(facet + vec3(0.0, 0.333, 0.667)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    col += fire * (0.06 + 0.22 * strength) * (0.4 + 0.6 * bright);

    // its edges catch the light: near where two faces meet
    float edge = 1e3;
    for (int i = 0; i < NP; i++) {
        float g = abs(dot(N[i], P) - D[i]);
        if (g > 1e-4) edge = min(edge, g);
    }
    col += vec3(0.85, 0.92, 1.0) * smoothstep(0.004, 0.0, edge) * (0.05 + 0.2 * strength + 0.06 * bright);

    // glints: where the light's reflected straight at the eye, a bright point that sparkles as it turns
    float g = max(dot(reflect(rd, nin), L), 0.0);
    float tw = 0.7 + 0.3 * sin(GameTime * 24000.0 * 0.35 + turn * 40.0);
    col += vec3(1.0) * pow(g, 600.0) * 6.0 * strength * tw;

    // (see-through: the world behind shows through it, more of its own light at a slant)
    float alpha = clamp(0.5 + F * 0.5 + max(max(col.r, col.g), col.b) * 0.15, 0.0, 1.0);
    vec3 hitW = centre + eye + rdW * tin;
    vec4 clip = ProjMat * ModelViewMat * vec4(hitW, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = vec4(col, alpha);
}
