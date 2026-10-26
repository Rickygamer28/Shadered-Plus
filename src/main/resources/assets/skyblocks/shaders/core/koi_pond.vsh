#version 150

// Shadered+'s koi pond: a box over the water. UV0 = the world position across it (x, z); Color alpha = how high (0 the
// water .. 1 the box's top); Color rgb = the pond's rectangle. Right in any view (world, mirror).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wray;
out vec2 uv;
out vec4 vColor;
out vec3 vpos;

void main() {
    uv = UV0;
    vColor = Color;
    vpos = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    mat4 iv = inverse(ModelViewMat);
    wray = ProjMat[2][3] < -0.5 ? Position - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
}
