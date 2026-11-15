#version 150

// Shadered+'s bottomless pit: the top of a pit block, a window down a shaft that isn't there. Position: from the
// camera, the world's way round (so the way you look through it); UV0: where on the top (x, z, in the block); Color:
// the hole's extent round it (x0, z0, x1, z1 in blocks from this block, + 128); UV1: where the walls' texture is in the
// block atlas (its corner, in texels); UV2: that texture's size (texels), and how lit the top is (0 .. 255).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 eyeDir;
out vec2 local;
flat out vec4 rect;
flat out ivec2 spriteAt;
flat out int spriteSize;
flat out float bright;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    eyeDir = Position;
    local = UV0;
    rect = floor(Color * 255.0 + 0.5) - 128.0;
    spriteAt = UV1;
    spriteSize = max(UV2.x, 1);
    bright = float(UV2.y) / 255.0;
}
