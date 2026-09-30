#version 150

// Light in the sky, drawn additively: Solenne, Vesper and its beam, the clusters' cores.
// UV0 spans the quad (0..1); the fragment shader shapes the light procedurally.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
