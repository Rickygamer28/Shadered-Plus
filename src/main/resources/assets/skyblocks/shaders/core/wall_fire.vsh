#version 150

// Shadered+'s fires: the surface the view enters the fire through (right in any view: the world, a mirror).
// UV0 = (along the fire, height in the block + 10 * flags); Color = flame colour, alpha = facing / on.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wray; // (the view ray, in the space the fire was drawn in: only moved, not turned)
out vec2 uvIn;
out vec4 vColor;

void main() {
    mat4 iv = inverse(ModelViewMat);
    wray = ProjMat[2][3] < -0.5 ? Position - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
    uvIn = UV0;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
