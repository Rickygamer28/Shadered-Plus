#version 150

// Shadered+'s fires: the surface the view enters the fire through. Position: relative to the camera.
// UV0 = (along the fire, height in the block + 10 * flags); Color = flame colour, alpha = facing / on.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wpos;
out vec2 uvIn;
out vec4 vColor;

void main() {
    wpos = Position;
    uvIn = UV0;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
