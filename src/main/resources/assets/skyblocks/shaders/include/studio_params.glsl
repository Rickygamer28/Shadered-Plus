// Written by tools/studio_spec.py: the Sky Studio's settings (StudioSpec.java has the same list).

uniform vec4 P0;
uniform vec4 P1;
uniform vec4 P2;
uniform vec4 P3;
uniform vec4 P4;
uniform vec4 P5;
uniform vec4 P6;
uniform vec4 P7;
uniform vec4 P8;
uniform vec4 P9;
uniform vec4 P10;
uniform vec4 P11;
uniform vec4 P12;
uniform vec4 P13;
uniform vec4 P14;
uniform vec4 P15;
uniform vec4 P16;
uniform vec4 P17;
uniform vec4 P18;
uniform vec4 P19;
uniform vec4 P20;
uniform vec4 P21;
uniform vec4 P22;
uniform vec4 P23;
uniform vec4 P24;
uniform vec4 P25;
uniform vec4 P26;
uniform vec4 P27;
uniform vec4 P28;
uniform vec4 P29;
uniform vec4 P30;
uniform vec4 P31;
uniform vec4 P32;
uniform vec4 P33;
uniform vec4 P34;
uniform vec4 P35;
uniform vec4 P36;
uniform vec4 P37;
uniform vec4 P38;
uniform vec4 P39;
uniform vec4 P40;
uniform vec4 P41;

#define S_global_brightness P0.x
#define S_global_contrast P0.y
#define S_global_saturation P0.z
#define S_global_hue_shift P0.w
#define S_global_speed P1.x
#define S_global_spin P1.y
#define S_global_vignette P1.z
#define S_gradient_on P1.w
#define S_gradient_top vec3(P2.x, P2.y, P2.z)
#define S_gradient_middle vec3(P2.w, P3.x, P3.y)
#define S_gradient_horizon vec3(P3.z, P3.w, P4.x)
#define S_gradient_ground vec3(P4.y, P4.z, P4.w)
#define S_gradient_horizon_height P5.x
#define S_gradient_glow P5.y
#define S_gradient_glow_width P5.z
#define S_gradient_curve P5.w
#define S_nebula_on P6.x
#define S_nebula_colour1 vec3(P6.y, P6.z, P6.w)
#define S_nebula_colour2 vec3(P7.x, P7.y, P7.z)
#define S_nebula_colour3 vec3(P7.w, P8.x, P8.y)
#define S_nebula_amount P8.z
#define S_nebula_scale P8.w
#define S_nebula_drift P9.x
#define S_nebula_softness P9.y
#define S_nebula_detail P9.z
#define S_nebula_brightness P9.w
#define S_nebula_swirl P10.x
#define S_stars_on P10.y
#define S_stars_colour vec3(P10.z, P10.w, P11.x)
#define S_stars_variety P11.y
#define S_stars_density P11.z
#define S_stars_size P11.w
#define S_stars_twinkle P12.x
#define S_stars_brightness P12.y
#define S_stars_milky_way P12.z
#define S_stars_band_tilt P12.w
#define S_stars_big_stars P13.x
#define S_aurora_on P13.y
#define S_aurora_colour1 vec3(P13.z, P13.w, P14.x)
#define S_aurora_colour2 vec3(P14.y, P14.z, P14.w)
#define S_aurora_brightness P15.x
#define S_aurora_height P15.y
#define S_aurora_thickness P15.z
#define S_aurora_wave_speed P15.w
#define S_aurora_waviness P16.x
#define S_aurora_rays P16.y
#define S_aurora_bands P16.z
#define S_moon_on P16.w
#define S_moon_colour vec3(P17.x, P17.y, P17.z)
#define S_moon_size P17.w
#define S_moon_height P18.x
#define S_moon_direction P18.y
#define S_moon_glow P18.z
#define S_moon_round P18.w
#define S_moon_craters P19.x
#define S_moon_rings P19.y
#define S_moon_ring_colour vec3(P19.z, P19.w, P20.x)
#define S_moon_orbit P20.y
#define S_planet_on P20.z
#define S_planet_colour1 vec3(P20.w, P21.x, P21.y)
#define S_planet_colour2 vec3(P21.z, P21.w, P22.x)
#define S_planet_size P22.y
#define S_planet_height P22.z
#define S_planet_direction P22.w
#define S_planet_bands P23.x
#define S_planet_rings P23.y
#define S_planet_ring_colour vec3(P23.z, P23.w, P24.x)
#define S_planet_glow P24.y
#define S_planet_turn P24.z
#define S_clouds_on P24.w
#define S_clouds_colour vec3(P25.x, P25.y, P25.z)
#define S_clouds_shadow vec3(P25.w, P26.x, P26.y)
#define S_clouds_amount P26.z
#define S_clouds_height P26.w
#define S_clouds_scale P27.x
#define S_clouds_speed P27.y
#define S_clouds_softness P27.z
#define S_rays_on P27.w
#define S_rays_colour vec3(P28.x, P28.y, P28.z)
#define S_rays_strength P28.w
#define S_rays_count P29.x
#define S_rays_spin P29.y
#define S_rays_width P29.z
#define S_shooting_on P29.w
#define S_shooting_colour vec3(P30.x, P30.y, P30.z)
#define S_shooting_how_often P30.w
#define S_shooting_trail P31.x
#define S_shooting_speed P31.y
#define S_shooting_size P31.z
#define S_mountains_on P31.w
#define S_mountains_colour vec3(P32.x, P32.y, P32.z)
#define S_mountains_far_colour vec3(P32.w, P33.x, P33.y)
#define S_mountains_height P33.z
#define S_mountains_roughness P33.w
#define S_mountains_layers P34.x
#define S_mountains_haze P34.y
#define S_grid_on P34.z
#define S_grid_colour vec3(P34.w, P35.x, P35.y)
#define S_grid_spacing P35.z
#define S_grid_line_width P35.w
#define S_grid_scroll P36.x
#define S_grid_glow P36.y
#define S_grid_sun P36.z
#define S_grid_sun_colour vec3(P36.w, P37.x, P37.y)
#define S_lake_on P37.z
#define S_lake_tint vec3(P37.w, P38.x, P38.y)
#define S_lake_clarity P38.z
#define S_lake_ripples P38.w
#define S_lake_ripple_speed P39.x
#define S_sparkles_on P39.y
#define S_sparkles_colour vec3(P39.z, P39.w, P40.x)
#define S_sparkles_amount P40.y
#define S_sparkles_size P40.z
#define S_sparkles_speed P40.w
#define S_sparkles_twinkle P41.x
