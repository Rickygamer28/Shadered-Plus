#version 150

// Shadered+'s ferrofluid: its surface (Position: from the camera, the world's way round; Normal: the surface's, the
// world's way round; Color: how lit it is where it stands).

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 worldPos;
out vec3 worldNormal;
out float lit;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    worldPos = Position;
    worldNormal = Normal;
    lit = Color.r;
}
