#version 150

// A soft round point (drawn additively): brightest in the middle, gone at the quad's edge.

uniform vec4 ColorModulator;

in vec2 corner;
in vec3 starColor;

out vec4 fragColor;

void main() {
    float r2 = dot(corner, corner);
    float a = max(exp(-r2 * 4.5) - 0.011, 0.0);
    if (a <= 0.0) {
        discard;
    }
    fragColor = vec4(starColor * a, 1.0) * ColorModulator;
}
