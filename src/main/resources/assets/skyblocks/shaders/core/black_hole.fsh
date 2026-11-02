#version 150

// Shadered+'s black holes (our own code). The view ray is bent by the hole's pull as it passes (traced in small
// steps), so what is behind the hole - the screen as drawn so far (Sampler0) - is seen warped round it, with a black
// shadow in the middle and a thin bright ring at its edge where light can just circle. The pull fades out at the edge
// of the hole's reach, so the warping meets the world without a seam.
// The small one (an ornament): a swirling vortex of glowing dust round it (dyeable colour). The big one: a hot
// accretion disk, brighter on the side turning towards you, its far side seen bent up over the top and under the
// bottom as with a real black hole.

in vec3 lp;
in vec3 lray;
in vec3 eyeLp;
flat in vec3 origin;
flat in float kind;
flat in float seedIn;
flat in vec4 vColor;

uniform sampler2D Sampler0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float HoleTime;
uniform float ItemMode;

out vec4 fragColor;

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(41.3, 289.1))) * 45758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}

float fbm(vec2 p) {
    return 0.5 * vnoise(p) + 0.3 * vnoise(p * 2.1 + 3.7) + 0.2 * vnoise(p * 4.3 + 9.1);
}

vec3 blackbody(float k) { // (0 cool red .. 1 white-hot)
    return mix(mix(vec3(0.6, 0.1, 0.02), vec3(1.0, 0.55, 0.15), smoothstep(0.0, 0.5, k)), vec3(1.0, 0.95, 0.85), smoothstep(0.5, 1.0, k));
}

