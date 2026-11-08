#version 150

// Shadered+'s whirlpool (our own code): the vortex's funnel. UV0: x round it (0..1), y down it (0 its rim .. 1 its
// throat); the colour: how dark it is there (deeper: darker) and how see-through; UV2: the light there.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;
out vec4 lightColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    lightColor = texelFetch(Sampler2, UV2 / 16, 0);
}
