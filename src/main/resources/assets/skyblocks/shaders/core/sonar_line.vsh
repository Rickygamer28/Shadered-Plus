#version 150

// Shadered+'s sonar outlines (client/SonarPulse): strips along a found block's edges, camera-relative.
in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 col;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    col = Color;
}
