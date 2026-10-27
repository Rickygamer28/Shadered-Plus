#version 150

// A mirror's face: shows its wall's reflection, looked up where this point was in the picture when it was taken (so
// a picture from a few frames ago still lines up with the mirror).
//
// The picture is cleared with alpha 0, so a texel nothing was drawn on is known. Right below the floor line at the
// mirror's base there is nothing to draw (it's inside the ground), and the slightest shift (a shader pack's
// anti-aliasing jitter, filtering) let that empty colour show as a white line along the bottom of the mirror. An
// empty texel takes the nearest drawn one within a few texels instead; a wide empty area (open sky with nothing
// on it) keeps its own colour.

uniform sampler2D Sampler0;
// how far into the picture it was drawn (the rest is left over from before)
uniform vec2 MirrorTexMax;

in vec4 pictureClip;

out vec4 fragColor;

const int REACH = 4;

void main() {
    vec2 uv = clamp(pictureClip.xy / pictureClip.w * 0.5 + 0.5, vec2(0.0), MirrorTexMax);
    vec4 here = texture(Sampler0, uv);
    if (here.a > 0.99) {
        fragColor = vec4(here.rgb, 1.0);
        return;
    }
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    for (int r = 1; r <= REACH; r++) {
        for (int i = 0; i < 8; i++) {
            float angle = float(i) * 0.785398;
            vec2 at = clamp(uv + vec2(cos(angle), sin(angle)) * float(r) * texel, vec2(0.0), MirrorTexMax);
            vec4 near = texture(Sampler0, at);
            if (near.a > 0.99) {
                fragColor = vec4(near.rgb, 1.0);
                return;
            }
        }
    }
    // partly drawn (blended edge of something see-through) or wide open: what is there
    fragColor = vec4(here.rgb, 1.0);
}
