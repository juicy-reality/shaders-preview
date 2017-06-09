#version 150 core
precision mediump float;

uniform sampler2D uSourceTex;
uniform sampler2D uSourceTex2;
// uniform mat4 u_MVPMatrix;

in vec2 vTexCoord;
out vec4 color;

void main(void)                    // The entry point for our fragment shader.
{
//    vec4 disp = texture(uSourceTex, vTexCoord);

//    color = texture(uSourceTex2, disp.rg);
    color = vec4(1.0, 1.0, 1.0, 1.0);
}