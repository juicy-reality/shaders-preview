import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.*;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengles.GLES20.GL_ARRAY_BUFFER;
import static org.lwjgl.opengles.GLES20.GL_STATIC_DRAW;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL31.glDrawArraysInstanced;


public class ShadersPreview {

    // The window handle
    private long window;

    private int drawProgramID;

    private int fullScreenVao;

    private int imageTextureID;

    private void drawTexture(int textureID, int textureID2)
    {
        glUseProgram(drawProgramID);

        // glActiveTexture(GL_TEXTURE0);
        // glBindTexture(GL_TEXTURE_2D, textureID);

        //glActiveTexture(GL_TEXTURE1);
        //glBindTexture(GL_TEXTURE_2D, textureID2);
        glBindVertexArray(fullScreenVao);
        glDrawArrays(GL_TRIANGLES, 0, 6);
//		glBindTexture(GL_TEXTURE_2D, 0);
//		glBindVertexArray(0);

//        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, 1);
    }

    // draws each frame
    private void drawFrame() {
        drawTexture(imageTextureID, imageTextureID);
    }

    private int createQuadFullScreenVao() {
		int vao = glGenVertexArrays();
		int vbo = glGenBuffers();
		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, vbo);
		ByteBuffer bb = BufferUtils.createByteBuffer(2 * 6);
		bb.put((byte) -1).put((byte) -1);
		bb.put((byte) 1).put((byte) -1);
		bb.put((byte) 1).put((byte) 1);
		bb.put((byte) 1).put((byte) 1);
		bb.put((byte) -1).put((byte) 1);
		bb.put((byte) -1).put((byte) -1);
		bb.flip();
		glBufferData(GL_ARRAY_BUFFER, bb, GL_STATIC_DRAW);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 2, GL_BYTE, false, 0, 0L);
		glBindVertexArray(0);
		return vao;
	}

    private void initVars() {
        // draw texture directly
        drawProgramID = glCreateProgram();
        int drawVertID = createShader("draw.vert", GL_VERTEX_SHADER);
		int drawFragID = createShader("draw.frag", GL_FRAGMENT_SHADER);

        glAttachShader(drawProgramID, drawVertID);
        glAttachShader(drawProgramID, drawFragID);
        glLinkProgram(drawProgramID);
        checkProgramStatus(drawProgramID);

        fullScreenVao = createQuadFullScreenVao();
    }

    public static CharSequence getShaderCode(String name) {
        InputStream is = ShadersPreview.class.getResourceAsStream(name);
        final DataInputStream dataStream = new DataInputStream(is);
        byte[] shaderCode;
        try {
            shaderCode = new byte[dataStream.available()];
            dataStream.readFully(shaderCode);
            is.close();
        } catch (Throwable e) {
            return "";
        }

        return new String(shaderCode);
    }

    private int createShader(String name, int type) {
        int shaderID = glCreateShader(type);
        glShaderSource(shaderID, getShaderCode(name));
		glCompileShader(shaderID);
		checkShaderStatus(name, shaderID);
		return shaderID;
    }

    public void checkProgramStatus(int programID) {
        int status = glGetProgrami(programID, GL_LINK_STATUS);
        if (status != GL_TRUE) {
            System.err.println(glGetProgramInfoLog(programID));
        }
    }

    private void checkShaderStatus(String name, int shaderID) {
        int status = glGetShaderi(shaderID, GL_COMPILE_STATUS);
        if (status != GL_TRUE) {
            System.err.println(name);
            System.err.println(glGetShaderInfoLog(shaderID));
        }
    }




    /**
     * have not touched rest of file
     */

    public void run() {
        System.out.println("LWJGL version " + Version.getVersion());

        init();
        loop();

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if ( !glfwInit() )
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);

        // Create the window
        window = glfwCreateWindow(568, 320, "Shaders Preview", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE )
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
        });

        // Get the thread stack and push a new frame
        try ( MemoryStack stack = stackPush() ) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(
                    window,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);
        // Make the window visible
        glfwShowWindow(window);

        GL.createCapabilities();

        initVars();
    }

    private void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GL.createCapabilities();

        // Set the clear color
        glClearColor(1.0f, 0.0f, 0.0f, 0.0f);

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while ( !glfwWindowShouldClose(window) ) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

            drawFrame();

            glfwSwapBuffers(window); // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();
        }
    }

    public static void main(String[] args) {
        new ShadersPreview().run();
    }

}