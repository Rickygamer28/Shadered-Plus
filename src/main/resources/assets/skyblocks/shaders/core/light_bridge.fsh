#version 150

// Shadered+'s light bridge: see-through hard light - bright edges, arrows of light running along it, a faint hexagon
// shimmer; rainbow along its length (Rainbow 1) or its one colour.

in vec2 uv;
in float shade;

uniform float Time;
uniform float Length;
uniform float Rainbow;
uniform vec3 Colour;

out vec4 fragColor;

vec3 hue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

float hexEdge(vec2 p) {
    p *= vec2(1.0, 1.1547);
    vec2 a = mod(p, vec2(1.0, 1.732)) - vec2(0.5, 0.866);
    vec2 b = mod(p + vec2(0.5, 0.866), vec2(1.0, 1.732)) - vec2(0.5, 0.866);
    vec2 g = dot(a, a) < dot(b, b) ? a : b;
    g = abs(g);
    float d = max(dot(g, normalize(vec2(1.0, 1.732))), g.x);
    return smoothstep(0.42, 0.5, d);
}

void main() {
    float along = uv.x, across = uv.y;
    vec3 c = Rainbow > 0.5 ? mix(hue(fract(along * 0.06 - Time * 0.08)), vec3(1.0), 0.15) : Colour;
    float edge = smoothstep(0.12, 0.0, across) + smoothstep(0.88, 1.0, across);
    float chevron = fract(along * 1.2 - Time * 1.4 - abs(across - 0.5) * 1.4);
    float arrows = smoothstep(0.0, 0.12, chevron) * smoothstep(0.35, 0.12, chevron);
    float hex = hexEdge(vec2(along, across) * 5.0 + vec2(Time * 0.2, 0.0)) * (0.5 + 0.5 * sin(Time * 2.0 + along));
    // fading in at the ends, from each emitter
    float ends = smoothstep(0.0, 0.4, along - 1.0) * smoothstep(0.0, 0.4, Length - along);
    float a = (0.22 + 0.55 * edge + 0.35 * arrows + 0.12 * hex) * mix(0.6, 1.0, ends);
    vec3 col = c * (0.6 + 0.9 * edge + 0.6 * arrows + 0.3 * hex) * shade;
    fragColor = vec4(col, clamp(a, 0.0, 0.95));
}
