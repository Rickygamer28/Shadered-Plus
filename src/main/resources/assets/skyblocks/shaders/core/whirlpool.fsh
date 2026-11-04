#version 150

// Shadered+'s whirlpool (our own code): each fluid it holds a band of the funnel, its own picture (its still texture,
// from the block atlas) flowing round and down it in a spiral - turning as one, sliding in towards the throat, faster
// further in - and where two bands meet, stirred into each other in swirls rather than a hard line.

uniform sampler2D Sampler0;
uniform float Time;
// each band's picture (its rectangle in the atlas: u0, v0, u1, v1) and tint (a: it glows)
uniform vec4 Rect0;
uniform vec4 Rect1;
uniform vec4 Rect2;
uniform vec4 Rect3;
uniform vec4 Rect4;
uniform vec4 Rect5;
uniform vec4 Tint0;
uniform vec4 Tint1;
uniform vec4 Tint2;
uniform vec4 Tint3;
uniform vec4 Tint4;
uniform vec4 Tint5;
// where each band ends, down the funnel (0..1); EdgesB.z: how many bands
uniform vec4 EdgesA;
uniform vec4 EdgesB;

in vec2 texCoord0;
in vec4 vertexColor;
in vec4 lightColor;

out vec4 fragColor;

const float TAU = 6.2831853;

vec4 rectOf(int i) {
    return i == 0 ? Rect0 : i == 1 ? Rect1 : i == 2 ? Rect2 : i == 3 ? Rect3 : i == 4 ? Rect4 : Rect5;
}

vec4 tintOf(int i) {
    return i == 0 ? Tint0 : i == 1 ? Tint1 : i == 2 ? Tint2 : i == 3 ? Tint3 : i == 4 ? Tint4 : Tint5;
}

float edgeOf(int i) {
    return i == 0 ? EdgesA.x : i == 1 ? EdgesA.y : i == 2 ? EdgesA.z : i == 3 ? EdgesA.w : i == 4 ? EdgesB.x : EdgesB.y;
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// one band's fluid at this point of its picture (wrapped inside its rectangle), lit
vec3 fluid(int i, vec2 local) {
    vec4 r = rectOf(i);
    vec4 c = texture(Sampler0, mix(r.xy, r.zw, fract(local)));
    vec4 t = tintOf(i);
    return c.rgb * t.rgb * mix(lightColor.rgb, vec3(1.0), t.a);
}

void main() {
    float a = texCoord0.x * TAU;
    float s = texCoord0.y;
    float r = max(1.0 - s * 0.93, 0.07);
    float inward = log(1.0 / r); // (0 at its rim, growing fast towards its throat)
    // the spiral: turning as one, twisted a fixed amount further in; the picture sliding in along it
    float swirl = a + Time * 1.1 + 2.2 * inward;
    vec2 local = vec2(swirl / TAU * 6.0, inward * 2.2 - Time * 0.7);
    // which band: stirred along the spiral where they meet
    int count = int(EdgesB.z + 0.5);
    // (made of whole turns of the spiral: no seam where the funnel's way round starts again)
    float stir = (sin(swirl * 3.0 + inward * 5.0 - Time * 0.9) * 0.55 + sin(swirl * 5.0 - inward * 3.0 + Time * 0.6) * 0.3
            + (noise(vec2(cos(swirl), sin(swirl)) * 2.5 + vec2(inward * 3.0 - Time * 0.45)) - 0.5) * 0.6) * 0.09;
    float sb = clamp(s + stir, 0.0, 0.999);
    int band = 0;
    for (int i = 0; i < 5; i++) {
        if (i < count - 1 && sb > edgeOf(i)) {
            band = i + 1;
        }
    }
    vec3 col = fluid(band, local);
    // soft into the neighbours on either side
    // (a neighbour's picture only looked up where it's mixed in)
    if (band > 0) {
        float w = 1.0 - smoothstep(0.0, 0.06, sb - edgeOf(band - 1));
        if (w > 0.002) {
            col = mix(col, fluid(band - 1, local), w * 0.5);
        }
    }
    if (band < count - 1) {
        float w = 1.0 - smoothstep(0.0, 0.06, edgeOf(band) - sb);
        if (w > 0.002) {
            col = mix(col, fluid(band + 1, local), w * 0.5);
        }
    }
    // lighter arms winding down it, and dark in its throat
    col *= 0.88 + 0.22 * max(0.0, sin(3.0 * swirl));
    col *= mix(1.0, 0.2, smoothstep(0.82, 1.0, s));
    // foam where the surface pours over the funnel's lip (s round 0: its rim, a splash ring), white, churning
    float churn = noise(vec2(cos(swirl), sin(swirl)) * 6.0 + vec2(Time * 1.7, -Time * 1.3));
    float foam = smoothstep(0.09, 0.0, abs(s)) * smoothstep(0.3, 0.7, churn + 0.25);
    col = mix(col, mix(vec3(1.0), tintOf(0).rgb, 0.2) * mix(lightColor.rgb, vec3(1.0), tintOf(0).a), foam * 0.85);
    fragColor = vec4(col * vertexColor.rgb, vertexColor.a);
}
