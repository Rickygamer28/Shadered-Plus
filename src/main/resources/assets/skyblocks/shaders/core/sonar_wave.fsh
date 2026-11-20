#version 150

// A sonar's ring: each pixel's place in the world (from the world's depth), its distance from where the pulse was
// sent; bright where that's the ring's radius, a fading glow and a grid of the blocks' edges behind it. Added as light.

uniform sampler2D Sampler0; // the world's depth
uniform mat4 InvViewProj;   // screen -> camera-relative world
uniform vec3 Origin;        // where it was sent (camera-relative)
uniform vec3 CamFract;      // the camera's place within its block (for the grid)
uniform float Radius;
uniform float Fade;
uniform vec2 ScreenSize;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float d = texture(Sampler0, uv).r;
    if (d >= 1.0) discard;
    vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec3 pos = p.xyz / p.w;
    float behind = Radius - length(pos - Origin); // (how far the ring's past it)
    if (behind < -0.6 || behind > 14.0) discard;
    float ring = exp(-behind * behind * 6.0) + exp(-max(behind, 0.0) * 4.0) * 0.35 * step(0.0, behind);
    // the blocks' edges, on the faces they're across (not along the face itself)
    vec3 w = pos + CamFract;
    vec3 n = normalize(cross(dFdx(pos), dFdy(pos)));
    vec3 e = smoothstep(0.06, 0.0, abs(fract(w) - 0.5) - 0.44) * (1.0 - abs(n));
    float grid = max(e.x, max(e.y, e.z)) * exp(-max(behind, 0.0) * 0.3) * step(0.0, behind);
    vec3 col = vec3(0.25, 0.9, 1.0) * (ring * 0.9 + grid * 0.35) + vec3(0.8, 1.0, 1.0) * exp(-behind * behind * 40.0) * 0.6;
    fragColor = vec4(col * Fade, 0.0);
}
