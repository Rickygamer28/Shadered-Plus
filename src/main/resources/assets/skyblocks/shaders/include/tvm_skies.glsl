// The Valley Mod's skybox shaders (TVM, by D0nkey100 - used with permission), as Shadered+ shader skies. Taken from
// TVM's shaders/program/filters.fsh as it is, the third-party Shadertoy ones with their own authors and licences named
// where they start (and in CREDITS.md). Shadered+'s changes: the sky's direction comes from the sky pass (SkyDir)
// instead of the screen and the camera; the screen is the sky picture's; no vignette; the Classic End Portal's sheets
// lie on the sky round you (no block face behind them here).

float SkyTime;      // seconds
vec3 SkyDir;        // where this pixel of the sky is (the sky pass's direction)
vec2 InSize;        // the sky picture's size (pixels)
vec2 SkyCoord;      // this pixel on it (0 .. 1)
float Aspect;
float VYaw = 0.0;
float VPitch = 0.0;
float VTan = 0.7;
float VZoom = 1.0;
float VTurn = 0.0;
float VCentre = 0.0;
bool VFull = false;

void tvmSetup(vec3 dir, float ticks, vec2 screen, float yaw, float pitch) {
    SkyDir = dir;
    SkyTime = ticks / 20.0;
    InSize = max(screen, vec2(1.0));
    SkyCoord = gl_FragCoord.xy / InSize;
    Aspect = InSize.x / InSize.y;
    VYaw = yaw;
    VPitch = pitch;
}


// The world direction seen at this pixel (no camera roll in Minecraft): forward, right and up from the camera's turn.
// Tips a direction up / down by the sky's own tilt (the Skybox Edit Tool's second centre slider).
vec3 tipSky(vec3 d) {
    float c = cos(VTurn);
    float s = sin(VTurn);
    return vec3(d.x, d.y * c - d.z * s, d.y * s + d.z * c);
}

// Where the camera points, as the sky sees it.
vec3 viewForward() {
    return tipSky(vec3(-sin(VYaw) * cos(VPitch), -sin(VPitch), cos(VYaw) * cos(VPitch)));
}

vec3 viewDirection() {
    return tipSky(normalize(SkyDir));
}

// --- Swirl skybox: the user's swirling paint shader (Shadertoy style), unchanged but for iTime -> SkyTime. ---
#define SPIN_ROTATION -2.0
#define SPIN_SPEED 5.0
#define OFFSET vec2(0.0)
#define COLOUR_1 vec4(0.871, 0.267, 0.231, 1.0)
#define COLOUR_2 vec4(0.0, 0.42, 0.706, 1.0)
#define COLOUR_3 vec4(0.086, 0.137, 0.145, 1.0)
#define CONTRAST 3.5
#define LIGTHING 0.4
#define SPIN_AMOUNT 0.25
#define PIXEL_FILTER 745.0
#define SPIN_EASE 1.0
#define PI 3.14159265359
#define IS_ROTATE false

vec4 swirlEffect(vec2 screenSize, vec2 screen_coords) {
    float pixel_size = length(screenSize.xy) / PIXEL_FILTER;
    vec2 uv = (floor(screen_coords.xy*(1./pixel_size))*pixel_size - 0.5*screenSize.xy)/length(screenSize.xy) - OFFSET;
    float uv_len = length(uv);

    float speed = (SPIN_ROTATION*SPIN_EASE*0.2);
    if(IS_ROTATE){
       speed = SkyTime * speed;
    }
    speed += 302.2;
    float new_pixel_angle = atan(uv.y, uv.x) + speed - SPIN_EASE*20.*(1.*SPIN_AMOUNT*uv_len + (1. - 1.*SPIN_AMOUNT));
    vec2 mid = (screenSize.xy/length(screenSize.xy))/2.;
    uv = (vec2((uv_len * cos(new_pixel_angle) + mid.x), (uv_len * sin(new_pixel_angle) + mid.y)) - mid);

    uv *= 30.;
    speed = SkyTime*(SPIN_SPEED);
    vec2 uv2 = vec2(uv.x+uv.y);

    for(int i=0; i < 5; i++) {
        uv2 += sin(max(uv.x, uv.y)) + uv;
        uv  += 0.5*vec2(cos(5.1123314 + 0.353*uv2.y + speed*0.131121),sin(uv2.x - 0.113*speed));
        uv  -= 1.0*cos(uv.x + uv.y) - 1.0*sin(uv.x*0.711 - uv.y);
    }

    float contrast_mod = (0.25*CONTRAST + 0.5*SPIN_AMOUNT + 1.2);
    float paint_res = min(2., max(0.,length(uv)*(0.035)*contrast_mod));
    float c1p = max(0.,1. - contrast_mod*abs(1.-paint_res));
    float c2p = max(0.,1. - contrast_mod*abs(paint_res));
    float c3p = 1. - min(1., c1p + c2p);
    float light = (LIGTHING - 0.2)*max(c1p*5. - 4., 0.) + LIGTHING*max(c2p*5. - 4., 0.);
    return (0.3/CONTRAST)*COLOUR_1 + (1. - 0.3/CONTRAST)*(COLOUR_1*c1p + COLOUR_2*c2p + vec4(c3p*COLOUR_3.rgb, c3p*COLOUR_1.a)) + light;
}

