#version 150

// The filters (The Valley Mod's, by D0nkey100 - used with permission: its shaders/program/filters.fsh, the effects
// themselves as they are). Here they're drawn on the filter block's faces over a copy of the picture drawn so far, instead
// of in a pass over the whole screen: this pixel's filter is the face's own (mode), the world behind it the copy's.
// 1 inverted, 2 posterize, 3 grayscale, 4 chromatic aberration, 5 blueprint (21: dyed), 6 Game Boy, 7 pixelation,
// 8 cross stitch, 9 red, 14 screen, 20 tint (dyed).

uniform sampler2D Sampler0; // the picture so far
uniform sampler2D Sampler1; // the Screen's light pattern (Shadered's pixel.png, by noodlegamer76)
uniform vec2 ScreenSize;
uniform vec3 CamPos;        // the camera's place in the world (kept under 1024 blocks: only its grid matters)

in vec3 point;
in vec2 onFace;
flat in int frame;
flat in int mode;
flat in vec3 dye;

out vec4 fragColor;

#define DiffuseSampler Sampler0
#define LedSampler Sampler1

vec2 texCoord;
vec2 oneTexel;
vec2 InSize;
float PixelSize;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float lumaAt(vec2 uv) {
    return luma(texture(DiffuseSampler, uv).rgb);
}

