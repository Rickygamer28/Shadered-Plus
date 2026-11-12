#version 150

// The crystal: its colour deepening towards the middle of each facet and brightening at the edges you see it side-on
// (a glassy rim), its hue sliding a little with the angle, light moving slowly up inside it, and now and then a sparkle.

in vec4 vColor;
in vec3 vNormal;
in vec3 vView;
in vec3 vPos;

uniform float GameTime;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

vec3 hueShift(vec3 c, float a) {
    const vec3 k = vec3(0.57735);
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}

void main() {
    float t = GameTime * 1200.0;
    vec3 n = normalize(vNormal);
    vec3 v = normalize(-vView);
    float facing = clamp(abs(dot(n, v)), 0.0, 1.0);
    float rim = pow(1.0 - facing, 2.2);
    vec3 base = vColor.rgb;
    vec3 col = hueShift(base, (1.0 - facing) * 0.9 - 0.25) * (0.55 + 0.45 * facing);
    // light moving up inside
    float glow = 0.5 + 0.5 * sin(vPos.y * 0.9 - t * 1.6 + vPos.x * 0.4);
    col += base * glow * 0.35;
    col += mix(base, vec3(1.0), 0.6) * rim * 1.1;
    // sparkles: a few cells twinkling at a time
    vec3 cell = floor(vPos * 6.0);
    float h = hash(cell);
    float tw = pow(max(0.0, sin(t * (2.0 + 3.0 * h) + h * 40.0)), 24.0);
    col += vec3(1.0) * tw * step(0.86, h) * 1.4;
    fragColor = vec4(col, 0.82 + rim * 0.18);
}
