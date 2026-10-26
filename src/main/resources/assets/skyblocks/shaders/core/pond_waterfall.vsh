#version 150

// Shadered+'s pond waterfall water, as the koi pond's own (our own code): UV0 = (across it 0..1, along it in blocks,
// growing downstream); Color = (foam, still, -, alpha).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 vColor;
out vec3 viewPos;

void main() {
    vec4 v = ModelViewMat * vec4(Position, 1.0);
    viewPos = v.xyz;
    gl_Position = ProjMat * v;
    uv = UV0;
    vColor = Color;
}
