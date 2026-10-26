#version 150

// Shadered+'s warp block (our own code): like looking through the block into another dimension. Sixteen layers of a
// glowing vein net, each turned its own way, its own size, drifting at its own speed, fixed to the screen (so it
// doesn't move with the block, like the End portal), each layer tinted from deep teal to bright blue, added up.

in vec4 clip;
in vec4 vColor;

uniform sampler2D Sampler0;
uniform float WarpTime;
uniform vec2 ScreenSize;

out vec4 fragColor;

vec3 layerColour(int i) {
    float f = float(i) / 15.0;
    vec3 c = mix(vec3(0.02, 0.07, 0.08), vec3(0.07, 0.3, 0.62), f * f);
    c += vec3(0.04, 0.02, 0.06) * sin(float(i) * 2.7 + 1.0); // (a little variation, some layers more violet)
    return max(c, vec3(0.0));
}

void main() {
    vec2 sp = clip.xy / clip.w;
    sp.x *= ScreenSize.x / max(ScreenSize.y, 1.0);
    float t = WarpTime;
    vec3 col = vec3(0.0);
    for (int i = 1; i <= 16; i++) {
        float fi = float(i);
        float a = fi * fi * 1.37 + fi * 0.61;
        mat2 rot = mat2(cos(a), sin(a), -sin(a), cos(a));
        float scale = 0.55 + 0.045 * fi;               // (the near layers bigger, the far ones finer)
        vec2 p = rot * (sp * scale) + vec2(fi * 0.173, t * (0.012 + fi * 0.0055));
        col += texture(Sampler0, p).r * layerColour(i - 1);
    }
    col *= vColor.rgb * 1.6;
    fragColor = vec4(col, 1.0);
}
