// Shadered+'s Sky Studio sky (our own code): a sky made of the layers a player sets up in the Sky Studio. Every number
// comes from the slot's settings (studio_params.glsl: S_<layer>_<setting>, all 0..1; written by tools/studio_spec.py).
// Above the horizon: colour gradient, milky way, nebula, stars, a planet, a sun or moon, light rays from it, aurora,
// clouds, shooting stars, sparkles. At and below it: mountains, then a grid floor, a lake mirroring the sky, or ground.
// The whole sky can spin, run faster or slower, and be recoloured (brightness, contrast, saturation, hue).

#moj_import <skyblocks:studio_params.glsl>

const float ST_PI = 3.14159265;

float st_hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float st_hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float st_noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(st_hash13(i), st_hash13(i + vec3(1, 0, 0)), f.x), mix(st_hash13(i + vec3(0, 1, 0)), st_hash13(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(st_hash13(i + vec3(0, 0, 1)), st_hash13(i + vec3(1, 0, 1)), f.x), mix(st_hash13(i + vec3(0, 1, 1)), st_hash13(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

float st_noise1(float x) {
    float i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(st_hash12(vec2(i, 7.1)), st_hash12(vec2(i + 1.0, 7.1)), f);
}

float st_fbm(vec3 p, float detail) {
    float v = 0.0, a = 0.5, total = 0.0;
    int octaves = 2 + int(detail * 3.0 + 0.5);
    for (int i = 0; i < 5; i++) {
        if (i >= octaves) break;
        v += a * st_noise(p);
        total += a;
        p = p * 2.07 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v / total;
}

vec3 st_dirAt(float az, float el) {
    return vec3(cos(el) * sin(az), sin(el), cos(el) * cos(az));
}

// how far off direction c, sideways and up
vec2 st_local(vec3 d, vec3 c) {
    vec3 side = normalize(cross(vec3(0.0, 1.0, 0.0), c));
    vec3 up = cross(c, side);
    return vec2(dot(d, side), dot(d, up));
}

vec3 st_hue(vec3 c, float shift) {
    float a = shift * 2.0 * ST_PI;
    vec3 k = vec3(0.57735);
    return c * cos(a) + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - cos(a));
}

float st_horizon() {
    return (S_gradient_horizon_height - 0.5) * 0.6;
}

// the sun or moon's direction (it can circle the sky)
vec3 st_moonDir(float t) {
    float az = S_moon_direction * 2.0 * ST_PI + t * 0.0004 * S_moon_orbit * 10.0;
    return st_dirAt(az, mix(0.05, 1.3, S_moon_height));
}

// a disc (round) or square, with craters, bands, a glow and maybe rings: drawn into col
void st_body(inout vec3 col, vec3 d, vec3 c, float size, float roundness, vec3 colour, float craters, float glow, float rings,
             vec3 ringColour, float bands, vec3 colour2, float turn, float t) {
    if (dot(d, c) <= 0.0) return;
    vec2 l = st_local(d, c);
    float r = mix(max(abs(l.x), abs(l.y)), length(l), roundness);
    if (rings > 0.01) {
        vec2 q = vec2(l.x, l.y * 3.2 + l.x * 0.35);
        float rr = length(q) / size;
        float ring = smoothstep(1.35, 1.45, rr) * (1.0 - smoothstep(2.1, 2.25, rr)) * (0.6 + 0.4 * sin(rr * 40.0));
        bool behind = q.y > 0.0 && r < size;
        if (!behind) col = mix(col, ringColour, ring * rings);
    }
    if (r < size) {
        vec2 p = l / size;
        vec3 body = colour;
        if (bands > 0.01) {
            float b = sin(p.y * 9.0 + st_noise(vec3(p * 3.0 + vec2(t * 0.0005 * turn * 10.0, 0.0), 1.0)) * 3.0 * bands);
            body = mix(colour, colour2, (b * 0.5 + 0.5) * bands);
        }
        if (craters > 0.01) {
            vec2 g = floor(p * 5.0);
            float cr = step(1.0 - craters * 0.45, st_hash12(g + 3.0));
            body = mix(body, body * 0.72, cr);
        }
        body *= 1.0 - 0.35 * pow(r / size, 3.0) * roundness;
        col = body;
    } else {
        col += colour * glow * 0.6 * exp(-(r - size) / max(size, 0.01) * 3.5);
    }
}

// the sky above (and seen in the lake): everything but what's below the horizon
vec3 st_upper(vec3 d, float t) {
    float horizon = st_horizon();
    float above = d.y - horizon;
    vec3 col = vec3(0.0);
    if (S_gradient_on > 0.5) {
        float h = clamp(above / max(1.0 - horizon, 0.05), 0.0, 1.0);
        h = pow(h, mix(2.2, 0.45, S_gradient_curve));
        vec3 sky = mix(S_gradient_horizon, S_gradient_middle, smoothstep(0.0, 0.35, h));
        sky = mix(sky, S_gradient_top, smoothstep(0.3, 1.0, h));
        col = above >= 0.0 ? sky : S_gradient_ground;
        col += S_gradient_horizon * S_gradient_glow * 0.8 * exp(-abs(above) * mix(30.0, 3.0, S_gradient_glow_width));
    }
    float sky = smoothstep(-0.02, 0.06, above);
    if (S_stars_on > 0.5 && S_stars_milky_way > 0.01) {
        float tilt = (S_stars_band_tilt - 0.5) * 2.4;
        vec3 n = normalize(vec3(sin(tilt), cos(tilt), 0.35));
        float band = exp(-pow(dot(d, n) / 0.18, 2.0));
        float haze = st_fbm(d * 6.0, 0.7);
        col += S_stars_colour * band * (0.15 + 0.35 * haze) * S_stars_milky_way * sky;
        float dust = step(0.985 - band * 0.03 * S_stars_milky_way, st_hash13(floor(d * 380.0)));
        col += S_stars_colour * dust * band * S_stars_milky_way * sky;
    }
    if (S_nebula_on > 0.5) {
        float s = mix(0.8, 5.0, S_nebula_scale);
        vec3 q = d * s;
        float sw = S_nebula_swirl * 2.5;
        q.xz = mat2(cos(sw * q.y), -sin(sw * q.y), sin(sw * q.y), cos(sw * q.y)) * q.xz;
        q += vec3(t * 0.006, 0.0, t * 0.004) * S_nebula_drift;
        float n = st_fbm(q, S_nebula_detail);
        float edge = mix(0.04, 0.35, S_nebula_softness);
        float cut = 1.0 - S_nebula_amount * 0.75 - 0.15;
        float m = smoothstep(cut, cut + edge, n);
        float k = st_fbm(q * 1.7 + 5.0, 0.4);
        vec3 c = mix(S_nebula_colour1, S_nebula_colour2, clamp(k * 1.6 - 0.3, 0.0, 1.0));
        c = mix(c, S_nebula_colour3, clamp((n - cut - edge) * 3.0, 0.0, 1.0) * 0.6);
        col += c * m * mix(0.3, 1.5, S_nebula_brightness) * smoothstep(-0.05, 0.12, above);
    }
    if (S_stars_on > 0.5 && above > 0.0) {
        for (int layer = 0; layer < 2; layer++) {
            float big = float(layer);
            if (big > 0.5 && S_stars_big_stars < 0.01) break;
            float scale = big > 0.5 ? 60.0 : 140.0;
            vec3 p = d * scale;
            vec3 cell = floor(p);
            float h = st_hash13(cell + big * 17.0);
            float chance = big > 0.5 ? S_stars_big_stars * 0.02 : S_stars_density * 0.06;
            if (h > 1.0 - chance) {
                vec3 f = fract(p) - 0.5;
                float r = mix(0.08, 0.3, S_stars_size) * (big > 0.5 ? 0.8 : 1.0);
                float on = step(max(max(abs(f.x), abs(f.y)), abs(f.z)), r);
                if (big > 0.5) on += 0.5 * step(max(abs(f.x) * 0.25, max(abs(f.y), abs(f.z))), r * 0.5) + 0.5 * step(max(abs(f.y) * 0.25, max(abs(f.x), abs(f.z))), r * 0.5);
                float tw = mix(1.0, 0.45 + 0.55 * sin(t * 0.08 * (0.5 + S_stars_twinkle * 2.0) + h * 80.0), S_stars_twinkle);
                vec3 tint = mix(vec3(1.0), vec3(st_hash13(cell + 1.0), st_hash13(cell + 2.0), st_hash13(cell + 3.0)) * 1.4, S_stars_variety);
                col += S_stars_colour * tint * min(on, 1.2) * tw * mix(0.4, 1.6, S_stars_brightness) * smoothstep(0.0, 0.08, above);
            }
        }
    }
    if (S_planet_on > 0.5) {
        vec3 c = st_dirAt(S_planet_direction * 2.0 * ST_PI, mix(0.05, 1.3, S_planet_height));
        st_body(col, d, c, mix(0.06, 0.5, S_planet_size), 1.0, S_planet_colour1, 0.0, S_planet_glow, S_planet_rings,
                S_planet_ring_colour, S_planet_bands, S_planet_colour2, S_planet_turn, t);
    }
    vec3 m = st_moonDir(t);
    if (S_moon_on > 0.5) {
        st_body(col, d, m, mix(0.03, 0.16, S_moon_size), S_moon_round, S_moon_colour, S_moon_craters, S_moon_glow, S_moon_rings,
                S_moon_ring_colour, 0.0, S_moon_colour, 0.0, t);
    }
    if (S_rays_on > 0.5) {
        vec2 l = st_local(d, m);
        float facing = dot(d, m);
        if (facing > 0.0) {
            float a = atan(l.y, l.x) + t * 0.002 * S_rays_spin * 10.0;
            float count = floor(mix(4.0, 24.0, S_rays_count));
            float ray = pow(0.5 + 0.5 * cos(a * count), mix(12.0, 1.5, S_rays_width));
            col += S_rays_colour * ray * S_rays_strength * pow(facing, 3.0) * 0.9 * sky;
        }
    }
    if (S_aurora_on > 0.5 && above > 0.0) {
        float az = atan(d.x, d.z);
        float el = asin(clamp(d.y, -1.0, 1.0));
        int bands = 1 + int(S_aurora_bands * 3.0 + 0.5);
        for (int i = 0; i < 4; i++) {
            if (i >= bands) break;
            float fi = float(i);
            float wave = S_aurora_waviness * 0.18;
            float centre = mix(0.2, 0.9, S_aurora_height) + 0.07 * fi
                    + wave * sin(az * 2.0 + fi * 2.1 + t * 0.004 * S_aurora_wave_speed * 10.0)
                    + wave * 0.5 * sin(az * 5.0 - fi + t * 0.007 * S_aurora_wave_speed * 10.0);
            float k = (el - centre) / mix(0.03, 0.14, S_aurora_thickness);
            float band = exp(-k * k) * (0.55 + 0.45 * st_noise(vec3(az * 6.0 + fi * 9.0, t * 0.01 * S_aurora_wave_speed * 10.0, fi)));
            float rays = 1.0 - S_aurora_rays * 0.5 * (0.5 + 0.5 * sin(az * 70.0 + fi * 7.0 + t * 0.02));
            col += mix(S_aurora_colour1, S_aurora_colour2, clamp(k * 0.5 + 0.5, 0.0, 1.0)) * band * rays * S_aurora_brightness * 0.6;
        }
    }
    if (S_clouds_on > 0.5 && above > 0.0) {
        float lift = mix(0.05, 0.6, S_clouds_height);
        vec2 p = d.xz / max(d.y + lift * 0.3, 0.02) * mix(0.3, 1.6, S_clouds_scale);
        p += vec2(t * 0.002, t * 0.001) * S_clouds_speed * 10.0;
        float n = st_fbm(vec3(p, 3.0), 0.7);
        float cut = 1.0 - S_clouds_amount;
        float c = smoothstep(cut, cut + mix(0.05, 0.4, S_clouds_softness), n);
        float shade = st_fbm(vec3(p * 1.3 + 2.0, 5.0), 0.5);
        vec3 cc = mix(S_clouds_shadow, S_clouds_colour, clamp(shade * 1.4, 0.0, 1.0));
        col = mix(col, cc, c * smoothstep(0.0, 0.15, above));
    }
    if (S_shooting_on > 0.5 && above > 0.0) {
        float period = mix(900.0, 90.0, S_shooting_how_often);
        float slot = floor(t / period), phase = fract(t / period);
        float dur = mix(0.25, 0.06, S_shooting_speed);
        if (phase < dur) {
            float s = phase / dur;
            float az0 = st_hash12(vec2(slot, 1.0)) * 6.2832, el0 = 0.35 + st_hash12(vec2(slot, 2.0)) * 0.6;
            float dirA = st_hash12(vec2(slot, 3.0)) * 6.2832;
            vec2 step2 = vec2(cos(dirA), -abs(sin(dirA)) - 0.2) * 0.5;
            vec2 head = vec2(az0, el0) + step2 * s;
            float az = atan(d.x, d.z), el = asin(clamp(d.y, -1.0, 1.0));
            vec2 rel = vec2(az, el) - head;
            rel.x = mod(rel.x + ST_PI, 2.0 * ST_PI) - ST_PI;
            vec2 back = -normalize(step2);
            float along = dot(rel, back);
            float across = abs(dot(rel, vec2(-back.y, back.x)));
            float len = mix(0.05, 0.35, S_shooting_trail);
            if (along > 0.0 && along < len) {
                col += S_shooting_colour * (1.0 - along / len) * smoothstep(mix(0.003, 0.012, S_shooting_size), 0.0, across) * (1.0 - s * 0.5);
            }
        }
    }
    if (S_sparkles_on > 0.5) {
        vec3 p = d * 40.0 + vec3(0.0, -t * 0.01 * S_sparkles_speed * 10.0, 0.0);
        vec3 cell = floor(p);
        float h = st_hash13(cell + 41.0);
        if (h > 1.0 - S_sparkles_amount * 0.08) {
            vec3 f = fract(p) - 0.5 - (vec3(st_hash13(cell + 5.0), st_hash13(cell + 6.0), st_hash13(cell + 7.0)) - 0.5) * 0.5;
            float r = mix(0.04, 0.16, S_sparkles_size);
            float tw = mix(1.0, 0.5 + 0.5 * sin(t * 0.15 + h * 50.0), S_sparkles_twinkle);
            col += S_sparkles_colour * smoothstep(r, 0.0, length(f)) * tw * 1.4;
        }
    }
    return col;
}

vec3 studioSky(vec3 d, float t) {
    t *= mix(0.0, 2.0, S_global_speed);
    float spin = t * 0.0006 * S_global_spin * 10.0;
    d.xz = mat2(cos(spin), -sin(spin), sin(spin), cos(spin)) * d.xz;
    float horizon = st_horizon();
    float above = d.y - horizon;
    vec3 col = st_upper(d, t);
    if (above < 0.0) {
        float below = -above;
        if (S_lake_on > 0.5) {
            vec3 r = vec3(d.x, horizon + below, d.z);
            float rip = S_lake_ripples * 0.04 * st_noise(vec3(d.xz / max(below, 0.01) * 4.0, t * 0.02 * S_lake_ripple_speed * 10.0));
            r.x += rip;
            r.z += rip * 0.7;
            vec3 mirrored = st_upper(normalize(r), t);
            col = mix(S_lake_tint * 0.35, mirrored * mix(vec3(1.0), S_lake_tint * 1.6, 0.35), S_lake_clarity);
        }
        if (S_grid_on > 0.5) {
            vec2 p = d.xz / max(below, 0.004) * mix(0.25, 1.2, S_grid_spacing);
            p.y += t * 0.01 * S_grid_scroll * 4.0;
            vec2 f = abs(fract(p) - 0.5);
            float width = mix(0.01, 0.08, S_grid_line_width) + 0.4 * (1.0 - smoothstep(0.0, 0.25, below));
            float line = max(smoothstep(0.5 - width, 0.5, f.x), smoothstep(0.5 - width, 0.5, f.y));
            float fade = smoothstep(0.0, 0.04, below);
            col = mix(col, S_grid_colour, line * fade * 0.9);
            col += S_grid_colour * S_grid_glow * 0.5 * exp(-below * 30.0);
        }
    }
    // a big striped sun sitting on the horizon (synthwave)
    if (S_grid_on > 0.5 && S_grid_sun > 0.01 && above > -0.02 && d.z > 0.0) {
        vec2 l = st_local(d, st_dirAt(0.0, horizon + 0.02));
        vec2 c = vec2(l.x, l.y - 0.12 * S_grid_sun);
        float size = 0.22 * S_grid_sun;
        if (length(c) < size) {
            float y = c.y / size;
            float stripes = step(0.0, y) + step(0.35, fract(y * 6.0 + t * 0.01));
            if (stripes > 0.5) col = mix(S_grid_sun_colour, S_gradient_horizon, clamp(-y, 0.0, 1.0) * 0.6);
        }
    }
    // mountains on the horizon, in layers fading into the haze
    if (S_mountains_on > 0.5) {
        float az = atan(d.x, d.z);
        int layers = 1 + int(S_mountains_layers * 2.0 + 0.5);
        for (int i = 2; i >= 0; i--) {
            if (i >= layers) continue;
            float fi = float(i);
            float h = 0.0, a = 1.0, f = mix(1.5, 4.0, S_mountains_roughness);
            for (int o = 0; o < 4; o++) {
                h += st_noise1(az * f * (1.0 + fi * 0.7) + fi * 13.0) * a;
                f *= 2.1;
                a *= 0.5 * (0.6 + S_mountains_roughness * 0.6);
            }
            float top = horizon + h * mix(0.04, 0.3, S_mountains_height) * (1.0 - fi * 0.25);
            if (d.y < top && d.y > horizon - 0.15) {
                float far = fi / 2.0;
                vec3 c = mix(S_mountains_colour, S_mountains_far_colour, far);
                col = mix(c, S_gradient_horizon, far * S_mountains_haze * 0.6);
            }
        }
    }
    col = st_hue(col, S_global_hue_shift);
    float grey = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(grey), col, S_global_saturation * 2.0);
    col = (col - 0.5) * mix(0.5, 1.8, S_global_contrast) + 0.5;
    col *= S_global_brightness * 2.0;
    return max(col, vec3(0.0));
}