void main() {
    bool big = kind > 0.5;
    vec3 C = big ? vec3(0.5) : vec3(0.5, 0.62, 0.5);
    float reach = big ? 1.5 : 0.36;
    float rs = big ? 0.21 : 0.07;  // (the shadow's radius)
    float t = HoleTime + seedIn * 50.0;

    vec3 rd = normalize(lray);
    vec3 ro = eyeLp;
    // the box is drawn both sides (so it's there with the eye inside it): from outside, only its near side counts
    vec3 rel = ro - C;
    float hb = reach * 1.03; // (client/BlackHoleRenderer's box)
    if (any(greaterThan(abs(rel), vec3(hb)))) {
        vec3 srd = vec3(rd.x < 0.0 ? -1.0 : 1.0, rd.y < 0.0 ? -1.0 : 1.0, rd.z < 0.0 ? -1.0 : 1.0) * max(abs(rd), vec3(1e-5));
        vec3 ta = (-hb - rel) / srd, tb = (hb - rel) / srd;
        float tIn = max(max(min(ta.x, tb.x), min(ta.y, tb.y)), min(ta.z, tb.z));
        if (dot(lp - ro, rd) > tIn + 0.02) {
            discard;
        }
    }
    vec3 oc = ro - C;
    float b = dot(oc, rd), c = dot(oc, oc) - reach * reach, h = b * b - c;
    if (h < 0.0 || -b + sqrt(max(h, 0.0)) < 0.0) {
        discard;
    }
    float t0 = max(-b - sqrt(h), 0.0);
    vec3 p = ro + rd * t0;
    vec3 dir = rd;
    // the disk's plane: tilted a little, so it is never seen exactly edge-on
    vec3 dn = normalize(vec3(0.1, 1.0, 0.32)); // (tilted: from the side, its top is seen)

    vec3 acc = vec3(0.0);
    float trans = 1.0;
    bool swallowed = false;
    float minR = 1e9;
    float side = dot(p - C, dn);
    for (int i = 0; i < 90; i++) {
        vec3 q = p - C;
        float r = length(q);
        minR = min(minR, r);
        if (r < rs) {
            swallowed = true;
            break;
        }
        if (r > reach * 1.001 && dot(q, dir) > 0.0) {
            break;
        }
        // the pull: strong near the hole, nothing at the edge of its reach
        float fade = 1.0 - smoothstep(reach * 0.45, reach, r);
        float dt = clamp(0.09 * r, 0.004 * reach, 0.06 * reach);
        dir = normalize(dir - q / r * (1.6 * rs / (r * r)) * fade * dt);
        vec3 pn = p + dir * dt;
        float side2 = dot(pn - C, dn);
        if (big && side * side2 < 0.0) { // crossing the disk's plane
            vec3 hp = p + dir * dt * (side / (side - side2));
            vec3 hq = hp - C;
            float hr = length(hq);
            float rin = rs * 1.9, rout = reach * 0.88;
            if (hr > rin && hr < rout) {
                float u = (hr - rin) / (rout - rin);
                vec3 e1 = normalize(cross(dn, vec3(0.0, 0.0, 1.0))), e2 = cross(dn, e1);
                float ang = atan(dot(hq, e2), dot(hq, e1));
                float spin = t * 1.2 / pow(hr / rs, 1.5); // (faster inside)
                float streak = fbm(vec2((ang + spin) * 3.0, hr * 9.0)) * 0.7 + 0.3 * fbm(vec2((ang + spin) * 9.0, hr * 30.0));
                float dens = smoothstep(0.0, 0.08, u) * smoothstep(1.0, 0.55, u) * (0.45 + 0.75 * streak);
                // the side turning towards you is brighter and hotter
                vec3 vel = normalize(cross(dn, hq));
                float dop = 1.0 + 0.75 * dot(vel, -dir);
                vec3 col = blackbody(clamp((1.0 - u) * 0.95 * dop - 0.05, 0.0, 1.0)) * (1.6 - u) * dop * dop * 1.3 * vColor.rgb / max(max(vColor.r, vColor.g), max(vColor.b, 0.001));
                float a = clamp(dens * 0.9, 0.0, 1.0);
                acc += trans * col * a;
                // (the dim outer rim and the dim side glow, they don't hide much: no dark smudge beyond the disk)
                trans *= 1.0 - a * clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0) * (1.0 - smoothstep(0.55, 1.0, u));
            }
        }
        if (!big && r > rs * 1.15 && r < reach * 0.8) { // the small one's swirl of glowing dust
            vec3 e1 = normalize(cross(dn, vec3(0.0, 0.0, 1.0))), e2 = cross(dn, e1);
            float hgt = dot(q, dn);
            float rr = length(q - dn * hgt);
            float ang = atan(dot(q, e2), dot(q, e1));
            float arms = 0.5 + 0.5 * sin(ang * 2.0 + log(rr / rs) * 5.0 - t * 2.5);
            float swirl = fbm(vec2(ang * 2.0 - t * 1.5 / (rr / rs), rr * 30.0));
            float dens = exp(-pow(hgt / (0.12 * rr + 0.008), 2.0)) * smoothstep(reach * 0.8, reach * 0.35, rr) * smoothstep(rs * 1.15, rs * 2.2, rr)
                       * (0.25 + 0.75 * arms * swirl);
            float a = clamp(dens * dt * 45.0, 0.0, 1.0);
            vec3 col = mix(vColor.rgb, vec3(1.0), smoothstep(rs * 3.5, rs * 1.3, rr) * 0.6) * (1.4 + 2.0 * smoothstep(rs * 3.0, rs * 1.2, rr));
            acc += trans * col * a;
            trans *= 1.0 - a * 0.85;
        }
        side = side2;
        p = pn;
        if (trans < 0.02) {
            break;
        }
    }

    vec3 bg = vec3(0.0);
    float bgA = 1.0;
    if (!swallowed) {
        if (ItemMode > 0.5) {
            bgA = 0.0; // (an item: nothing behind to bend)
        } else {
            // where the bent ray goes, on the screen. Off the screen (bent round to the side, or back): as near to
            // that as the screen goes - along the way from the straight ray's point to it - not a smear of its edge
            mat4 pm = ProjMat * ModelViewMat;
            vec3 bent = origin + p + dir * 2.0;
            vec3 eyeW = origin + ro;
            vec3 straight = eyeW + rd * length(bent - eyeW);
            vec4 clip = pm * vec4(bent, 1.0);
            vec2 nd = clip.xy / max(clip.w, 1e-4);
            if (clip.w < 0.01 || any(greaterThan(abs(nd), vec2(0.995)))) {
                float lo = 0.0, hi = 1.0;
                for (int k = 0; k < 8; k++) {
                    float m = 0.5 * (lo + hi);
                    vec4 cm = pm * vec4(mix(straight, bent, m), 1.0);
                    if (cm.w > 0.01 && all(lessThan(abs(cm.xy / cm.w), vec2(0.995)))) {
                        lo = m;
                    } else {
                        hi = m;
                    }
                }
                clip = pm * vec4(mix(straight, bent, lo), 1.0);
                nd = clip.xy / max(clip.w, 1e-4);
            }
            bg = texture(Sampler0, clamp(nd * 0.5 + 0.5, vec2(0.001), vec2(0.999))).rgb;
        }
    }
    // the photon ring: a thin bright line where light can just circle the hole
    float ring = exp(-pow((minR - rs * 1.25) / (rs * 0.09), 2.0)) * (swallowed ? 0.0 : 1.0);
    vec3 ringCol = big ? vec3(1.0, 0.85, 0.6) : mix(vColor.rgb, vec3(1.0), 0.5);
    acc += trans * ringCol * ring * (big ? 0.9 : 0.6);

    vec3 col = acc + trans * bg * bgA;
    float alpha = clamp(1.0 - trans * (1.0 - bgA) + (swallowed ? 1.0 : 0.0), 0.0, 1.0);
    if (ItemMode > 0.5 && alpha < 0.02) {
        discard;
    }
    fragColor = vec4(col, ItemMode > 0.5 ? alpha : 1.0);
}
