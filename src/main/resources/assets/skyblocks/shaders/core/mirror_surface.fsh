#version 150

// A mirror's face: shows its wall's reflection, looked up where this point was in the picture when it was taken (so
// a picture from a few frames ago still lines up with the mirror). The variants (our own code):
//   round   - a round mirror in a gold ring (the block's wood showing in the corners);
//   cracked - split into shards round where it was hit, each a little askew, bright cracks between;
//   vanity  - a white lacquered frame round the outside of the whole mirror, warm bulbs along its sides and top.

uniform sampler2D Sampler0;
// how far into the picture it was drawn (the rest is left over from before)
uniform vec2 MirrorTexMax;

in vec4 pictureClip;
in vec2 faceUv;
flat in vec3 faceCode;

out vec4 fragColor;

float hash11(float n) {
    return fract(sin(n * 12.9898 + 4.1414) * 43758.5453);
}

vec3 reflection(vec2 shift) {
    vec2 uv = pictureClip.xy / pictureClip.w * 0.5 + 0.5 + shift;
    return texture(Sampler0, clamp(uv, vec2(0.0), MirrorTexMax)).rgb;
}

void main() {
    int kind = int(faceCode.x + 0.5);
    float seed = faceCode.y;
    int edges = int(faceCode.z + 0.5);
    vec2 p = faceUv;

    if (kind == 1) { // round
        float r = length(p - 0.5);
        if (r > 0.5) {
            discard;
        }
        if (r > 0.43) {
            float k = (r - 0.43) / 0.07;
            vec2 dir = normalize(p - 0.5);
            float bevel = 0.55 + 0.45 * dot(dir * (k < 0.5 ? -1.0 : 1.0), normalize(vec2(-0.6, 0.8)));
            vec3 gold = mix(vec3(0.55, 0.38, 0.1), vec3(1.0, 0.85, 0.4), bevel) * (0.8 + 0.2 * sin(k * 3.14159));
            fragColor = vec4(gold, 1.0);
            return;
        }
        vec3 c = reflection(vec2(0.0));
        c *= 1.0 - 0.25 * smoothstep(0.36, 0.43, r); // (a soft edge to the glass)
        fragColor = vec4(c, 1.0);
        return;
    }

    if (kind == 2) { // cracked: hit hard in one spot - straight cracks running out from it, rings of cracks round it
        // joining them, every shard tilted its own way (its reflection jumps across the cracks), some fallen out
        const int N = 10;
        const float TAU = 6.2831853;
        vec2 hit = vec2(0.32 + 0.36 * hash11(seed * 3.1 + 1.0), 0.32 + 0.36 * hash11(seed * 5.7 + 2.0));
        vec2 d = p - hit;
        float r = length(d);
        float a = atan(d.y, d.x) + 3.14159265;
        // the spokes: each at its own angle; which two this point lies between
        float u = a / TAU * float(N);
        float s0 = floor(u);
        float sec = s0;
        float b0 = s0 + 0.7 * (hash11(mod(s0, float(N)) + seed * 17.0) - 0.5);
        if (u < b0) {
            sec = s0 - 1.0;
        } else {
            float b1 = s0 + 1.0 + 0.7 * (hash11(mod(s0 + 1.0, float(N)) + seed * 17.0) - 0.5);
            if (u >= b1) sec = s0 + 1.0;
        }
        float sA = sec, sB = sec + 1.0;
        float angA = (sA + 0.7 * (hash11(mod(sA, float(N)) + seed * 17.0) - 0.5)) / float(N) * TAU;
        float angB = (sB + 0.7 * (hash11(mod(sB, float(N)) + seed * 17.0) - 0.5)) / float(N) * TAU;
        vec2 dA = -vec2(cos(angA), sin(angA)), dB = -vec2(cos(angB), sin(angB)); // (atan + pi: the angle's turned round)
        float crack = min(abs(d.x * dA.y - d.y * dA.x), abs(d.x * dB.y - d.y * dB.x)); // (straight out from the hit)
        // the rings: straight pieces from spoke to spoke
        float ring = 0.0;
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float ra = (0.07 + 0.13 * fk) * (0.75 + 0.5 * hash11(mod(sA, float(N)) * 7.0 + fk * 3.0 + seed * 5.0));
            float rb = (0.07 + 0.13 * fk) * (0.75 + 0.5 * hash11(mod(sB, float(N)) * 7.0 + fk * 3.0 + seed * 5.0));
            if (k == 2 && hash11(sec * 3.7 + seed) < 0.35) continue; // (the outer ring not all the way round)
            vec2 A = dA * ra, B = dB * rb;
            vec2 e = B - A;
            vec2 nrm = normalize(vec2(e.y, -e.x));
            if (dot(nrm, -A) > 0.0) nrm = -nrm; // (pointing away from the hit)
            float side = dot(d - A, nrm);
            if (side > 0.0) ring += 1.0;
            float h = clamp(dot(d - A, e) / dot(e, e), 0.0, 1.0);
            crack = min(crack, length(d - A - e * h));
        }
        float shard = mod(sec, float(N)) * 5.0 + ring + seed * 53.0;
        vec2 rnd = vec2(hash11(shard * 1.3), hash11(shard * 2.7)) - 0.5;
        // each shard tilted: its reflection shifted, and turned a little across it
        vec2 shift = rnd * 0.035 + vec2(-d.y, d.x) * (hash11(shard * 5.3) - 0.5) * 0.06;
        shift *= smoothstep(0.0, 0.04, r);
        vec3 c = reflection(shift) * (0.82 + 0.25 * hash11(shard * 4.1));
        // fallen out: the dark back of the frame showing
        bool gone = ring >= 1.0 && ring <= 2.0 && hash11(shard * 9.1) < 0.1;
        if (gone) {
            c = vec3(0.07, 0.065, 0.06) * (0.8 + 0.4 * hash11(floor(p.x * 32.0) + floor(p.y * 32.0) * 31.0));
        }
        // the crushed middle: tiny chips, every which way, white with powdered glass
        if (r < 0.05) {
            float ua = a / TAU * 26.0, ur = r * 70.0 + 0.5 * hash11(floor(ua) + seed);
            vec2 cell = vec2(floor(ua), floor(ur));
            vec2 cr = vec2(hash11(cell.x * 3.1 + cell.y * 7.7 + seed), hash11(cell.x * 5.3 + cell.y * 2.9 + seed)) - 0.5;
            c = reflection(cr * 0.05) * (0.7 + 0.3 * hash11(cell.x + cell.y * 13.0)) + vec3(0.3) * (1.0 - r / 0.05);
            vec2 f = fract(vec2(ua, ur));
            crack = min(crack, min(min(f.x, 1.0 - f.x) * r * TAU / 26.0, min(f.y, 1.0 - f.y) / 70.0));
        }
        // the cracks: a dark line, the broken edge beside it catching the light
        float line = 1.0 - smoothstep(0.0012, 0.0028, crack);
        float glint = (1.0 - smoothstep(0.0028, 0.0065, crack)) * (1.0 - line);
        c = mix(c, vec3(0.04), line * 0.85);
        c += vec3(0.85, 0.9, 1.0) * glint * 0.45 * (0.6 + 0.4 * hash11(shard * 6.6));
        fragColor = vec4(min(c, vec3(1.0)), 1.0);
        return;
    }

    if (kind == 3) { // vanity: a frame round the outside, bulbs on it
        const float B = 0.16;
        // (how far from each side's frame, in frame widths; a side joined on to more of the mirror has none)
        float left = (edges & 1) != 0 ? 99.0 : p.x / B;
        float right = (edges & 2) != 0 ? 99.0 : (1.0 - p.x) / B;
        float bottom = (edges & 4) != 0 ? 99.0 : p.y / B;
        float top = (edges & 8) != 0 ? 99.0 : (1.0 - p.y) / B;
        float m = min(min(left, right), min(bottom, top));
        vec3 c;
        if (m < 1.0) {
            // the frame: white lacquer, a little rounded towards the glass
            c = vec3(0.93, 0.9, 0.84) * (0.75 + 0.25 * sin(m * 3.14159));
            // bulbs: two along each side and the top of the frame (not the bottom)
            float bd = 9.0;
            for (int k = 0; k < 2; k++) {
                float at = (float(k) + 0.5) / 2.0;
                if (left < 1.0) bd = min(bd, length(p - vec2(B * 0.5, at)));
                if (right < 1.0) bd = min(bd, length(p - vec2(1.0 - B * 0.5, at)));
                if (top < 1.0) bd = min(bd, length(p - vec2(at, 1.0 - B * 0.5)));
            }
            float glow = smoothstep(0.055, 0.035, bd);
            c = mix(c, vec3(1.0, 0.93, 0.75), glow);
            c += vec3(1.0, 0.8, 0.5) * 0.35 * exp(-bd * bd / 0.004) * (1.0 - glow);
            fragColor = vec4(min(c, vec3(1.0)), 1.0);
            return;
        }
        c = reflection(vec2(0.0));
        // the bulbs' warm light on the glass near the frame
        float near = min(min(left, right), top);
        c += vec3(0.25, 0.18, 0.08) * exp(-(near - 1.0) * 3.0) * 0.6;
        fragColor = vec4(min(c, vec3(1.0)), 1.0);
        return;
    }

    fragColor = vec4(reflection(vec2(0.0)), 1.0);
}
