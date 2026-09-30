#version 150

#moj_import <cosmicbreach:sky_common.glsl>

// Thalassa, the ringed gas giant, ray-traced on a quad: a sphere with drifting bands (Sampler0,
// an equirectangular band map) lit by Solenne, and a ring plane (Sampler1, the ring's colour and
// opacity by radius). The ring shades the planet and the planet shades the ring. With the sun
// behind the planet (the eclipse) the atmosphere lights up as a thin halo and the rings glow.
// Output is premultiplied (blend ONE, ONE_MINUS_SRC_ALPHA). In daylight the planet's night side is
// partly see-through and the day sky's light lies over the whole disc, like the Moon by day.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec4 ColorModulator;
uniform vec3 PlanetPos;      // the planet's centre (camera at the origin)
uniform vec4 PlanetGeom;     // x: radius, y and z: ring inner and outer radius (planet radii), w: band drift
uniform vec3 RingAxis;       // the planet's axis, normal to the rings (unit)
uniform vec3 LightDir;       // towards the light that falls on the planet (unit)
uniform vec4 PlanetLight;    // rgb: that light's colour and strength, a: how backlit (0..1)
uniform vec4 PlanetParams;   // x: night side opacity, y: day sky over the disc, z: sun glare over the disc, w: halo strength
uniform vec3 PlanetAmbient;  // the night side's faint light

in vec3 rayPos;

out vec4 fragColor;

const float PI = 3.14159265;

vec4 ring_at(float r) {
    float t = (r - PlanetGeom.y) / (PlanetGeom.z - PlanetGeom.y);
    if (t <= 0.0 || t >= 1.0) {
        return vec4(0.0);
    }
    return textureLod(Sampler1, vec2(t, 0.5), 0.0);
}

void main() {
    vec3 v = normalize(rayPos);
    vec3 C = PlanetPos;
    float R = PlanetGeom.x;
    vec3 A = RingAxis;
    vec3 L = LightDir;
    float toSun = max(dot(v, L), 0.0);

    // where this ray meets the planet's silhouette and the ring plane (derivatives first, before any branch)
    float b = dot(v, C);
    float closest = sqrt(max(dot(C, C) - b * b, 0.0));
    float aa = max(fwidth(closest), 1e-4) * 1.25;
    float va = dot(v, A);
    float tR = abs(va) > 1e-5 ? dot(C, A) / va : -1.0;
    float rr = length(v * tR - C) / R;
    float edge = max(fwidth(rr), 1e-4);
    bool ringHit = tR > 0.0 && rr > PlanetGeom.y && rr < PlanetGeom.z;
    if (!ringHit && closest > R * 1.6) {
        discard;   // most of the quad: nothing but sky
    }

    // ------------------------------------------------------------ the planet
    float cover = 1.0 - smoothstep(R - aa, R + aa, closest);
    float tS = b - sqrt(max(R * R - closest * closest, 0.0));
    float la = dot(L, A);
    vec3 planet = vec3(0.0);
    float planetA = 0.0;
    float back = PlanetLight.a * pow(toSun, 2.0);
    if (cover > 0.0) {
        vec3 N = normalize(v * tS - C);
        float mu = max(dot(N, -v), 0.0);
        vec3 ref = abs(A.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
        vec3 E0 = normalize(cross(A, ref));
        vec3 E1 = cross(A, E0);
        float lat = asin(clamp(dot(N, A), -1.0, 1.0));
        float lon = atan(dot(N, E1), dot(N, E0));
        float jet = 0.6 * sin(lat * 7.0) + 0.4 * sin(lat * 13.0 + 1.3);
        vec2 bandUv = vec2(lon / (2.0 * PI) + PlanetGeom.w * (1.0 + 0.45 * jet), 0.5 - lat / PI);
        vec3 albedo = textureLod(Sampler0, bandUv, 0.0).rgb;

        float ndl = dot(N, L);
        float day = smoothstep(-0.15, 0.3, ndl) * (0.3 + 0.7 * max(ndl, 0.0));
        float limb = 0.55 + 0.45 * sqrt(mu);
        float shadow = 0.0;
        if (abs(la) > 1e-4) {
            float tr = dot(C - v * tS, A) / la;
            if (tr > 0.0) {
                shadow = ring_at(length(v * tS + L * tr - C) / R).a * 0.8;
            }
        }
        planet = albedo * PlanetLight.rgb * (day * limb * (1.0 - shadow)) + albedo * PlanetAmbient;
        // the thin bright air at the lit limb
        planet += PlanetLight.rgb * vec3(0.6, 0.85, 0.95) * (pow(1.0 - mu, 3.0) * clamp(ndl + 0.25, 0.0, 1.0) * 0.4);
        // backlit: the sun behind the planet sets its air alight at the edge
        planet += vec3(1.0, 0.9, 0.7) * (pow(1.0 - mu, 5.0) * back * PlanetParams.w);
        planetA = cover * mix(PlanetParams.x, 1.0, clamp(day * 1.6, 0.0, 1.0));
    }

    // the halo outside the edge: forward-scattered sunlight in the upper air
    float out1 = max(closest - R, 0.0) / R;
    vec3 halo = vec3(1.0, 0.88, 0.66) * ((exp(-out1 * 22.0) * 0.9 + exp(-out1 * 5.0) * 0.25) * back * PlanetParams.w * (1.0 - cover));

    // ------------------------------------------------------------ the rings
    vec4 ring = vec4(0.0);
    bool ringInFront = false;
    if (ringHit) {
        vec3 Q = v * tR;
        vec4 prof = ring_at(rr);
        prof.a *= smoothstep(PlanetGeom.y, PlanetGeom.y + edge * 1.5, rr) * (1.0 - smoothstep(PlanetGeom.z - edge * 1.5, PlanetGeom.z, rr));
        if (prof.a > 0.0) {
            vec3 toC = C - Q;
            float bb = dot(L, toC);
            float inShadow = (bb > 0.0 && dot(toC, toC) - bb * bb < R * R) ? 1.0 : 0.0;
            bool litFace = (la > 0.0) == (-va > 0.0);
            vec3 front = prof.rgb * (0.3 + 0.7 * abs(la));
            vec3 through = prof.rgb * ((1.0 - prof.a) * (0.25 + 1.6 * pow(toSun, 2.0)));
            vec3 rc = (litFace ? front : through + front * 0.12) * PlanetLight.rgb * (1.0 - inShadow * 0.92);
            rc += vec3(1.0, 0.9, 0.72) * (PlanetLight.a * pow(toSun, 6.0) * 0.7);
            rc += prof.rgb * PlanetAmbient * 2.0;
            ring = vec4(rc * prof.a, prof.a);
            ringInFront = cover <= 0.0 || tR < tS;
        }
    }

    // ------------------------------------------------------------ together, with the air in front
    vec4 body = vec4(planet * planetA, planetA);
    vec4 outc = ringInFront ? ring + body * (1.0 - ring.a) : body + ring * (1.0 - body.a);
    float over = outc.a;
    outc.rgb += sky_gradient(v) * (PlanetParams.y * over) + sun_glow(v) * (PlanetParams.z * over) + halo;
    fragColor = outc * ColorModulator;
}
