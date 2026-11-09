#version 150

// Shadered+'s weather globe: a box round the glass ball, the ball and what's inside it traced in the fragment stage.
// PoseInv takes the drawn positions back to the block's own space (the camera is at their origin).

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 PoseInv;

out vec3 lp;
out vec3 eye;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    lp = (PoseInv * vec4(Position, 1.0)).xyz;
    // the camera: in a perspective view where the drawn positions' origin is (as for blocks), else (an item in a
    // menu: straight on) far back along the view, from each point
    mat4 iv = inverse(ModelViewMat);
    if (ProjMat[2][3] < -0.5) {
        eye = (PoseInv * vec4((iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz, 1.0)).xyz;
    } else {
        vec3 back = (PoseInv * vec4((iv * vec4(0.0, 0.0, 1.0, 0.0)).xyz, 0.0)).xyz;
        eye = lp + normalize(back) * 8.0;
    }
}
