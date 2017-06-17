import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import stolen.Matrix;


import java.io.DataInputStream;

import java.io.InputStream;
import java.nio.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;


import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.ARBComputeShader.glDispatchCompute;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindFragDataLocation;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL42.glBindImageTexture;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_load;
import static org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.opengl.GL20.glCreateProgram;


public class ShadersPreview {

    public static final float AMENDMENT_STEP = 0.05f;
    public static final int WINDOW_WIDTH = 568;
    public static final int WINDOW_HEIGHT = 320;
    public static final float ROTATION_STEP_ANGLE = 1.0f;
    // The window handle
    private long window;

    private int drawProgramID;

    private int fullScreenVao;

    private int imageTextureID;

    private int depthTextureID;

    private float[] mViewMatrix = new float[16];
    private float[] mModelMatrix= new float[16];
    private float[] mMVPMatrix=new float[16];
    private float[] mProjectionMatrix=new float[16];
    private int     workgroupSize;
    private int mMVPMatrixHandle;

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private int                    computeProgramID, clearProgramID;
    private int                    resultTextureId;

    private static final String   S_COMP_SHADER_HEADER = "#version 310 es\n#define LOCAL_SIZE %d\n";
    private float distanceAmendment = 0f;
    private float horizontalShift;
    private float horizontalAngleRotation = 0f;


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
        runClearShader(resultTextureId);
        runComputeFilter(depthTextureID, resultTextureId);
        drawTexture(resultTextureId, imageTextureID);
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

        clearProgramID = glCreateProgram();
        int clearCompID = createShader("clear.comp", GL_COMPUTE_SHADER);
        glAttachShader(clearProgramID, clearCompID);
        glLinkProgram(clearProgramID);
        checkProgramStatus(clearProgramID);

        computeProgramID =  glCreateProgram();
        int computeCompID = createShader("draw.comp", GL_COMPUTE_SHADER);
        glAttachShader(computeProgramID, computeCompID);
        glLinkProgram(computeProgramID);
        checkProgramStatus(computeProgramID);

        // we tell how this called in shaders
		glBindAttribLocation(drawProgramID, 0, "vertex");
		glBindFragDataLocation(drawProgramID, 0, "color");

        fullScreenVao = createQuadFullScreenVao();

        imageTextureID = loadTexture("src/main/resources/color2.png");
        depthTextureID = loadTexture("src/main/resources/depth2.png");
        resultTextureId= createFramebufferTexture();

        glUseProgram(drawProgramID);
        // Set sampler2d in GLSL fragment shader to texture unit 0
        glUniform1i(glGetUniformLocation(drawProgramID, "uSourceTex"), 0);
        glUniform1i(glGetUniformLocation(drawProgramID, "uSourceTex2"), 1);

        glUseProgram(computeProgramID);
        // Set sampler2d in GLSL fragment shader to texture unit 0
        glUniform1i(glGetUniformLocation(computeProgramID, "inputImage"), 0);
        glUniform1i(glGetUniformLocation(computeProgramID, "resultImage"), 1);

        //TODO no idea what is this
        workgroupSize = 16;
        System.out.println("Work group size = "+workgroupSize);
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

