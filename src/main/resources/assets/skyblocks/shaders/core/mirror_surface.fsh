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
        if (r > 0.475) {
            discard;
        }
        if (r > 0.43) {
            float k = (r - 0.43) / 0.045;
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

    if (kind == 2) { // cracked: shards round the point it was hit
        vec2 hit = vec2(0.3 + 0.4 * hash11(seed * 3.1 + 1.0), 0.3 + 0.4 * hash11(seed * 5.7 + 2.0));
        vec2 d = p - hit;
        float r = length(d);
        float a = atan(d.y, d.x) + 0.06 * sin(r * 40.0 + seed);
        float n = 7.0;
        float sector = floor((a + 3.14159) / 6.28318 * n + hash11(seed) * 0.5);
        float ring = r < 0.11 ? 0.0 : r < 0.27 ? 1.0 : 2.0;
        float shard = sector + ring * 13.0 + seed * 31.0;
        // how near a crack: the spokes, and the rings round the hit
        float fa = fract((a + 3.14159) / 6.28318 * n + hash11(seed) * 0.5);
        float spoke = min(fa, 1.0 - fa) * 6.28318 / n * r;
        float rings = min(abs(r - 0.11 - 0.02 * sin(a * 3.0 + seed)), abs(r - 0.27 - 0.03 * sin(a * 5.0 + seed * 2.0)));
        float crack = min(spoke, rings);
        crack = min(crack, r * 0.5); // (the crushed middle)
        vec2 shift = (vec2(hash11(shard * 1.3), hash11(shard * 2.7)) - 0.5) * 0.012 * smoothstep(0.0, 0.05, r);
        vec3 c = reflection(shift) * (0.86 + 0.14 * hash11(shard * 4.1));
        float line = 1.0 - smoothstep(0.002, 0.007, crack);
        c = mix(c, vec3(0.92, 0.95, 1.0), line * 0.75);
        c = mix(c, vec3(0.75, 0.8, 0.85), smoothstep(0.035, 0.0, r) * 0.6); // (the crushed spot)
        fragColor = vec4(c, 1.0);
        return;
    }

    if (kind == 3) { // vanity: a frame round the outside, bulbs on it
        const float B = 0.16;
        float left = (edges & 1) != 0 ? 1.0 : p.x / B;
        float right = (edges & 2) != 0 ? 1.0 : (1.0 - p.x) / B;
        float bottom = (edges & 4) != 0 ? 1.0 : p.y / B;
        float top = (edges & 8) != 0 ? 1.0 : (1.0 - p.y) / B;
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
