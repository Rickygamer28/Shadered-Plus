#version 150

// The ferrofluid: glossy black. Nearly no colour of its own - what you see is the room reflected in it (darker below,
// the light above), strongest where you see it at a grazing angle, and sharp glints off its spikes' sides.

in vec3 worldPos;
in vec3 worldNormal;
in float lit;

out vec4 fragColor;

void main() {
    vec3 n = normalize(worldNormal);
    vec3 v = normalize(-worldPos);
    if (dot(n, v) < 0.0) {
        n = -n;
    }
    vec3 r = reflect(-v, n);
    vec3 room = mix(vec3(0.025, 0.025, 0.03), vec3(0.46, 0.5, 0.58), smoothstep(-0.15, 0.85, r.y));
    float fresnel = 0.05 + 0.95 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
    vec3 key = normalize(vec3(0.35, 1.0, 0.25));
    vec3 fill = normalize(vec3(-0.6, 0.45, -0.5));
    float glint = pow(max(dot(r, key), 0.0), 90.0) * 1.3 + pow(max(dot(r, fill), 0.0), 36.0) * 0.3;
    vec3 c = vec3(0.012) + room * (0.08 + 0.85 * fresnel) + vec3(glint);
    c *= mix(0.3, 1.0, lit);
    fragColor = vec4(c, 1.0);
}
