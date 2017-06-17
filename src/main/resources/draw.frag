#version 150 core
precision mediump float;

#define WIDTH 640.0
#define HEIGHT 480.0

uniform sampler2D uSourceTex;
uniform sampler2D uSourceTex2;
// uniform mat4 u_MVPMatrix;
in vec2 texcoord;
out vec4 color;

void main(void)                    // The entry point for our fragment shader.
{
    float dy = 1.0 / HEIGHT;
    float dx = 1.0 / WIDTH;

    vec4 disp = texture(uSourceTex, texcoord);
    //color=disp;
    // return ;
    color = texture(uSourceTex2, disp.rg);

     // color = texture(uSourceTex, disp.rg);
//      color = vec4(texcoord.r, texcoord.g, 1.0, 1.0);
}
