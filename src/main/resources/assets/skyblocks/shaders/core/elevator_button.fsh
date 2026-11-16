#version 150

in vec4 colour;

out vec4 fragColor;

void main() {
    if (colour.a < 0.01) {
        discard;
    }
    fragColor = colour;
}
