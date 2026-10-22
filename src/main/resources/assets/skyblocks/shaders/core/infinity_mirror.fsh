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

void main() {
    vec3 sight = normalize(facePos - Eye);
    vec3 p = facePos - Origin;
    vec3 d = vec3(dot(sight, AxisU), dot(sight, AxisV), dot(sight, Inward));
    float w = Size.x - 2.0 * FRAME, h = Size.y - 2.0 * FRAME;
    float a = dot(p, AxisU) - FRAME, b = dot(p, AxisV) - FRAME;
    vec3 base = Rainbow > 0.5 ? hue(fract(Time * 0.05)) : ColorFront;
    // the glass edge: dark, lit a little by the LEDs
    float fresnel = pow(1.0 - clamp(d.z, 0.0, 1.0), 4.0);
    if (a < 0.0 || b < 0.0 || a > w || b > h) {
        float edge = min(min(a, b), min(w - a, h - b)); // (how far into the edge, negative)
        vec3 col = vec3(0.025, 0.026, 0.035) + base * 0.05 + vec3(0.05) * fresnel;
        col += base * 0.25 * exp(edge * 60.0); // the strip's glow spilling onto the edge
        fragColor = vec4(col, 1.0);
        return;
    }
    d.z = max(d.z, 1e-4);
    // where the line of sight meets the tunnel's walls
    float ta = d.x > 1e-6 ? (w - a) / d.x : (d.x < -1e-6 ? -a / d.x : 1e9);
    float tb = d.y > 1e-6 ? (h - b) / d.y : (d.y < -1e-6 ? -b / d.y : 1e9);
    float t, s, len, around;
    if (ta < tb) {
        t = ta;
        s = b + t * d.y;
        len = h;
        around = d.x > 0.0 ? w + s : 2.0 * w + h + (h - s); // (right wall / left wall)
    } else {
        t = tb;
        s = a + t * d.x;
        len = w;
        around = d.y > 0.0 ? w + h + (w - s) : s; // (top wall / bottom wall)
    }
    float depth = t * d.z;
    float k = floor(depth / SPACING);                          // which reflection
    float dz = (fract(depth / SPACING) - 0.5) * SPACING;       // from that ring's strip
    float n = max(1.0, floor(len / LED_GAP + 0.5));
    float gap = len / n;
    float ds = (fract(s / gap) - 0.5) * gap;                   // from the nearest LED along it
    // far down the tunnel the LEDs get smaller than a pixel: spread them out (same light) instead of flickering
    float footprint = max(fwidth(depth), fwidth(s));
    float r = max(LED_SIZE, footprint * 0.75);
    float glowR = max(LED_SIZE * 3.0, footprint);
    float dist2 = ds * ds + dz * dz;
    float led = (LED_SIZE * LED_SIZE) / (r * r) * exp(-dist2 / (r * r));
    float glow = 0.22 * (9.0 * LED_SIZE * LED_SIZE) / (glowR * glowR) * exp(-dist2 / (glowR * glowR));
    float strip = (1.0 - smoothstep(0.012, 0.02 + footprint, abs(dz))) * 0.05;
    float light = pow(KEEP, k) * (0.86 + 0.14 * sin(Time * 2.5 - k * 0.55)); // dimmer each time, a slow wave inwards
    vec3 color;
    if (Rainbow > 0.5) {
        color = hue(fract(around / (2.0 * (w + h)) - Time * 0.08 + k * 0.02));
    } else {
        color = mix(ColorFront, ColorDeep, 1.0 - exp(-k * 0.08));
    }
    vec3 lit = color * (led * 2.4 + glow + strip + 0.012) * light;
    lit += vec3(1.0) * led * 0.9 * light;                      // an LED's hot white middle
    vec3 col = 1.0 - exp(-lit * 1.6);
    col += vec3(0.04, 0.045, 0.06) * fresnel;                   // the front glass
    fragColor = vec4(col, 1.0);
}
