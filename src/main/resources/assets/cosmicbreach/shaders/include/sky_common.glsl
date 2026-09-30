#version 150

// Shared by Aetheria's sky shaders (sky_dome, sky_body). World directions: +Y up, +X east, -Z north.
// The colours come from client/sky/SkyModel (per layer and time of day); nothing here is noise.

uniform vec3 ZenithColor;   // straight up
uniform vec3 MidColor;      // about 25 degrees up
uniform vec3 HazeColor;     // about 5 degrees up
uniform vec3 HorizonColor;  // the horizon itself: the fog colour, so distant terrain melts into the sky
uniform vec3 LowColor;      // about 25 degrees down: the air between the layers
uniform vec3 NadirColor;    // straight down: the Deep's glow far below
uniform vec3 SunDir;        // towards Solenne (unit)
uniform vec4 SunGlow;       // rgb colour of the glare, a = its strength (already scaled by how much of the disc shows)

// The sky's colour for view direction d (unit), without the nebula.
vec3 sky_gradient(vec3 d) {
    float h = d.y;
    if (h >= 0.0) {
        vec3 c = mix(HorizonColor, HazeColor, smoothstep(0.0, 0.045, h));
        c = mix(c, MidColor, smoothstep(0.03, 0.4, h));
        return mix(c, ZenithColor, smoothstep(0.32, 1.0, h));
    }
    float g = -h;
    vec3 c = mix(HorizonColor, LowColor, smoothstep(0.0, 0.2, g));
    return mix(c, NadirColor, smoothstep(0.2, 1.0, g));
}

// Glare around the sun: a wide halo and a tight core, stronger near the horizon where the light crosses more air.
vec3 sun_glow(vec3 d) {
    float mu = max(dot(d, SunDir), 0.0);
    float g = 0.42 * pow(mu, 5.0) + 0.33 * pow(mu, 32.0) + 0.25 * pow(mu, 400.0);
    float low = 1.0 - smoothstep(0.0, 0.5, abs(d.y));
    return SunGlow.rgb * (SunGlow.a * g * (0.75 + 0.5 * low));
}

// A tiny dither so the smooth gradients never band on an 8-bit screen.
float sky_dither(vec2 fragCoord) {
    return (fract(sin(dot(fragCoord, vec2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0;
}

// The nebula cubemap atlases (tools/sky/cube.py): six faces of 512 px in a 3 by 2 grid of 520 px tiles,
// each with a 4 px gutter baked with the true continuation of the view, so bilinear filtering has no seams.
vec2 sky_cube_uv(vec3 d) {
    vec3 a = abs(d);
    float face;
    vec2 uv;
    if (a.x >= a.y && a.x >= a.z) {
        if (d.x > 0.0) { face = 0.0; uv = vec2(-d.z, -d.y) / a.x; }
        else { face = 1.0; uv = vec2(d.z, -d.y) / a.x; }
    } else if (a.y >= a.z) {
        if (d.y > 0.0) { face = 2.0; uv = vec2(d.x, d.z) / a.y; }
        else { face = 3.0; uv = vec2(d.x, -d.z) / a.y; }
    } else {
        if (d.z > 0.0) { face = 4.0; uv = vec2(d.x, -d.y) / a.z; }
        else { face = 5.0; uv = vec2(-d.x, -d.y) / a.z; }
    }
    vec2 tile = vec2(mod(face, 3.0), floor(face / 3.0));
    vec2 px = tile * 520.0 + 4.0 + (uv * 0.5 + 0.5) * 512.0;
    return px / vec2(1560.0, 1040.0);
}
