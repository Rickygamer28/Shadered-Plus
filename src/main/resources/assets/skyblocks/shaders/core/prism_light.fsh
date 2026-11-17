#version 150

// The prism's light (prism_light.vsh): a rainbow - red at one end to violet at the other, soft at its edges, brightest
// in its middle colours - or a faint ray of the colours through the air. Added onto what's there.

in vec2 uv;
in vec4 tint;

out vec4 fragColor;

vec3 spectrum(float h) {
    // red .. orange, yellow, green, cyan, blue .. violet
    vec3 c = clamp(vec3(abs(h * 6.0 - 3.0) - 1.0, 2.0 - abs(h * 6.0 - 2.0), 2.0 - abs(h * 6.0 - 4.0)), 0.0, 1.0);
    c = mix(c, vec3(1.0), 0.08);
    return c * (1.0 - 0.35 * smoothstep(0.82, 1.0, h)); // (violet the dimmest, as it is)
}

void main() {
    float h, shape;
    if (uv.x > 1.5) {
        float along = uv.x - 2.0;
        h = clamp(uv.y * 0.5 + 0.5, 0.0, 1.0) * 0.85;
        shape = (1.0 - abs(uv.y)) * smoothstep(0.0, 0.15, along) * (1.0 - along * 0.6);
    } else {
        h = clamp(uv.x * 0.5 + 0.5, 0.0, 1.0) * 0.85;
        float ends = 1.0 - smoothstep(0.7, 1.0, abs(uv.x));
        shape = ends * exp(-uv.y * uv.y * 3.5) * (1.0 - smoothstep(0.85, 1.0, abs(uv.y)));
    }
    vec3 c = spectrum(h);
    // (a coloured light: its rainbow leans to its colour)
    c = mix(c, c * tint.rgb * 1.6, 1.0 - step(2.95, tint.r + tint.g + tint.b));
    float a = clamp(shape * tint.a, 0.0, 1.0);
    if (a < 0.003) discard;
    fragColor = vec4(c, a);
}
