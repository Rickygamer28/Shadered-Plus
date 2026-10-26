#version 150

// Shadered+'s warp block: the block's faces; where on the screen each point is (for the layers drifting across it).

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 clip;
out vec4 vColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    clip = gl_Position;
    vColor = Color;
}
