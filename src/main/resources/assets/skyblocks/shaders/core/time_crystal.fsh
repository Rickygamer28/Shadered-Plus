#version 150

// The time crystal, in 3D (blocks, from the box's middle): a long eight-sided crystal, purple, lit from inside, turning
// slowly and bobbing; round it two rune rings - thin bands of light, tilted, their runes dashes and dots - spinning
// opposite ways (faster the faster it's set), a ring of little clock ticks round the inner one. Stopped, they hang still
// and dim. Writes its own depth (what's in front of the crystal hides it; it hides what's behind).

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

in vec3 lp;
flat in vec3 eye;
flat in vec3 centre;
flat in float half_;
flat in float turn;
flat in float running;
flat in float speed;
flat in int part;

out vec4 fragColor;

const float TAU = 6.2831853;

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

// the ray's entry and exit of a ball (radius r round c): (t0, t1), t1 < t0 if it misses
vec2 ball(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - r * r);
    if (h < 0.0) return vec2(1.0, -1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}

mat3 rotX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

mat3 rotZ(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0);
}

float bob;
mat3 gemRot;

// the crystal: a long octahedron (its points up and down)
float gem(vec3 p) {
    p = gemRot * (p - vec3(0.0, bob, 0.0));
    p = abs(p);
    return (p.x + p.z + p.y * 0.36 - 0.25) * 0.68;
}

// a ring: (distance to its band, how far round it is 0 .. 1)
vec2 ring(vec3 p, mat3 m, float r, float spin) {
    vec3 q = m * p;
    float a = atan(q.z, q.x);
    vec2 d = vec2(length(q.xz) - r, q.y);
    float u = fract(a / TAU + spin);
    return vec2(length(d), u);
}

mat3 ringA, ringB;

// the runes along a ring (u 0 .. 1 round it), how bright: dashes and dots, in groups
float runes(float u, float n, float seed) {
    float cell = u * n;
    float i = floor(cell), f = fract(cell);
    float h = hash1(i + seed);
    float mark = h < 0.45 ? step(0.15, f) * step(f, 0.85)                   // a dash
               : h < 0.75 ? step(abs(f - 0.5), 0.16)                        // a dot
               : step(abs(f - 0.3), 0.1) + step(abs(f - 0.7), 0.1);          // two dots
    return mark * step(0.08, hash1(i * 3.7 + seed));
}

vec3 gemNormal(vec3 p) {
    vec2 e = vec2(0.002, 0.0);
    return normalize(vec3(gem(p + e.xyy) - gem(p - e.xyy), gem(p + e.yxy) - gem(p - e.yxy), gem(p + e.yyx) - gem(p - e.yyx)));
}

