#version 150

// An infinity cube: six half mirrors with LED strips along the twelve edges. Light bounces between every pair of
// facing walls, so the box is seen again and again in every direction: its edges make a glowing grid that goes on
// forever. Mirror images of a box fill space with copies of it, so the line of sight from the eye is simply followed
// straight on through that endless grid of boxes, box by box; the further it goes, the more reflections (dimmer). In
// each box it passes, the twelve strips are checked: how close the line of sight really comes to each one (in 3D,
// within that box, so every strip is counted exactly once) and where along it, for the LED there. The boxes are exactly the blocks, so the grid lines
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
const int STEPS = 40;          // boxes followed at most

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

// Across a strip: a soft line r wide at distance x, widened to at least a pixel (same light), never thinner than that.
float line(float x, float r, float fw) {
    float wide = sqrt(r * r + fw * fw);
    return r / wide * exp(-x * x / (wide * wide));
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

// The light (x: LEDs, y: glow) of the strip running along `axis` at (s1, s2) on the other two axes, inside the box at
// `origin`, for the line of sight q + sight * t with t from t0 to t1 (its part in that box). `reflections` per block.
vec3 strip(int axis, float s1, float s2, vec3 local, vec3 sight, float t0, float t1, vec3 size, float eyeDistance,
           float perBlock, vec3 origin) {
    vec2 o = others(local, axis), d = others(sight, axis);
    vec2 w = vec2(s1, s2) - o;
    float dd = max(dot(d, d), 1e-8);
    float t = clamp(dot(w, d) / dd, t0, t1);                  // closest along the line of sight, within this box
    vec2 off = o + d * t - vec2(s1, s2);
    float dist2 = dot(off, off);
    float fw = PixelSize * (eyeDistance + t);
    float reach = 3.0 * (LED_SIZE * 3.0) + 3.0 * fw;
    if (dist2 > reach * reach) {
        return vec3(0.0);                                     // too far to light this pixel
    }
    float dist = sqrt(dist2);
    float len = size[axis];
    float gap = len / max(1.0, floor(len / LED_GAP + 0.5));
    float along = local[axis] + sight[axis] * t;
    float x = (fract(along / gap) - 0.5) * gap;
    float fwAlong = fw / max(sqrt(dd), 0.05);                 // (a strip seen end-on: its LEDs bunch up)
    float reflections = t * perBlock;
    float light = pow(KEEP, reflections) * (0.86 + 0.14 * sin(Time * 2.5 - reflections * 0.5));
    float led = line(dist, LED_SIZE, fw) * bump(x, LED_SIZE, gap, fwAlong);
    float glow = line(dist, LED_SIZE * 3.0, fw) * bump(x, LED_SIZE * 3.0, gap, fwAlong);
    // (an LED's hot white middle only while it's bigger than a pixel: blurred together they'd turn the grid grey)
    float sharp = LED_SIZE * LED_SIZE / (LED_SIZE * LED_SIZE + fw * fw);
    vec3 at = origin + local + sight * t;
    return (ledColor(at, reflections) * (led * 2.4 + glow * 0.15) + vec3(led * 0.9 * sharp)) * light;
}

void main() {
    vec3 sight = normalize(facePos - Eye);
    vec3 p = facePos - BoxMin;
    float eyeDistance = length(facePos - Eye);
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
    float perBlock = dot(abs(sight), 1.0 / size);            // walls passed per block travelled (on average)
    vec3 q = p + sight * 1e-4;                               // (a hair inside the face)
    vec3 inv = 1.0 / max(abs(sight), vec3(1e-5));
    vec3 cell = floor(q / size);
    vec3 next = (cell + step(0.0, sight)) * size;            // the next wall on each axis
    vec3 tNext = abs(next - q) * inv;
    vec3 tStep = size * inv;
    vec3 cellStep = sign(sight);
    vec3 lit = vec3(0.0);
    float t0 = 0.0;
    vec3 hi = size - INSET;
    for (int i = 0; i < STEPS; i++) {
        int axis = tNext.x < tNext.y ? (tNext.x < tNext.z ? 0 : 2) : (tNext.y < tNext.z ? 1 : 2);
        float t1 = tNext[axis];
        // this box: from t0 to t1, positions relative to its corner
        vec3 origin = cell * size;
        vec3 local = q - origin;
        if (pow(KEEP, t0 * perBlock) < 0.004) {
            break;
        }
        lit += strip(0, INSET, INSET, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(0, hi.y, INSET, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(0, INSET, hi.z, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(0, hi.y, hi.z, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(1, INSET, INSET, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(1, hi.x, INSET, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(1, INSET, hi.z, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(1, hi.x, hi.z, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(2, INSET, INSET, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(2, hi.x, INSET, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(2, INSET, hi.y, local, sight, t0, t1, size, eyeDistance, perBlock, origin)
             + strip(2, hi.x, hi.y, local, sight, t0, t1, size, eyeDistance, perBlock, origin);
        lit += ledColor(origin, t0 * perBlock) * 0.004 * pow(KEEP, t0 * perBlock); // (a faint glow in the glass)
        // on to the next box
        t0 = t1;
        tNext[axis] += tStep[axis];
        cell[axis] += cellStep[axis];
    }
    vec3 col = 1.0 - exp(-lit * 1.5);
    float fresnel = pow(1.0 - abs(sight[face]), 4.0);
    col += vec3(0.04, 0.045, 0.06) * fresnel;                // the glass
    fragColor = vec4(col, 1.0);
}
