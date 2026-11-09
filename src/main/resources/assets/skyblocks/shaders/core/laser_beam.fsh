#version 150

// Shadered+'s party laser: a thin white-hot core in a coloured glow, added to what's behind; the spot where it lands
// a small bright dot with a halo.

in vec2 uv;
in vec4 col;

out vec4 fragColor;

void main() {
    float a;
    if (uv.x > 1.5) {
        vec2 q = vec2(uv.x - 3.0, uv.y);
        float r2 = dot(q, q);
        a = exp(-r2 * 30.0) * 1.6 + exp(-r2 * 5.0) * 0.5;
    } else {
        float v = uv.y;
        a = exp(-v * v * 60.0) * 1.4 + exp(-v * v * 5.0) * 0.35;
        a *= smoothstep(0.0, 0.03, uv.x); // (from the lens)
    }
    vec3 c = mix(col.rgb, vec3(1.0), clamp(a - 1.0, 0.0, 0.6)); // (white at the very middle)
    fragColor = vec4(c * a * col.a, 1.0);
}
