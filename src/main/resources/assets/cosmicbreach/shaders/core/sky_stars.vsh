#version 150

// Stars and star clusters: static quads on a sphere in the celestial frame, turned into the world
// by SkyRot (once a day around the pole). Each star's colour carries its brightness; its alpha is
// the star's own phase, so every star twinkles on its own clock.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 SkyRot;
uniform vec4 StarParams;   // x: brightness, y: twinkle depth, z: time in seconds, w: brightness below the horizon

out vec2 corner;
out vec3 starColor;

void main() {
    vec3 world = (SkyRot * vec4(Position, 1.0)).xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(world, 1.0);

    float phase = Color.a * 6.2831853;
    float speed = 1.1 + 2.4 * fract(Color.a * 7.31);
    float wave = 0.6 * sin(StarParams.z * speed + phase) + 0.4 * sin(StarParams.z * speed * 2.63 + phase * 3.7);
    float twinkle = max(1.0 + StarParams.y * wave, 0.0);
    // thinner near the horizon (more air), and dimmer below it (looking down between the layers)
    float up = normalize(world).y;
    float air = mix(StarParams.w, 1.0, smoothstep(-0.12, 0.02, up)) * mix(0.45, 1.0, smoothstep(0.0, 0.25, abs(up)));
    starColor = Color.rgb * (twinkle * air * StarParams.x);
    corner = UV0 * 2.0 - 1.0;
}
