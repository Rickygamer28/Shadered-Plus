#version 150

// Shadered+'s lava lamp (our own code): the view ray, inside the block, finds the round glass, then the blobs of wax
// in the liquid - rising from the warm bottom, stretching, merging and breaking up, sinking again. Glowing.

in vec3 wpos;
in vec4 vColor;
in vec2 uv; // (big lamp or not, the height in the lamp's block)

uniform vec3 CamPos;
uniform float LampTime;

out vec4 fragColor;

float hash1(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

float hash11(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

// the glass: round, widest low down, narrowing towards the top (block coordinates: y 0.32 .. 0.8)
float radiusAt(float y) {
    float u = clamp((y - 0.32) / 0.48, 0.0, 1.0);
    return 0.158 - 0.075 * u * u - 0.025 * smoothstep(0.1, 0.0, u);
}

float glass(vec3 p) {
    return max(length(p.xz - 0.5) - radiusAt(p.y), max(0.315 - p.y, p.y - 0.8)) * 0.9;
}

float wax(vec3 p, float t, float on) {
    // the pool of wax at the bottom, and a little at the top
    float d = (length(vec3(p.x - 0.5, (p.y - 0.33) * 2.6, p.z - 0.5)) - 0.13) / 2.6;
    d = smin(d, (length(vec3(p.x - 0.5, (p.y - 0.795) * 3.0, p.z - 0.5)) - 0.06) / 3.0, 0.02);
    for (int k = 0; k < 6; k++) {
        float fk = float(k);
        float sp = 0.035 + 0.035 * hash11(fk * 3.7);
        float ph = hash11(fk * 7.1) * 6.2831;
        float up = 0.5 - 0.5 * cos(t * sp * 6.2831 + ph); // (0 at the bottom .. 1 at the top)
        float rising = sin(t * sp * 6.2831 + ph);          // (which way it's going: it stretches as it goes)
        float y = mix(0.355, 0.75, up * on);
        float a = ph * 3.0 + t * 0.05;
        float rr = (0.015 + 0.04 * hash11(fk * 1.3)) * radiusAt(y) / 0.158;
        vec3 c = vec3(0.5 + rr * cos(a), y, 0.5 + rr * sin(a));
        float r = mix(0.05, 0.03, up) * (0.75 + 0.5 * hash11(fk * 5.9)) * (0.6 + 0.4 * on);
        float stretch = 1.0 + 0.35 * abs(rising) * on;
        vec3 q = p - c;
        q.y /= stretch;
        d = smin(d, (length(q) - r) * min(stretch, 1.0 / stretch + 0.5), 0.06);
    }
    return max(d, glass(p) + 0.006); // (always inside the glass)
}

void main() {
    vec3 world = wpos + CamPos;
    vec3 org = floor(world);
    org.y = floor(world.y - uv.y + 0.5); // (the block the lamp stands in, even for the top of the big lamp)
    vec3 p0 = world - org;
    vec3 rd = normalize(wpos);
    if (uv.x > 0.5) {
        // the big lamp: work in the small lamp's space (stretched across and up; client/LavaLampRenderer's numbers)
        vec3 sc = vec3(1.55, 2.2, 1.55);
        p0 = vec3((p0.x - 0.5) / sc.x + 0.5, (p0.y + 0.143) / sc.y, (p0.z - 0.5) / sc.z + 0.5);
        rd = normalize(rd / sc);
    }
    float seed = hash1(org);
    float t = LampTime + seed * 137.0;
    float on = vColor.a > 0.5 ? 1.0 : 0.0;
    vec3 wc = vColor.rgb;

    // find the glass
    float tt = 0.0;
    bool hit = false;
    for (int i = 0; i < 48; i++) {
        float d = glass(p0 + rd * tt);
        if (d < 0.0008) {
            hit = true;
            break;
        }
        tt += max(d, 0.0015);
        if (tt > 0.9) {
            break;
        }
    }
    if (!hit) {
        discard;
    }
    vec3 pg = p0 + rd * tt;
    vec3 gn = normalize(vec3(pg.x - 0.5, 0.25 * (radiusAt(pg.y - 0.01) - radiusAt(pg.y + 0.01)) / 0.02, pg.z - 0.5));
    if (pg.y > 0.795) gn = vec3(0.0, 1.0, 0.0);
    if (pg.y < 0.32) gn = vec3(0.0, -1.0, 0.0);

    // the liquid, lit from the warm bottom (when on)
    vec3 liquid = wc * 0.18 + vec3(0.035, 0.02, 0.05);
    float bottomGlow = on * (0.35 + 0.65 * smoothstep(0.62, 0.32, pg.y));
    vec3 col = liquid * (0.5 + 1.6 * bottomGlow);

    // the wax
    float ts = tt + 0.002;
    bool w = false;
    vec3 q;
    for (int i = 0; i < 64; i++) {
        q = p0 + rd * ts;
        if (glass(q) > 0.003) {
            break;
        }
        float d = wax(q, t, on);
        if (d < 0.0012) {
            w = true;
            break;
        }
        ts += max(d * 0.8, 0.0025);
    }
    if (w) {
        vec2 e = vec2(0.002, -0.002);
        vec3 n = normalize(e.xyy * wax(q + e.xyy, t, on) + e.yyx * wax(q + e.yyx, t, on)
                         + e.yxy * wax(q + e.yxy, t, on) + e.xxx * wax(q + e.xxx, t, on));
        float heat = on * (0.55 + 0.45 * smoothstep(0.65, 0.33, q.y));
        float rim = pow(1.0 - max(dot(n, -rd), 0.0), 2.0);
        vec3 waxCol = wc * (0.25 + 1.1 * heat) * (0.75 + 0.25 * n.y) + wc * rim * 0.6 * (0.3 + on);
        col = mix(waxCol, col, clamp((ts - tt) * 2.0, 0.0, 0.35)); // (deeper in, a little of the liquid's colour)
    }
    // the glass: a sheen at the edges, a highlight
    float fres = pow(1.0 - abs(dot(gn, -rd)), 3.0);
    col += vec3(0.55, 0.62, 0.7) * fres * 0.35;
    col += vec3(1.0) * pow(max(dot(reflect(rd, gn), normalize(vec3(0.3, 1.0, 0.2))), 0.0), 40.0) * 0.5;
    fragColor = vec4(col, 1.0);
}
