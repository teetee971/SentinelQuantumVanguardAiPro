package com.sentinel.quantum.ui.screens

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import android.view.View
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Real GPU 3D ray-marched D.1 geometry. Purely decorative, no permissions or data access.
 * Use as a background layer, never as a touch target.
 */
internal class D1VolumetricView(context: Context, animate: Boolean) : GLSurfaceView(context) {
    private val scene = D1SceneRenderer(animate)

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setZOrderOnTop(false)
        preserveEGLContextOnPause = true
        setRenderer(scene)
        renderMode = if (animate) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if (!animate) requestRender()
    }

    fun suspendRendering() = onPause()
    fun resumeRendering() = onResume()
}

private class D1SceneRenderer(private val animate: Boolean) : GLSurfaceView.Renderer {
    private var program = 0
    private var position = -1
    private var time = -1
    private var resolution = -1
    private var width = 1
    private var height = 1
    private val start = SystemClock.elapsedRealtime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.027f, 0.058f, 0.11f, 1f)
        val vert = shader(GLES20.GL_VERTEX_SHADER, VERTEX)
        val frag = shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
        if (vert == 0 || frag == 0) return
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vert)
        GLES20.glAttachShader(program, frag)
        GLES20.glLinkProgram(program)
        val success = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, success, 0)
        if (success[0] == 0) {
            Log.w("SentinelD1", "3D shader linking failed")
            GLES20.glDeleteProgram(program)
            program = 0
        }
        GLES20.glDeleteShader(vert)
        GLES20.glDeleteShader(frag)
        if (program != 0) {
            position = GLES20.glGetAttribLocation(program, "aPosition")
            time = GLES20.glGetUniformLocation(program, "uTime")
            resolution = GLES20.glGetUniformLocation(program, "uResolution")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0 || position < 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(time, if (animate) (SystemClock.elapsedRealtime() - start) / 1000f else 0f)
        GLES20.glUniform2f(resolution, width.toFloat(), height.toFloat())
        val coords = java.nio.ByteBuffer.allocateDirect(8 * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        coords.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, coords)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        if (id == 0) return 0
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != 0) return id
        Log.w("SentinelD1", "3D shader compilation failed: ${GLES20.glGetShaderInfoLog(id).take(160)}")
        GLES20.glDeleteShader(id)
        return 0
    }

    companion object {
        private const val VERTEX = """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main() {
                vUv = aPosition;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """
        private const val FRAGMENT = """
            precision mediump float;
            varying vec2 vUv;
            uniform float uTime;
            uniform vec2 uResolution;

            float sdTorus(vec3 p, vec2 t) {
                vec2 q = vec2(length(p.xz) - t.x, p.y);
                return length(q) - t.y;
            }
            mat2 rot(float a) {
                float c = cos(a), s = sin(a);
                return mat2(c, -s, s, c);
            }
            float scene(vec3 p) {
                p.xy *= rot(0.42 + sin(uTime * 0.20) * 0.19);
                p.yz *= rot(0.57 + uTime * 0.12);
                float ring1 = sdTorus(p, vec2(0.90, 0.045));
                float ring2 = sdTorus(p.yzx, vec2(0.70, 0.032));
                float ring3 = sdTorus(p.zxy, vec2(0.54, 0.028));
                return min(ring1, min(ring2, ring3));
            }
            void main() {
                vec2 uv = vUv;
                uv.x *= uResolution.x / max(uResolution.y, 1.0);
                vec3 origin = vec3(0.0, 0.0, 3.1);
                vec3 dir = normalize(vec3(uv, -2.0));
                float distanceTraveled = 0.0;
                float glow = 0.0;
                float hit = 0.0;
                vec3 pos = origin;
                for (int i = 0; i < 56; i++) {
                    pos = origin + dir * distanceTraveled;
                    float d = scene(pos);
                    glow += 0.008 / (0.06 + abs(d));
                    if (d < 0.004) { hit = 1.0; break; }
                    if (distanceTraveled > 6.0) break;
                    distanceTraveled += max(d * 0.72, 0.022);
                }
                vec3 base = mix(vec3(0.018, 0.035, 0.075), vec3(0.035, 0.10, 0.19), smoothstep(-1.2, 1.2, vUv.y));
                float shade = 0.55 + 0.45 * smoothstep(-1.0, 1.0, pos.z);
                vec3 cyan = vec3(0.09, 0.78, 1.0);
                vec3 color = base + cyan * min(glow * 0.055, 0.40);
                color += cyan * hit * shade * 0.75;
                gl_FragColor = vec4(color, 1.0);
            }
        """
    }
}
