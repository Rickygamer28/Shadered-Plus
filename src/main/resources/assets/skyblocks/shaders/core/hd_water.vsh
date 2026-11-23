#version 150

// Shadered+'s HD water (the Water Fountain): UV0 - on the surface, the world's x and z; on a stream, along it (blocks)
// and across it (-1 .. 1). Color: red 0 the surface / 1 a stream, green foam, blue daylight, alpha how see-through.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec3 viewPos;
out vec4 data;

void main() {
    vec4 v = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * v;
    viewPos = v.xyz;
    uv = UV0;
    data = Color;
}
