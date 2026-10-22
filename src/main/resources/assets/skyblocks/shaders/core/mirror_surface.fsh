#version 150

// A mirror's face: shows its wall's reflection (drawn from the mirrored camera, same screen layout) at this pixel.

uniform sampler2D Sampler0;
uniform vec2 MirrorScreenSize;

out vec4 fragColor;

void main() {
    fragColor = vec4(texture(Sampler0, gl_FragCoord.xy / MirrorScreenSize).rgb, 1.0);
}
