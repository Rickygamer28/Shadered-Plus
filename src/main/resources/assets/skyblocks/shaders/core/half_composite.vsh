#version 150

// A half-size picture put back on screen over the faces it was drawn for (positions relative to the camera, as they
// were drawn: the same place on screen and the same depth).

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
