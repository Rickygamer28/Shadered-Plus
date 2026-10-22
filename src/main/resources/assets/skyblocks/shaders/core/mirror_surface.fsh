#version 150

// A mirror's face: shows its wall's reflection, looked up where this point was in the picture when it was taken (so
// a picture from a few frames ago still lines up with the mirror).

uniform sampler2D Sampler0;

in vec4 pictureClip;

out vec4 fragColor;

void main() {
    vec2 uv = pictureClip.xy / pictureClip.w * 0.5 + 0.5;
    fragColor = vec4(texture(Sampler0, clamp(uv, 0.0, 1.0)).rgb, 1.0);
}
