#version 150

// Shadered+'s arcade cabinet: its screen playing a demo while nobody plays - Snake, then Breakout, by turns - on an old
// curved tube: scanlines, the glow of the phosphor, dark corners. (The scores are written over it by the renderer.)

in vec2 uv;

uniform float Time;
uniform float Seed;
uniform float Power;
uniform float Live;   // 1: someone's playing - their game's picture (Sampler0, Cells wide and high) instead of the demo
uniform vec2 Cells;
uniform sampler2D Sampler0;

out vec4 fragColor;

const vec2 GRID = vec2(16.0, 12.0);
const float CYCLE = 18.0;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7)) + Seed * 17.13) * 43758.5453);
}

// how near p is to a box (soft edged: a glow rnd what's lit)
float boxGlow(vec2 p, vec2 a, vec2 b, float soft) {
    vec2 d = max(max(a - p, p - b), 0.0);
    return exp(-length(d) * soft);
}

float tri(float x) {
    return abs(fract(x) * 2.0 - 1.0);
}

vec3 snake(vec2 p, float t) {
    vec2 cell = floor(p * GRID);
    vec2 inCell = fract(p * GRID);
    float n = GRID.x * GRID.y;
    float row = cell.y;
    float k = row * GRID.x + (mod(row, 2.0) < 0.5 ? cell.x : GRID.x - 1.0 - cell.x); // (its place on the snake's way)
    float head = floor(t * 7.0);
    float len = 5.0 + floor(mod(head, n) / 24.0) * 2.0; // (longer with each bite)
    float behind = mod(head - k, n);
    vec3 col = vec3(0.0);
    float inner = boxGlow(inCell, vec2(0.12), vec2(0.88), 30.0);
    if (behind < len) {
        float fade = 1.0 - behind / (len + 4.0);
        col = mix(vec3(0.1, 0.55, 0.15), vec3(0.45, 1.0, 0.3), fade) * inner;
        if (behind < 0.5) {
            col = vec3(0.75, 1.0, 0.5) * inner;
        }
    }
    float food = mod(floor(mod(head, n) / 24.0) * 24.0 + 24.0, n); // (always the next bite on its way)
    if (abs(k - food) < 0.5) {
        col += vec3(1.0, 0.2, 0.25) * boxGlow(inCell, vec2(0.3), vec2(0.7), 14.0) * (0.8 + 0.2 * sin(t * 12.0));
    }
    return col + vec3(0.04, 0.06, 0.12);
}

vec3 breakout(vec2 p, float t, float rnd) {
    vec3 col = vec3(0.02, 0.02, 0.07);
    // the bricks: 8 by 5 at the top, going one by one as the rnd goes on
    vec2 b = vec2(p.x * 8.0, (p.y - 0.62) * 15.0);
    if (b.y >= 0.0 && b.y < 5.0) {
        vec2 id = floor(b);
        float gone = hash(id + rnd * 3.1);
        if (gone > t / CYCLE * 1.15) {
            vec3 rows[5] = vec3[5](vec3(1.0, 0.25, 0.3), vec3(1.0, 0.6, 0.15), vec3(1.0, 0.95, 0.2), vec3(0.3, 1.0, 0.4), vec3(0.3, 0.6, 1.0));
            vec2 f = fract(b);
            col = rows[int(id.y)] * (boxGlow(f, vec2(0.06, 0.12), vec2(0.94, 0.88), 40.0) * 0.9 + 0.1);
        }
    }
    // the ball, bouncing about the lower part; the paddle under it following
    vec2 ball = vec2(0.04 + 0.92 * tri(t * 0.29 + rnd * 0.13), 0.1 + 0.52 * tri(t * 0.47));
    float d = length((p - ball) * vec2(1.333, 1.0));
    col += vec3(1.0) * smoothstep(0.022, 0.012, d) + vec3(0.5, 0.7, 1.0) * exp(-d * 40.0) * 0.4;
    float px = clamp(ball.x + 0.04 * sin(t * 1.3), 0.1, 0.9);
    col += vec3(0.6, 0.9, 1.0) * boxGlow(p, vec2(px - 0.09, 0.05), vec2(px + 0.09, 0.075), 300.0);
    return col;
}

