#version 150

// The sky lantern's light: the colour of its sky - the average of the sky's picture (a grid of samples over its
// middle, as the lantern shows it), as a light's colour (its hue, at full strength) - the block's own colours tinted
// towards it (multiplied in: SkyLanternRenderer), as strongly as the lantern reaches there.

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
    // (a light, not a picture: its hue, its brightest channel full - a dark sky still gives a coloured light)
    float peak = max(max(c.r, c.g), c.b);
    vec3 hue = peak < 0.02 ? vec3(1.0) : c / peak;
    // (a grey sky barely tints; a colourful one strongly: its colour a little richer than the picture's average)
    float grey = (hue.r + hue.g + hue.b) / 3.0;
    hue = clamp(mix(vec3(grey), hue, 1.35), 0.0, 1.0);
    fragColor = vec4(mix(vec3(1.0), hue, clamp(vertexColor.a, 0.0, 1.0)), 1.0);
}
