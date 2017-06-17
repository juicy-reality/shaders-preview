#version 150 core
precision mediump float;

uniform sampler2D uResult0Tex;
uniform sampler2D uImage0Tex;

uniform sampler2D uResult1Tex;
uniform sampler2D uImage1Tex;

uniform vec4 uMixer;

in vec2 texcoord;
out vec4 color;

void main(void)
{
    vec4 disp0 = texture(uResult0Tex, texcoord);
    vec4 color0 = texture(uImage0Tex, disp0.rg);

    vec4 disp1 = texture(uResult1Tex, texcoord);
    vec4 color1 = texture(uImage1Tex, disp1.rg);

    color = color0 * uMixer.x + color1 *  uMixer.y;
}
