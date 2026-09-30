#version 150

// Vanilla 1.21.1's fog.glsl, unchanged for fog shapes 0 (sphere) and 1 (cylinder), plus shapes 2 and up
// for Cosmic Breach's Aetheria: a cylinder whose height counts (shape - 1) * 0.05 of its radius, so a player
// on a floating island can see the layers far below. Only Aetheria's fog ever sets those shapes
// (client/sky/AetheriaFog), so everywhere else this file behaves exactly like vanilla's.

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (vertexDistance <= fogStart) {
        return inColor;
    }

    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

float linear_fog_fade(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 1.0;
    } else if (vertexDistance >= fogEnd) {
        return 0.0;
    }

    return smoothstep(fogEnd, fogStart, vertexDistance);
}

float fog_distance(vec3 pos, int shape) {
    if (shape == 0) {
        return length(pos);
    } else if (shape == 1) {
        float distXZ = length(pos.xz);
        float distY = abs(pos.y);
        return max(distXZ, distY);
    } else {
        return max(length(pos.xz), abs(pos.y) * float(shape - 1) * 0.05);
    }
}
