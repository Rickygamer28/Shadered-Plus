#version 150

// Shadered+'s pond waterfall water (our own code), in the koi pond's colours: the pond's deep green water, clear and
// glassy, running down in streaks, catching the sky's light in ripples, frothing white in streaks and where it's
// rough (the lip, where it lands), its edges thin and ragged; the pool on top still, rippling a little.

uniform float PondTime;
uniform float Daylight;

in vec2 uv;
in vec4 vColor;
in vec3 viewPos;

out vec4 fragColor;

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

float noise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash22(i).x, hash22(i + vec2(1, 0)).x, f.x), mix(hash22(i + vec2(0, 1)).x, hash22(i + vec2(1, 1)).x, f.x), f.y);
}

void main() {
    float t = PondTime;
    float day = clamp(Daylight, 0.0, 1.0);
    float light = 0.2 + 0.8 * day;
    bool still = vColor.g > 0.5;
    vec3 water = vec3(0.06, 0.2, 0.17);
    vec3 sky = mix(vec3(0.03, 0.04, 0.08), vec3(0.62, 0.76, 0.9), day);
    if (still) { // the pool on top: still water, the sky in it rippling
        vec2 p = uv * 7.0;
        float r = noise2(p + vec2(t * 0.4, t * 0.3)) * 0.6 + noise2(p * 2.3 - t * 0.5) * 0.4;
        vec3 c = mix(water * light, sky, 0.3 + 0.25 * r);
        c += vec3(1.0) * pow(r, 6.0) * 0.5 * day;
        fragColor = vec4(c, 0.82);
        return;
    }
    // running water: streaks moving down it, faster in the middle
    float across = uv.x;
    float mid = 1.0 - abs(across - 0.5) * 2.0;
    float s = uv.y * 5.0 - t * (2.6 + 0.8 * mid);
    float streaks = noise2(vec2(across * 9.0, s)) * 0.55 + noise2(vec2(across * 21.0, s * 2.1)) * 0.3 + noise2(vec2(across * 47.0, s * 4.0)) * 0.15;
    float ripple = noise2(vec2(across * 13.0, uv.y * 8.0 - t * 4.0));
    // white water: in streaks, more where it's rough (the vertex foam: the lip, the drop, where it lands)
    float foam = smoothstep(0.62 - 0.35 * vColor.r, 0.9 - 0.3 * vColor.r, streaks);
    foam = max(foam, smoothstep(0.75, 0.95, noise2(vec2(across * 30.0, s * 3.0))) * vColor.r);
    vec3 c = water * light;
    c = mix(c, sky, 0.18 + 0.4 * pow(ripple, 2.0));                // (the sky caught in it)
    c += vec3(1.0) * pow(ripple, 8.0) * 0.6 * day;                 // (glints)
    c = mix(c, vec3(0.9, 0.95, 0.97) * light, foam * 0.9);
    // its edges thin and ragged
    float edge = smoothstep(0.0, 0.22, across) * smoothstep(1.0, 0.78, across);
    float rag = noise2(vec2(across * 6.0, s * 0.7));
    if (edge < rag * 0.6) {
        discard;
    }
    float alpha = (0.5 + 0.45 * foam) * vColor.a * (0.6 + 0.4 * edge);
    fragColor = vec4(c, alpha);
}
