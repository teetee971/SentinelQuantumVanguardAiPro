#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <setjmp.h>
#include <jni.h>
#include "jpeglib.h"

/* No global decode state: concurrent calls own their decoder, error handler and JNI references. */
typedef struct {
    struct jpeg_decompress_struct jpeg;
    struct jpeg_error_mgr error;
    struct jpeg_progress_mgr progress;
    jmp_buf jump;
    JNIEnv *env;
    jclass bridge;
    jmethodID interrupted;
    int result;
} Validation;

static void fail(j_common_ptr info) {
    Validation *v = (Validation *)info->client_data;
    longjmp(v->jump, 1);
}
static void diagnostic(j_common_ptr info, int level) {
    /* Warnings include premature end of entropy, which ImageDecoder hides. */
    if (level < 0) fail(info);
}
static void progress(j_common_ptr info) {
    Validation *v = (Validation *)info->client_data;
    if (v->jpeg.input_scan_number > 128) { v->result = 2; fail(info); }
    if ((*v->env)->CallStaticBooleanMethod(v->env, v->bridge, v->interrupted)) {
        v->result = 3; fail(info);
    }
    if ((*v->env)->ExceptionCheck(v->env)) fail(info);
}

JNIEXPORT jint JNICALL Java_com_sentinel_quantum_mmsjpeg_StrictJpegValidator_validateNative(
        JNIEnv *env, jclass bridge, jbyteArray input) {
    jsize length = (*env)->GetArrayLength(env, input);
    if (length < 4 || length > 5 * 1024 * 1024) return 2;
    Validation *v = calloc(1, sizeof(*v));
    if (!v) return 2;
    v->result = 1;
    v->env = env; v->bridge = bridge;
    v->interrupted = (*env)->GetStaticMethodID(env, bridge, "interrupted", "()Z");
    if (!v->interrupted) { free(v); return 4; }
    jbyte *bytes = (*env)->GetByteArrayElements(env, input, NULL);
    if (!bytes) { free(v); return 2; }
    v->jpeg.err = jpeg_std_error(&v->error);
    v->error.error_exit = fail;
    v->error.emit_message = diagnostic;
    v->jpeg.client_data = v;
    if (setjmp(v->jump) == 0) {
        jpeg_create_decompress(&v->jpeg);
        v->jpeg.mem->max_memory_to_use = 64L * 1024 * 1024;
        v->progress.progress_monitor = progress;
        v->jpeg.progress = &v->progress;
        jpeg_mem_src(&v->jpeg, (unsigned char *)bytes, (unsigned long)length);
        if (jpeg_read_header(&v->jpeg, TRUE) != JPEG_HEADER_OK) fail((j_common_ptr)&v->jpeg);
        if (!v->jpeg.image_width || !v->jpeg.image_height ||
            (unsigned long long)v->jpeg.image_width * v->jpeg.image_height > 20000000ULL ||
            v->jpeg.data_precision != 8) {
            v->result = 2; fail((j_common_ptr)&v->jpeg);
        }
        /* Progressive coefficient buffers cannot fall back to disk (jmemnobs).
           Reject resource-heavy inputs before requesting their virtual arrays. */
        unsigned long long coefficient_bytes = 0;
        for (int i = 0; i < v->jpeg.num_components; ++i) {
            jpeg_component_info *component = &v->jpeg.comp_info[i];
            /* Round to MCU boundaries as the virtual coefficient arrays do. */
            unsigned long long columns = (component->width_in_blocks + component->h_samp_factor - 1) /
                component->h_samp_factor * component->h_samp_factor;
            unsigned long long rows = (component->height_in_blocks + component->v_samp_factor - 1) /
                component->v_samp_factor * component->v_samp_factor;
            coefficient_bytes += columns * rows * sizeof(JBLOCK);
        }
        if (v->jpeg.progressive_mode && coefficient_bytes > 60ULL * 1024 * 1024) {
            v->result = 2; fail((j_common_ptr)&v->jpeg);
        }
        v->jpeg.scale_num = 1; v->jpeg.scale_denom = 8;
        if (v->jpeg.jpeg_color_space == JCS_CMYK || v->jpeg.jpeg_color_space == JCS_YCCK)
            v->jpeg.out_color_space = JCS_CMYK;
        if (!jpeg_start_decompress(&v->jpeg)) fail((j_common_ptr)&v->jpeg);
        JSAMPARRAY row = (*v->jpeg.mem->alloc_sarray)((j_common_ptr)&v->jpeg, JPOOL_IMAGE,
            v->jpeg.output_width * v->jpeg.output_components, 1);
        while (v->jpeg.output_scanline < v->jpeg.output_height) {
            progress((j_common_ptr)&v->jpeg);
            if (jpeg_read_scanlines(&v->jpeg, row, 1) != 1) fail((j_common_ptr)&v->jpeg);
        }
        if (!jpeg_finish_decompress(&v->jpeg)) fail((j_common_ptr)&v->jpeg);
        v->result = 0;
    }
    int result = v->result;
    jpeg_destroy_decompress(&v->jpeg);
    (*env)->ReleaseByteArrayElements(env, input, bytes, JNI_ABORT);
    free(v);
    return result;
}
