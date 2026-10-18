#version 150

// The depth of an Island Painting's picture, into Shadered+'s painting depth layer (see IrisIslandTarget).

out vec4 fragColor;

void main() {
    fragColor = vec4(gl_FragCoord.z, 0.0, 0.0, 1.0);
}
