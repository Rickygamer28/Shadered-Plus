#version 150

// A sonar's found blocks, added as light (alpha next to nothing - spin_aura.fsh): their edges bright (alpha 1), their
// faces (alpha 0) a soft fill with a glint of light sweeping across them, now and then a second - shiny, through walls.
in vec4 col;

uniform float GameTime;

out vec4 fragColor;

void main() {
    if (col.a > 0.5) {
        fragColor = vec4(col.rgb * 1.2, 1.0 / 255.0);
        return;
    }
    float t = GameTime * 1200.0;
    float d = (gl_FragCoord.x + gl_FragCoord.y) * 0.0025 - t * 0.7;
    float glint = pow(1.0 - abs(fract(d) * 2.0 - 1.0), 14.0);
    float shimmer = 0.85 + 0.15 * sin(gl_FragCoord.y * 0.15 + t * 6.0);
    vec3 c = col.rgb * (0.22 * shimmer + glint * 0.55) + vec3(1.0) * glint * 0.18 * max(max(col.r, col.g), col.b);
    fragColor = vec4(c, 1.0 / 255.0);
}