void main() {
    float t = GameTime * 1200.0;
    float live = running;
    bob = 0.05 * sin(t * 0.9);
    gemRot = rotY(t * 0.25);
    float spinA = turn * TAU, spinB = -turn * TAU * 0.73;
    ringA = rotY(spinA) * rotX(0.42) * rotZ(0.18);
    ringB = rotY(spinB) * rotX(-0.3) * rotZ(-0.38);

    vec3 ro = eye;
    vec3 rd = normalize(lp - eye);
    vec3 inv = 1.0 / (sign(rd) * max(abs(rd), vec3(1e-6)));
    vec3 ta = (vec3(-half_) - ro) * inv, tb = (vec3(half_) - ro) * inv;
    float t0 = max(max(max(min(ta.x, tb.x), min(ta.y, tb.y)), min(ta.z, tb.z)), 0.0);
    float t1 = min(min(max(ta.x, tb.x), max(ta.y, tb.y)), max(ta.z, tb.z));
    if (t1 <= t0) {
        discard;
    }

    vec3 glow = vec3(0.0);
    float glowA = 0.0;
    float tt = t0, hitT = -1.0, nearT = -1.0, nearD = 1e9;
    vec3 colA = vec3(0.35, 1.0, 0.9), colB = vec3(0.85, 0.45, 1.0);
    float dim = 0.3 + 0.7 * live;
    if (part == 1) {
        // the crystal alone: only where it can be (a ball round it), traced to its surface
        vec2 bs = ball(ro, rd, vec3(0.0, bob, 0.0), 0.76);
        if (bs.y <= bs.x || bs.y < t0) {
            discard;
        }
        tt = max(bs.x, t0);
        for (int i = 0; i < 64 && tt < bs.y; i++) {
            float g = gem(ro + rd * tt);
            if (g < 0.0015) {
                hitT = tt;
                break;
            }
            tt += max(g, 0.002);
        }
        if (hitT < 0.0) {
            discard;
        }
    } else {
        // the rings' light, and the crystal: only in the ball the rings (and the haze round them) fill
        vec2 bs = ball(ro, rd, vec3(0.0), 1.36);
        if (bs.y <= bs.x) {
            discard;
        }
        tt = max(t0, bs.x);
        float tEnd = min(t1, bs.y);
        for (int i = 0; i < 128; i++) {
            vec3 p = ro + rd * tt;
            float g = gem(p);
            if (g < 0.0015) {
                hitT = tt;
                break;
            }
            // the rings: light only (their runes glowing, a haze round them) - the runes and ticks worked out only near
            // their ring (further, their light is nothing to see)
            vec3 qa = ringA * p, qb = ringB * p;
            float da = length(vec2(length(qa.xz) - 0.78, qa.y)), db = length(vec2(length(qb.xz) - 1.02, qb.y));
            float dt = length(vec2(length(qa.xz) - 0.66, qa.y));
            float hA = exp(-da * 12.0) * 0.05 * dim, hB = exp(-db * 12.0) * 0.05 * dim;
            if (da < 0.14 || dt < 0.08) {
                float ua = fract(atan(qa.z, qa.x) / TAU);
                hA += exp(-da * 70.0) * (runes(ua, 48.0, 1.0) * dim + 0.1)
                    + exp(-dt * 110.0) * step(fract(ua * 60.0), 0.2) * dim * 0.8;
            }
            if (db < 0.14) {
                float ub = fract(atan(qb.z, qb.x) / TAU);
                hB += exp(-db * 70.0) * (runes(ub, 64.0, 7.0) * dim + 0.1);
            }
            float rd_ = min(min(da, db), dt);
            if (rd_ < nearD) {
                nearD = rd_;
                nearT = tt;
            }
            // (fine steps near the rings, where their light is; longer ones away from them, where it's next to nothing)
            float st = clamp(min(g, rd_ * 0.6), 0.003, rd_ > 0.3 ? 0.2 : 0.06);
            glow += (colA * hA + colB * hB) * st * 30.0;
            glowA += (hA + hB) * st * 30.0;
            tt += st;
            if (tt > tEnd) {
                break;
            }
        }
    }
    glowA = clamp(glowA, 0.0, 1.0);
    vec4 c = vec4(glow, glowA);
    float depthT = nearT;
    if (hitT > 0.0) {
        vec3 p = ro + rd * hitT;
        vec3 n = gemNormal(p);
        vec3 q = gemRot * (p - vec3(0.0, bob, 0.0));
        // facets: each its own shade of purple, lit from above and by the rings; lit from inside (brighter where you
        // look straight into it), a pale rim, a sharp highlight
        vec3 light = normalize(vec3(0.4, 0.9, 0.3));
        float diff = 0.45 + 0.55 * max(dot(n, light), 0.0);
        float facing = abs(dot(n, rd));
        vec3 base = mix(vec3(0.32, 0.12, 0.62), vec3(0.62, 0.38, 1.0), diff);
        float inner = pow(facing, 3.0) * (0.55 + 0.45 * sin(t * 0.12 * (1.0 + speed)) * live);
        vec3 col = base + vec3(0.75, 0.55, 1.0) * inner * (0.4 + 0.8 * live);
        // a fine clockwork of lines inside, seen through the faces
        float lines = smoothstep(0.92, 1.0, abs(sin(q.y * 40.0 + t * 0.05 * live))) * 0.25;
        col += vec3(0.4, 1.0, 0.9) * lines * facing;
        col += vec3(0.7, 0.9, 1.0) * pow(1.0 - facing, 3.0) * 0.8;
        vec3 h = normalize(light - rd);
        col += vec3(1.0) * pow(max(dot(n, h), 0.0), 60.0) * 0.9;
        c = part == 1 ? vec4(col, 1.0) : part == 2 ? c : vec4(c.rgb + col * (1.0 - c.a * 0.5), 1.0);
        depthT = hitT;
    } else if (part == 1) {
        discard;
    }
    if (c.a < 0.01 || depthT < 0.0) {
        discard;
    }
    vec4 clip = ProjMat * ModelViewMat * vec4(centre + ro + rd * depthT, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = vec4(c.rgb / max(c.a, 0.001), c.a);
}
