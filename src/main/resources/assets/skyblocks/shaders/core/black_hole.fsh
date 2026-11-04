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
uniform sampler2D Sampler1; // (a singularity in its chamber: the depth of what's drawn so far, if HasDepth)
uniform float HasDepth;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float HoleTime;
uniform float ItemMode;
uniform float DepthOnly; // (a singularity in its chamber, second pass: only its shadow's depth)

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

// where the straight ray (from o, relative to the middle) first meets the chamber's frames - the twelve edges of a
// cube this many blocks across - or -1
float frameHit(vec3 o, vec3 d, float size) {
    float r = (size - 1.0) * 0.5;
    float best = 1.0e9;
    vec3 inv = 1.0 / (sign(d) * max(abs(d), vec3(1e-6)));
    for (int axis = 0; axis < 3; axis++) {
        for (int k = 0; k < 4; k++) {
            vec2 s = vec2(k & 1, k >> 1) * 2.0 - 1.0;
            vec3 lo, hi;
            if (axis == 0) { lo = vec3(-r - 0.5, s.x * r - 0.5, s.y * r - 0.5); hi = vec3(r + 0.5, s.x * r + 0.5, s.y * r + 0.5); }
            else if (axis == 1) { lo = vec3(s.x * r - 0.5, -r - 0.5, s.y * r - 0.5); hi = vec3(s.x * r + 0.5, r + 0.5, s.y * r + 0.5); }
            else { lo = vec3(s.x * r - 0.5, s.y * r - 0.5, -r - 0.5); hi = vec3(s.x * r + 0.5, s.y * r + 0.5, r + 0.5); }
            vec3 ta = (lo - o) * inv, tb = (hi - o) * inv;
            float tn = max(max(min(ta.x, tb.x), min(ta.y, tb.y)), min(ta.z, tb.z));
            float tx = min(min(max(ta.x, tb.x), max(ta.y, tb.y)), max(ta.z, tb.z));
            if (tx >= max(tn, 0.0) && tn < best) best = max(tn, 0.0);
        }
    }
    return best < 1.0e8 ? best : -1.0;
}

// whether the point (relative to the middle) is in one of the chamber's frames
bool inFrame(vec3 q, float size) {
    float r = (size - 1.0) * 0.5;
    vec3 a = abs(q);
    if (any(greaterThan(a, vec3(r + 0.52)))) return false;
    vec3 rim = step(vec3(r - 0.52), a);
    return rim.x + rim.y + rim.z >= 2.0;
}

// what's drawn so far at this screen position (-1..1), as a point in the block's space
vec3 sceneAt(vec2 nd, mat4 inv) {
    float d = texture(Sampler1, clamp(nd * 0.5 + 0.5, vec2(0.001), vec2(0.999))).r;
    vec4 w = inv * vec4(nd, d * 2.0 - 1.0, 1.0);
    return w.xyz / w.w - origin;
}

// whether a frame is at or right beside this screen position (-1..1): a pixel or so round it too, so a frame's
// outline - its edge pixels, half frame and half what's behind - isn't picked up and bent round the hole either
bool frameNear(vec2 nd, mat4 inv, float size) {
    vec2 px = 2.0 / vec2(textureSize(Sampler1, 0));
    vec3 C = vec3(0.5);
    for (int j = 0; j < 9; j++) {
        vec2 o = vec2(float(j % 3) - 1.0, float(j / 3) - 1.0) * 1.75;
        if (inFrame(sceneAt(nd + o * px, inv) - C, size)) {
            return true;
        }
    }
    return false;
}

