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
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindFragDataLocation;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.opengl.GL20.glCreateProgram;


public class ShadersPreview {

    // The window handle
    private long window;

    private int drawProgramID;

    private int fullScreenVao;

    private int imageTextureID;

    private int depthTextureID;

    private void drawTexture(int textureID, int textureID2)
    {
        glUseProgram(drawProgramID);

        glBindVertexArray(fullScreenVao);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureID);

        glActiveTexture(GL_TEXTURE0 + 1);
        glBindTexture(GL_TEXTURE_2D, textureID2);

        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    // draws each frame
    private void drawFrame() {
        drawTexture(imageTextureID, depthTextureID);
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
        // config opengl
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // draw texture directly
        drawProgramID = glCreateProgram();
        int drawVertID = createShader("draw.vert", GL_VERTEX_SHADER);
		int drawFragID = createShader("draw.frag", GL_FRAGMENT_SHADER);
        glAttachShader(drawProgramID, drawVertID);
        glAttachShader(drawProgramID, drawFragID);
        glLinkProgram(drawProgramID);
        checkProgramStatus(drawProgramID);
        // we tell how this called in shaders
		glBindAttribLocation(drawProgramID, 0, "vertex");
		glBindFragDataLocation(drawProgramID, 0, "color");

        fullScreenVao = createQuadFullScreenVao();

        imageTextureID = loadTexture("src/main/resources/color_alpha.png");
        depthTextureID = loadTexture("src/main/resources/depth_rgb.png");

        glUseProgram(drawProgramID);
        // Set sampler2d in GLSL fragment shader to texture unit 0
        glUniform1i(glGetUniformLocation(drawProgramID, "uSourceTex"), 0);
        glUniform1i(glGetUniformLocation(drawProgramID, "uSourceTex2"), 1);
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

    public static int loadTexture(String path) {
        ByteBuffer image;
        int width, height;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            /* Prepare image buffers */
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            /* Load image */
            stbi_set_flip_vertically_on_load(true);
            image = stbi_load(path, w, h, comp, 4);
            if (image == null) {
                throw new RuntimeException("Failed to load a texture file!("+path+")"
                        + System.lineSeparator() + stbi_failure_reason());
            }

            /* Get width and height of image */
            width = w.get();
            height = h.get();
        }

        // create texture
        int textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, image);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        return textureID;
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
        glClearColor(1.0f, 1.0f, 1.0f, 1.0f);

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