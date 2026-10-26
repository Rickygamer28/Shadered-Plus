#version 150

// Shadered+'s static block (our own code): TV snow - every pixel a random grey, new every frame.

in vec2 uv;
in vec4 vColor;

uniform float Frame;

out vec4 fragColor;

// a whole-number hash (mixing the bits of the point on the face and the frame number)
uint mixBits(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

void main() {
    uvec2 q = uvec2(uv * 2048.0); // (finer than any screen pixel: noise per pixel)
    uint h = mixBits(q.x ^ mixBits(q.y ^ mixBits(uint(Frame))));
    float v = float(h & 0xFFFFu) / 65535.0;
    fragColor = vec4(vec3(v) * vColor.rgb, 1.0);
}
