#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
// this point -> where it is in the reflection picture (the picture's clip space)
uniform mat4 MirrorTexMat;

out vec4 pictureClip;
out vec2 faceUv;      // (0..1 across this block's face)
flat out vec3 faceCode; // which mirror, its own number, which sides join on to more of it (client/MirrorRenderer.faceMesh)

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    pictureClip = MirrorTexMat * vec4(Position, 1.0);
    vec2 hi = floor(UV0 / 4.0 + 0.0001);
    faceUv = UV0 - hi * 4.0;
    float kind = mod(hi.x, 4.0);
    faceCode = vec3(kind, floor(hi.x / 4.0), hi.y);
}
