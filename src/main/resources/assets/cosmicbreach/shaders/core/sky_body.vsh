#version 150

// Thalassa: one quad around the planet and its rings; the fragment shader ray-traces both.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 rayPos;

void main() {
    rayPos = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