// The swirl painted onto the sky around you instead of the screen: the view direction becomes a point on a fixed
// 1920 x 1080 canvas (a stereographic map of the sphere, centered on north / south, so it has no seam), so the sky stays
// put in the world while you turn.
vec3 swirlSkybox() {
    vec3 dir = viewDirection();
    vec2 p = dir.xy / (1.0 + abs(dir.z));
    vec2 canvas = vec2(1920.0, 1080.0);
    vec2 coords = 0.5 * canvas + p * 0.6 * length(canvas);
    return clamp(swirlEffect(canvas, coords).rgb, 0.0, 1.0);
}

// --- Light Cycle skybox: "Light Cycle" by Noztol (Shadertoy, https://www.shadertoy.com/view/7XVSWG; Shadertoy's default
// licence, CC BY-NC-SA 3.0). Unchanged but for iTime -> SkyTime, iResolution -> the screen, and the ray's direction: it
// follows where you look (looking south is straight down the tunnel), so the tunnel flies around you like a sky. ---
#define LC_MAX_STEPS 110
#define LC_MAX_DIST 80.0
#define LC_SURF_DIST 0.001

const vec3 COL_BG     = vec3(0.004, 0.007, 0.014);
const vec3 COL_CYAN   = vec3(0.0, 0.85, 1.0);
const vec3 COL_ORANGE = vec3(1.0, 0.28, 0.0);
const vec3 COL_BLUE   = vec3(0.04, 0.18, 0.75);

