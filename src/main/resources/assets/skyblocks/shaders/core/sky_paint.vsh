#version 150

// Shadered+'s sky paint: a sky painted onto an item, as a picture fixed to it (not a window into the sky behind, as
// sky blocks are - so it's the sky you picked, wherever the item is). UV0: where on the item's texture (0 .. 1, down
// from the top); Color: red = the look (SkyblockPass: 0 normal, 1 inverted, 2 posterised, 3 greyscale, 4 chromatic,
// 5 screen, 6 blueprint, 7 game boy, 8 background), green = the texture's width in pixels.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
flat out int look;
flat out float texels;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    look = int(Color.r * 255.0 + 0.5);
    texels = max(Color.g * 255.0, 1.0);
}
