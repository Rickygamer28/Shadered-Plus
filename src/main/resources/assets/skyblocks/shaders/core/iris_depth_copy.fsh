#version 150

// Copies Minecraft's main depth into Shadered+'s own depth texture (see IrisIslandTarget): the world's depth before
// see-through blocks are drawn, and at the end of the frame (HandOnly) the held item's depth on top of it.

uniform sampler2D MainDepthSampler;
uniform mat4 WorldProjMat;
uniform float HandOnly;

in vec2 texCoord0;

out vec4 fragColor;

// Depth buffer value -> distance from the camera in blocks.
float distanceOf(float depth) {
    float ndc = depth * 2.0 - 1.0;
    return WorldProjMat[3][2] / (ndc + WorldProjMat[2][2]);
}

void main() {
    float depth = texelFetch(MainDepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    // the shader pack draws the hand squashed towards the camera (an eighth of its depth): nothing in the world gets
    // that close, so anything nearer than this is the hand
    if (HandOnly > 0.5 && !(depth < 1.0 && distanceOf(depth) < 0.15)) {
        discard;
    }
    gl_FragDepth = depth;
    fragColor = vec4(0.0);
}
