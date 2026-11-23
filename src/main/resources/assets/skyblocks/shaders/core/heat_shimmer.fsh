#version 150

// Heat shimmer: what's behind the hot air (the frame so far, Sampler0) seen a little bent, the bends rising and
// wavering - strongest low in the middle of the sheet, fading to nothing at its edges and top, and smaller far away.

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float GameTime;

in vec2 uv;

out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}


void main() {
    float t = GameTime * 1200.0;
    vec2 p = vec2(uv.x * 2.5, uv.y * 3.0 - t * 1.8);
    vec2 bend = vec2(noise(vec3(p, t * 0.7)), noise(vec3(p + 5.2, t * 0.7))) - 0.5;
    bend += (vec2(noise(vec3(p * 2.7, t)), noise(vec3(p * 2.7 + 9.1, t))) - 0.5) * 0.5;
    float fade = smoothstep(1.0, 0.4, abs(uv.x)) * smoothstep(0.0, 0.15, uv.y) * smoothstep(1.0, 0.45, uv.y);
    float far = clamp(gl_FragCoord.w * 4.0, 0.15, 1.0);
    vec2 at = gl_FragCoord.xy / ScreenSize + bend * 0.012 * fade * far;
    fragColor = vec4(texture(Sampler0, at).rgb, 1.0);
}