mat2 lcRot(float a) {
    float s = sin(a), c = cos(a);
    return mat2(c, -s, s, c);
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdBox2D(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

vec2 getPath(float z) {
    return vec2(
        sin(z * 0.05) * 2.4 + cos(z * 0.025) * 1.0,
        cos(z * 0.04) * 2.0 + sin(z * 0.02) * 0.8
    );
}

vec3 toTunnelSpace(vec3 p) {
    p.xy -= getPath(p.z);
    p.xy *= lcRot(p.z * 0.02 + SkyTime * 0.05);
    return p;
}

vec2 foldSquare(vec2 p) {
    p = abs(p);
    if (p.y > p.x) p.xy = p.yx;
    return p;
}

vec3 lcMap(vec3 pWorld) {
    vec3 p = toTunnelSpace(pWorld);
    vec3 q = vec3(foldSquare(p.xy), p.z);

    float zPeriod = 4.0;
    vec3 qCell = q;
    qCell.z = mod(q.z + zPeriod * 0.5, zPeriod) - zPeriod * 0.5;

    // 1. Watertight Closed Square Tunnel
    float dWalls = 4.0 - q.x;
    float dRecessCenter = sdBox(qCell - vec3(4.0, 0.0, 0.0), vec3(0.5, 1.488, 1.488)) - 0.012;
    float dRecessSide   = sdBox(qCell - vec3(4.0, 2.75, 0.0), vec3(0.5, 0.738, 1.488)) - 0.012;
    float dCarve = min(dRecessCenter, dRecessSide);
    float dArch = max(dWalls, -dCarve);

    // 2. Flush 360-Degree Cyan Light Ribs
    float dCyanRail1 = sdBox2D(q.xy - vec2(4.0, 1.5), vec2(0.05, 0.05));
    float dCyanRail2 = sdBox2D(q.xy - vec2(4.0, 2.0), vec2(0.05, 0.05));
    float dCyanRail3 = sdBox2D(q.xy - vec2(4.0, 3.5), vec2(0.05, 0.05));
    float dCyanRails = min(min(dCyanRail1, dCyanRail2), dCyanRail3);
    float dCyanRing1 = sdBox(vec3(qCell.x - 4.0, qCell.y - 2.0, abs(qCell.z) - 1.5), vec3(0.05, 2.0, 0.05));
    float dCyanRing2 = sdBox(vec3(qCell.x - 4.0, qCell.y - 2.0, abs(qCell.z) - 2.0), vec3(0.05, 2.0, 0.05));
    float dCyanRings = min(dCyanRing1, dCyanRing2);
    float dCyan = min(dCyanRails, dCyanRings);

    // 3. Corner Orange Light Streams
    float dOrange = sdBox2D(q.xy - vec2(3.92, 3.92), vec2(0.11, 0.11));

    float dScene = min(dArch, min(dCyan, dOrange));
    return vec3(dScene, dCyan, dOrange);
}

vec3 lcNormal(vec3 p) {
    const vec2 k = vec2(1.0, -1.0);
    const float h = 0.002;
    return normalize(
        k.xyy * lcMap(p + k.xyy * h).x +
        k.yyx * lcMap(p + k.yyx * h).x +
        k.yxy * lcMap(p + k.yxy * h).x +
        k.xxx * lcMap(p + k.xxx * h).x
    );
}

float boxFilteredLine(float coord, float scale, float lineWidth, float footprint) {
    float w = max(footprint * scale, 0.0001);
    float p = coord * scale;
    float a = p + 0.5 * w;
    float b = p - 0.5 * w;
    float coverage = (floor(a) + min(fract(a) / lineWidth, 1.0) -
                      floor(b) - min(fract(b) / lineWidth, 1.0)) / (w / lineWidth);
    return clamp(coverage, 0.0, 1.0);
}

float getAntiAliasedGrid(vec3 pWorld, vec3 nWorld, vec3 rd, float t, float pixelSpread) {
    vec3 p = toTunnelSpace(pWorld);
    vec3 q = vec3(foldSquare(p.xy), p.z);

    vec3 nLocal = nWorld;
    nLocal.xy *= lcRot(p.z * 0.02 + SkyTime * 0.05);
    vec3 nAbs = abs(nLocal);
    if (abs(p.y) > abs(p.x)) nAbs.xy = nAbs.yx;

    vec3 axisWeight = 1.0 - smoothstep(0.35, 0.65, nAbs);

    float cosTheta = max(abs(dot(nWorld, rd)), 0.15);
    float worldFootprint = (t * pixelSpread * 1.35) / cosTheta;

    float gxMinor = boxFilteredLine(q.x + 0.25, 2.0, 0.035, worldFootprint) * axisWeight.x;
    float gyMinor = boxFilteredLine(q.y + 0.25, 2.0, 0.035, worldFootprint) * axisWeight.y;
    float gzMinor = boxFilteredLine(q.z + 0.25, 2.0, 0.035, worldFootprint) * axisWeight.z;
    float lineMinor = max(gxMinor, max(gyMinor, gzMinor));

    float gyMajor = boxFilteredLine(q.y + 0.5, 1.0, 0.032, worldFootprint) * axisWeight.y;
    float gzMajor = boxFilteredLine(q.z + 0.5, 1.0, 0.032, worldFootprint) * axisWeight.z;
    float lineMajor = max(gyMajor, gzMajor);

    float fadeMajor = exp(-0.0007 * t * t);
    float fadeMinor = exp(-0.0016 * t * t);

    float c = lineMajor * 0.85 * fadeMajor + lineMinor * 0.45 * fadeMinor;

    float pulse = sin(p.z * 0.35 - SkyTime * 3.0) * 0.5 + 0.5;
    c *= (0.45 + 0.55 * pow(pulse, 3.0));

    return clamp(c, 0.0, 1.0);
}

vec3 lightCycleSkybox() {
    vec2 iResolution = InSize;
    vec2 fragCoord = SkyCoord * InSize;
    float minRes = min(iResolution.x, iResolution.y);
    float pixelSpread = 1.0 / minRes;

    float speed = 8.0;
    // (Shadered+: the flight starts over every ten minutes - far down the tunnel the numbers lose their precision, and
    // after a long game the tunnel broke up into noise)
    float camZ = mod(SkyTime, 600.0) * speed;
    vec3 ro = vec3(getPath(camZ), camZ);

    float targetZ = camZ + 5.0;
    vec3 ta = vec3(getPath(targetZ), targetZ);

    vec3 fwd = normalize(ta - ro);
    float bankAngle = -fwd.x * 0.45 + sin(SkyTime * 0.3) * 0.1;
    vec3 up = vec3(sin(bankAngle), cos(bankAngle), 0.0);
    vec3 right = normalize(cross(fwd, up));
    up = cross(right, fwd);

    // TVM: the ray goes where you look (world south = down the tunnel, west = its right, up = up).
    vec3 look = viewDirection();
    vec3 rd = normalize(fwd * look.z + right * -look.x + up * look.y);

    float t = 0.0;
    vec3 glowAccum = vec3(0.0);

    float minCyanCone   = 1e5;
    float minOrangeCone = 1e5;
    float hitZ = 0.0;

    for (int i = 0; i < LC_MAX_STEPS; i++) {
        vec3 p = ro + rd * t;
        vec3 res = lcMap(p);
        float d       = res.x;
        float dCyan   = res.y;
        float dOrange = res.z;

        float coneRadius = max(t * pixelSpread * 1.25, 0.003);

        minCyanCone   = min(minCyanCone,   dCyan   / coneRadius);
        minOrangeCone = min(minOrangeCone, dOrange / coneRadius);

        float stepDist = max(d * 0.65, 0.002);

        glowAccum += COL_CYAN * (stepDist * 0.022 / (0.015 + dCyan * dCyan * 3.5));

        float packet = pow(sin(p.z * 0.25 + SkyTime * 10.0) * 0.5 + 0.5, 2.5);
        vec3 orangeEmit = mix(COL_ORANGE * 0.45, COL_ORANGE * 2.2, packet);
        glowAccum += orangeEmit * (stepDist * 0.035 / (0.012 + dOrange * dOrange * 3.5));

        glowAccum += COL_BLUE * (stepDist * 0.0035 / (0.2 + d * d));

        if (d < LC_SURF_DIST || t > LC_MAX_DIST) {
            hitZ = p.z;
            break;
        }

        t += stepDist;
    }

    vec3 col = COL_BG;

    if (t < LC_MAX_DIST) {
        vec3 p = ro + rd * t;
        vec3 n = lcNormal(p);
        vec3 v = -rd;

        float fresnel = pow(clamp(1.0 - max(dot(n, v), 0.0), 0.0, 1.0), 3.0);

        float grid = getAntiAliasedGrid(p, n, rd, t, pixelSpread);
        vec3 baseColor = vec3(0.008, 0.012, 0.022);
        vec3 rimColor  = mix(COL_BLUE, COL_CYAN, 0.55) * fresnel * 0.9;
        vec3 gridColor = COL_CYAN * grid * 1.5;
        col = baseColor + rimColor + gridColor;

        float cyanCoverage = 1.0 - smoothstep(-0.5, 1.2, minCyanCone);
        vec3 cyanSurf = COL_CYAN * 2.4 + vec3(0.75);
        col = mix(col, cyanSurf, cyanCoverage);

        float orangeCoverage = 1.0 - smoothstep(-0.5, 1.2, minOrangeCone);
        float packet = pow(sin(hitZ * 0.25 + SkyTime * 10.0) * 0.5 + 0.5, 2.5);
        vec3 orangeSurf = mix(COL_ORANGE * 1.0, COL_ORANGE * 2.8 + vec3(0.95, 0.65, 0.25), packet);
        col = mix(col, orangeSurf, orangeCoverage);

        float fog = 1.0 - exp(-0.00075 * t * t);
        col = mix(col, COL_BG, fog);
    }

    col += glowAccum * 0.8;
    col = (col * (2.51 * col + 0.03)) / (col * (2.43 * col + 0.59) + 0.14);

    // (Shadered+: no vignette - a sky has no screen edge)

    col = pow(max(col, 0.0), vec3(1.0 / 2.2));

    float dither = fract(sin(dot(fragCoord, vec2(12.9898, 78.233))) * 43758.5453);
    col += (dither - 0.5) / 255.0;

    return clamp(col, 0.0, 1.0);
}

// --- The Universe Within - by Martijn Steinrucken aka BigWings 2018 (shadertoy.com/view/lscczl).
// License Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.
// TVM changes: iTime -> SkyTime; the screen is replaced by the view direction (stereographic map like the swirl, so it
// stays put in the world); the mouse parallax follows where you look; the music (FFT) glow uses a fixed value; the
// vignette and the 230-second fade in / out are removed (a sky has no screen edge and should never go black).
#define UW_S(a, b, t) smoothstep(a, b, t)
#define UW_NUM_LAYERS 4.

float uwN21(vec2 p) {
    vec3 a = fract(vec3(p.xyx) * vec3(213.897, 653.453, 253.098));
    a += dot(a, a.yzx + 79.76);
    return fract((a.x + a.y) * a.z);
}

vec2 uwGetPos(vec2 id, vec2 offs, float t) {
    float n = uwN21(id + offs);
    float n1 = fract(n * 10.);
    float n2 = fract(n * 100.);
    float a = t + n;
    return offs + vec2(sin(a * n1), cos(a * n2)) * .4;
}

float uwDfLine(in vec2 a, in vec2 b, in vec2 p) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0., 1.);
    return length(pa - ba * h);
}

