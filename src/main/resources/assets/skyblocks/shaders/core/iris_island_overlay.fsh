#version 150

// Lays Island Paintings and island cats (drawn into Shadered+'s own target while an Iris shader pack is active, already
// hidden wherever something is in front of them) over the pack's final image, where the target has something.
// Adapted from Shadered Compat Fix's iris_overlay (github.com/Rickygamer28/shadered-compat-fix, MIT License).

// Not called Sampler0: Minecraft fills Sampler0-11 from RenderSystem's shader textures on every draw.
uniform sampler2D OverlaySampler;    // our colors (premultiplied, transparent where nothing was drawn)

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texelFetch(OverlaySampler, ivec2(gl_FragCoord.xy), 0);
    if (color.a <= 0.0 && dot(color.rgb, color.rgb) <= 0.0) {
        discard;
    }
    // A pixel with color but no alpha (alpha writes were masked off) is solid.
    if (color.a <= 0.0) {
        color.a = 1.0;
    }
    fragColor = color;
}
