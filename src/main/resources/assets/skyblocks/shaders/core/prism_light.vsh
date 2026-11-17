#version 150

// Shadered+'s prism (client/PrismRenderer): the light through the crystal - the white beam in, the fan of colours out
// (a sheet through the air, a soft glow round each band) and the rainbow where it lands. UV0.x: the colour (0 red ..
// 1 violet; below 0, the white of the light coming in), UV0.y: across a soft strip (-1 .. 1; 0, the sheet itself).
// Color: the light's colour (white: a plain rainbow), alpha how bright.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 tint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    tint = Color;
}
