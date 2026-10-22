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
    private val coords = java.nio.ByteBuffer.allocateDirect(8 * 4)
        .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

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
        coords.position(0)
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
            // A real signed-distance scene: central solid sphere and orbital 3D tori.
            // The sphere gives the hologram body and specular depth beyond wire rings.
            float scene(vec3 p) {
                vec3 q = p;
                q.xy *= rot(0.40 + sin(uTime * 0.20) * 0.17);
                q.yz *= rot(0.54 + uTime * 0.11);
                float ring1 = sdTorus(q, vec2(0.95, 0.038));
                float ring2 = sdTorus(q.yzx, vec2(0.83, 0.031));
                float ring3 = sdTorus(q.zxy, vec2(0.74, 0.024));
                float core = length(p) - 0.57;
                return min(core, min(ring1, min(ring2, ring3)));
            }
            float star(vec2 xy) {
                vec2 cell = floor(xy);
                vec2 local = fract(xy) - 0.5;
                float id = fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
                vec2 jitter = vec2(fract(id * 19.13), fract(id * 41.37)) - 0.5;
                return step(0.975, id) * (1.0 - smoothstep(0.005, 0.060, length(local - jitter * 0.6)));
            }
            // Hexagonal microstructure remains local to the sphere surface; no texture assets.
            float hexGrid(vec2 p) {
                vec2 a = abs(mod(p, vec2(1.0, 1.732)) - vec2(0.5, 0.866));
                vec2 b = abs(mod(p - vec2(0.5, 0.866), vec2(1.0, 1.732)) - vec2(0.5, 0.866));
                vec2 d = min(a, b);
                return 1.0 - smoothstep(0.38, 0.46, max(d.x * 0.866 + d.y * 0.5, d.y));
            }
            void main() {
                vec2 uv = vUv;
                uv.x *= uResolution.x / max(uResolution.y, 1.0);
                vec3 origin = vec3(0.0, 0.0, 3.1);
                vec3 dir = normalize(vec3(uv, -2.0));
                float travel = 0.0;
                float glow = 0.0;
                float hit = 0.0;
                vec3 pos = origin;
                for (int i = 0; i < 56; i++) {
                    pos = origin + dir * travel;
                    float d = scene(pos);
                    glow += 0.006 / (0.055 + abs(d));
                    if (d < 0.005) { hit = 1.0; break; }
                    if (travel > 6.0) break;
                    travel += max(d * 0.76, 0.018);
                }
                vec3 color = mix(vec3(0.008, 0.020, 0.045), vec3(0.022, 0.075, 0.13),
                                 smoothstep(-1.0, 1.0, vUv.y));
                vec3 cyan = vec3(0.06, 0.69, 1.0);
                float stars = star(uv * 14.0 + vec2(uTime * 0.025, 0.0));
                stars += star(uv * 25.0 - vec2(uTime * 0.014, 0.0)) * 0.45;
                color += cyan * stars * 0.26;
                color += cyan * min(glow * 0.046, 0.32);
                if (hit > 0.5) {
                    vec3 normal = normalize(pos);
                    float sphereSurface = abs(length(pos) - 0.57);
                    if (sphereSurface > 0.035) {
                        normal = normalize(vec3(
                            scene(pos + vec3(0.012, 0.0, 0.0)) - scene(pos - vec3(0.012, 0.0, 0.0)),
                            scene(pos + vec3(0.0, 0.012, 0.0)) - scene(pos - vec3(0.0, 0.012, 0.0)),
                            scene(pos + vec3(0.0, 0.0, 0.012)) - scene(pos - vec3(0.0, 0.0, 0.012))
                        ));
                    }
                    vec3 light = normalize(vec3(-0.5, 0.8, 1.2));
                    float diffuse = max(dot(normal, light), 0.0);
                    float fresnel = pow(1.0 - max(dot(normal, -dir), 0.0), 3.0);
                    float spec = pow(max(dot(reflect(-light, normal), -dir), 0.0), 18.0);
                    float isCore = 1.0 - smoothstep(0.015, 0.05, sphereSurface);
                    vec3 solid = mix(cyan * (0.60 + 0.55 * diffuse), vec3(0.012, 0.085, 0.20) +
                                     cyan * (0.15 * diffuse + 0.80 * fresnel), isCore);
                    // The central core has a subdued crystalline honeycomb texture,
                    // while the orbital tori stay bright and visibly separate.
                    float cells = hexGrid(normal.xy * 10.0 + vec2(normal.z * 1.2, 0.0));
                    solid += cyan * isCore * (0.045 + 0.14 * cells) * (0.25 + diffuse);
                    color += solid + vec3(0.48, 0.86, 1.0) * spec * 0.65;
                }
                gl_FragColor = vec4(min(color, vec3(1.0)), 1.0);
            }
        """
    }
}
