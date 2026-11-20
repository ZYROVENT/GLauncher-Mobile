/*
 * Se inyecta en el proceso del juego con LD_PRELOAD y apaga el tagging de
 * punteros de bionic antes de que la JVM toque la GC.
 *
 * Android 12+ exige la etiqueta en el byte superior de los punteros y OpenJDK
 * libera punteros sin ella: bionic aborta con "Pointer tag for ... was truncated"
 * (SIGABRT / código 134). mallopt() lo desactiva y, según la propia bionic,
 * al poner CheckMask a 0 las librerías ya etiquetadas se pueden seguir libreando.
 */
#include <dlfcn.h>
#include <android/log.h>

#ifndef M_BIONIC_SET_HEAP_TAGGING_LEVEL
#define M_BIONIC_SET_HEAP_TAGGING_LEVEL (-204)
#endif
#ifndef M_HEAP_TAGGING_LEVEL_NONE
#define M_HEAP_TAGGING_LEVEL_NONE 0
#endif

typedef int (*mallopt_fn)(int, int);

__attribute__((constructor))
static void glauncher_disable_tagging(void) {
    mallopt_fn malloptPtr = (mallopt_fn)dlsym(RTLD_DEFAULT, "mallopt");
    int result = (malloptPtr != NULL)
            ? malloptPtr(M_BIONIC_SET_HEAP_TAGGING_LEVEL, M_HEAP_TAGGING_LEVEL_NONE)
            : -1;
    __android_log_print(ANDROID_LOG_INFO, "GLauncher",
                        "Tagging de punteros desactivado en el JVM (mallopt=%d)", result);
}
