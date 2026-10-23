#version 150

// An infinity cube: six half mirrors with LED strips along the twelve inside edges. Light bounces between every pair
// of facing walls, so the box is seen again and again in every direction: its edges make a glowing grid that goes on
// forever. Mirror images of a box fill space with copies of it, so the line of sight from the eye is simply followed
// straight on through that endless grid of boxes, wall to wall; each wall it passes is one more reflection (dimmer),
// and where it passes close to a box's edge, that edge's LEDs light the pixel.

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

const float FRAME = 0.06;      // the metal frame along the outside edges
const float LED_GAP = 0.0625;  // between two LEDs on a strip
const float LED_SIZE = 0.013;  // an LED's radius
const float KEEP = 0.84;       // light left after each reflection
const int STEPS = 40;          // walls followed at most

vec3 hue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

// A soft bump at distance x from its middle (r wide), repeating every period; fw: what one pixel covers there. Wider
// than a pixel it keeps its light, and once about as wide as the period it becomes the average (no sparkle far away).
float bump(float x, float r, float period, float fw) {
    float wide = sqrt(r * r + fw * fw * 0.5);
    float average = 1.7725 * r / period;
    float blend = smoothstep(0.18, 0.45, wide / period);
    return mix(r / wide * exp(-x * x / (wide * wide)), average, blend);
}

vec2 others(vec3 v, int axis) {
    return axis == 0 ? v.yz : (axis == 1 ? v.xz : v.xy);
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
        // the metal frame: dark, a little shine, lit by the LEDs right behind its inner edge
        vec3 col = vec3(0.045, 0.045, 0.055) + vec3(0.03) * smoothstep(0.0, FRAME, border);
        col += base * 0.35 * exp((border - FRAME) * 90.0);
        fragColor = vec4(col, 1.0);
        return;
    }

    // inside: the inner box (just inside the frame) and its endless mirror images
    vec3 size = BoxSize - 2.0 * FRAME;
    vec3 q = p - FRAME;
    vec3 inv = 1.0 / max(abs(sight), vec3(1e-5));
    vec3 cell = floor(q / size);
    vec3 next = (cell + step(0.0, sight)) * size;      // the next wall on each axis
    vec3 tNext = abs(next - q) * inv;
    vec3 tStep = size * inv;
    vec3 lit = vec3(0.0);
    for (int i = 0; i < STEPS; i++) {
        int axis = tNext.x < tNext.y ? (tNext.x < tNext.z ? 0 : 2) : (tNext.y < tNext.z ? 1 : 2);
        float t = tNext[axis];
        tNext[axis] += tStep[axis];
        float bounces = float(i);
        float light = pow(KEEP, bounces) * (0.86 + 0.14 * sin(Time * 2.5 - bounces * 0.5));
        if (light < 0.004) {
            break;
        }
        vec3 at = q + sight * t;
        float fw = PixelSize * (eyeDistance + t);
        // on this wall: the edges lying in it are where one of the other two coordinates is a whole box. How far the
        // line of sight passes from such an edge (really, in 3D, not just on this wall: on the wall alone the lines got
        // thicker and thinner with the angle and the grid swam as you moved), and where along the edge it passes
        vec2 c = others(at, axis);
        vec2 period = others(size, axis);
        vec2 off = c - period * floor(c / period + 0.5);   // from the nearest edge, each way, on this wall
        vec2 n = max(vec2(1.0), floor(period / LED_GAP + 0.5));
        vec2 gap = period / n;
        float da = abs(sight[axis]);
        vec2 db = others(sight, axis);
        // edges running along the wall's second axis (first coordinate whole), then along its first
        float s1 = max(length(vec2(da, db.x)), 1e-4), s2 = max(length(vec2(da, db.y)), 1e-4);
        float dist1 = abs(off.x) * da / s1, dist2 = abs(off.y) * da / s2;
        float near1 = c.y - db.y * off.x * db.x / (s1 * s1); // (where along the edge the sight passes closest)
        float near2 = c.x - db.x * off.y * db.y / (s2 * s2);
        float along1 = (fract(near1 / gap.y) - 0.5) * gap.y, along2 = (fract(near2 / gap.x) - 0.5) * gap.x;
        float spacing1 = max(period.x * da / s1, 1e-3), spacing2 = max(period.y * da / s2, 1e-3);
        float fwAlong1 = fw / max(s1, 0.05), fwAlong2 = fw / max(s2, 0.05); // (an edge seen end-on: LEDs bunch up)
        float led = 0.6 * (bump(dist1, LED_SIZE, spacing1, fw) * bump(along1, LED_SIZE, gap.y, fwAlong1)
                + bump(dist2, LED_SIZE, spacing2, fw) * bump(along2, LED_SIZE, gap.x, fwAlong2));
        float glow = 0.12 * (bump(dist1, LED_SIZE * 3.0, spacing1, fw) * bump(along1, LED_SIZE * 3.0, gap.y, fwAlong1)
                + bump(dist2, LED_SIZE * 3.0, spacing2, fw) * bump(along2, LED_SIZE * 3.0, gap.x, fwAlong2));
        vec3 color;
        if (Rainbow > 0.5) {
            // colours flowing through the grid, like an addressable LED strip running a rainbow
            color = hue(fract(dot(at, vec3(0.23, 0.31, 0.17)) + bounces * 0.05 - Time * 0.45));
        } else {
            color = mix(ColorFront, ColorDeep, 1.0 - exp(-bounces * 0.12));
        }
        lit += (color * (led * 2.4 + glow + 0.006) + vec3(led * 0.9)) * light;
    }
    vec3 col = 1.0 - exp(-lit * 1.5);
    float fresnel = pow(1.0 - abs(dot(sight, face == 0 ? vec3(1, 0, 0) : (face == 1 ? vec3(0, 1, 0) : vec3(0, 0, 1)))), 4.0);
    col += vec3(0.04, 0.045, 0.06) * fresnel;                // the glass
    fragColor = vec4(col, 1.0);
}
