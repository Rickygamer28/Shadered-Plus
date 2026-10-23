#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Pull;

// Pulled towards the camera by Pull blocks along its own line of sight (same place on screen, only nearer), for
// the depth tests: in a Sky Island the faces are tiny, and with a shader pack the world's depth is jittered by its
// anti-aliasing, so the island's own blocks right under the faces won now and then and the faces flickered.
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
}
