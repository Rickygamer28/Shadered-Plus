#version 150

// The vortex: arms of the pad's sky (its picture, swirled round with the spin) spiralling up the funnel, bright at
// their edges, the gaps between them clear; streaks of light whipping round faster the faster it spins; fading out at
// the funnel's foot and its rim.

uniform sampler2D Sampler0;
uniform float GameTime;

in vec2 at;
in float speed;
in float turn;
in float strength;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    float round_ = at.x, up = at.y;
    // the arms: turning with the pad, twisting as they rise (more arms, tighter, the faster it goes)
    float arms = 3.0;
    float swirl = round_ * arms + up * (1.2 + 1.6 * speed) - turn * arms; // (turning the way the plate does)
    float arm = abs(fract(swirl) - 0.5) * 2.0;           // 0 in an arm's middle .. 1 between arms
    float width = 0.45 + 0.25 * speed;
    float body = smoothstep(width, width - 0.25, arm);
    float edge = smoothstep(width - 0.05, width - 0.12, arm) * (1.0 - smoothstep(width - 0.12, width - 0.3, arm));
    // the sky in the arms: its picture, wrapped round and dragged along by the spin
    vec2 skyAt = vec2(fract(round_ - turn + up * 0.25), 0.3 + 0.45 * up);
    vec3 sky = texture(Sampler0, skyAt).rgb;
    sky = mix(sky, sky * 1.5 + 0.05, 0.4);
    // streaks of light racing round
    float lane = floor(up * 14.0);
    float streak = fract(round_ * 2.0 - turn * (2.0 + floor(hash(vec2(lane, 1.0)) * 3.0)) + hash(vec2(lane, 7.0)));
    float spark = smoothstep(0.92, 1.0, streak) * step(0.55, hash(vec2(lane, 3.0))) * speed;
    // fading at its foot and its rim
    float fade = smoothstep(0.0, 0.15, up) * (1.0 - smoothstep(0.75, 1.0, up));
    vec3 col = sky * body + vec3(0.85, 0.92, 1.0) * (edge * 0.8 + spark * 1.2);
    float a = clamp(body * 0.85 + edge * 0.6 + spark, 0.0, 1.0) * fade * strength;
    if (a < 0.01) {
        discard;
    }
    fragColor = vec4(col, a);
}
