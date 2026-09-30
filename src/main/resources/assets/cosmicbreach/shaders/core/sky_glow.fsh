#version 150

// Procedural sky lights, added onto the sky (blend ONE, ONE). GlowMode picks the shape:
//   0 Solenne: a white-gold disc with a darker, warmer limb and a soft corona with slow streamers.
//      GlowParams: x disc radius (fraction of the quad), y corona strength, z streamer turn, w disc strength.
//   1 a star with four spikes (Vesper). x core size, y spike strength, z spike turn, w brightness.
//   2 a beam: UV.x runs out from the source, UV.y across. w brightness.
//   3 a soft round glow (a cluster's heart). w brightness.
// The vertex colour tints everything; GlowColor2 is the corona's colour.

uniform vec4 ColorModulator;
uniform int GlowMode;
uniform vec4 GlowParams;
uniform vec3 GlowColor2;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec2 uv = texCoord * 2.0 - 1.0;
    float r = length(uv);
    vec3 col;
    if (GlowMode == 0) {
        float rd = GlowParams.x;
        float disc = 1.0 - smoothstep(rd * 0.965, rd * 1.035, r);
        float depth = sqrt(max(1.0 - (r / rd) * (r / rd), 0.0));
        vec3 discCol = mix(vec3(1.0, 0.8, 0.5), vec3(1.0, 0.985, 0.94), pow(depth, 0.45));
        float a = atan(uv.y, uv.x);
        float streak = 0.72 + 0.28 * sin(a * 7.0 + GlowParams.z) * sin(a * 3.0 - GlowParams.z * 0.6 + 1.7);
        float cr = max(r - rd, 0.0) / max(1.0 - rd, 1e-3);
        float outside = smoothstep(rd * 0.96, rd * 1.06, r);
        float corona = (exp(-cr * 10.0) * 0.85 + exp(-cr * 3.2) * 0.35 * streak) * outside * (1.0 - smoothstep(0.8, 1.0, r));
        col = vertexColor.rgb * discCol * (disc * GlowParams.w) + GlowColor2 * (corona * GlowParams.y);
    } else if (GlowMode == 1) {
        float c = cos(GlowParams.z);
        float s = sin(GlowParams.z);
        vec2 ro = vec2(c * uv.x - s * uv.y, s * uv.x + c * uv.y);
        float core = exp(-r * r / (GlowParams.x * GlowParams.x));
        float halo = exp(-r * 5.5) * 0.3;
        float spikes = exp(-abs(ro.x) * 90.0) * exp(-abs(ro.y) * 2.6) + exp(-abs(ro.y) * 90.0) * exp(-abs(ro.x) * 2.6);
        float fade = 1.0 - smoothstep(0.75, 1.0, r);
        col = vertexColor.rgb * ((core + halo + spikes * GlowParams.y) * fade * GlowParams.w);
    } else if (GlowMode == 2) {
        float across = uv.y;
        float along = texCoord.x;
        float shape = exp(-across * across * 7.0) * pow(1.0 - along, 1.6) * smoothstep(0.0, 0.04, along);
        col = vertexColor.rgb * (shape * GlowParams.w);
    } else {
        col = vertexColor.rgb * (exp(-r * r * 3.5) * (1.0 - smoothstep(0.8, 1.0, r)) * GlowParams.w);
    }
    fragColor = vec4(col * vertexColor.a, 1.0) * ColorModulator;
}
