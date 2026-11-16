#version 150

// The time crystal's warp: what's seen through the space inside its dome, bent - slow swirls through the air and rings
// rippling out from the crystal (faster the faster it's set), the colours split a little at the strongest. How strong:
// how far the eye's ray runs inside the dome before it meets something (Sampler1: the picture's depth) - nothing in
// front of the dome is bent, and what's just inside it barely. Sampler0: the picture so far.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float GameTime;

in vec3 at;
flat in vec3 eye;
flat in vec3 centre;
flat in float radius;
flat in float speed;
flat in float strength;
flat in mat4 unproject;
flat in vec2 crystalAt;

out vec4 fragColor;

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / size;
    // how far to what's there
    float depth = texture(Sampler1, uv).r;
    vec4 w = unproject * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 rd = normalize(at - eye);
    float sceneT = depth >= 1.0 ? 1e6 : dot(w.xyz / w.w - eye, rd);
    // the ray inside the dome: in its ball, above its floor
    vec3 oc = eye - centre;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - radius * radius);
    if (h <= 0.0) {
        discard;
    }
    h = sqrt(h);
    float t0 = -b - h, t1 = -b + h;
    if (abs(rd.y) > 1e-5) {
        float tp = (centre.y - eye.y) / rd.y;
        if (rd.y > 0.0) {
            t0 = max(t0, tp);
        } else {
            t1 = min(t1, tp);
        }
    } else if (eye.y < centre.y) {
        discard;
    }
    t0 = max(t0, 0.0);
    t1 = min(t1, sceneT);
    float inside = t1 - t0;
    if (inside <= 0.0) {
        discard;
    }
    float amount = smoothstep(0.0, 4.0, inside) * strength;
    float t = GameTime * 1200.0 * (0.6 + 0.35 * speed);
    // the middle of the stretch inside, from the crystal
    vec3 m = eye + rd * (t0 + t1) * 0.5 - centre;
    float r = length(m.xz);
    float ripple = sin(r * 2.4 - t * 0.35) * exp(-r / max(radius, 1.0) * 0.8);
    vec2 swirl = vec2(sin(m.y * 1.7 + t * 0.11 + m.x * 0.9), cos(m.x * 1.3 - t * 0.09 + m.z * 1.1));
    // (on the screen: the ripple pushes out from the crystal's place on it)
    vec2 away = crystalAt.x < -5.0 ? vec2(0.0) : uv - crystalAt;
    away = length(away) > 1e-4 ? normalize(away) : vec2(0.0);
    vec2 offset = (swirl * 0.0045 + away * ripple * 0.006) * amount;
    vec3 col;
    col.r = texture(Sampler0, uv + offset * 1.25).r;
    col.g = texture(Sampler0, uv + offset).g;
    col.b = texture(Sampler0, uv + offset * 0.75).b;
    // a faint violet in the ripples' crests
    col += vec3(0.5, 0.3, 1.0) * max(ripple, 0.0) * 0.035 * amount;
    fragColor = vec4(col, 1.0);
}
