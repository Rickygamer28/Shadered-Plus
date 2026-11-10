#version 150

// Shadered+'s teleport pad beam: rings of light rising up a column, streaks running up it, fading out towards the top
// and bright at the bottom, where it meets the pad. Added onto what's behind it.

uniform float GameTime;

in vec2 uv;
in vec4 col;

out vec4 fragColor;

void main() {
    float t = GameTime * 1200.0;
    float h = uv.y;
    float rings = smoothstep(0.30, 0.5, abs(fract(h * 5.0 - t * 1.6) - 0.5));
    float streak = pow(0.5 + 0.5 * sin(uv.x * 6.2831 * 9.0 + h * 3.0 - t * 2.0), 6.0);
    float fade = (1.0 - h) * (1.0 - h);
    float base = smoothstep(0.25, 0.0, h);
    float a = (0.25 + 0.6 * rings + 0.5 * streak) * fade + base * 0.8;
    vec3 c = mix(col.rgb, vec3(1.0), 0.25 * rings + 0.5 * base);
    fragColor = vec4(c * a * col.a, 1.0);
}