float uwLine(vec2 a, vec2 b, vec2 uv) {
    float r1 = .04;
    float r2 = .01;
    float d = uwDfLine(a, b, uv);
    float d2 = length(a - b);
    float fade = UW_S(1.5, .5, d2);
    fade += UW_S(.05, .02, abs(d2 - .75));
    return UW_S(r1, r2, d) * fade;
}

float uwNetLayer(vec2 st, float n, float t) {
    vec2 id = floor(st) + n;
    st = fract(st) - .5;
    vec2 p[9];
    int i = 0;
    for (float y = -1.; y <= 1.; y++) {
        for (float x = -1.; x <= 1.; x++) {
            p[i++] = uwGetPos(id, vec2(x, y), t);
        }
    }
    float m = 0.;
    float sparkle = 0.;
    for (int i = 0; i < 9; i++) {
        m += uwLine(p[4], p[i], st);
        float d = length(st - p[i]);
        float s = (.005 / (d * d));
        s *= UW_S(1., .7, d);
        float pulse = sin((fract(p[i].x) + fract(p[i].y) + t) * 5.) * .4 + .6;
        pulse = pow(pulse, 20.);
        s *= pulse;
        sparkle += s;
    }
    m += uwLine(p[1], p[3], st);
    m += uwLine(p[1], p[5], st);
    m += uwLine(p[7], p[5], st);
    m += uwLine(p[7], p[3], st);
    float sPhase = (sin(t + n) + sin(t * .1)) * .25 + .5;
    sPhase += pow(sin(t * .1) * .5 + .5, 50.) * 5.;
    m += sparkle * sPhase;
    return m;
}

