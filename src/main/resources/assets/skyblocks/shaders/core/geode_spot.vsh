#version 150

// Shadered+'s geode lamp's spots of light on the walls. UV0: the corner (-1 .. 1) + 4 * the spot's seed (across), the
// corner (up). Color: the light's colour, and how strong it is (alpha).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 corner;
out vec4 vColor;
flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    float s = floor((UV0.x + 1.5) / 4.0);
    seed = s;
    corner = vec2(UV0.x - 4.0 * s, UV0.y);
    vColor = Color;
}
