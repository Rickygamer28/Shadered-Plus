#version 150

// The colour, and the distance along the view (straight in front of the camera), as sqrt(distance / 256) in the
// alpha (1: nothing there, the sky).

uniform sampler2D Sampler0; // colour
uniform sampler2D Sampler1; // depth
uniform float Near;
uniform float Far;

in vec2 uv;

out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, uv).rgb;
    float d = texture(Sampler1, uv).r;
    float a = 1.0;
    if (d < 0.99999) {
        float z = 2.0 * Near * Far / (Far + Near - (2.0 * d - 1.0) * (Far - Near));
        a = min(sqrt(z / 256.0), 0.996);
    }
    fragColor = vec4(c, a);
}
