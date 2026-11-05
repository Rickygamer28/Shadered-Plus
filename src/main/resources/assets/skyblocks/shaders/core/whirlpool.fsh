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
// Alone: x 6 (how many times its pictures go round it), y 1 (its throat dark), z 0. In a tank: z 1 - and its pieces carry
// more (see tank()): x how many times its pictures go round it at the most (out at its walls), w how far out the
// furthest of it is (to read the colour's blue as how far out it is)
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
// fast the unwrapped picture moves across the screen (dx, dy), not the wrapped one (no line of blurred texture where it
// wraps)
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

vec3 mixed(vec2 local, vec2 dx, vec2 dy) {
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

// which band at sb (0 its rim .. 1 its throat), stirred: and its neighbours' share
void pickBand(float sb) {
    int count = int(EdgesB.z + 0.5);
    band = 0;
    for (int i = 0; i < 5; i++) {
        if (i < count - 1 && sb > edgeOf(i)) {
            band = i + 1;
        }
    }
    // soft into the neighbours on either side
    wPrev = band > 0 ? 1.0 - smoothstep(0.0, 0.06, sb - edgeOf(band - 1)) : 0.0;
    wNext = band < count - 1 ? 1.0 - smoothstep(0.0, 0.06, edgeOf(band) - sb) : 0.0;
}

// how the bands are stirred into each other where they meet (made of whole turns of the spiral: no seam where the way
// round starts again)
float stirAt(float swirl, float inward) {
    return (sin(swirl * 3.0 + inward * 5.0 - Time * 0.9) * 0.55 + sin(swirl * 5.0 - inward * 3.0 + Time * 0.6) * 0.3
            + (noise(vec2(cos(swirl), sin(swirl)) * 2.5 + vec2(inward * 3.0 - Time * 0.45)) - 0.5) * 0.6) * 0.09;
}

vec3 foamColour() {
    return mix(vec3(1.0), tintOf(0).rgb, 0.2) * mix(lightColor.rgb, vec3(1.0), tintOf(0).a);
}

// Alone: the funnel turning as one. UV0: x round it (0..1), y down it (0 its rim .. 1 its throat); colour: how dark.
void alone() {
    float a = texCoord0.x * TAU;
    float s = texCoord0.y;
    float r = max(1.0 - s * 0.93, 0.07);
    float inward = log(1.0 / r); // (0 at its rim, growing fast towards its throat)
    // the spiral: turning as one, twisted a fixed amount further in; the picture sliding in along it
    float swirl = a + Time * 1.1 + 2.2 * inward;
    vec2 local = vec2(swirl / TAU * 6.0, inward * 2.2 - Time * 0.7);
    pickBand(clamp(s + stirAt(swirl, inward), 0.0, 0.999));
    vec3 col = mixed(local, dFdx(local), dFdy(local));
    // lighter arms winding down it, and dark in its throat
    col *= 0.88 + 0.22 * max(0.0, sin(3.0 * swirl));
    col *= mix(1.0, 0.2, smoothstep(0.82, 1.0, s));
    // foam where it pours over its rim, white, churning
    float churn = noise(vec2(cos(swirl), sin(swirl)) * 6.0 + vec2(Time * 1.7, -Time * 1.3));
    float foam = smoothstep(0.09, 0.0, abs(s)) * smoothstep(0.3, 0.7, churn + 0.25);
    col = mix(col, foamColour(), foam * 0.85);
    fragColor = vec4(col * vertexColor.rgb, vertexColor.a);
}

// In a tank: the pool's surface, the funnel, the spout and its crown of foam, all one picture. UV0: x round it (0..1),
// y how far in along it, in steps that keep its pictures square everywhere (on the surface, log(lip / how far out);
// down the funnel and spout, each bit of the way over how far out it is). Colour: red how light, green which band
// ((s + 1) / 3.2: s -1 at the walls, 0 the lip, 1 the throat and on down; 2 and up the crown of foam, s - 2 from
// its inner edge), blue how far out (of Shape.w), alpha how see-through.
const float PERIOD = 1.5;

void tank() {
    float a = texCoord0.x * TAU;
    float xi = texCoord0.y;
    float shade = vertexColor.r;
    float sRaw = vertexColor.g * 3.2 - 1.0;
    float R = max(vertexColor.b * Shape.w, 0.05);
    bool crown = sRaw > 1.5;
    float s = crown ? 1.0 : sRaw;
    // the pattern (its arms, where its bands meet, its foam): turning as one, slowly, wound into a spiral
    float swirl = a + Time * 0.5 + 1.4 * xi;
    pickBand(clamp(s + stirAt(swirl, xi * 0.6), 0.0, 0.999));
    // it turns like water: slow out at the walls, faster and faster in, and flows in (down the spout: down it)
    float spin = min(5.0, 0.45 * pow(2.5 / R, 1.5));
    float slide = min(3.0, 0.3 + 0.5 / R);
    // its pictures about a block across wherever they are: as many times round as fit (halving, inwards, as it
    // narrows - each step blended into the next)
    float level = clamp(log2(Shape.x / max(TAU * R, 1.0)), 0.0, log2(Shape.x) - 1.0);
    float l0 = floor(level), lw = smoothstep(0.25, 0.75, level - l0);
    vec3 col = vec3(0.0);
    // each part at its own speed - which would wind the picture tighter and tighter for ever, so two copies of it take
    // turns: each flows a while from where it started, fading in and out again, while the other takes over
    for (int i = 0; i < 2; i++) {
        float ph = fract(Time / PERIOD + 0.5 * float(i));
        float w = 1.0 - abs(2.0 * ph - 1.0);
        float t = ph * PERIOD;
        float ai = a + spin * t + 2.4 * float(i);
        float xii = xi - slide * t + 0.37 * float(i);
        vec2 da = vec2(dFdx(ai), dFdy(ai)), dxi = vec2(dFdx(xii), dFdy(xii));
        for (int j = 0; j < 2; j++) {
            float lv = l0 + float(j);
            float n = Shape.x / exp2(min(lv, log2(Shape.x) - 1.0));
            float m = floor(n * 0.5); // (its pictures' rows leaning along the spiral; whole numbers: no seam)
            // (round and in, turned and scaled alike: its pictures square)
            vec2 local = vec2(n * ai - m * xii, m * ai + n * xii) / TAU;
            vec2 dx = vec2(n * da.x - m * dxi.x, m * da.x + n * dxi.x) / TAU;
            vec2 dy = vec2(n * da.y - m * dxi.y, m * da.y + n * dxi.y) / TAU;
            col += w * (j == 0 ? 1.0 - lw : lw) * mixed(local, dx, dy);
        }
    }
    // lighter arms winding in
    col *= 0.9 + 0.18 * max(0.0, sin(3.0 * swirl));
    // foam: where the surface pours over the funnel's lip, and the crown round the spout's foot
    float churn = noise(vec2(cos(swirl), sin(swirl)) * 7.0 + vec2(xi * 2.0 + Time * 1.7, -Time * 1.3));
    float foam = crown ? smoothstep(0.13, 0.02, sRaw - 2.0) * smoothstep(0.1, 0.5, churn + 0.35)
            : smoothstep(0.09, 0.0, abs(s)) * smoothstep(0.3, 0.7, churn + 0.25);
    col = mix(col * shade, foamColour(), foam * 0.85);
    fragColor = vec4(col, vertexColor.a);
}

void main() {
    if (Shape.z > 0.5) {
        tank();
    } else {
        alone();
    }
}