vec3 tetris(vec2 p, float t, float rnd) {
    vec3 col = vec3(0.02, 0.02, 0.06);
    float ch = 0.045, cw = ch * 0.75; // (square cells on the 4:3 glass)
    vec2 o = vec2(0.5 - cw * 5.0, 0.05);
    vec2 q = (p - o) / vec2(cw, ch);
    if (q.x < -0.3 || q.x > 10.3 || q.y < -0.3 || q.y > 20.0) return col;
    if (q.x < 0.0 || q.x > 10.0 || q.y < 0.0) return vec3(0.23, 0.29, 0.42); // (the well's walls)
    vec2 id = floor(q), f = fract(q);
    float lit = boxGlow(f, vec2(0.08), vec2(0.92), 40.0) * 0.9 + 0.1;
    vec3 pieces[7] = vec3[7](vec3(0.0, 0.9, 1.0), vec3(1.0, 0.9, 0.29), vec3(0.7, 0.36, 1.0), vec3(0.36, 1.0, 0.42),
                             vec3(1.0, 0.3, 0.37), vec3(0.3, 0.65, 1.0), vec3(1.0, 0.6, 0.18));
    // the stack, growing as the round goes on (with holes), and a piece falling onto it
    float stack = floor(t / CYCLE * 15.0);
    if (id.y < stack && hash(id + rnd * 1.7) > 0.18) {
        return pieces[int(hash(vec2(id.y, rnd)) * 6.99)] * lit;
    }
    float drop = fract(t / 2.2);
    float col0 = floor(hash(vec2(floor(t / 2.2), rnd)) * 8.0);
    float py = floor(19.0 - drop * (19.0 - stack));
    if (id.x >= col0 && id.x <= col0 + 1.0 && id.y >= py && id.y <= py + 1.0) {
        return pieces[int(mod(floor(t / 2.2), 7.0))] * lit;
    }
    return col + vec3(0.03, 0.04, 0.08) * step(0.94, max(f.x, f.y));
}

void main() {
    // the tube's curve: the picture bowed out, nothing past its edges
    vec2 c = uv * 2.0 - 1.0;
    c *= 1.0 + dot(c, c) * vec2(0.035, 0.05);
    vec2 p = c * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0) {
        fragColor = vec4(0.01, 0.01, 0.012, 1.0);
        return;
    }
    float rnd = floor(Time / CYCLE);
    float t = mod(Time, CYCLE);
    vec3 col;
    if (Live > 0.5) {
        // someone's game: its pixels, each a little lit square (a fine picture: only a little)
        vec2 cells = max(Cells, vec2(1.0));
        vec2 id = floor(p * cells);
        vec2 f = fract(p * cells);
        col = texture(Sampler0, (id + 0.5) / cells * vec2(1.0, -1.0) + vec2(0.0, 1.0)).rgb;
        float own = cells.x > 48.0 ? 0.3 : 0.85;
        col *= boxGlow(f, vec2(0.07), vec2(0.93), 30.0) * own + (1.0 - own);
    } else {
        float g = mod(rnd, 3.0);
        col = g < 0.5 ? snake(p, Time) : g < 1.5 ? breakout(p, t, rnd) : tetris(p, t, rnd);
        // a blink of white between demos
        col += vec3(0.6) * max(0.0, 1.0 - t * 4.0) * step(1.0, Time);
    }
    // the tube: scanlines, the phosphor's three colours, rolling brightness, dark corners
    float scan = 0.72 + 0.28 * sin(p.y * 360.0);
    float column = mod(floor(gl_FragCoord.x), 3.0);
    vec3 mask = column < 0.5 ? vec3(1.0, 0.8, 0.8) : column < 1.5 ? vec3(0.8, 1.0, 0.8) : vec3(0.8, 0.8, 1.0);
    float roll = 0.94 + 0.06 * sin(p.y * 6.0 - Time * 3.0);
    float vignette = smoothstep(1.25, 0.35, length(c * vec2(0.9, 1.0)));
    col *= scan * mask * roll * vignette * 1.35;
    col = col * Power + vec3(0.012, 0.014, 0.016);
    fragColor = vec4(col, 1.0);
}
