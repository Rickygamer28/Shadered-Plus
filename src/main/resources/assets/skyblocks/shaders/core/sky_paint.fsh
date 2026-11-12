#version 150

// The sky's picture (the screen-sized picture Shadered draws of it), its middle spread over the item, seen through the
// look chosen - the same as Shadered's sky block looks, done on the picture itself.

in vec2 uv;
flat in int look;
flat in float texels;

uniform sampler2D Sampler0;

out vec4 fragColor;

vec2 place(vec2 q) {
    // the middle of the picture, square (the picture is the screen's shape), the right way up
    vec2 size = vec2(textureSize(Sampler0, 0));
    float aspect = size.x / max(size.y, 1.0);
    vec2 c = (q - 0.5) * 0.7;
    return vec2(0.5 + c.x / aspect, 0.5 - c.y);
}

// how blurred the picture's sampled: by how fast it changes across the face the least steep way (a face seen almost
// edge on - an item's side, armour's edge - otherwise gets the picture's tiniest copy: one flat colour, a line)
float lod = 0.0;

vec3 sky(vec2 q) {
    return textureLod(Sampler0, place(q), lod).rgb;
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 p = place(uv);
    vec2 gx = dFdx(p) * size, gy = dFdy(p) * size;
    lod = max(0.0, 0.5 * log2(max(min(dot(gx, gx), dot(gy, gy)), 1.0e-8)));
    vec3 c = sky(uv);
    if (look == 1) {
        c = 1.0 - c;
    } else if (look == 2) {
        c = floor(c * 4.0) / 4.0;
    } else if (look == 3) {
        c = vec3(luma(c));
    } else if (look == 4) {
        float o = 0.025;
        c = vec3(sky(uv + vec2(o, 0.0)).r, c.g, sky(uv - vec2(o, 0.0)).b);
    } else if (look == 5) {
        // a screen: its pixels in a grid, each cell lit in the middle
        vec2 grid = uv * texels * 2.0;
        vec3 cell = sky((floor(grid) + 0.5) / (texels * 2.0));
        vec2 f = fract(grid) - 0.5;
        c = cell * (1.15 - dot(f, f) * 1.6);
    } else if (look == 6) {
        float d = 0.6 / texels;
        float l = luma(c);
        float edge = abs(l - luma(sky(uv + vec2(0.0, d)))) + abs(l - luma(sky(uv - vec2(0.0, d))))
                + abs(l - luma(sky(uv + vec2(d, 0.0)))) + abs(l - luma(sky(uv - vec2(d, 0.0))));
        c = mix(vec3(0.1, 0.3, 0.6), vec3(0.8, 0.9, 1.0), smoothstep(0.02, 0.1, edge));
    } else if (look == 7) {
        ivec2 px = ivec2(floor(uv * texels * 4.0));
        mat4 threshold = mat4(1.0, 9.0, 3.0, 11.0, 13.0, 5.0, 15.0, 7.0, 4.0, 12.0, 2.0, 10.0, 16.0, 8.0, 14.0, 6.0) / 17.0;
        float dither = threshold[px.x % 4][px.y % 4];
        c = mix(vec3(0.06, 0.22, 0.06), vec3(0.60, 0.74, 0.38), step(dither, luma(c)));
    } else if (look == 8) {
        c *= 0.75;
    }
    fragColor = vec4(c, 1.0);
}
