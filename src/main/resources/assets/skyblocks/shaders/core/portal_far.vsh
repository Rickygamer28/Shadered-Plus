#version 150

// A portal whose other end is too far to draw live: its face, each point where the portal takes it (relative to
// where the other end's pictures were taken).

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 PortalMat;

out vec3 q;
out vec2 faceUv;
flat out vec3 faceCode;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    q = (PortalMat * vec4(Position, 1.0)).xyz;
    vec2 hi = floor(UV0 / 4.0 + 0.0001);
    faceUv = UV0 - hi * 4.0;
    faceCode = vec3(mod(hi.x, 8.0), floor(hi.x / 8.0), hi.y);
}
