#version 150

// Shadered+'s koi pond: the water surface. UV0 = the world position across it (x, z); Color alpha = 16 + which sides
// carry on into another pond (1 north, 2 south, 4 east, 8 west). Right in any view (world, mirror).

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 wray;
out vec2 uv;
out vec4 vColor;

void main() {
    uv = UV0;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    mat4 iv = inverse(ModelViewMat);
    wray = ProjMat[2][3] < -0.5 ? Position - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
}
