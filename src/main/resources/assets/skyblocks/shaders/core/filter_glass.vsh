#version 150

// Shadered+'s filter blocks (from The Valley Mod by D0nkey100, used with permission): a filter block's faces, each pixel
// showing the world behind it through the filter. Position: from the camera, the world's way round; Color: red, green,
// blue the dye's colour (the dyed filters), alpha which filter (the effect's number, as TVM numbers them).

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 point;
flat out int mode;
flat out vec3 dye;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    point = Position;
    mode = int(Color.a * 255.0 + 0.5);
    dye = Color.rgb;
}
