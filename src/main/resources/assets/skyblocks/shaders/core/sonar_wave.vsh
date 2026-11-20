#version 150

// Shadered+'s sonar ring (client/SonarPulse): one quad over the whole screen.
in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