// 4 x 4 ordered dither threshold, 0..1.
float bayer(vec2 pixel) {
    int x = int(mod(pixel.x, 4.0));
    int y = int(mod(pixel.y, 4.0));
    int table[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    return (float(table[x + y * 4]) + 0.5) / 16.0;
}

vec2 crossStitchCell() {
    float size = PixelSize * 2.5;
    return (floor(texCoord * InSize / size) + 0.5) * size / InSize;
}

vec3 crossStitch(vec3 thread) {
    float size = PixelSize * 2.5;
    vec2 pixel = texCoord * InSize;
    vec2 cellIndex = floor(pixel / size);
    vec2 local = (pixel - cellIndex * size) / size;
    thread = floor(thread * 6.0 + 0.5) / 6.0;
    float under = abs(local.x - local.y) * 0.7071;
    float over = abs(local.x + local.y - 1.0) * 0.7071;
    float width = 0.13;
    vec2 fromCenter = abs(local - 0.5);
    float inside = step(max(fromCenter.x, fromCenter.y), 0.46);
    float weave = 0.04 * (step(0.5, fract(pixel.x / 2.0)) - step(0.5, fract(pixel.y / 2.0)));
    vec3 cloth = vec3(0.93, 0.89, 0.80) + weave;
    float corner = length(min(local, 1.0 - local));
    cloth = mix(vec3(0.55, 0.50, 0.42), cloth, smoothstep(0.06, 0.12, corner));
    vec3 color = cloth;
    if (inside > 0.5 && under < width) {
        float shade = 1.0 - under / width;
        color = thread * (0.70 + 0.35 * shade);
    }
    if (inside > 0.5 && over < width) {
        float shade = 1.0 - over / width;
        color = thread * (0.75 + 0.40 * shade);
    } else if (inside > 0.5 && over < width + 0.03 && under < width) {
        color *= 0.75;
    }
    return clamp(color, 0.0, 1.0);
}

vec2 faceCoords(vec3 world, vec3 normal) {
    vec3 n = abs(normal);
    if (n.y >= n.x && n.y >= n.z) return world.xz;
    if (n.x >= n.z) return world.zy;
    return world.xy;
}

// The Screen (from the Shadered mod by noodlegamer76, its "Screen" pass, as TVM wrote it for its filters): close up, the
// face is a wall of little lights, 64 to a block, each showing the world at its middle through a red / green / blue
// light pattern; further away it fades to a plain window.
void ledCell(out vec2 coord, out vec3 led, out float strength) {
    vec2 grid = faceCoords(CamPos + point, cross(dFdx(point), dFdy(point))) * 64.0;
    strength = clamp((2.4 - length(point)) / (2.4 - 0.7), 0.0, 1.0);
    vec2 toCenter = floor(grid) + 0.5 - grid;
    vec2 dGdx = dFdx(grid);
    vec2 dGdy = dFdy(grid);
    float det = dGdx.x * dGdy.y - dGdx.y * dGdy.x;
    vec2 offset = vec2(0.0);
    if (abs(det) > 0.00001) {
        offset.x = (toCenter.x * dGdy.y - toCenter.y * dGdy.x) / det;
        offset.y = (toCenter.y * dGdx.x - toCenter.x * dGdx.y) / det;
    }
    coord = (clamp(gl_FragCoord.xy + offset, vec2(0.5), InSize - 0.5)) / InSize;
    led = clamp(texture(LedSampler, fract(grid)).rgb, 0.0, 1.0);
}

vec3 posterize(vec3 c) {
    return min(floor(c * 4.0), 3.0) / 3.0;
}

vec3 gameBoy(vec3 c) {
    vec2 cell = floor(texCoord * InSize / 2.0);
    float shade = clamp(luma(c) * 3.0 + bayer(cell) - 0.5, 0.0, 3.0);
    int level = int(floor(shade + 0.5));
    vec3 palette[4] = vec3[4](vec3(0.06, 0.22, 0.06), vec3(0.19, 0.38, 0.19), vec3(0.55, 0.67, 0.06), vec3(0.61, 0.74, 0.06));
    return palette[clamp(level, 0, 3)];
}

vec3 redLens(vec3 c) {
    float l = luma(c);
    return vec3(0.12 + 0.88 * sqrt(l), 0.35 * l * l, 0.35 * l * l);
}

vec3 tintLens(vec3 c, vec3 d) {
    float l = luma(c);
    return mix(vec3(0.35 * l * l), vec3(0.12 + 0.88 * sqrt(l)), d);
}

vec3 blueprintPaper(float line, vec3 d, bool dyed) {
    vec3 paper = dyed ? d * 0.62 + 0.02 : vec3(0.10, 0.30, 0.60);
    vec3 ink = dyed ? mix(d, vec3(1.0), 0.8) : vec3(0.80, 0.90, 1.00);
    return mix(paper, ink, line);
}

float blueprintGrid() {
    vec2 pixel = texCoord * InSize;
    return (mod(pixel.x, 24.0) < 1.0 || mod(pixel.y, 24.0) < 1.0) ? 0.15 : 0.0;
}

void main() {
    InSize = max(ScreenSize, vec2(1.0));
    texCoord = gl_FragCoord.xy / InSize;
    oneTexel = 1.0 / InSize;
    PixelSize = max(4.0, floor(InSize.y / 270.0 + 0.5));
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    vec3 result = color;
    if (mode == 1) {
        result = 1.0 - color;
    } else if (mode == 2) {
        result = posterize(color);
    } else if (mode == 3) {
        result = vec3(luma(color));
    } else if (mode == 4) {
        vec2 shift = vec2(oneTexel.x * max(3.0, InSize.y / 270.0), 0.0);
        result = vec3(texture(DiffuseSampler, texCoord + shift).r, color.g, texture(DiffuseSampler, texCoord - shift).b);
    } else if (mode == 5 || mode == 21) {
        float gx = lumaAt(texCoord + vec2(oneTexel.x, 0.0)) - lumaAt(texCoord - vec2(oneTexel.x, 0.0));
        float gy = lumaAt(texCoord + vec2(0.0, oneTexel.y)) - lumaAt(texCoord - vec2(0.0, oneTexel.y));
        float edge = smoothstep(0.05, 0.25, length(vec2(gx, gy)));
        result = blueprintPaper(max(edge, blueprintGrid()), dye, mode == 21);
    } else if (mode == 6) {
        result = gameBoy(color);
    } else if (mode == 7) {
        vec2 cell = (floor(texCoord * InSize / PixelSize) + 0.5) * PixelSize / InSize;
        result = texture(DiffuseSampler, cell).rgb;
    } else if (mode == 8) {
        result = crossStitch(texture(DiffuseSampler, crossStitchCell()).rgb);
    } else if (mode == 9) {
        result = redLens(color);
    } else if (mode == 14) {
        vec2 coord;
        vec3 led;
        float strength;
        ledCell(coord, led, strength);
        result = mix(color, led * texture(DiffuseSampler, coord).rgb, strength);
    } else if (mode == 20) {
        result = tintLens(color, dye);
    }
    // the frame round the pane's edges (not filtered): dark, a lighter line inside it, two sixteenths of a block
    vec2 f = fract(onFace / 4.0) * 4.0; // (where on the face, without the flags)
    float edge = 9.0;
    if (gl_FrontFacing) { // (from inside the pane: no frames)
        if ((frame & 1) != 0) edge = min(edge, f.x);
        if ((frame & 2) != 0) edge = min(edge, 1.0 - f.x);
        if ((frame & 4) != 0) edge = min(edge, f.y);
        if ((frame & 8) != 0) edge = min(edge, 1.0 - f.y);
    }
    if (edge < 1.0 / 16.0) {
        result = vec3(0.20, 0.22, 0.25);
    } else if (edge < 2.0 / 16.0) {
        result = vec3(0.38, 0.41, 0.46);
    }
    fragColor = vec4(result, 1.0);
}
