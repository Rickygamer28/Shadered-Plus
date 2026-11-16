#version 150

// The sun in the jar: a ball of boiling light - granules churning over its face, white-hot in the middle and deep
// orange at its rim (limb darkening), dark spots drifting across - and round it a corona, flares licking out and
// falling back, and a warm glow.

uniform float GameTime;

in vec2 q;
flat in float phase;
flat in float bright;

out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(3.1, 1.7, 5.3);
        a *= 0.5;
    }
    return v;
}

void main() {
    const float R = 0.5;
    float seconds = GameTime * 1200.0;
    float r = length(q);
    if (r >= R) {
        // the corona: flares (bright tongues out of the rim, rising and falling) over a warm glow
        float around = atan(q.y, q.x);
        float out_ = (r - R) / (1.0 - R);
        vec2 ring = vec2(cos(around), sin(around)) * 2.0;
        float tongues = fbm(vec3(ring * 1.5, seconds * 0.35 - out_ * 2.0));
        float flare = smoothstep(0.35, 0.85, tongues) * exp(-out_ * (3.0 + 4.0 * (1.0 - tongues)));
        float glow = exp(-out_ * 5.0) * 0.55;
        float a = clamp(glow + flare * 1.2, 0.0, 1.0) * smoothstep(1.0, 0.85, r);
        vec3 col = mix(vec3(1.0, 0.45, 0.08), vec3(1.0, 0.85, 0.4), flare);
        fragColor = vec4(col * bright, a * bright);
        return;
    }
    vec3 n = vec3(q / R, sqrt(max(0.0, 1.0 - dot(q, q) / (R * R))));
    // its face, turning slowly: churning granules, and a few dark spots
    float a = seconds * 0.05;
    vec3 s = vec3(n.x * cos(a) + n.z * sin(a), n.y, -n.x * sin(a) + n.z * cos(a));
    float granules = fbm(s * 7.0 + vec3(0.0, seconds * 0.3, 0.0));
    float spots = smoothstep(0.72, 0.8, noise(s * 2.5 + vec3(seconds * 0.02)));
    float heat = 0.75 + 0.35 * granules - 0.55 * spots;
    // white-hot in the middle, orange towards the rim
    float limb = pow(n.z, 0.45);
    vec3 col = mix(vec3(1.0, 0.42, 0.05), vec3(1.0, 0.95, 0.75), limb) * heat * (0.75 + 0.45 * limb);
    fragColor = vec4(min(col, vec3(1.0)) * bright + vec3(0.25, 0.12, 0.0) * (1.0 - limb) * bright, 1.0);
}
