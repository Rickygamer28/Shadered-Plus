#version 150

// Shadered+'s shader paint: the shaders painted onto an item's pixels at the infusion altar. UV0: where on the item's
// texture (0 .. 1, down from the top); Color: red = the style (ShaderPaintStyles), green = the texture's width in pixels.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
flat out int style;
flat out float texels;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    style = int(Color.r * 255.0 + 0.5);
    texels = max(Color.g * 255.0, 1.0);
}
