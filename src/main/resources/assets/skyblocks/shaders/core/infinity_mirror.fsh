#version 150

// An infinity mirror: a half mirror in front, a mirror at the back and a strip of LEDs around the inside, so the LED
// ring is seen again and again, smaller and dimmer each time, down a tunnel that never ends. Nothing is drawn twice:
// the line of sight from the eye through this pixel is followed into an endless rectangular tunnel behind the face,
// and where it meets the tunnel's walls tells which reflection of the ring (and which LED on it) is seen there.

uniform vec3 Eye;        // the eye (relative to the camera position: the view bobbing can move it a little)
uniform vec3 Origin;     // the frame's corner on the face
uniform vec3 AxisU;      // along the frame's width
uniform vec3 AxisV;      // along its height
uniform vec3 Inward;     // into the block
uniform vec2 Size;       // the frame's size, blocks
uniform vec3 ColorFront; // the LEDs' colour up front...
uniform vec3 ColorDeep;  // ...and far down the tunnel
uniform float Rainbow;   // 1: every colour, running round the rings
uniform float Time;      // seconds
// which blocks of the frame's rectangle are mirrors (1 bit each, 24 to a number, row by row: u + width * v); Full: all
// of them (a plain rectangle, or too big to say block by block). The tunnel follows the frame's own shape: round a
// hole, along an L...
uniform float Full;
uniform vec4 Occ0;
uniform vec4 Occ1;
uniform vec4 Occ2;
uniform vec4 Occ3;
uniform vec4 Occ4;
uniform vec4 Occ5;
uniform vec4 Occ6;
uniform vec4 Occ7;

in vec3 facePos;

out vec4 fragColor;

const float FRAME = 0.07;      // the dark glass edge around the tunnel
const float SPACING = 0.2;     // between two reflections of the LED ring
const float LED_GAP = 0.0625;  // between two LEDs on the strip
const float LED_SIZE = 0.015;  // an LED's radius
const float KEEP = 0.87;       // light left after each reflection

