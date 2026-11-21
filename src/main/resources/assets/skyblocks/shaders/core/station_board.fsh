#version 150

// The board: faint light filling it, scanlines rolling down it, a bright frame, now and then a flicker.

uniform float GameTime;

in vec4 col;
in vec2 uv;

out vec4 fragColor;

void main() {
    float T = GameTime * 1200.0;
    vec2 e = min(uv, 1.0 - uv);
    float frame = smoothstep(0.035, 0.0, min(e.x * 0.35, e.y));
    float scan = 0.65 + 0.35 * sin(uv.y * 90.0 + T * 6.0);
    float roll = smoothstep(0.08, 0.0, abs(fract(uv.y - T * 0.25) - 0.5)) * 0.4;
    float flick = 0.92 + 0.08 * sin(T * 37.0) * step(0.97, fract(sin(floor(T * 3.0)) * 43758.5));
    float fill = 0.18 * scan + roll * 0.3;
    float a = clamp((fill + frame) * flick, 0.0, 1.0);
    fragColor = vec4(col.rgb * (0.6 + frame * 0.8 + roll), a * 0.85);
}
