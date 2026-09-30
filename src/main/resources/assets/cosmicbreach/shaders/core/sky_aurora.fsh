#version 150

// Aurora light, added onto the sky: a bright hem, rays climbing from it (Sampler0.r varies only
// along the ribbon, so it reads as vertical streaks), slow folds (Sampler0.g), green at the hem,
// teal above, violet at the top. Sampler0 is a baked tileable texture: no noise is computed here.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform vec4 AuroraParams;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float t = AuroraParams.x;
    float u = texCoord.x;
    float v = texCoord.y;
    float rays = texture(Sampler0, vec2(u * 0.9 + t * 0.011, 0.25)).r;
    float rays2 = texture(Sampler0, vec2(u * 2.3 - t * 0.017, 0.75)).r;
    float fold = texture(Sampler0, vec2(u * 0.33 + t * 0.005, v * 0.55 - t * 0.009)).g;
    float hem = smoothstep(0.0, 0.06, v) * (1.0 - smoothstep(0.82, 1.0, v));
    float profile = hem * (exp(-v * 2.6) * 0.85 + 0.15);
    float streak = 0.3 + 0.7 * rays * (0.55 + 0.45 * rays2);
    vec3 col = mix(vec3(0.38, 1.0, 0.62), vec3(0.22, 0.86, 0.95), smoothstep(0.06, 0.35, v));
    col = mix(col, vec3(0.62, 0.36, 1.0), smoothstep(0.35, 0.95, v));
    float light = profile * streak * (0.5 + 0.5 * fold) * vertexColor.a * AuroraParams.y;
    fragColor = vec4(col * vertexColor.rgb * light, 1.0) * ColorModulator;
}
