#version 150 core

in vec4 inPosition;
in highp vec2 aTexCoord;
out highp vec2 vTexCoord;

void main()
{
   vTexCoord = aTexCoord;
   gl_Position = inPosition;
}
