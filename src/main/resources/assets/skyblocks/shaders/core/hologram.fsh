#version 150

// Shadered+'s hologram (our own code): the item's texture as brightness in the hologram's colour, see-through,
// fine scan lines drifting up, a soft flicker, now and then a glitch band, and bright edges where it curves away.

in vec2 uv;
in vec4 vColor;
in vec3 vNormal;
in vec3 wray;
in float wy;

uniform sampler2D Sampler0;
uniform vec3 HoloColor;
uniform float HoloTime;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, uv) * vColor;
    if (tex.a < 0.1) {
        discard;
    }
    float t = HoloTime;
    float lum = dot(tex.rgb, vec3(0.3, 0.59, 0.11));
    float scan = 0.7 + 0.3 * sin(wy * 160.0 - t * 6.0);
    float band = smoothstep(0.02, 0.0, abs(fract(wy * 1.3 - t * 0.35) - 0.5) - 0.02); // (a brighter band sliding up)
    float flicker = 0.9 + 0.1 * sin(t * 31.0) * sin(t * 7.3);
    float edge = pow(1.0 - abs(dot(normalize(vNormal), normalize(wray))), 2.0);
    vec3 col = HoloColor * (0.18 + 0.95 * lum) * scan * flicker + HoloColor * edge * 0.55 + mix(HoloColor, vec3(1.0), 0.5) * band * 0.25;
    fragColor = vec4(col, 0.8);
}
