#version 150

// The aura, drawn adding light. On the floor: a hypnotic spiral wheeling round under the cat, in the pad's sky and
// slowly changing colours, wider the faster it spins. Round the cat: its spin made visible - comet streaks of colour
// whipping round it (faster, longer and more of them the faster it goes), and rings of light rising off the plate.

uniform sampler2D Sampler0; // the pad's sky
uniform float GameTime;

in vec2 at;
in float speed;
in float turn;
in float part;
in float strength;

out vec4 fragColor;

const float TAU = 6.2831853;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    float seconds = GameTime * 1200.0;
    vec3 col;
    if (part < 0.5) {
        // the floor: a spiral of six arms turning with the plate, tighter the faster
        vec2 p = at * 2.0 - 1.0;
        float r = length(p);
        if (r > 1.0) {
            discard;
        }
        float round_ = atan(p.y, p.x) / TAU;
        float arms = 6.0;
        float twist = (0.9 + 1.4 * speed) * log(r + 0.08);
        float s = fract(round_ * arms + twist + turn * arms);
        float arm = smoothstep(0.5, 0.15, abs(s - 0.5));
        vec3 sky = texture(Sampler0, vec2(fract(round_ - turn), 0.35 + 0.3 * r)).rgb;
        vec3 tint = hue(round_ + r * 0.6 - seconds * 0.08);
        vec3 c = mix(sky * 1.6 + 0.05, tint, 0.45);
        // a bright ring hugging the plate, and the spiral fading out towards the rim
        float plate = smoothstep(0.06, 0.0, abs(r - 0.2)) * (0.6 + 0.4 * speed);
        float fade = pow(1.0 - r, 1.4) * smoothstep(0.12, 0.2, r);
        col = c * arm * fade * (0.55 + 0.45 * speed) + vec3(0.75, 0.9, 1.0) * plate;
    } else {
        // round the cat: lanes of comet streaks, each racing round at its own pace
        float round_ = at.x, up = at.y;
        float lanes = 10.0;
        float lane = floor(up * lanes);
        float inLane = fract(up * lanes);
        float pace = 1.0 + floor(hash(vec2(lane, 2.0)) * 3.0);
        float head = fract(round_ - turn * pace + hash(vec2(lane, 9.0)));
        float tail = pow(head, 14.0 - 8.0 * speed) * smoothstep(1.0, 0.97, head); // (a bright head, its streak trailing behind)
        float thin = smoothstep(0.5, 0.15, abs(inLane - 0.5));
        float lit = step(0.35 - 0.3 * speed, hash(vec2(lane, 5.0)));   // (more lanes lit up the faster it spins)
        vec3 c = hue(hash(vec2(lane, 4.0)) + seconds * 0.1) * 0.6 + texture(Sampler0, vec2(fract(round_ + turn), 0.5)).rgb * 0.6;
        float streak = tail * thin * lit;
        // rings rising off the plate, closer together the faster it goes
        float rise = fract(up * (2.0 + 2.0 * speed) - seconds * (0.4 + 1.6 * speed));
        float ring = smoothstep(0.05, 0.0, abs(rise - 0.5)) * 0.45;
        float fade = smoothstep(0.0, 0.12, up) * (1.0 - smoothstep(0.6, 1.0, up));
        col = (c * streak * 1.3 + vec3(0.8, 0.9, 1.0) * ring * speed) * fade;
    }
    col *= strength;
    if (max(col.r, max(col.g, col.b)) < 0.004) {
        discard;
    }
    fragColor = vec4(col, 1.0);
}
