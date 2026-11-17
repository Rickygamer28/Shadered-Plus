#version 150

// The prism's light (prism_light.vsh): the colours of the spectrum, red through orange, yellow, green, cyan and blue
// to violet, soft across a strip; or the white light going in. Added onto what's there (blended ONE, ONE).

in vec2 uv;
in vec4 tint;

out vec4 fragColor;

vec3 spectrum(float h) {
    h = clamp(h, 0.0, 1.0) * 0.83;
    vec3 c = clamp(vec3(abs(h * 6.0 - 3.0) - 1.0, 2.0 - abs(h * 6.0 - 2.0), 2.0 - abs(h * 6.0 - 4.0)), 0.0, 1.0);
    return c * (1.0 - 0.3 * smoothstep(0.7, 0.83, h)); // (violet the dimmest, as it is)
}

void main() {
    vec3 c = uv.x < -0.5 ? vec3(1.0) : spectrum(uv.x);
    // (a coloured light: its rainbow leans to its colour)
    c = mix(c, c * tint.rgb * 1.6, 1.0 - step(2.95, tint.r + tint.g + tint.b));
    float across = abs(uv.y);
    float shape = exp(-across * across * 2.5) * (1.0 - smoothstep(0.85, 1.0, across));
    float a = clamp(shape * tint.a, 0.0, 1.0);
    if (a < 0.002) discard;
    // (added as light, blended ONE, ONE, its alpha next to nothing - as spin_aura.fsh)
    fragColor = vec4(c * a, 1.0 / 255.0);
}