vec3 universeWithin() {
    vec3 dir = viewDirection();
    vec2 uv = dir.xy / (1.0 + abs(dir.z)) * 0.8;
    // TVM: the parallax (was the mouse) follows the camera's turn.
    vec2 M = vec2(sin(VYaw), -sin(VPitch)) * 0.5;

    float t = SkyTime * .1;
    float s = sin(t);
    float c = cos(t);
    mat2 rot = mat2(c, -s, s, c);
    vec2 st = uv * rot;
    M *= rot * 2.;

    float m = 0.;
    for (float i = 0.; i < 1.; i += 1. / UW_NUM_LAYERS) {
        float z = fract(t + i);
        float size = mix(15., 1., z);
        float fade = UW_S(0., .6, z) * UW_S(1., .8, z);
        m += fade * uwNetLayer(st * size - M * z, i, SkyTime);
    }

    float fft = 0.25; // TVM: was the music's loudness
    float glow = -uv.y * fft * 2.;

    vec3 baseCol = vec3(s, cos(t * .4), -sin(t * .24)) * .4 + .6;
    vec3 col = baseCol * m;
    col += baseCol * glow;
    return clamp(col, 0.0, 1.0);
}

// --- Data Cube: Shadertoy shader https://www.shadertoy.com/view/f3GSzd (Common + Volume + Image tabs, pasted by the
// user; author / title still to be added to the credits). Unchanged apart from: names prefixed bx / BX_ so they can't
// clash with the other skyboxes; iTime -> SkyTime, iResolution -> InSize, iFrame -> 0; the 128^3 volume (its Volume tab
// only stored sceneSDF) is skipped and sceneSDF is called directly; PI / TAU / HIGH_RES / antialiasing dropped (unused
// or the same as above).
#define BX_N 128.0
#define BX_SCALE 4.0
#define BX_S(d) 1.-smoothstep(-1.5,1.5, (d)*InSize.y )
#define bxDeg45 .707
#define BX_R45(p) (( p + vec2(p.y,-p.x) ) *bxDeg45)
#define BX_B(p,s) max(abs(p).x-s.x,abs(p).y-s.y)
#define bxTri(p,s) max(BX_R45(p).x,max(BX_R45(p).y,BX_B(p,s)))
#define BX_ZERO 0
#define BX_FONT_THICK 0.07;
#define BX_MAT_0 0.0
#define BX_MAT_1 1.0
#define BX_MAT_2 2.0

vec2 bxCombine(vec2 val1, vec2 val2 ){
    return (val1.x < val2.x)?val1:val2;
}

mat2 bxRot(float a){ float c=cos(a), s=sin(a); return mat2(c,-s,s,c); }

float bxHash(vec3 p){
    return fract(sin(dot(p,vec3(123.456,789.345,1011.987)))*98778.654);
}

float bxLineTo(vec2 p, vec2 a, vec2 b, float c, float d){
    float k = dot(p-a,b-a)/dot(b-a,b-a);
    vec2 closeTo = mix(a,b,clamp(k,c,d));
    return length(closeTo-p);
}

float bxC0(vec2 p){
    p.x = abs(p.x);
    float d = bxLineTo(p, vec2(0.0,0.5),vec2(0.4,0.0),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(0.4,0.0),vec2(0.0,-0.5),0.,1.)-BX_FONT_THICK;
    return min(d,d2);
}

