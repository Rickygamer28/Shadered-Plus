#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// this point -> where it is in the reflection picture (the picture's clip space)
uniform mat4 MirrorTexMat;

out vec4 pictureClip;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    pictureClip = MirrorTexMat * vec4(Position, 1.0);
}
