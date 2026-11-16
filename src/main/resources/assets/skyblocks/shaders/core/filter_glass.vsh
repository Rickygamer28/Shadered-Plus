#version 150

// Shadered+'s filter blocks (from The Valley Mod by D0nkey100, used with permission): a filter block's faces, each pixel
// showing the world behind it through the filter. Position: from the camera, the world's way round; UV0: where on its face (0 .. 1 each way), plus 4 x which
// of its edges get a frame (1, 2: the u edges; on v the same for the v edges); Color: red, green,
// blue the dye's colour (the dyed filters), alpha which filter (the effect's number, as TVM numbers them).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 point;
out vec2 onFace;
flat out int frame;
flat out int mode;
flat out vec3 dye;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    point = Position;
    onFace = UV0;
    // (which edges get a frame: 4 x the u edges' flags on u, the v edges' on v - the same at every corner)
    frame = int(floor(UV0.x / 4.0)) | int(floor(UV0.y / 4.0)) << 2;
    mode = int(Color.a * 255.0 + 0.5);
    dye = Color.rgb;
}