void main() {
    gl_FragDepth = gl_FragCoord.z;
    bool big = kind > 0.5;
    vec3 C = big ? vec3(0.5) : vec3(0.5, 0.62, 0.5);
    float reach = kind > 2.5 ? 4.6 * vColor.a : kind > 1.5 ? 0.38 + 0.92 * vColor.a : big ? 1.5 : 0.36; // (the singularity: grows as it fills)
    float rs = kind > 2.5 ? reach * 0.17 : kind > 1.5 ? reach * 0.14 : big ? 0.21 : 0.07;  // (the shadow's radius)
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
    vec3 p0 = ro + rd * max(t0, 0.25); // (where the ray meets its reach - just ahead, from inside it: what's drawn later behind is hidden from here)
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
            if (kind > 2.5) {
                // (in a chamber: not the frames' picture bent round - further along the bent ray, past them, where
                // the screen shows what's beyond rather than a frame)
                float size = (kind - 3.0) * 2.0 + 5.0;
                float k = 2.0;
                for (int j = 0; j < (HasDepth > 0.5 ? 0 : 4); j++) {
                    vec3 xl = p + dir * k;
                    vec3 toX = xl - ro;
                    float L = length(toX);
                    float hit = frameHit(ro - C, toX / L, size);
                    if (hit < 0.0) {
                        break; // (the screen there shows no frame, near or far)
                    }
                    k *= 3.0;
                }
                bent = origin + p + dir * k;
            }
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
            if (kind > 2.5) {
                // (the point looked up still behind a frame from the eye: this pixel's own, not bent - no frame's
                // picture bent round the hole)
                bool frameThere;
                if (HasDepth > 0.5) {
                    frameThere = frameNear(nd, inverse(pm), (kind - 3.0) * 2.0 + 5.0);
                } else {
                    vec4 back = inverse(pm) * vec4(nd, clip.z / max(clip.w, 1e-4), 1.0);
                    vec3 xl = back.xyz / back.w - origin;
                    vec3 toX = xl - ro;
                    float L = length(toX);
                    frameThere = frameHit(ro - C, toX / max(L, 1e-4), (kind - 3.0) * 2.0 + 5.0) > 0.0;
                }
                if (frameThere) { // (the screen there shows a frame)
                    vec4 own = pm * vec4(eyeW + rd * 4.0, 1.0);
                    nd = own.xy / max(own.w, 1e-4);
                }
            }
            bg = texture(Sampler0, clamp(nd * 0.5 + 0.5, vec2(0.001), vec2(0.999))).rgb;
        }
    }
    // the photon ring: a thin bright line where light can just circle the hole
    float ring = exp(-pow((minR - rs * 1.25) / (rs * 0.09), 2.0)) * (swallowed ? 0.0 : 1.0);
    vec3 ringCol = kind > 1.5 ? mix(vColor.rgb, vec3(1.0), 0.6) : big ? vec3(1.0, 0.85, 0.6) : mix(vColor.rgb, vec3(1.0), 0.5);
    acc += trans * ringCol * ring * (big ? 0.9 : 0.6);

    vec3 col = acc + trans * bg * bgA;
    float alpha = clamp(1.0 - trans * (1.0 - bgA) + (swallowed ? 1.0 : 0.0), 0.0, 1.0);
    if (ItemMode > 0.5 && alpha < 0.02) {
        discard;
    }
    if (DepthOnly > 0.5) {
        // (its shadow's depth only: what's drawn after it - what it holds, drifting round - hidden behind the shadow)
        if (!swallowed) {
            discard;
        }
        vec4 sc = ProjMat * ModelViewMat * vec4(origin + p, 1.0);
        gl_FragDepth = sc.w > 1e-4 ? clamp(sc.z / sc.w * 0.5 + 0.5, 0.0, 1.0) : gl_FragCoord.z;
        fragColor = vec4(0.0);
        return;
    }
    fragColor = vec4(col, ItemMode > 0.5 ? alpha : 1.0);
    if (ItemMode < 0.5) {
        // its depth: the edge of its reach (Shadered's models and sky blocks are drawn after it, in their own pass, and
        // can't be bent: behind the hole they're hidden by it, not drawn on top of it)
        // (a singularity: what it holds drifts round in its disk, drawn after it - its depth there the shadow's, or
        // halfway in elsewhere: what's in front of its middle is seen, what's behind it hidden)
        vec3 dp = p0;
        if (kind > 2.5) {
            // (in its containment chamber: its frames aren't bent - where the view meets one, it stays as it is (the
            // hole just behind it), unless it's behind the shadow; everything else is, things thrown in too. Its
            // depth isn't kept: a second pass keeps only its shadow's, so what it holds, drawn after, shows in front)
            float size3 = (kind - 3.0) * 2.0 + 5.0;
            float tf;
            if (HasDepth > 0.5) {
                // (what's really here: a frame - not something in front of one, as an item thrown in - left as it is)
                mat4 pm3 = ProjMat * ModelViewMat;
                vec4 ownC = pm3 * vec4(origin + ro + rd * 4.0, 1.0);
                vec3 here = sceneAt(ownC.xy / max(ownC.w, 1e-4), inverse(pm3));
                tf = inFrame(here - C, size3) ? dot(here - ro, rd) : -1.0;
            } else {
                tf = frameHit(ro - C, rd, size3);
            }
            if (swallowed && tf > dot(p - ro, rd)) {
                tf = -1.0;
            }
            if (tf > 0.0 && DepthOnly < 0.5) {
                discard; // (a frame here, in front of the shadow: left as it is - from inside the hole's box too)
            }
            dp = swallowed ? p : p0;
        } else if (kind > 1.5) {
            dp = swallowed ? p : ro + rd * max(dot(C - ro, rd), 0.25);
        }
        vec4 dc = ProjMat * ModelViewMat * vec4(origin + dp, 1.0);
        if (dc.w > 1e-4) gl_FragDepth = clamp(dc.z / dc.w * 0.5 + 0.5, 0.0, 1.0);
    }
}