float bxC1(vec2 p){
    float d = bxLineTo(p, vec2(0.1,0.5),vec2(-0.15,0.2),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(0.1,0.5),vec2(0.0,-0.4),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(0.2,-0.35),vec2(-0.2,-0.5),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    return d;
}

float bxC2(vec2 p){
    float d = bxLineTo(p, vec2(0.45,0.5),vec2(-0.45,0.4),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(0.45,0.5),vec2(-0.5,-0.5),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(-0.5,-0.5),vec2(0.45,-0.45),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    return d;
}

float bxC3(vec2 p){
    float d = bxLineTo(p, vec2(0.45,0.5),vec2(-0.45,0.4),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(0.45,0.5),vec2(-0.3,-0.1),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(-0.3,-0.1),vec2(0.45,-0.45),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(-0.5,-0.5),vec2(0.45,-0.45),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    return d;
}

float bxC4(vec2 p){
    float d = bxLineTo(p, vec2(0.0,0.5),vec2(-0.45,-0.2),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(0.0,0.5),vec2(0.0,-0.5),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(-0.45,-0.2),vec2(0.45,0.05),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    return d;
}

float bxC5(vec2 p){
    float d = bxLineTo(p, vec2(0.45,0.5),vec2(-0.45,0.4),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(-0.45,0.4),vec2(-0.3,0.0),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(-0.3,0.0),vec2(0.45,-0.25),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(-0.5,-0.5),vec2(0.45,-0.25),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    return d;
}

float bxC6(vec2 p){
    float d = bxLineTo(p, vec2(0.0,0.5),vec2(-0.5,0.0),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(-0.5,0.0),vec2(0.0,-0.5),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(0.5,0.0),vec2(0.0,-0.5),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(0.5,0.0),vec2(-0.5,0.0),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    return d;
}

float bxC7(vec2 p){
    float d = bxLineTo(p, vec2(0.45,0.5),vec2(-0.35,0.4),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(0.45,0.5),vec2(-0.1,-0.5),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    return d;
}

float bxC8(vec2 p){
    p.x = abs(p.x);
    p.y = abs(p.y)-0.25;
    float d = bxLineTo(p, vec2(0.0,0.25),vec2(0.4,0.0),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(0.4,0.0),vec2(0.0,-0.25),0.,1.)-BX_FONT_THICK;
    return min(d,d2);
}

float bxC9(vec2 p){
    float d = bxLineTo(p, vec2(0.45,0.5),vec2(-0.35,0.3),0.,1.)-BX_FONT_THICK;
    float d2 = bxLineTo(p, vec2(0.45,0.5),vec2(-0.1,-0.5),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    d2 = bxLineTo(p, vec2(-0.35,0.3),vec2(0.145,-0.05),0.,1.)-BX_FONT_THICK;
    d = min(d,d2);
    return d;
}

float bxDrawNumber(vec2 p, int num){
    vec2 prevP = p;
    float d = 10.;
    float s = 1.5;
    p*=s;
    if(num == 0){
        d = bxC0(p);
    } else if(num == 1){
        d = bxC1(p);
    } else if(num == 2){
        d = bxC2(p);
    } else if(num == 3){
        d = bxC3(p);
    } else if(num == 4){
        d = bxC4(p);
    } else if(num == 5){
        d = bxC5(p);
    } else if(num == 6){
        d = bxC6(p);
    } else if(num == 7){
        d = bxC7(p);
    } else if(num == 8){
        d = bxC8(p);
    } else if(num == 9){
        d = bxC9(p);
    }

    return d / s;
}

float bxNumbers(vec2 p, float t, float n){
    float speed = 10.;
    p*=6.;
    vec2 prevP = p;
    float d = bxDrawNumber(p,int(mod((n*3.)+speed*t*n,10.)));
    p.x += 0.7;
    float d2 = bxDrawNumber(p,int(mod((n*6.)+speed*t*n,10.)));
    d = min(d,d2);
    p = prevP;
    p.x -= 0.7;
    d2 = bxDrawNumber(p,int(mod((n*9.)+speed*t*n,10.)));
    d = min(d,d2);
    return d;
}

float bxArrow(vec2 p){
    p*=bxRot(radians(90.));
    vec2 prevP = p;
    float s = 0.294;
    p.y-=s*0.5;
    float d = bxTri(p,vec2(s));
    p.y+=s*0.5;
    float d2 = bxTri(p,vec2(s));
    d = max(-d2,d);
    return d*0.6;
}

float bxBg(vec2 p, float t){
    vec2 prevP = p;
    p.y-=t*0.1;
    p*=5.;
    vec2 id = floor(p);
    vec2 gr = fract(p)-0.5;
    float n = bxHash(vec3(id,1.));
    float d = abs(BX_B(gr, vec2(0.5)))-0.003;
    d = max(-(abs(gr.x)-0.4),d);
    d = max(-(abs(gr.y)-0.4),d);
    float d2 = bxNumbers(gr, t, n);
    d = min(d,d2);
    return d;
}

// thx! iq https://iquilezles.org/articles/distfunctions/
float bxSdBox(vec3 p, vec3 b){
    vec3 q = abs(p) - b;
    return length(max(q,0.0)) + min(max(q.x,max(q.y,q.z)),0.0);
}

// thx! iq https://iquilezles.org/articles/distfunctions/
float bxSdBoxFrame( vec3 p, vec3 b, float e )
{
       p = abs(p  )-b;
  vec3 q = abs(p+e)-e;
  return min(min(
    length(max(vec3(p.x,q.y,q.z),0.0))+min(max(p.x,max(q.y,q.z)),0.0),
    length(max(vec3(q.x,p.y,q.z),0.0))+min(max(q.x,max(p.y,q.z)),0.0)),
    length(max(vec3(q.x,q.y,p.z),0.0))+min(max(q.x,max(q.y,p.z)),0.0));
}

float bxFractal(vec3 p, float t){
    vec3 prevP = p;
    float d = 10.;
    for(int i = 0; i<2; i++){
        p = abs(p)-float(i)*0.12;
        p.xz*=bxRot(radians(30.*t));
        p.yz*=bxRot(radians(30.*t));
        float d2 = bxSdBoxFrame(p,vec3(0.12*float(i)),0.02);
        d = min(d,d2);
    }
    p = prevP;
    return d;
}

vec2 bxSceneSDF(vec3 p, float t){
    vec3 prevP = p;
    p.y -= t*0.2;
    p *= BX_SCALE;
    vec3 id2 = floor(p + 0.5);
    vec3 s = sign(p - id2);
    float d = 10., id = 0.;
    for (int z=0; z<=1; z++)
    for (int y=0; y<=1; y++)
    for (int x=0; x<=1; x++){
        vec3 c = id2 + vec3(x,y,z)*s;
        vec3 q = p - c;
        float n = bxHash(c);
        
        float dd = 0.0, mid = 0.0;
        if(n<0.1){
            mid = BX_MAT_0;
            dd = bxFractal(q,100.+t*max(n,0.1)*5.);
        } else if(n>=0.1 && n<0.5) {            
            vec2 rd = vec2(bxSdBoxFrame(q, vec3(0.445), 0.02),BX_MAT_1);
            
            q.xz*=bxRot(radians(t*30.*n*(n<0.3 ? -1. : 1.)));
            q.y+=sin(t*5.*n)*0.05;
            float dd2 = bxDrawNumber(q.xy, int(mod((n*100.)+t*n*5.,10.)));
            dd2 = max(abs(q.z)-0.03,dd2);
            vec2 rd2 = vec2(dd2,BX_MAT_2);
            rd = bxCombine(rd,rd2);
            
            dd = rd.x;
            mid = rd.y;
        } else if(n>=0.5 && n<0.75) {
            mid = BX_MAT_0;
            q.xz*=bxRot(radians(70.+t*30.*n*(n<0.65 ? -1. : 1.)));
            q.yz*=bxRot(radians(100.+t*30.*n*(n<0.65 ? -1. : 1.)));
            dd = bxArrow(q.xy);
            dd = max(abs(q.z)-0.06,dd);
        } else if(n>=0.75) {
            mid = BX_MAT_2;
            q.xz*=bxRot(radians(80.+t*30.*n*(n<0.8 ? -1. : 1.)));
            q.yz*=bxRot(radians(90.+t*30.*n*(n<0.8 ? -1. : 1.)));
            dd = bxSdBoxFrame(q, vec3(0.2),0.02);
            dd = min(length(q)-0.1,dd);
        }
        
        if (dd < d){ 
            d = dd;
            id = mid;
        }
    }
    d = max(bxSdBox(prevP, vec3(0.38)), d / BX_SCALE);
    return vec2(d, id);
}
vec2 bxBoxIntersection( in vec3 ro, in vec3 rd)
{
    vec3 m = 1.0/rd;
    vec3 n = m*ro;
    vec3 k = abs(m)*0.5;
    vec3 t1 = -n - k;
    vec3 t2 = -n + k;
    float tN = max( max( t1.x, t1.y ), t1.z );
    float tF = min( min( t2.x, t2.y ), t2.z );
    return vec2( tN, tF );
}

// from iq's advice to reduce compile time. Thank you!
vec3 bxNorm(vec3 p, float t, out float mid)
{
    vec3 n = vec3(0.0);
    float best = 1e9;
    mid = 0.0;
    for( int i=BX_ZERO; i<4; i++ )
    {
        vec3 e = 0.5773*(2.0*vec3((((i+3)>>1)&1),((i>>1)&1),(i&1))-1.0);
        vec2 r = bxSceneSDF(p+0.0002*e, t);
        n += e*r.x;
        if (r.x < best) { best = r.x; mid = r.y; }
    }
    return normalize(n);
}

// TVM: a window onto the cube. It sits ahead of you whichever way you look, seen from the side you look at it from (turn
// to look around it), and slowly turns on its own (the shader's own idle turn, 45 + 10 degrees a second). The number grid
// behind it lies on the sky round you (Shadered+: in the original it was on the screen).
vec3 dataCubeSkybox() {
    vec3 dir = viewDirection();
    vec3 fwd = viewForward();
    vec3 ro = -fwd * 1.1; // closer than the original 1.5: the game sees wider than its lens
    vec3 rd = dir;
    mat2 turn = bxRot(radians(45. + 10. * SkyTime));
    ro.xz *= turn;
    rd.xz *= turn;

    vec3 col = vec3(0.);
    // (Shadered+: the number grid on the sky round you, not stuck to the screen - through a sky block it slid along
    // with the camera)
    vec2 grid = dir.xy / (1.0 + abs(dir.z)) * 1.6;
    float dd = bxBg(grid, SkyTime);
    col = mix(col, vec3(0.15), BX_S(dd));

    vec2 tb = bxBoxIntersection(ro, rd);
    if (tb.x < tb.y && tb.y > 0.0) {
        float t = max(tb.x, 0.0);
        for (int i = 0; i < 96; i++) {
            vec3 p = ro + rd * t;
            float d = bxSceneSDF(p, SkyTime).x;
            if (d < 0.0005) {
                float m = 0.0;
                vec3 n = bxNorm(p, SkyTime, m);
                vec3 lp = vec3(0.6, 0.8, -0.4);
                vec3 ld = normalize(lp - p);
                float dif = clamp(dot(n, ld), 0.0, 1.0);
                float amb = 0.5 + 0.5 * n.y;
                vec3 mat = vec3(0.);
                if (m == BX_MAT_0)      mat = vec3(0.9);
                else if (m == BX_MAT_1) mat = vec3(0.1);
                else if (m == BX_MAT_2) mat = vec3(0.6);
                col = mat * (0.2 * amb + 0.9 * dif);
                break;
            }
            t += d;
            if (t > tb.y) break;
        }
    }
    col = pow(col, vec3(0.4545));
    return clamp(col, 0.0, 1.0);
}

// --- The block's face under this pixel (for the effects painted on the faces instead of the sky). ---
// point: the spot on the face, in blocks from the camera along the world's axes; ray: the direction it is seen in.

const vec3[] EP_COLORS = vec3[](
    vec3(0.022087, 0.098399, 0.110818),
    vec3(0.011892, 0.095924, 0.089485),
    vec3(0.027636, 0.101689, 0.100326),
    vec3(0.046564, 0.109883, 0.114838),
    vec3(0.064901, 0.117696, 0.097189),
    vec3(0.063761, 0.086895, 0.123646),
    vec3(0.084817, 0.111994, 0.166380),
    vec3(0.097489, 0.154120, 0.091064),
    vec3(0.106152, 0.131144, 0.195191),
    vec3(0.097721, 0.110188, 0.187229),
    vec3(0.133516, 0.138278, 0.148582),
    vec3(0.070006, 0.243332, 0.235792),
    vec3(0.196766, 0.142899, 0.214696),
    vec3(0.047281, 0.315338, 0.321970),
    vec3(0.204675, 0.390010, 0.302066),
    vec3(0.080955, 0.314821, 0.661491)
);

const mat4 EP_SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

mat4 epLayer(float layer) {
    float gameTime = SkyTime / 1200.0; // the game's GameTime: one per day (20 minutes)
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 17.0 / layer,
        0.0, 1.0, 0.0, (2.0 + layer / 1.5) * (gameTime * 1.5),
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );
    float angle = radians((layer * layer * 4321.0 + layer * 9.0) * 2.0);
    mat2 rotate = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    mat2 scale = mat2((4.5 - layer / 4.0) * 2.0);
    return mat4(scale * rotate) * translate * EP_SCALE_TRANSLATE;
}

vec3 endPortalSkybox(int layers) {
    vec2 uv;
    if (VFull) {
        uv = (SkyCoord - 0.5) / VZoom + 0.5 + vec2(-VCentre, VTurn) / 6.2831853;
    } else {
        vec3 dir = viewDirection();
        // (Shadered+: as close as the End Portal's own stars are on the screen - smaller, they broke up into dark specks)
        uv = dir.xy / (1.0 + abs(dir.z)) * 1.6 + 0.5;
    }
    vec4 proj = vec4(uv, 0.0, 1.0);
    vec3 color = textureProj(EndSkySampler, proj).rgb * EP_COLORS[0];
    for (int i = 0; i < layers; i++) {
        color += textureProj(EndPortalSampler, proj * epLayer(float(i + 1))).rgb * EP_COLORS[i];
    }
    return clamp(color, 0.0, 1.0);
}

// --- Classic End Portal skybox: the End Portal as it was drawn before 1.17. Sixteen sheets of stars lie at different
// depths behind the block's face (the first, the end sky, 65 blocks deep; the others 15 down to 1), each turned and
// scaled its own way and drifting slowly, so they slide over each other as you move. The colours are the old renderer's
// (its fixed random seed 31100). In full view, and on the item icons, the sheets are flat on the screen. ---
const vec3[] CEP_COLORS = vec3[](
    vec3(0.1000, 0.1000, 0.1000),
    vec3(0.0063, 0.0510, 0.0475),
    vec3(0.0147, 0.0542, 0.0535),
    vec3(0.0249, 0.0589, 0.0615),
    vec3(0.0349, 0.0634, 0.0523),
    vec3(0.0345, 0.0471, 0.0670),
    vec3(0.0463, 0.0611, 0.0908),
    vec3(0.0536, 0.0848, 0.0501),
    vec3(0.0590, 0.0729, 0.1084),
    vec3(0.0550, 0.0620, 0.1053),
    vec3(0.0763, 0.0790, 0.0849),
    vec3(0.0408, 0.1419, 0.1375),
    vec3(0.1181, 0.0857, 0.1288),
    vec3(0.0296, 0.1971, 0.2012),
    vec3(0.1364, 0.2600, 0.2014),
    vec3(0.0607, 0.2361, 0.4961)
);

vec3 classicEndPortalSkybox() {
    // (Shadered+: a sky has no block face to lie behind - each sheet laid on the sky round you instead, the deeper
    // ones smaller, so they still slide over each other as the view turns)
    vec3 d = viewDirection();
    vec2 p = d.xy / (1.0 + abs(d.z));
    float drift = fract(SkyTime / 700.0);
    vec3 color = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        float depth = i == 0 ? 65.0 : float(16 - i);
        float scale = i == 0 ? 0.125 : (i == 1 ? 0.5 : 0.0625);
        vec2 uv = p * (2.0 + depth * 0.25); // (Shadered+: half as fine - the stars were specks)
        float angle = radians(float(i * i * 4321 + i * 9) * 2.0);
        uv = (mat2(cos(angle), -sin(angle), sin(angle), cos(angle)) * (uv - 0.5) + 0.5) * scale;
        uv.y += drift;
        if (i == 0) {
            color = texture(EndSkySampler, uv).rgb * CEP_COLORS[0];
        } else {
            color += texture(EndPortalSampler, uv).rgb * CEP_COLORS[i];
        }
    }
    return clamp(color, 0.0, 1.0);
}

// --- The plain colour effects, shared by the filter blocks and the filter items lying over a skybox. ---
