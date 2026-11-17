#version 150

// Aquarium Water (client/AquariumWaterRenderer): positions are relative to the camera.
in vec3 Position;
in vec2 UV0;   // x: how deep under the surface, y: how thick the water is behind this face (blocks)
in vec4 Color; // r: a top face; gba: the face's outward normal

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 vPos;
out vec2 vInfo;
out float vTop;
out vec3 vNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vPos = Position;
    vInfo = UV0;
    vTop = Color.r;
    vNormal = Color.gba * 2.0 - 1.0;
}
