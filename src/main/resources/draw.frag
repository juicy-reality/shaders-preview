#version 150 core
precision mediump float;

uniform sampler2D uResultTex;
uniform sampler2D uImageTex;

in vec2 texcoord;
out vec4 color;

void main(void)
{
    vec4 disp = texture(uResultTex, texcoord);

    color = texture(uImageTex, disp.rg);
}
