#version 150

// Adapted from Draconic Evolution by brandon3055 (https://github.com/Draconic-Inc/Draconic-Evolution), used under its
// "Don't Be a Jerk" licence (portions of the code may be copied into other projects; source kept public).
// Shadered+ credits brandon3055 / Draconic Evolution in the blocks' tooltips. All original rights his.
// (chaos_block.fsh; the texture it reads is Shadered+'s own, not Draconic Evolution's.)

#moj_import <skyblocks:brandon_chaos.glsl>

uniform sampler2D Sampler0;
uniform mat4 ProjMat;
uniform float Time;
uniform float Yaw;
uniform float Pitch;
uniform float Alpha;

in vec2 texCoord0;
in vec3 fPos;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // (the face's normal from the position's slope, instead of a normal in the vertex data)
    vec3 n = normalize(cross(dFdx(fPos), dFdy(fPos)));
    vec2 posMod = normalize(ProjMat * vec4(n, 0.0)).xy / 100.0;
    fragColor = chaos(Sampler0, Time, Yaw, Pitch, Alpha, fPos, posMod);
}
