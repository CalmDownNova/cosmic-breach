#version 150

// The Breach: one quad at the bottom of the hole. Its vertices arrive relative to the camera (block
// entities are drawn that way), so the position is also the direction the camera looks through it.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewDir;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    viewDir = Position;
    texCoord0 = UV0;
}
