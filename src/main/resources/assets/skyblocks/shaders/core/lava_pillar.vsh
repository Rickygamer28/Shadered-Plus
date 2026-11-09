#version 150

// Shadered+'s lava lamp pillar: a box round its glass, the glass and the wax inside traced in the fragment stage.
// PoseInv takes the drawn positions back to the block's own space (the camera is at their origin).

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 PoseInv;

out vec3 lp;
flat out vec3 eye;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    lp = (PoseInv * vec4(Position, 1.0)).xyz;
    eye = (PoseInv * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
}
