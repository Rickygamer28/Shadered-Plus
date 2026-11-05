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
// x: how many times its pictures go round it (6 alone; more round a tank's big funnel, so they're about a block across);
// y: how dark its throat (1 alone, a hole; 0 in a tank, where it pours on down as a spout - s past 1: how far down that)
uniform vec4 Shape;

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

// one band's fluid at this point of its picture (wrapped inside its rectangle), lit - its mipmap level chosen from how
// fast the unwrapped picture moves across the screen (dx, dy: worked out where every pixel does it), not the wrapped
// one (no line of blurred texture where it wraps)
vec3 fluid(int i, vec2 local, vec2 dx, vec2 dy) {
    vec4 r = rectOf(i);
    vec2 size = r.zw - r.xy;
    vec4 c = textureGrad(Sampler0, r.xy + size * fract(local), dx * size, dy * size);
    vec4 t = tintOf(i);
    return c.rgb * t.rgb * mix(lightColor.rgb, vec3(1.0), t.a);
}

// which band this pixel is in, and how much of each neighbour is mixed in
int band;
float wPrev;
float wNext;

vec3 mixed(vec2 local) {
    vec2 dx = dFdx(local), dy = dFdy(local);
    vec3 col = fluid(band, local, dx, dy);
    // (a neighbour's picture only looked up where it's mixed in)
    if (wPrev > 0.002) {
        col = mix(col, fluid(band - 1, local, dx, dy), wPrev * 0.5);
    }
    if (wNext > 0.002) {
        col = mix(col, fluid(band + 1, local, dx, dy), wNext * 0.5);
    }
    return col;
}

// (in a tank: how long each of the two pictures flows before fading out for the other, starting again)
const float PERIOD = 2.0;

void main() {
    float a = texCoord0.x * TAU;
    float s = texCoord0.y;
    float r = max(1.0 - min(s, 1.0) * 0.93, 0.07);
    float inward = log(1.0 / r) + max(s - 1.0, 0.0); // (0 at its rim, growing fast towards its throat, on down a spout)
    float k = Shape.x / 6.0;
    // in a tank, it turns like water does: slowly out at the walls, faster and faster further in (alone: as one)
    bool flowing = Shape.z > 0.5;
    float spin = flowing ? 0.12 + 0.9 * pow(clamp(inward + 0.66, 0.0, 3.3), 1.3) : 1.1;
    // the spiral's pattern (its arms, where its bands meet, its foam): turning as one, twisted a fixed amount further in
    float swirl = a + Time * (flowing ? 0.6 : 1.1) + 2.2 * inward;
    // which band: stirred along the spiral where they meet
    int count = int(EdgesB.z + 0.5);
    // (made of whole turns of the spiral: no seam where the funnel's way round starts again)
    float stir = (sin(swirl * 3.0 + inward * 5.0 - Time * 0.9) * 0.55 + sin(swirl * 5.0 - inward * 3.0 + Time * 0.6) * 0.3
            + (noise(vec2(cos(swirl), sin(swirl)) * 2.5 + vec2(inward * 3.0 - Time * 0.45)) - 0.5) * 0.6) * 0.09;
    float sb = clamp(s + stir, 0.0, 0.999);
    band = 0;
    for (int i = 0; i < 5; i++) {
        if (i < count - 1 && sb > edgeOf(i)) {
            band = i + 1;
        }
    }
    // soft into the neighbours on either side
    wPrev = band > 0 ? 1.0 - smoothstep(0.0, 0.06, sb - edgeOf(band - 1)) : 0.0;
    wNext = band < count - 1 ? 1.0 - smoothstep(0.0, 0.06, edgeOf(band) - sb) : 0.0;
    // the fluids' pictures flowing round and in along it. Alone: turning as one. In a tank, each part at its own speed -
    // which would wind the picture tighter and tighter for ever, so two copies of it take turns: each flows for a while
    // from where it started, fading in and out again, while the other (half a turn behind) takes over
    vec3 col = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float t = Time, w = 1.0;
        if (flowing) {
            float ph = fract(Time / PERIOD + 0.5 * float(i));
            w = 1.0 - abs(2.0 * ph - 1.0);
            t = ph * PERIOD;
        } else if (i > 0) {
            break;
        }
        // (the second copy shifted, round and along: the two never the same picture)
        float sw = a + spin * t + 2.2 * inward + 2.4 * float(i);
        col += w * mixed(vec2(sw / TAU * Shape.x, (inward * 2.2 - 0.7 * t) * k + 0.5 * float(i)));
    }
    // lighter arms winding down it, and dark in its throat
    col *= 0.88 + 0.22 * max(0.0, sin(3.0 * swirl));
    col *= mix(1.0, 0.2, smoothstep(0.82, 1.0, s) * Shape.y);
    // foam where the surface pours over the funnel's lip (s round 0: its rim, a splash ring), white, churning
    float churn = noise(vec2(cos(swirl), sin(swirl)) * 6.0 + vec2(Time * 1.7, -Time * 1.3));
    float foam = smoothstep(0.09, 0.0, abs(s)) * smoothstep(0.3, 0.7, churn + 0.25);
    col = mix(col, mix(vec3(1.0), tintOf(0).rgb, 0.2) * mix(lightColor.rgb, vec3(1.0), tintOf(0).a), foam * 0.85);
    fragColor = vec4(col * vertexColor.rgb, vertexColor.a);
}
