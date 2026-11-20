#version 150

// A sonar outline's colour (already as bright as it should be), added as light: alpha next to nothing (spin_aura.fsh).
in vec4 col;

out vec4 fragColor;

void main() {
    fragColor = vec4(col.rgb * 1.2, 1.0 / 255.0);
}
