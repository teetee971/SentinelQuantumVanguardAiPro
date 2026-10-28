#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/fs.h>
#include <sys/syscall.h>
#include <unistd.h>

JNIEXPORT jint JNICALL
Java_com_sentinel_quantum_security_RecordingVaultNative_renameNoReplace(
    JNIEnv *env,
    jobject receiver,
    jstring source,
    jstring destination
) {
    (void)receiver;

    if (source == NULL || destination == NULL) {
        return EINVAL;
    }

    const char *src = (*env)->GetStringUTFChars(env, source, NULL);
    if (src == NULL) {
        return ENOMEM;
    }

    const char *dst = (*env)->GetStringUTFChars(env, destination, NULL);
    if (dst == NULL) {
        (*env)->ReleaseStringUTFChars(env, source, src);
        return ENOMEM;
    }

    errno = 0;

    long result = syscall(
        __NR_renameat2,
        AT_FDCWD,
        src,
        AT_FDCWD,
        dst,
        RENAME_NOREPLACE
    );

    int status = result == 0 ? 0 : errno;

    (*env)->ReleaseStringUTFChars(env, destination, dst);
    (*env)->ReleaseStringUTFChars(env, source, src);

    return status;
}
