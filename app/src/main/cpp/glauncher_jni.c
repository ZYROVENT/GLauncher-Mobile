/*
 * Desactiva el tagging de punteros de Android (AArch64) en el proceso de la app.
 *
 * Android 12+ reserva el byte superior de los punteros; OpenJDK libera punteros
 * sin esa etiqueta y bionic aborta con "Pointer tag for ... was truncated"
 * (SIGABRT / código 134) nada más arrancar el cliente. La flag del manifiesto
 * (allowNativeHeapPointerTagging) solo la aplica el sistema a apps con
 * targetSdk 30+, y nosotros tenemos que quedarnos en 28 para poder hacer
 * exec() del binario java, así que lo hacemos en código.
 *
 * Se llama en LauncherApp.onCreate() antes de lanzar el JVM: el proceso hijo
 * hereda el estado del tagging.
 */
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>

/* Definidos en <malloc.h> (API 31), pero los resolvemos en tiempo de ejecución
 * para no atarnos a un nivel de API concreto ni romper en equipos viejos. */
#ifndef M_BIONIC_SET_HEAP_TAGGING_LEVEL
#define M_BIONIC_SET_HEAP_TAGGING_LEVEL (-204)
#endif
#ifndef M_HEAP_TAGGING_LEVEL_NONE
#define M_HEAP_TAGGING_LEVEL_NONE 0
#endif

typedef int (*mallopt_fn)(int, int);

JNIEXPORT jboolean JNICALL
Java_com_zyrovent_glauncher_LauncherApp_disablePointerTagging(JNIEnv *env, jobject thiz) {
    int malloptResult = -1;

    mallopt_fn malloptPtr = (mallopt_fn)dlsym(RTLD_DEFAULT, "mallopt");
    if (malloptPtr != NULL) {
        malloptResult = malloptPtr(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE);
    }

    /* OJO: aquí NO se toca prctl(PR_SET_TAGGED_ADDR_CTRL). Quitar el bit de
     * Top-Byte-Ignore en el proceso de la app rompe los futex de ART
     * ("futex wait failed ... Bad address") y la RenderThread aborta. */

    __android_log_print(ANDROID_LOG_INFO, "GLauncher",
                        "Tagging de punteros desactivado en la app (mallopt=%d)",
                        malloptResult);

    return malloptResult == 1 ? JNI_TRUE : JNI_FALSE;
}