vec3 hue(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

bool cellOn(ivec2 c) {
    ivec2 sz = ivec2(Size + 0.5);
    if (c.x < 0 || c.y < 0 || c.x >= sz.x || c.y >= sz.y) return false;
    if (Full > 0.5) return true;
    int i = c.x + sz.x * c.y;
    int vi = i / 96, comp = (i / 24) - vi * 4, bit = i - (i / 24) * 24;
    vec4 o = vi == 0 ? Occ0 : vi == 1 ? Occ1 : vi == 2 ? Occ2 : vi == 3 ? Occ3 : vi == 4 ? Occ4 : vi == 5 ? Occ5 : vi == 6 ? Occ6 : Occ7;
    float f = comp == 0 ? o.x : comp == 1 ? o.y : comp == 2 ? o.z : o.w;
    return ((int(f + 0.5) >> bit) & 1) != 0;
}

// A soft bump at distance x from its middle (r wide), repeating every period; fw: what one pixel covers here.
float bump(float x, float r, float period, float fw) {
    float wide = sqrt(r * r + fw * fw);
    float average = 1.7725 * r / period; // (sqrt(pi) r / period: its light spread evenly)
    float blend = smoothstep(0.18, 0.45, wide / period);
    return mix(r / wide * exp(-x * x / (wide * wide)), average, blend);
}

void main() {
    vec3 sight = normalize(facePos - Eye);
    vec3 p = facePos - Origin;
    vec3 d = vec3(dot(sight, AxisU), dot(sight, AxisV), dot(sight, Inward));
    vec2 q = vec2(dot(p, AxisU), dot(p, AxisV)); // (on the frame: 0 .. Size, a block a unit)
    ivec2 sz = ivec2(Size + 0.5);
    ivec2 cell = clamp(ivec2(floor(q)), ivec2(0), sz - 1);
    if (!cellOn(cell)) { // (right on a seam: the mirror beside it)
        cell = clamp(ivec2(floor(q + (q - (vec2(cell) + 0.5)) * 0.01)), ivec2(0), sz - 1);
    }
    vec3 base = Rainbow > 0.5 ? hue(fract(Time * 0.2)) : ColorFront;
    // the glass edge round the frame's shape (outside, and round any hole in it): dark, lit a little by the LEDs
    float fresnel = pow(1.0 - clamp(d.z, 0.0, 1.0), 4.0);
    {
        vec2 lo = vec2(cell), hi = lo + 1.0;
        bool wl = !cellOn(cell + ivec2(-1, 0)), wr = !cellOn(cell + ivec2(1, 0));
        bool wb = !cellOn(cell + ivec2(0, -1)), wt = !cellOn(cell + ivec2(0, 1));
        float edge = 1.0e9;
        if (wl) edge = min(edge, q.x - lo.x - FRAME);
        if (wr) edge = min(edge, hi.x - q.x - FRAME);
        if (wb) edge = min(edge, q.y - lo.y - FRAME);
        if (wt) edge = min(edge, hi.y - q.y - FRAME);
        // (and a little corner post where only the block across the corner is missing)
        if (!wl && !wb && !cellOn(cell + ivec2(-1, -1))) edge = min(edge, max(q.x - lo.x - FRAME, q.y - lo.y - FRAME));
        if (!wr && !wb && !cellOn(cell + ivec2(1, -1))) edge = min(edge, max(hi.x - q.x - FRAME, q.y - lo.y - FRAME));
        if (!wl && !wt && !cellOn(cell + ivec2(-1, 1))) edge = min(edge, max(q.x - lo.x - FRAME, hi.y - q.y - FRAME));
        if (!wr && !wt && !cellOn(cell + ivec2(1, 1))) edge = min(edge, max(hi.x - q.x - FRAME, hi.y - q.y - FRAME));
        if (edge < 0.0) {
            vec3 col = vec3(0.025, 0.026, 0.035) + base * 0.05 + vec3(0.05) * fresnel;
            col += base * 0.25 * exp(edge * 60.0); // the strip's glow spilling onto the edge
            fragColor = vec4(col, 1.0);
            return;
        }
    }
    d.z = max(d.z, 1e-4);
    // where the line of sight meets the tunnel's walls: block by block through the frame's shape, until it reaches a
    // side with no mirror beyond it (its wall, the edge's width in from there)
    vec2 pos = q;
    ivec2 c = cell;
    float t = 0.0, s = 0.0, around = 0.0;
    bool hit = false;
    for (int i = 0; i < 96; i++) {
        vec2 lo = vec2(c), hi = lo + 1.0;
        bool wl = !cellOn(c + ivec2(-1, 0)), wr = !cellOn(c + ivec2(1, 0));
        bool wb = !cellOn(c + ivec2(0, -1)), wt = !cellOn(c + ivec2(0, 1));
        vec2 elo = lo + vec2(wl ? FRAME : 0.0, wb ? FRAME : 0.0), ehi = hi - vec2(wr ? FRAME : 0.0, wt ? FRAME : 0.0);
        float tx = d.x > 1e-6 ? (ehi.x - pos.x) / d.x : d.x < -1e-6 ? (elo.x - pos.x) / d.x : 1e9;
        float ty = d.y > 1e-6 ? (ehi.y - pos.y) / d.y : d.y < -1e-6 ? (elo.y - pos.y) / d.y : 1e9;
        tx = max(tx, 0.0);
        ty = max(ty, 0.0);
        if (tx < ty) {
            pos += d.xy * tx;
            t += tx;
            if (d.x > 0.0 ? wr : wl) { // (a wall: up and down it)
                hit = true;
                s = pos.y;
                around = d.x > 0.0 ? Size.x + pos.y : 2.0 * Size.x + Size.y + (Size.y - pos.y);
                break;
            }
            c.x += d.x > 0.0 ? 1 : -1;
        } else {
            pos += d.xy * ty;
            t += ty;
            if (d.y > 0.0 ? wt : wb) { // (a wall: along it)
                hit = true;
                s = pos.x;
                around = d.y > 0.0 ? Size.x + Size.y + (Size.x - pos.x) : pos.x;
                break;
            }
            c.y += d.y > 0.0 ? 1 : -1;
        }
    }
    if (!hit) {
        fragColor = vec4(vec3(0.01) + vec3(0.04, 0.045, 0.06) * fresnel, 1.0);
        return;
    }
    float depth = t * d.z;
    float dz = (fract(depth / SPACING) - 0.5) * SPACING;       // from that ring's strip
    float gap = LED_GAP;
    float ds = (fract(s / gap) - 0.5) * gap;                   // from the nearest LED along it
    // An LED is a soft dot: a bump across the strip times a bump along it. Each bump is widened to what one pixel
    // covers there (same light), and once it's about as wide as the space between two LEDs (or two rings) it turns
    // into their average: far down the tunnel, LEDs smaller than a pixel would otherwise sparkle and crawl as you move.
    float fs = fwidth(s), fz = fwidth(depth);
    float led = bump(ds, LED_SIZE, gap, fs) * bump(dz, LED_SIZE, SPACING, fz);
    float glow = 0.22 * bump(ds, LED_SIZE * 3.0, gap, fs) * bump(dz, LED_SIZE * 3.0, SPACING, fz);
    float strip = bump(dz, 0.014, SPACING, fz) * 0.05;
    // (smooth with depth, not a step per ring: steps would show as lines once the rings blur together)
    float ring = depth / SPACING - 0.5;
    float light = pow(KEEP, ring) * (0.86 + 0.14 * sin(Time * 2.5 - ring * 0.55)); // dimmer each time, a slow wave inwards
    vec3 color;
    if (Rainbow > 0.5) {
        // colours running round the rings and on down the tunnel, like an addressable LED strip running a rainbow
        color = hue(fract(around / (Size.x + Size.y) + ring * 0.06 - Time * 0.45));
    } else {
        color = mix(ColorFront, ColorDeep, 1.0 - exp(-max(ring, 0.0) * 0.08));
    }
    vec3 lit = color * (led * 2.4 + glow + strip + 0.012) * light;
    lit += vec3(1.0) * led * 0.9 * light;                      // an LED's hot white middle
    vec3 col = 1.0 - exp(-lit * 1.6);
    col += vec3(0.04, 0.045, 0.06) * fresnel;                   // the front glass
    fragColor = vec4(col, 1.0);
}
