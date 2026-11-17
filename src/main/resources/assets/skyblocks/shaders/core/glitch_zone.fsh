#version 150

// The glitch zone: what's seen within its reach of the block (a ball; where the picture's depth puts it) stutters -
// a slight jitter all the time, and in bursts (every few seconds, a few frames long) it tears into slices shifted
// sideways, its colours split, blocky in places. Outside the ball (and what's in front of it), untouched.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float GameTime;

in vec3 at;
flat in vec3 eye;
flat in vec3 centre;
flat in float reach;
flat in float seed;
flat in mat4 unproject;

out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 uv = gl_FragCoord.xy / size;
    float depth = texture(Sampler1, uv).r;
    if (depth >= 1.0) {
        discard; // (the sky)
    }
    vec4 w = unproject * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 p = w.xyz / w.w;
    float d = length(p - centre) / reach;
    if (d > 1.0) {
        discard;
    }
    float t = GameTime * 1200.0;
    float edge = smoothstep(1.0, 0.75, d);
    // bursts: a few frames every few seconds; between them, a slight jitter
    float slot = floor(t / 6.0);
    float burst = step(0.82, hash(vec3(slot, seed, 1.0)));
    float frame = floor(t * 1.5);
    float strength = (0.12 + burst * 0.88) * edge;
    float row = floor(gl_FragCoord.y / mix(14.0, 4.0, hash(vec3(frame, seed, 2.0))));
    float tear = (hash(vec3(row, frame, seed)) - 0.5) * 0.12 * strength * step(0.55, hash(vec3(row, frame, 7.0)));
    vec2 q = uv + vec2(tear, 0.0);
    // blocky in places
    if (burst > 0.5 && hash(vec3(floor(uv * 12.0), frame)) > 0.8) {
        vec2 px = vec2(24.0);
        q = (floor(q * size / px) + 0.5) * px / size;
    }
    float split = 0.008 * strength;
    vec3 col;
    col.r = texture(Sampler0, q + vec2(split, 0.0)).r;
    col.g = texture(Sampler0, q).g;
    col.b = texture(Sampler0, q - vec2(split, 0.0)).b;
    // now and then a band of the missing-texture magenta
    col = mix(col, vec3(0.97, 0.0, 0.86), burst * edge * step(0.97, hash(vec3(row, frame, 11.0))) * 0.6);
    fragColor = vec4(col, 1.0);
}
