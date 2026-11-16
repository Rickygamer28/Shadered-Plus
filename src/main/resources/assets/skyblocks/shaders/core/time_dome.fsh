#version 150

// The time crystal's dome. Inside (its far side, from outside; all of it, from in it): a sky running through day and
// night as fast as the crystal is set (a day every 2 minutes at x4 ... every 15 seconds at x256) - the sun and the moon
// leaving bright trails along their path, the stars drawing circles round the pole at night, the light going from blue
// to sunset orange to deep night - with faint hour lines (twelve, like a clock's) up it. Outside (its near side): only a
// glassy rim, brighter at its edges. Its day goes with the clock face on the ground (shaders/core/clock_face).

uniform float GameTime;

in vec2 at;
in vec3 fromEye;
flat in float speed;
flat in float side;
flat in float strength;

out vec4 fragColor;

const float TAU = 6.2831853;
const float PI = 3.14159265;

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

// how many days a game day, at each speed (its day: 2400, 1200, 600, 300 ticks)
float daysPerDay(float s) {
    return s < 0.5 ? 10.0 : s < 1.5 ? 20.0 : s < 2.5 ? 40.0 : 80.0;
}

void main() {
    vec3 dir = vec3(cos(at.y) * cos(at.x), sin(at.y), cos(at.y) * sin(at.x));
    vec3 view = normalize(fromEye);
    float facing = dot(dir, view); // > 0: this bit faces away from the eye (the dome's far side, or seen from inside)
    if (side > 0.5) {
        // the outside: a glassy rim, only on the near side
        if (facing > 0.0) {
            discard;
        }
        float rim = pow(1.0 - abs(facing), 4.0);
        float a = (0.04 + 0.5 * rim) * strength;
        if (a < 0.01) {
            discard;
        }
        fragColor = vec4(vec3(0.55, 0.85, 1.0), a);
        return;
    }
    if (facing < 0.0) {
        discard;
    }
    float phase = fract(GameTime * daysPerDay(speed));
    // the sun's path: a circle tilted a little (its pole: the stars turn round it)
    vec3 pole = normalize(vec3(0.0, 0.42, 1.0));
    vec3 east = vec3(1.0, 0.0, 0.0);
    vec3 up = cross(pole, east);
    float sunA = phase * TAU;
    vec3 sun = east * cos(sunA) + up * sin(sunA);
    float h = sun.y;
    float day = smoothstep(-0.18, 0.22, h);
    float dusk = exp(-h * h * 30.0);
    float el = max(dir.y, 0.0);
    // the sky's light
    vec3 dayCol = mix(vec3(0.70, 0.84, 1.0), vec3(0.24, 0.47, 0.95), pow(el, 0.6));
    vec3 nightCol = mix(vec3(0.07, 0.06, 0.16), vec3(0.015, 0.02, 0.07), pow(el, 0.5));
    vec3 col = mix(nightCol, dayCol, day);
    float towardSun = max(dot(normalize(vec3(dir.x, 0.0, dir.z) + 1e-4), normalize(vec3(sun.x, 0.0, sun.z) + 1e-4)), 0.0);
    col = mix(col, vec3(1.0, 0.48, 0.18), dusk * pow(1.0 - el, 3.0) * (0.35 + 0.65 * towardSun) * 0.9);
    // along the path: how far round (0 .. 2 pi) and how far off it
    float off = dot(dir, pole);
    float along = atan(dot(dir, up), dot(dir, east));
    // the sun, its trail behind it
    float behind = mod(sunA - along, TAU);
    float onPath = exp(-off * off * 900.0);
    float trail = onPath * exp(-behind * 1.6) * step(behind, 3.0);
    col += vec3(1.0, 0.65, 0.25) * trail * 1.4;
    col += vec3(1.0, 0.95, 0.8) * smoothstep(0.9985, 0.9995, dot(dir, sun)) * 2.0;
    col += vec3(1.0, 0.7, 0.35) * pow(max(dot(dir, sun), 0.0), 60.0) * 0.6;
    // the moon, opposite, a paler trail
    vec3 moon = -sun;
    float mBehind = mod(sunA + PI - along, TAU);
    float mOn = exp(-off * off * 2500.0);
    col += vec3(0.75, 0.82, 1.0) * mOn * exp(-mBehind * 2.0) * step(mBehind, 2.5) * 0.9;
    col += vec3(0.92, 0.95, 1.0) * smoothstep(0.9988, 0.9994, dot(dir, moon)) * 1.5;
    // the stars, drawing arcs round the pole at night
    float th = acos(clamp(dot(dir, pole), -1.0, 1.0));
    float ph = atan(dot(dir, up), dot(dir, east));
    float band = floor(th * 140.0);
    float inBand = abs(fract(th * 140.0) - 0.5);
    float hs = hash1(band);
    float star0 = hash1(band * 1.37 + 5.0) * TAU;
    float len = 0.5 + 0.9 * hash1(band + 9.1);
    float sb = mod(star0 + sunA - ph, TAU);
    float arc = step(hs, 0.45) * smoothstep(0.32, 0.12, inBand) * step(sb, len) * (1.0 - sb / len * 0.7);
    col += vec3(0.85, 0.9, 1.0) * arc * (1.0 - day) * (0.5 + 0.5 * hash1(band * 2.3));
    // twelve faint hour lines up the dome, and its foot glowing
    float hourLine = smoothstep(0.012, 0.0, abs(fract(at.x / TAU * 12.0 + 0.5) - 0.5) / 12.0 * TAU * cos(at.y));
    col += vec3(0.35, 1.0, 0.9) * hourLine * 0.18 * (1.0 - el * 0.6);
    col += vec3(0.4, 0.9, 1.0) * exp(-el * 40.0) * 0.5;
    fragColor = vec4(col, 0.92 * strength);
}
