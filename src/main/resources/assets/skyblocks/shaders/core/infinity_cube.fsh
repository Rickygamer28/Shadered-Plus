#version 150

// An infinity cube: six half mirrors with LED strips along the twelve edges. Light bounces between every pair of
// facing walls, so the box is seen again and again in every direction: its edges make a glowing grid that goes on
// forever. Mirror images of a box fill space with copies of it, so the line of sight from the eye is simply followed
// straight on through that endless grid of boxes, wall to wall; the further it goes, the more reflections (dimmer),
// and where it passes close to a strip, its LEDs light the pixel. The boxes are exactly the blocks, so the grid lines
// up with them; each strip sits just inside the bars, so in the reflections they come in pairs along every wall.

uniform vec3 Eye;        // the eye (relative to the camera position)
uniform vec3 BoxMin;     // the cube's lowest corner
uniform vec3 BoxSize;    // its size, blocks
uniform vec3 ColorFront; // the LEDs' colour up front...
uniform vec3 ColorDeep;  // ...and after many reflections
uniform float Rainbow;   // 1: every colour
uniform float Time;      // seconds
uniform float PixelSize; // how wide one pixel is at one block away

in vec3 facePos;

out vec4 fragColor;

const float FRAME = 0.06;      // the black bars along the edges
const float INSET = 0.072;     // the LED strips: on the bars' inner side, this far in from both walls
const float LED_GAP = 0.0625;  // between two LEDs on a strip
const float LED_SIZE = 0.013;  // an LED's radius
const float KEEP = 0.84;       // light left after each reflection
const int STEPS = 40;          // walls followed at most

vec3 hue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

// A soft bump at distance x from its middle (r wide), repeating every period; fw: one pixel's width there. Widened to
// at least a pixel (same light), so a thin line or a small LED never flickers between one pixel and none; once about
// as wide as the period it becomes the average glow (no sparkle far away).
float bump(float x, float r, float period, float fw) {
    float wide = sqrt(r * r + fw * fw);
    float average = 1.7725 * r / period;
    float blend = smoothstep(0.18, 0.45, wide / period);
    return mix(r / wide * exp(-x * x / (wide * wide)), average, blend);
}

vec2 others(vec3 v, int axis) {
    return axis == 0 ? v.yz : (axis == 1 ? v.xz : v.xy);
}

vec3 ledColor(vec3 at, float reflections) {
    if (Rainbow > 0.5) {
        // colours flowing through the grid, like an addressable LED strip running a rainbow
        return hue(fract(dot(at, vec3(0.23, 0.31, 0.17)) + reflections * 0.05 - Time * 0.45));
    }
    return mix(ColorFront, ColorDeep, 1.0 - exp(-reflections * 0.12));
}

// The strips near a wall crossed at `at` (wall across `axis`, at the whole-box coordinate A): the ones on side `side`
// of it (+1 / -1: towards bigger / smaller coordinates), for both directions the strips run in. Light of their LEDs
// (x) and glow (y) for the line of sight passing there; distances are the real 3D distance to each strip.
vec2 strips(int axis, vec3 at, vec3 sight, float side, vec3 size, float fw) {
    float A = size[axis] * floor(at[axis] / size[axis] + 0.5);
    float wa = A - (A + side * INSET);                      // (from the strip to the wall, across it)
    vec2 c = others(at, axis);
    vec2 period = others(size, axis);
    vec2 offW = c - period * floor(c / period + 0.5);       // from the nearest wall, each way, on this wall
    vec2 strip = sign(offW + 1e-6) * INSET;                 // the nearest strip each way
    vec2 w = offW - strip;
    vec2 gap = period / max(vec2(1.0), floor(period / LED_GAP + 0.5));
    float da = sight[axis];
    vec2 db = others(sight, axis);
    vec2 result = vec2(0.0);
    for (int k = 0; k < 2; k++) {
        // k = 0: strips running along the wall's second axis (lying at a whole first coordinate), k = 1: the other way
        float wb = k == 0 ? w.x : w.y, d1 = k == 0 ? db.x : db.y, d2 = k == 0 ? db.y : db.x;
        float s = max(length(vec2(da, d1)), 1e-4);
        float dist = abs(wa * d1 - wb * da) / s;
        float along = (k == 0 ? c.y : c.x) - d2 * (wa * da + wb * d1) / (s * s);
        float g = k == 0 ? gap.y : gap.x;
        float spacing = (k == 0 ? period.x : period.y) * max(abs(da) / s, 0.3);
        float fwAlong = fw / max(s, 0.05);
        float x = (fract(along / g) - 0.5) * g;
        result.x += bump(dist, LED_SIZE, spacing, fw) * bump(x, LED_SIZE, g, fwAlong);
        result.y += bump(dist, LED_SIZE * 3.0, spacing, fw) * bump(x, LED_SIZE * 3.0, g, fwAlong);
    }
    return result;
}