    private int createFramebufferTexture() {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		ByteBuffer black = null;
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, WIDTH, HEIGHT, 0, GL_RGBA, GL_INT, black);
		glBindTexture(GL_TEXTURE_2D, 0);
		return tex;
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
        window = glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Shaders Preview", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE ) {
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop
            }else if (key == GLFW_KEY_LEFT||key == GLFW_KEY_RIGHT){
                System.out.println("Direction button pressed:"+key);
                horizontalAngleRotation = (key == GLFW_KEY_LEFT )? horizontalAngleRotation - ROTATION_STEP_ANGLE :horizontalAngleRotation+ROTATION_STEP_ANGLE;            }
        });
        glfwSetScrollCallback(window, (window, xoffset, yoffset)->{
            if(yoffset>=0){
                distanceAmendment += AMENDMENT_STEP;
            }else{
                distanceAmendment -= AMENDMENT_STEP;
            }
        });

        glfwSetCursorPosCallback(window, (window, xpos, ypos)->{
            horizontalShift = (float)xpos/(WINDOW_HEIGHT/2)-1;
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
        initInitialView();

    }

    private void initInitialView() {
        // init camera view
        // Position the eye behind the origin.
        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = 1.5f;

        // We are looking toward the distance
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -3.0f;

        // Set our up vector. This is where our head would be pointing were we holding the camera.
        final float upX = 0.0f;
        final float upY = 1.0f;
        final float upZ = 0.0f;

        // Set the view matrix. This matrix can be said to represent the camera position.
        // NOTE: In OpenGL 1, a ModelView matrix is used, which is a combination of a model and
        // view matrix. In OpenGL 2, we can keep track of these matrices separately if we choose.
        Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);
        mMVPMatrixHandle = glGetUniformLocation(computeProgramID, "u_MVPMatrix");
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
            recalculate();

            drawFrame();

            glfwSwapBuffers(window); // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();
        }
    }

    private void recalculate() {

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

//b
        // Do a complete rotation every 10 seconds.
        long time = System.currentTimeMillis() % 5000L - 2500L;
        float angleInDegrees = (-90.0f / 10000.0f) * ((int) time);
        System.out.println("recalculatiing "+ angleInDegrees);
        mModelMatrix = Matrix.setIdentityM(mModelMatrix, 0);
        mModelMatrix = Matrix.rotateM(mModelMatrix, 0, 5, 1.0f, 0.0f, 0.0f);
//        System.out.println("Current mModelMatrix" + Arrays.toString(mModelMatrix));

//        System.out.println("Current mModelMatrix" + Arrays.toString(mModelMatrix));
        mModelMatrix = Matrix.rotateM(mModelMatrix, 0, horizontalAngleRotation, 0.0f, 1.0f, 0.0f);
        mModelMatrix = Matrix.translateM(mModelMatrix, 0, 0.0f+horizontalShift, 0.0f, 1.0f);


        System.out.println("Current mModelMatrix" + Arrays.toString(mModelMatrix));


        // This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        mMVPMatrix = Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
        System.out.println("Applying horizontal shift: "+horizontalShift);
        mProjectionMatrix = Matrix.frustumM(mProjectionMatrix, 0, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f+distanceAmendment, 40.0f);
        System.out.println("Current mProjectionMatrix" + Arrays.toString(mProjectionMatrix));

        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        mMVPMatrix = Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
        System.out.println("Current mMVPMatrix" + Arrays.toString(mMVPMatrix));
//        try {
//            TimeUnit.SECONDS.sleep(1);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

    }

    private void runClearShader(int resultTexId)
    {
        glUseProgram(clearProgramID);

        glBindImageTexture(1, resultTexId, 0, false, 0, GL_READ_WRITE, GL_RGBA8);

        glDispatchCompute(WIDTH / workgroupSize, HEIGHT / workgroupSize, 1);

        glMemoryBarrier(GL_COMPUTE_SHADER_BIT);
    }

    private void runComputeFilter(int sourceTexId, int resultTexId)
    {
        glUseProgram(computeProgramID);

        glBindImageTexture(0, sourceTexId, 0, false, 0, GL_READ_ONLY, GL_RGBA8);
        glBindImageTexture(1, resultTexId, 0, false, 0, GL_READ_WRITE, GL_RGBA8);

        int mMVPMatrixHandle = glGetUniformLocation(computeProgramID, "u_MVPMatrix");
        glUniformMatrix4fv(mMVPMatrixHandle,false, mMVPMatrix);
        glDispatchCompute(WIDTH / workgroupSize,  HEIGHT / workgroupSize, 1);

        // GL_COMPUTE_SHADER_BIT is the same as GL_SHADER_IMAGE_ ACCESS_BARRIER_BIT
        glMemoryBarrier(GL_COMPUTE_SHADER_BIT);
    }


    public static void main(String[] args) {
        new ShadersPreview().run();
    }

}