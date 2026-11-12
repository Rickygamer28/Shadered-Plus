#version 150

// The sky lantern's light: the colour of its sky - the average of the sky's picture (a grid of samples over its
// middle, as the lantern shows it), brightened to a light's colour - added onto the block's own colours, as strongly as
// the lantern reaches there.

in vec4 vertexColor;

uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 5; i++) {
        for (int j = 0; j < 5; j++) {
            vec2 q = vec2(0.15 + 0.175 * float(i), 0.15 + 0.175 * float(j));
            sum += textureLod(Sampler0, q, 0.0).rgb;
        }
    }
    vec3 c = sum / 25.0;
    // (a light, not a picture: its hue kept, its brightness lifted - a dark sky still gives a dim, coloured light)
    float peak = max(max(c.r, c.g), c.b);
    vec3 hue = c / max(peak, 0.04);
    vec3 light = hue * mix(0.45, 1.0, clamp(peak * 1.6, 0.0, 1.0));
    fragColor = vec4(light, vertexColor.a);
}
