#version 150

// Shadered+'s stage spotlight: a broad soft cone of light (UV0.x 0-1 along it, UV0.y -1..1 across), brightest at the
// lamp; the pool of light where it lands (UV0.x 2-4) - added to what's behind.

in vec2 uv;
in vec4 col;

out vec4 fragColor;

void main() {
    float a;
    if (uv.x > 1.5) {
        vec2 q = vec2(uv.x - 3.0, uv.y);
        float r = length(q);
        a = smoothstep(1.0, 0.55, r) * 0.75 + exp(-r * r * 4.0) * 0.35;
    } else {
        float v = uv.y;
        a = exp(-v * v * 2.2) * 0.5 * (1.0 - uv.x * 0.55) * smoothstep(0.0, 0.04, uv.x);
        a += exp(-v * v * 30.0) * 0.12 * (1.0 - uv.x); // (a brighter middle)
    }
    fragColor = vec4(col.rgb * a * col.a, 1.0);
}
