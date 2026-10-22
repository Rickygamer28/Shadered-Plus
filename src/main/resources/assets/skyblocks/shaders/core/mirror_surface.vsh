#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Pull;
// this point -> where it is in the reflection picture (the picture's clip space)
uniform mat4 MirrorTexMat;

out vec4 pictureClip;

// Pulled towards the camera by Pull blocks along its own line of sight (same spot on screen, only nearer): with a
// shader pack the world's depth is jittered by its anti-aliasing, and the floor right at a mirror's base came out
// in front of the face's bottom row of pixels while moving, leaving a line of the plain block showing.
vec4 pulled(vec4 view) {
    float distance = length(view.xyz);
    if (Pull <= 0.0 || distance <= 0.0) {
        return view;
    }
    float margin = max(Pull, distance * Pull * 0.15);
    return vec4(view.xyz * max(1.0 - margin / distance, 0.05), view.w);
}

void main() {
    gl_Position = ProjMat * pulled(ModelViewMat * vec4(Position, 1.0));
    pictureClip = MirrorTexMat * vec4(Position, 1.0);
}
