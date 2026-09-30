#version 150

// Aurora ribbons: static curtains in the sky (UV0.x along a ribbon, UV0.y from its hem to its top),
// swaying slowly. The vertex alpha fades each ribbon's ends.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 AuroraParams;   // x: time in seconds, y: brightness

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    float t = AuroraParams.x;
    float sway = sin(t * 0.21 + UV0.x * 1.9) * 0.6 + sin(t * 0.13 - UV0.x * 3.1) * 0.4;
    vec3 side = normalize(vec3(-Position.z, 0.0, Position.x));
    vec3 p = Position + side * (sway * (1.2 + 2.2 * UV0.y));
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
