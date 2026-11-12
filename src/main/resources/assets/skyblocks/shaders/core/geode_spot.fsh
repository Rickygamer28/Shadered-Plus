#version 150

// A spot of light thrown by the crystals: soft round edges, inside it the bright wavering lines light makes through
// cut glass, and a few glints - added onto what's there.

in vec2 corner;
in vec4 vColor;
flat in float seed;

uniform float GameTime;

out vec4 fragColor;

void main() {
    float t = GameTime * 1200.0;
    float r = length(corner);
    if (r > 1.0) {
        discard;
    }
    float soft = smoothstep(1.0, 0.25, r);
    vec2 p = corner * 3.0;
    float a = seed * 2.399;
    float lines = 0.0;
    for (int i = 0; i < 3; i++) {
        float ang = a + float(i) * 2.094;
        vec2 d = vec2(cos(ang), sin(ang));
        float w = sin(dot(p, d) * 3.1 + t * (0.7 + 0.2 * float(i)) + seed);
        lines += pow(1.0 - abs(w), 6.0);
    }
    float glint = pow(max(0.0, sin(t * 2.3 + seed * 5.1)), 30.0) * smoothstep(0.35, 0.0, r);
    float k = soft * (0.35 + 0.55 * lines) + glint * 0.8;
    float flicker = 0.85 + 0.15 * sin(t * 1.3 + seed * 1.7);
    fragColor = vec4(vColor.rgb * flicker, min(1.0, k * vColor.a * 0.8)); // (added on: colour times alpha)
}
