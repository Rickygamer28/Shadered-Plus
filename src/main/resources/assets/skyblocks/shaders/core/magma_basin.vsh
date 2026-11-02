#version 150

// Shadered+'s magma basin: the lava's surface. UV0: the world x / z there (so basins side by side make one pool),
// Color: the lava's colour. Works out the view ray (in the world's axes: block entities are only moved).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 wpos;
out vec3 wray;
out vec4 vColor;

void main() {
    vColor = Color;
    wpos = UV0;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    mat4 iv = inverse(ModelViewMat);
    wray = ProjMat[2][3] < -0.5 ? Position - (iv * vec4(0.0, 0.0, 0.0, 1.0)).xyz : (iv * vec4(0.0, 0.0, -1.0, 0.0)).xyz;
}
