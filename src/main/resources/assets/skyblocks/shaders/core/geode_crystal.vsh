#version 150

// Shadered+'s geode lamp crystals: faceted prisms, coloured per crystal (Color), lit from inside.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vColor;
out vec3 vNormal;
out vec3 vView;
out vec3 vPos;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vColor = Color;
    vNormal = normalize(mat3(ModelViewMat) * Normal);
    vView = view.xyz;
    vPos = Position;
}
