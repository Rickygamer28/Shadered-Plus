#version 150

// An infinity cube: six half mirrors with LED strips along the twelve edges. Light bounces between every pair of
// facing walls, so the box is seen again and again in every direction: its edges make a glowing grid that goes on
// forever. Mirror images of a box fill space with copies of it, so the line of sight from the eye is simply followed
// straight on through that endless grid of boxes, wall to wall; the further it goes, the more reflections (dimmer),
// and where it passes close to a box's edge, that edge's LEDs light the pixel. The strips sit right on the cube's
// edges, so the grid lines up with the blocks.

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

const float FRAME = 0.02;      // the thin metal frame along the edges (the front strips glow on it)
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

// LEDs on a strip at distance d from the line of sight, `along` the strip, strips `spacing` apart, LEDs `gap` apart
float leds(float d, float along, float spacing, float gap, float fw, float fwAlong, float size) {
    return bump(d, size, spacing, fw) * bump((fract(along / gap) - 0.5) * gap, size, gap, fwAlong);
}

void main() {
    vec3 sight = normalize(facePos - Eye);
    vec3 p = facePos - BoxMin;
    float eyeDistance = length(facePos - Eye);
    float pixel = PixelSize * eyeDistance;

    // the face this pixel is on (the axis it's flat across), and how far from that face's border
    vec3 toSide = min(p, BoxSize - p);
    int face = toSide.x < toSide.y ? (toSide.x < toSide.z ? 0 : 2) : (toSide.y < toSide.z ? 1 : 2);
    vec2 across = others(toSide, face);
    float border = min(across.x, across.y);
    vec2 onFace = others(p, face), faceSize = others(BoxSize, face);
    // the strip on the nearest edge of this face (seen through the glass, right at the frame)
    bool firstNear = across.x < across.y;
    float edgeLength = firstNear ? faceSize.y : faceSize.x;
    float edgeGap = edgeLength / max(1.0, floor(edgeLength / LED_GAP + 0.5));
    float front = leds(border, firstNear ? onFace.y : onFace.x, 1.0, edgeGap, pixel, pixel, LED_SIZE);
    vec3 frontColor = ledColor(p, 0.0);

    vec3 col;
    if (border < FRAME) {
        col = vec3(0.045, 0.045, 0.055) + vec3(0.03) * smoothstep(0.0, FRAME, border);
        vec3 lit = frontColor * front * 2.4 + vec3(front * 0.9);
        col = col + (1.0 - exp(-lit * 1.5));
        fragColor = vec4(col, 1.0);
        return;
    }

    // inside: the box and its endless mirror images (a hair inside the face, so the face itself isn't a wall)
    vec3 size = BoxSize;
    vec3 q = p + sight * 1e-4;
    vec3 inv = 1.0 / max(abs(sight), vec3(1e-5));
    vec3 cell = floor(q / size);
    vec3 next = (cell + step(0.0, sight)) * size;      // the next wall on each axis
    vec3 tNext = abs(next - q) * inv;
    vec3 tStep = size * inv;
    float perBlock = dot(abs(sight), 1.0 / size);        // walls passed per block travelled (on average)
    vec3 lit = frontColor * (front * 2.4) + vec3(front * 0.9);
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
        // the edges lying in this wall (one of its two coordinates a whole box): how far the line of sight really
        // passes from them (in 3D), and where along them
        vec2 c = others(at, axis);
        vec2 period = others(size, axis);
        vec2 off = c - period * floor(c / period + 0.5);
        vec2 gap = period / max(vec2(1.0), floor(period / LED_GAP + 0.5));
        float da = abs(sight[axis]);
        vec2 db = others(sight, axis);
        float s1 = max(length(vec2(da, db.x)), 1e-4), s2 = max(length(vec2(da, db.y)), 1e-4);
        float near1 = c.y - db.y * off.x * db.x / (s1 * s1);
        float near2 = c.x - db.x * off.y * db.y / (s2 * s2);
        float spacing1 = max(period.x * da / s1, 1e-3), spacing2 = max(period.y * da / s2, 1e-3);
        float fwAlong1 = fw / max(s1, 0.05), fwAlong2 = fw / max(s2, 0.05);
        float led = 0.6 * (leds(abs(off.x) * da / s1, near1, spacing1, gap.y, fw, fwAlong1, LED_SIZE)
                + leds(abs(off.y) * da / s2, near2, spacing2, gap.x, fw, fwAlong2, LED_SIZE));
        float glow = 0.12 * (leds(abs(off.x) * da / s1, near1, spacing1, gap.y, fw, fwAlong1, LED_SIZE * 3.0)
                + leds(abs(off.y) * da / s2, near2, spacing2, gap.x, fw, fwAlong2, LED_SIZE * 3.0));
        vec3 color = ledColor(at, reflections);
        lit += (color * (led * 2.4 + glow + 0.006) + vec3(led * 0.9)) * light;
    }
    col = 1.0 - exp(-lit * 1.5);
    float fresnel = pow(1.0 - abs(dot(sight, face == 0 ? vec3(1, 0, 0) : (face == 1 ? vec3(0, 1, 0) : vec3(0, 0, 1)))), 4.0);
    col += vec3(0.04, 0.045, 0.06) * fresnel;                // the glass
    fragColor = vec4(col, 1.0);
}
