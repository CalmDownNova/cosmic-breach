#version 150

// Aetheria's sky seen through the Breach, by view direction, like the End portal's starfield but ours:
// the Upper Reach's daytime gradient and its baked nebula cubemap (the atlas the sky dome samples, see
// sky_common.glsl), turned over so that looking down into the hole shows the sky you are about to fall
// into. The nebula turns slowly; the light spills over the rim of the hole.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;   // the day's fraction, 0 to 1 over 24000 ticks

in vec3 viewDir;
in vec2 texCoord0;

out vec4 fragColor;

// The nebula atlas: six faces of 512 px in a 3 by 2 grid of 520 px tiles with 4 px gutters.
vec2 cube_uv(vec3 d) {
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

void main() {
    vec3 d = normalize(viewDir);
    vec3 s = vec3(d.x, -d.y, d.z);
    float h = clamp(s.y, 0.0, 1.0);

    // the Reach by day: warm haze at the horizon, clear blue overhead
    vec3 sky = mix(vec3(0.95, 0.90, 0.78), vec3(0.80, 0.88, 0.97), smoothstep(0.0, 0.08, h));
    sky = mix(sky, vec3(0.45, 0.70, 0.95), smoothstep(0.05, 0.4, h));
    sky = mix(sky, vec3(0.22, 0.46, 0.86), smoothstep(0.35, 1.0, h));

    // the nebula, turning once every three minutes
    float a = GameTime * 6.2831853 * 6.6667;
    vec3 r = vec3(cos(a) * s.x - sin(a) * s.z, s.y, sin(a) * s.x + cos(a) * s.z);
    vec4 neb = texture(Sampler0, cube_uv(r));
    float lum = dot(neb.rgb, vec3(0.3, 0.55, 0.15));
    vec3 hue = neb.rgb / max(max(neb.r, max(neb.g, neb.b)), 0.02);
    float veil = clamp(smoothstep(0.02, 0.5, lum) * 0.75, 0.0, 0.8);
    sky = mix(sky, mix(vec3(1.0), hue, 0.6) * 0.97, veil);
    sky += neb.rgb * 0.3;

    // light spilling over the rim of the hole (the UVs span the whole 2 by 2 opening: 0.1 is a fifth of a block)
    vec2 e = min(texCoord0, 1.0 - texCoord0);
    float rim = 1.0 - smoothstep(0.0, 0.1, min(e.x, e.y));
    sky = mix(sky, vec3(1.0, 0.95, 0.82), rim * 0.5);

    fragColor = vec4(sky, 1.0) * ColorModulator;
}
