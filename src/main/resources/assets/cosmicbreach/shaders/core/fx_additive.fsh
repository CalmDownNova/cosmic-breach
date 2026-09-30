#version 150

#moj_import <fog.glsl>

// Light added onto the scene (blend SRC_ALPHA, ONE): the texture's alpha is the shape, the vertex
// colour the tint and the fade. No alpha cut-off, so soft edges and fades stay smooth; far away
// the light fades out with the fog instead of turning fog-coloured.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.a *= linear_fog_fade(vertexDistance, FogStart, FogEnd);
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