void main() {
    vec3 sight = normalize(facePos - Eye);
    vec3 p = facePos - BoxMin;
    float eyeDistance = length(facePos - Eye);
    float pixel = PixelSize * eyeDistance;
    vec3 base = Rainbow > 0.5 ? hue(fract(Time * 0.2)) : ColorFront;

    // the face this pixel is on (the axis it's flat across), and how far from that face's border
    vec3 toSide = min(p, BoxSize - p);
    int face = toSide.x < toSide.y ? (toSide.x < toSide.z ? 0 : 2) : (toSide.y < toSide.z ? 1 : 2);
    vec2 across = others(toSide, face);
    float border = min(across.x, across.y);
    if (border < FRAME) {
        // the black bars: a little metal shine, lit by the strips right behind their inner edge
        vec3 col = vec3(0.04, 0.04, 0.05) + vec3(0.03) * smoothstep(0.0, FRAME, border);
        col += base * 0.35 * exp((border - FRAME) * 90.0);
        fragColor = vec4(col, 1.0);
        return;
    }

    vec3 size = BoxSize;
    float perBlock = dot(abs(sight), 1.0 / size);           // walls passed per block travelled (on average)
    vec3 lit = vec3(0.0);
    // the strips just behind the face looked through (the real ones, along this face's edges)
    {
        vec2 l = strips(face, p, sight, sign(sight[face]), size, pixel);
        float sharp = LED_SIZE * LED_SIZE / (LED_SIZE * LED_SIZE + pixel * pixel);
        lit += ledColor(p, 0.0) * (l.x * 2.4 + l.y * 0.12) * 0.6 + vec3(l.x * 0.9 * sharp) * 0.6;
    }
    // then on through the box and its endless mirror images (a hair inside the face, so the face isn't a wall again)
    vec3 q = p + sight * 1e-4;
    vec3 inv = 1.0 / max(abs(sight), vec3(1e-5));
    vec3 cell = floor(q / size);
    vec3 next = (cell + step(0.0, sight)) * size;           // the next wall on each axis
    vec3 tNext = abs(next - q) * inv;
    vec3 tStep = size * inv;
    for (int i = 0; i < STEPS; i++) {
        int axis = tNext.x < tNext.y ? (tNext.x < tNext.z ? 0 : 2) : (tNext.y < tNext.z ? 1 : 2);
        float t = tNext[axis];
        tNext[axis] += tStep[axis];
        // how many reflections deep: smooth with the distance (counting walls one by one jumped where two walls are
        // crossed at nearly the same spot, and those spots sparkled as you moved)
        float reflections = t * perBlock;
        float light = pow(KEEP, reflections) * (0.86 + 0.14 * sin(Time * 2.5 - reflections * 0.5));
        if (light < 0.004) {
            break;
        }
        vec3 at = q + sight * t;
        float fw = PixelSize * (eyeDistance + t);
        // the strips on both sides of this wall
        vec2 l = strips(axis, at, sight, 1.0, size, fw) + strips(axis, at, sight, -1.0, size, fw);
        // (an LED's hot white middle only while it's bigger than a pixel: blurred together they'd turn the grid grey)
        float sharp = LED_SIZE * LED_SIZE / (LED_SIZE * LED_SIZE + fw * fw);
        lit += (ledColor(at, reflections) * (l.x * 2.4 + l.y * 0.12 + 0.006) + vec3(l.x * 0.9 * sharp)) * 0.6 * light;
    }
    vec3 col = 1.0 - exp(-lit * 1.5);
    float fresnel = pow(1.0 - abs(sight[face]), 4.0);
    col += vec3(0.04, 0.045, 0.06) * fresnel;                // the glass
    fragColor = vec4(col, 1.0);
}
