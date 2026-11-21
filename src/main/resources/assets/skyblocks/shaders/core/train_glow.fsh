#version 150

uniform float GameTime;

in vec4 col;
in vec2 uv;

out vec4 fragColor;

void main() {
    float T = GameTime * 1200.0;
    float across = exp(-uv.x * uv.x * 5.0) * 0.7 + exp(-uv.x * uv.x * 60.0) * 0.8;
    float pulse = 0.75 + 0.25 * sin(uv.y * 2.2 - T * 5.0);
    float sparks = pow(max(sin(uv.y * 0.7 - T * 9.0), 0.0), 18.0) * 0.8;
    vec3 c = col.rgb * col.a * across * (pulse + sparks);
    fragColor = vec4(c, 1.0 / 255.0);
}
