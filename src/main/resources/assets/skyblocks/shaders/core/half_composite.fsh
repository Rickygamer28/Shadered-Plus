#version 150

// The half-size picture of the heavy blocks (aquariums, infinity cubes), smoothly scaled up. Its pixels where nothing
// was drawn (blocks in front, past the faces' edges) have no alpha: they're left out of the blend, so nothing from
// behind or in front bleeds into the edges. The faces themselves are drawn at full size, so their outline and
// whatever is in front of them stay sharp.

uniform sampler2D Sampler0; // the half-size picture (alpha 1 where drawn)
uniform vec2 HalfSize;      // its size in pixels
uniform vec2 MainSize;      // the screen's

out vec4 fragColor;

vec4 texel(ivec2 p) {
    return texelFetch(Sampler0, clamp(p, ivec2(0), ivec2(HalfSize) - 1), 0);
}

void main() {
    vec2 tc = gl_FragCoord.xy / MainSize * HalfSize - 0.5;
    ivec2 i = ivec2(floor(tc));
    vec2 f = tc - floor(tc);
    vec4 a = texel(i), b = texel(i + ivec2(1, 0)), c = texel(i + ivec2(0, 1)), d = texel(i + ivec2(1, 1));
    float wa = (1.0 - f.x) * (1.0 - f.y) * a.a, wb = f.x * (1.0 - f.y) * b.a, wc = (1.0 - f.x) * f.y * c.a, wd = f.x * f.y * d.a;
    float sum = wa + wb + wc + wd;
    if (sum > 1.0e-3) {
        fragColor = vec4((a.rgb * wa + b.rgb * wb + c.rgb * wc + d.rgb * wd) / sum, 1.0);
        return;
    }
    // (a sliver none of the four nearest were drawn for - right along something in front: the nearest drawn one round it)
    vec3 acc = vec3(0.0);
    float n = 0.0;
    for (int y = -1; y <= 2; y++) {
        for (int x = -1; x <= 2; x++) {
            vec4 s = texel(i + ivec2(x, y));
            acc += s.rgb * s.a;
            n += s.a;
        }
    }
    if (n < 0.5) {
        discard;
    }
    fragColor = vec4(acc / n, 1.0);
}
