#version 150

#moj_import <cosmicbreach:sky_common.glsl>

// The sky's base (gradient and glare) and the nebula dome: the two layers' baked cubemaps
// (Sampler0 and Sampler1) cross-faded by the camera's height. The nebula's RGB is light added
// to the sky, its alpha dust that dims what is behind it.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec4 ColorModulator;
uniform mat4 CelestialMat;   // world direction to the rotating celestial frame
uniform vec4 NebulaParams;   // x: Sampler0 to Sampler1, y: light gain, z: dust gain, w: gain below the horizon
uniform float NebulaVeil;    // by day the clouds are sunlit veils of their own colour rather than light

in vec3 viewDir;

out vec4 fragColor;

void main() {
    vec3 d = normalize(viewDir);
    vec3 sky = sky_gradient(d) + sun_glow(d);

    vec3 c = normalize((CelestialMat * vec4(d, 0.0)).xyz);
    vec2 uv = sky_cube_uv(c);
    vec4 neb = mix(texture(Sampler0, uv), texture(Sampler1, uv), NebulaParams.x);
    float gain = NebulaParams.y * mix(1.0, NebulaParams.w, smoothstep(0.02, -0.35, d.y));
    // the nebula thins towards the horizon haze
    gain *= mix(0.35, 1.0, smoothstep(0.0, 0.3, abs(d.y)));
    sky = sky * (1.0 - neb.a * NebulaParams.z);
    // by day: pastel veils in the cloud's own hue, as if lit by the sun, over the blue
    float lum = dot(neb.rgb, vec3(0.3, 0.55, 0.15));
    vec3 hue = neb.rgb / max(max(neb.r, max(neb.g, neb.b)), 0.02);
    float veil = clamp(smoothstep(0.02, 0.5, lum) * NebulaVeil, 0.0, 0.8) * mix(0.35, 1.0, smoothstep(0.0, 0.3, abs(d.y)));
    sky = mix(sky, mix(vec3(1.0), hue, 0.55) * 0.97, veil);
    sky += neb.rgb * gain;

    fragColor = vec4(sky + sky_dither(gl_FragCoord.xy), 1.0) * ColorModulator;
}
