#version 150

// The sky dome: a cube around the camera (rotation only in ModelViewMat), coloured per pixel.

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewDir;

void main() {
    viewDir = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
