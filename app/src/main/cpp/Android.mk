LOCAL_PATH := $(call my-dir)

# Alineación de segmentos ELF a 16 KB: obligatorio en Google Play para apps con
# targetSdk 35+ y necesario para cargar en los dispositivos Android 15+ con página
# de 16 KB. Con esta bandera la misma .so sigue cargando en dispositivos de 4 KB.
ifneq (,$(filter $(TARGET_ARCH_ABI),arm64-v8a x86_64))
    GLAUCHER_PAGE_SIZE_LDFLAGS := -Wl,-z,max-page-size=16384
endif

# ---------------------------------------------------------------- glauncher
# Desactiva el tagging de punteros en el proceso de la app (evita el
# "Pointer tag ... was truncated" / error 134 que abortaba a la app).
include $(CLEAR_VARS)
LOCAL_MODULE := glauncher
LOCAL_SRC_FILES := \
    glauncher_jni.c \
    inprocess_jvm.c
LOCAL_LDLIBS := -llog -ldl -landroid
LOCAL_LDFLAGS := $(GLAUCHER_PAGE_SIZE_LDFLAGS)
include $(BUILD_SHARED_LIBRARY)

# ---------------------------------------------------------------- tagoff
# Se inyecta en el proceso del juego con LD_PRELOAD y hace lo mismo dentro
# del JVM, donde Android 12+ abortaba a OpenJDK en free().
include $(CLEAR_VARS)
LOCAL_MODULE := tagoff
LOCAL_SRC_FILES := tagoff.c
LOCAL_LDLIBS := -llog -ldl
LOCAL_LDFLAGS := $(GLAUCHER_PAGE_SIZE_LDFLAGS)
include $(BUILD_SHARED_LIBRARY)

# ---------------------------------------------------------------- pojavexec
# Puente gráfico (EGL/gl4es) y de entrada de PojavLauncher, usado por el
# stub de GLFW. Simplificado: sin exithook (requiere bytehook) ni puentes AWT.
POJAV_PATH := $(LOCAL_PATH)/pojavjni

include $(CLEAR_VARS)
LOCAL_PATH := $(POJAV_PATH)
LOCAL_LDLIBS := -ldl -llog -landroid
LOCAL_MODULE := pojavexec
LOCAL_SRC_FILES := \
    bigcoreaffinity.c \
    egl_bridge.c \
    ctxbridges/loader_dlopen.c \
    ctxbridges/gl_bridge.c \
    ctxbridges/osm_bridge.c \
    ctxbridges/egl_loader.c \
    ctxbridges/osmesa_loader.c \
    ctxbridges/swap_interval_no_egl.c \
    environ/environ.c \
    jvm_hooks/emui_iterator_fix_hook.c \
    jvm_hooks/java_exec_hooks.c \
    jvm_hooks/lwjgl_dlopen_hook.c \
    input_bridge_v3.c \
    jre_launcher.c \
    utils.c \
    stdio_is.c \
    driver_helper/nsbypass.c

ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS += -DADRENO_POSSIBLE
endif
LOCAL_LDFLAGS += $(GLAUCHER_PAGE_SIZE_LDFLAGS)
include $(BUILD_SHARED_LIBRARY)
