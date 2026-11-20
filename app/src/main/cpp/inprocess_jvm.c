/*
 * Arranque del JVM dentro del propio proceso de la app.
 *
 * Android 10+ (targetSdk 29) prohíbe por SELinux/W^X ejecutar binarios del
 * directorio privado de la app, así que el modelo de "proceso hijo" con exec()
 * no puede existir. La alternativa (la que usa Pojav) es cargar libjli.so con
 * dlopen y llamar a JLI_Launch en este mismo proceso: no hay exec en ningún
 * punto y por tanto la restricción no aplica.
 *
 * Antes de llamar a JLI_Launch:
 *  - se precargan por ruta absoluta las librerías del runtime (el linker del
 *    proceso ya leyó LD_LIBRARY_PATH al arrancar, así que no sirve cambiarlo),
 *  - se redirigen fd 1/2 a una tubería para leer la salida del juego,
 *  - se reinician los manejadores de señales, como hace Pojav, para que la JVM
 *    pueda instalar los suyos.
 */
#include <android/log.h>
#include <android/native_window_jni.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <ucontext.h>
#include <unwind.h>
#include <unistd.h>

#define LOG_TAG "GLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef jint (*JLI_Launch_func)(
    int argc, char **argv,
    int jargc, const char **jargv,
    int appclassc, const char **appclassv,
    const char *fullversion, const char *dotversion,
    const char *pname, const char *lname,
    jboolean javaargs, jboolean cpwildcard, jboolean javaw, jint ergo);

#define FULL_VERSION "17.0.0-internal"
#define DOT_VERSION "17"

static int saved_out = -1;
static int saved_err = -1;

/* ------------------------------------------------------------------ utils */

static int is_gl_alias(const char *name) {
    return strcmp(name, "libGLX.so.0") == 0 ||
           strcmp(name, "libGL.so.1") == 0 ||
           strcmp(name, "libGL.so") == 0;
}

/* Lista de .so a precargar, en orden de descubrimiento. */
static int collect_libs(const char *dirpath, int depth, char **out, int max) {
    if (depth > 3) return 0;
    DIR *dir = opendir(dirpath);
    if (!dir) return 0;

    int count = 0;
    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL && count < max) {
        if (entry->d_name[0] == '.') continue;
        char path[4096];
        snprintf(path, sizeof(path), "%s/%s", dirpath, entry->d_name);

        struct stat st;
        if (stat(path, &st) != 0) continue;
        if (S_ISDIR(st.st_mode)) {
            count += collect_libs(path, depth + 1, out + count, max - count);
        } else if (strstr(entry->d_name, ".so") != NULL) {
            /* Los alias libGLX/libGL son copias de gl4es: cargarlos aquí dejaría
               la misma librería abierta varias veces (estado duplicado del GL). */
            if (is_gl_alias(entry->d_name)) continue;
            out[count++] = strdup(path);
        }
    }
    closedir(dir);
    return count;
}

/*
 * Precarga todas las librerías del runtime.
 *
 * El linker de Android ya leyó LD_LIBRARY_PATH cuando arrancó el proceso, así
 * que la única forma de que `libnio.so` encuentre `libnet.so` es tenerlas ya
 * cargadas en el espacio global. Se dan varias pasadas porque el orden de
 * cpio no respeta dependencias: lo que falla en la primera suele cargar en la
 * segunda o tercera.
 */
#define MAX_PRELOAD 512
static char *preload_paths[MAX_PRELOAD];
static int preload_count = 0;

static void collect_dir(const char *dirpath, int depth) {
    preload_count += collect_libs(
        dirpath, depth, preload_paths + preload_count, MAX_PRELOAD - preload_count);
}

static int load_collected(void) {
    int loaded = 0;
    for (int pass = 0; pass < 4; pass++) {
        int progress = 0;
        for (int i = 0; i < preload_count; i++) {
            if (preload_paths[i] == NULL) continue;
            if (dlopen(preload_paths[i], RTLD_NOW | RTLD_GLOBAL) != NULL) {
                loaded++;
                progress++;
                preload_paths[i] = NULL;
            } else {
                const char *reason = dlerror();
                if (pass == 3) {
                    LOGE("No se pudo precargar %s: %s",
                         preload_paths[i], reason != NULL ? reason : "?");
                }
            }
        }
        if (progress == 0) break;
    }
    return loaded;
}

static char **to_argv(JNIEnv *env, jobjectArray array, jint offset, int *count) {
    jsize length = (*env)->GetArrayLength(env, array);
    jsize usable = length > offset ? length - offset : 0;

    char **argv = (char **) calloc((size_t) usable + 1, sizeof(char *));
    for (jsize i = 0; i < usable; i++) {
        jstring element = (jstring) (*env)->GetObjectArrayElement(env, array, i + offset);
        const char *utf = element != NULL ? (*env)->GetStringUTFChars(env, element, NULL) : NULL;
        argv[i] = strdup(utf != NULL ? utf : "");
        if (utf != NULL) (*env)->ReleaseStringUTFChars(env, element, utf);
        if (element != NULL) (*env)->DeleteLocalRef(env, element);
    }
    argv[usable] = NULL;
    *count = (int) usable;
    return argv;
}

static void free_argv(char **argv) {
    for (int i = 0; argv[i] != NULL; i++) free(argv[i]);
    free(argv);
}

/*
 * Dejamos limpios solo los señales de fallo que la JVM va a interceptar.
 * NO se tocan las de tiempo real ni SIGQUIT/SIGCHLD: pertenecen a ART y a
 * bionic, y resetearlas a SIG_DFL mata el proceso en cuanto ART intente
 * suspender un hilo (acción por defecto de esas señales: terminar).
 */
static void reset_signal_handlers(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    /* Se limpian todos los manejadores, como hace Pojav: la JVM necesita un
       lienzo en blanco para instalar los suyos. SIGSEGV va a SIG_IGN porque
       Android rechaza ponerlo en SIG_DFL. */
    for (int sig = SIGHUP; sig < NSIG; sig++) {
        sa.sa_handler = (sig == SIGSEGV) ? SIG_IGN : SIG_DFL;
        sigaction(sig, &sa, NULL);
    }
}

/*
 * Manejadores de diagnóstico: si algo provoca una señal antes de que la JVM
 * tome el control, queda registrado en logcat en vez de morir en silencio.
 */
/*
 * Sin root no hay tombstone, así que el manejador traduce el PC del fallo a
 * "librería + offset" con dladdr: es lo único que permite saber si el segfault
 * viene de libjvm, de libgl4es o de las natives de LWJGL.
 */
static void log_fault_location(void *context) {
    if (context == NULL) return;
    ucontext_t *uc = (ucontext_t *) context;
#if defined(__aarch64__)
    void *pc = (void *) uc->uc_mcontext.pc;
    void *lr = (void *) uc->uc_mcontext.regs[30];
#elif defined(__arm__)
    void *pc = (void *) uc->uc_mcontext.arm_pc;
    void *lr = (void *) uc->uc_mcontext.arm_lr;
#else
    void *pc = NULL;
    void *lr = NULL;
#endif
    Dl_info info;
    if (dladdr(pc, &info) != 0) {
        LOGE("Fallo en %s (+0x%lx) · símbolo %s",
             info.dli_fname != NULL ? info.dli_fname : "?",
             (unsigned long) ((char *) pc - (char *) info.dli_fbase),
             info.dli_sname != NULL ? info.dli_sname : "?");
    } else {
        LOGE("Fallo en %p (dirección sin librería)", pc);
    }
    if (dladdr(lr, &info) != 0) {
        LOGE("Llamador %s (+0x%lx) · símbolo %s",
             info.dli_fname != NULL ? info.dli_fname : "?",
             (unsigned long) ((char *) lr - (char *) info.dli_fbase),
             info.dli_sname != NULL ? info.dli_sname : "?");
    }
}

static void crash_logger(int sig, siginfo_t *info, void *context) {
    LOGE("Señal %d (%s) recibida · code=%d · addr=%p",
         sig, strsignal(sig), info != NULL ? info->si_code : 0,
         info != NULL ? info->si_addr : NULL);
    log_fault_location(context);
    signal(sig, SIG_DFL);
    raise(sig);
}

/*
 * Manejador encadenado: se instala DESPUÉS de crear la VM y guarda el manejador
 * que dejó HotSpot. Así se registra el lugar del fallo (librería + offset) sin
 * pisar el manejador de la JVM, que es quien convierte los accesos nulos en
 * NullPointerException; si se pisa, cualquier excepción Java mata el proceso.
 */
static struct sigaction g_previous_actions[NSIG];
static int g_previous_valid[NSIG];

/* Descriptor compartido solo para la pila del fallo (válido durante el volcado). */
static int g_backtrace_fd = -1;

static _Unwind_Reason_Code backtrace_frame(struct _Unwind_Context *ctx, void *arg) {
    int *frames = (int *) arg;
    if (*frames >= 24) return _URC_END_OF_STACK;
    uintptr_t ip = (uintptr_t) _Unwind_GetIP(ctx);
    if (ip == 0) return _URC_NO_REASON;
    Dl_info dl;
    if (dladdr((void *) ip, &dl) != 0) {
        dprintf(g_backtrace_fd, "  #%d %s+0x%lx %s\n", *frames,
                dl.dli_fname != NULL ? dl.dli_fname : "?",
                (unsigned long) ((char *) ip - (char *) dl.dli_fbase),
                dl.dli_sname != NULL ? dl.dli_sname : "?");
    } else {
        dprintf(g_backtrace_fd, "  #%d %p\n", *frames, (void *) ip);
    }
    (*frames)++;
    return _URC_NO_REASON;
}

/* Deja constancia en disco: logcat se rota y se pierde el detalle del fallo. */
static void write_crash_file(int sig, siginfo_t *info, void *context) {
    int fd = open("crash.log", O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd < 0) return;
    struct sigaction current;
    memset(&current, 0, sizeof(current));
    sigaction(sig, NULL, &current);
    dprintf(fd, "senal=%d code=%d addr=%p\n", sig,
            info != NULL ? info->si_code : 0, info != NULL ? info->si_addr : NULL);
    if (context != NULL) {
        ucontext_t *uc = (ucontext_t *) context;
#if defined(__aarch64__)
        void *pc = (void *) uc->uc_mcontext.pc;
        void *lr = (void *) uc->uc_mcontext.regs[30];
#elif defined(__arm__)
        void *pc = (void *) uc->uc_mcontext.arm_pc;
        void *lr = (void *) uc->uc_mcontext.arm_lr;
#else
        void *pc = NULL;
        void *lr = NULL;
#endif
        Dl_info dl;
        dprintf(fd, "pc_raw=%p lr_raw=%p\n", pc, lr);
        if (dladdr(pc, &dl) != 0) {
            dprintf(fd, "pc=%s+0x%lx sym=%s\n", dl.dli_fname != NULL ? dl.dli_fname : "?",
                    (unsigned long) ((char *) pc - (char *) dl.dli_fbase),
                    dl.dli_sname != NULL ? dl.dli_sname : "?");
        }
        if (dladdr(lr, &dl) != 0) {
            dprintf(fd, "lr=%s+0x%lx sym=%s\n", dl.dli_fname != NULL ? dl.dli_fname : "?",
                    (unsigned long) ((char *) lr - (char *) dl.dli_fbase),
                    dl.dli_sname != NULL ? dl.dli_sname : "?");
        }
    }
    int frames = 0;
    g_backtrace_fd = fd;
    dprintf(fd, "pila:\n");
    _Unwind_Backtrace(backtrace_frame, &frames);
    g_backtrace_fd = -1;
    close(fd);
}

/*
 * Buffer circular en memoria: HotSpot lanza SIGSEGV constantemente (null checks
 * y comprobaciones de pila), así que escribir a disco en cada una ralentizaría
 * el juego. Solo se vuelca cuando llega la señal con la que muere el proceso.
 */
#define CRASH_RING 64

typedef struct {
    int sig;
    int code;
    void *addr;
    void *pc;
    Dl_info pc_info;
} crash_event;

static crash_event g_ring[CRASH_RING];
static volatile int g_ring_index = 0;

static void get_pc_lr(void *context, void **pc_out, void **lr_out) {
    *pc_out = NULL;
    *lr_out = NULL;
    if (context == NULL) return;
    ucontext_t *uc = (ucontext_t *) context;
#if defined(__aarch64__)
    *pc_out = (void *) uc->uc_mcontext.pc;
    *lr_out = (void *) uc->uc_mcontext.regs[30];
#elif defined(__arm__)
    *pc_out = (void *) uc->uc_mcontext.arm_pc;
    *lr_out = (void *) uc->uc_mcontext.arm_lr;
#endif
}

static void dump_ring(int fd) {
    int count = g_ring_index < CRASH_RING ? g_ring_index : CRASH_RING;
    dprintf(fd, "eventos=%d\n", count);
    for (int i = 0; i < count; i++) {
        int idx = (g_ring_index - count + i + CRASH_RING * 2) % CRASH_RING;
        dprintf(fd, "  senal=%d code=%d addr=%p pc=%s+0x%lx lr_dl=%s\n",
                g_ring[idx].sig, g_ring[idx].code, g_ring[idx].addr,
                g_ring[idx].pc_info.dli_fname != NULL ? g_ring[idx].pc_info.dli_fname : "?",
                (unsigned long) ((char *) g_ring[idx].pc -
                    (char *) g_ring[idx].pc_info.dli_fbase),
                g_ring[idx].pc_info.dli_sname != NULL ? g_ring[idx].pc_info.dli_sname : "?");
    }
}

static void chained_crash_logger(int sig, siginfo_t *info, void *context) {
    int code = info != NULL ? info->si_code : 0;
    void *pc = NULL;
    void *lr = NULL;
    get_pc_lr(context, &pc, &lr);

    crash_event *slot = &g_ring[g_ring_index % CRASH_RING];
    slot->sig = sig;
    slot->code = code;
    slot->addr = info != NULL ? info->si_addr : NULL;
    slot->pc = pc;
    memset(&slot->pc_info, 0, sizeof(slot->pc_info));
    if (pc != NULL) dladdr(pc, &slot->pc_info);
    g_ring_index++;

    /*
     * Los null checks normales de HotSpot caen en la primera página con
     * code=1 (SEGV_MAPERR). Son pocos, así que se vuelca la pila de cada uno:
     * el que sea fatal quedará registrado con sus llamadores.
     */
    if (sig == SIGSEGV && code == 1 && (uintptr_t) slot->addr < 4096) {
        int fd = open("crash.log", O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (fd >= 0) {
            dprintf(fd, "=== SEGV addr=%p pc=%p ===\n", slot->addr, pc);
            int frames = 0;
            g_backtrace_fd = fd;
            _Unwind_Backtrace(backtrace_frame, &frames);
            g_backtrace_fd = -1;
            close(fd);
        }
    }

    /* SIGABRT/SIGTRAP: la JVM se rinde; aquí sí interesa el detalle. */
    if (sig == SIGABRT || sig == SIGTRAP || sig == SIGILL || sig == SIGBUS) {
        LOGE("Señal %d (%s) · code=%d · addr=%p",
             sig, strsignal(sig), code, info != NULL ? info->si_addr : NULL);
        log_fault_location(context);
        write_crash_file(sig, info, context);
        int fd = open("crash.log", O_WRONLY | O_CREAT | O_APPEND, 0644);
        if (fd >= 0) {
            dump_ring(fd);
            close(fd);
        }
    }
    if (sig > 0 && sig < NSIG && g_previous_valid[sig]) {
        struct sigaction *previous = &g_previous_actions[sig];
        if (previous->sa_flags & SA_SIGINFO) {
            if (previous->sa_sigaction != NULL) {
                previous->sa_sigaction(sig, info, context);
            }
        } else if (previous->sa_handler != SIG_DFL && previous->sa_handler != SIG_IGN &&
                   previous->sa_handler != NULL) {
            previous->sa_handler(sig);
        }
        /*
         * Si el manejador de la JVM vuelve, es que atendió la seña (por ejemplo
         * un null check que ya se convirtió en NullPointerException): hay que
         * devolver el control, NO relanzar la seña o mataríamos el juego.
         */
        return;
    }
    signal(sig, SIG_DFL);
    raise(sig);
}

static void install_chained_crash_logger(void) {
    static const int watched[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT };
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = chained_crash_logger;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    for (size_t i = 0; i < sizeof(watched) / sizeof(watched[0]); i++) {
        int sig = watched[i];
        struct sigaction previous;
        if (sigaction(sig, NULL, &previous) == 0) {
            if (previous.sa_sigaction == chained_crash_logger) continue;
            g_previous_actions[sig] = previous;
            g_previous_valid[sig] = 1;
            sigaction(sig, &sa, NULL);
        }
    }
}

static void install_crash_loggers(void) {
    static const int watched[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT, SIGTRAP, SIGSYS };
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_logger;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    for (size_t i = 0; i < sizeof(watched) / sizeof(watched[0]); i++) {
        sigaction(watched[i], &sa, NULL);
    }
}

struct jli_call {
    JLI_Launch_func launch;
    int argc;
    char **argv;
    jint result;
};

typedef jint (*CreateJavaVM_t)(JavaVM **, void **, void *);
typedef jint (*GetDefaultJavaVMInitArgs_t)(void *);

static JavaVM *probe_vm = NULL;
static JavaVM *game_vm = NULL;

/* Informe de la VM para la consola de prueba: prueba que HotSpot ejecuta código. */
static void vm_report(JNIEnv *env) {
    jclass systemClass = (*env)->FindClass(env, "java/lang/System");
    if (systemClass == NULL) {
        LOGE("Informe: no se encontró java.lang.System");
        return;
    }
    jmethodID getProperty = (*env)->GetStaticMethodID(
        env, systemClass, "getProperty",
        "(Ljava/lang/String;)Ljava/lang/String;");

    static const char *keys[] = {
        "java.version", "java.vendor", "java.vm.name", "java.vm.version",
        "os.arch", "sun.arch.data.model", "java.home", "user.dir"
    };
    for (size_t i = 0; i < sizeof(keys) / sizeof(keys[0]); i++) {
        jstring key = (*env)->NewStringUTF(env, keys[i]);
        jstring value = (jstring) (*env)->CallStaticObjectMethod(env, systemClass, getProperty, key);
        const char *text = value != NULL ? (*env)->GetStringUTFChars(env, value, NULL) : NULL;
        printf("%s = %s\n", keys[i], text != NULL ? text : "(null)");
        fflush(stdout);
        if (text != NULL) (*env)->ReleaseStringUTFChars(env, value, text);
        if (value != NULL) (*env)->DeleteLocalRef(env, value);
        (*env)->DeleteLocalRef(env, key);
    }

    jclass runtimeClass = (*env)->FindClass(env, "java/lang/Runtime");
    if (runtimeClass != NULL) {
        jmethodID getRuntime = (*env)->GetStaticMethodID(env, runtimeClass, "getRuntime", "()Ljava/lang/Runtime;");
        jmethodID maxMemory = (*env)->GetMethodID(env, runtimeClass, "maxMemory", "()J");
        jmethodID totalMemory = (*env)->GetMethodID(env, runtimeClass, "totalMemory", "()J");
        if (getRuntime != NULL && maxMemory != NULL) {
            jobject runtime = (*env)->CallStaticObjectMethod(env, runtimeClass, getRuntime);
            if (runtime != NULL) {
                jlong max = (*env)->CallLongMethod(env, runtime, maxMemory);
                jlong total = totalMemory != NULL
                    ? (*env)->CallLongMethod(env, runtime, totalMemory) : 0;
                printf("memoria.max = %lld MB\n", (long long) (max / (1024 * 1024)));
                printf("memoria.total = %lld MB\n", (long long) (total / (1024 * 1024)));
                fflush(stdout);
            }
        }
    }

    printf("=== LA MÁQUINA VIRTUAL FUNCIONA ===\n");
    fflush(stdout);
}

struct game_call {
    CreateJavaVM_t create;
    JavaVMInitArgs args;
    int argc;
    char **argv;
    const char *main_class;
    jint result;
};

/*
 * Arranca la VM y ejecuta la clase principal en el mismo hilo nativo. Es lo que
 * hacía libjli, pero sin su maquinaria: en Android 10+ JLI_Launch termina con el
 * proceso asesinado (SIGKILL) antes de crear la máquina virtual.
 */
static void *game_thread_main(void *arg) {
    struct game_call *call = (struct game_call *) arg;
    JNIEnv *env = NULL;

    jint rc = call->create(&game_vm, (void **) &env, &call->args);
    LOGI("VM: JNI_CreateJavaVM -> %d", rc);
    /*
     * El manejador encadenado registra la señal y luego delega en el de la JVM.
     * HotSpot usa SIGSEGV para los null checks, así que tras delegar se vuelve
     * sin relanzar la seña: relanzarla convertía un NullPointerException normal
     * en una muerte del proceso.
     */
    if (rc == 0) install_chained_crash_logger();
    if (rc != 0 || env == NULL) {
        call->result = rc != 0 ? rc : -1;
        return NULL;
    }
    LOGI("VM: máquina virtual creada (versión JNI 0x%x)", (*env)->GetVersion(env));

    if (call->main_class == NULL) {
        vm_report(env);
        call->result = 0;
        return NULL;
    }

    /*
     * FindClass en un hilo nativo busca en el bootstrap loader, así que no ve
     * los jars del classpath. La clase del juego se carga con el ClassLoader
     * del sistema, que sí los conoce.
     */
    jclass mainClass = NULL;
    jclass classLoaderClass = (*env)->FindClass(env, "java/lang/ClassLoader");
    if (classLoaderClass != NULL) {
        jmethodID getSystemClassLoader = (*env)->GetStaticMethodID(
            env, classLoaderClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
        jmethodID loadClass = (*env)->GetMethodID(
            env, classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        if (getSystemClassLoader != NULL && loadClass != NULL) {
            jobject loader = (*env)->CallStaticObjectMethod(env, classLoaderClass, getSystemClassLoader);
            if (loader != NULL) {
                char dotted[512];
                size_t len = strlen(call->main_class);
                if (len >= sizeof(dotted)) len = sizeof(dotted) - 1;
                for (size_t i = 0; i < len; i++) {
                    dotted[i] = call->main_class[i] == '/' ? '.' : call->main_class[i];
                }
                dotted[len] = '\0';

                jstring className = (*env)->NewStringUTF(env, dotted);
                mainClass = (jclass) (*env)->CallObjectMethod(env, loader, loadClass, className);
                (*env)->DeleteLocalRef(env, className);
                if ((*env)->ExceptionCheck(env)) {
                    (*env)->ExceptionDescribe(env);
                    (*env)->ExceptionClear(env);
                    mainClass = NULL;
                }
            }
        }
    }
    if (mainClass == NULL) {
        LOGE("VM: no se encontró la clase %s en el classpath", call->main_class);
        call->result = -2;
        return NULL;
    }
    LOGI("VM: clase %s cargada, preparando main()", call->main_class);

    jmethodID mainMethod =
        (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
    if (mainMethod == NULL) {
        LOGE("VM: la clase %s no tiene main(String[])", call->main_class);
        (*env)->ExceptionDescribe(env);
        call->result = -3;
        return NULL;
    }

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray arguments = (*env)->NewObjectArray(env, call->argc, stringClass, NULL);
    for (int i = 0; i < call->argc; i++) {
        jstring value = (*env)->NewStringUTF(env, call->argv[i]);
        (*env)->SetObjectArrayElement(env, arguments, i, value);
        (*env)->DeleteLocalRef(env, value);
    }

    LOGI("VM: llamando a %s.main() con %d argumentos", call->main_class, call->argc);
    (*env)->CallStaticVoidMethod(env, mainClass, mainMethod, arguments);

    if ((*env)->ExceptionCheck(env)) {
        LOGE("VM: excepción sin controlar dentro de main");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        call->result = -4;
    } else {
        LOGI("VM: main terminó sin excepciones");
        call->result = 0;
    }
    return NULL;
}

JNIEXPORT jint JNICALL
Java_com_zyrovent_glauncher_launch_InProcessLaunch_createAndRun(
        JNIEnv *env, jclass clazz, jstring jvmPath, jobjectArray optionsArray,
        jstring mainClassJ, jobjectArray argsArray) {

    const char *path = (*env)->GetStringUTFChars(env, jvmPath, NULL);
    void *handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (handle == NULL) {
        LOGE("VM: no se pudo cargar libjvm.so: %s", dlerror());
        (*env)->ReleaseStringUTFChars(env, jvmPath, path);
        return -100;
    }
    LOGI("VM: libjvm.so cargada (%s)", path);
    (*env)->ReleaseStringUTFChars(env, jvmPath, path);

    CreateJavaVM_t create = (CreateJavaVM_t) dlsym(handle, "JNI_CreateJavaVM");
    GetDefaultJavaVMInitArgs_t defaults =
        (GetDefaultJavaVMInitArgs_t) dlsym(handle, "JNI_GetDefaultJavaVMInitArgs");
    if (create == NULL) {
        LOGE("VM: JNI_CreateJavaVM no existe en libjvm.so");
        return -101;
    }

    int count = (int) (*env)->GetArrayLength(env, optionsArray);
    JavaVMOption *options = (JavaVMOption *) calloc((size_t) count + 1, sizeof(JavaVMOption));
    for (int i = 0; i < count; i++) {
        jstring element = (jstring) (*env)->GetObjectArrayElement(env, optionsArray, i);
        const char *utf = (*env)->GetStringUTFChars(env, element, NULL);
        options[i].optionString = strdup(utf != NULL ? utf : "");
        options[i].extraInfo = NULL;
        if (utf != NULL) (*env)->ReleaseStringUTFChars(env, element, utf);
        (*env)->DeleteLocalRef(env, element);
    }

    int argc = argsArray != NULL ? (int) (*env)->GetArrayLength(env, argsArray) : 0;
    char **argv = (char **) calloc((size_t) argc + 1, sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring element = (jstring) (*env)->GetObjectArrayElement(env, argsArray, i);
        const char *utf = (*env)->GetStringUTFChars(env, element, NULL);
        argv[i] = strdup(utf != NULL ? utf : "");
        if (utf != NULL) (*env)->ReleaseStringUTFChars(env, element, utf);
        (*env)->DeleteLocalRef(env, element);
    }

    const char *mainClass = NULL;
    if (mainClassJ != NULL) {
        mainClass = (*env)->GetStringUTFChars(env, mainClassJ, NULL);
    }

    reset_signal_handlers();
    install_crash_loggers();

    struct game_call call;
    memset(&call, 0, sizeof(call));
    call.create = create;
    call.args.version = JNI_VERSION_1_6;
    call.args.nOptions = count;
    call.args.options = options;
    call.args.ignoreUnrecognized = JNI_TRUE;
    call.argc = argc;
    call.argv = argv;
    call.main_class = mainClass;
    call.result = -1;
    if (defaults != NULL) {
        jint rc = defaults(&call.args);
        LOGI("VM: JNI_GetDefaultJavaVMInitArgs -> %d", rc);
    }

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 16L * 1024 * 1024);
    pthread_t thread;
    int created = pthread_create(&thread, &attr, game_thread_main, &call);
    pthread_attr_destroy(&attr);
    if (created != 0) {
        LOGE("VM: no se pudo crear el hilo: %s", strerror(created));
        return -102;
    }
    LOGI("VM: hilo lanzado, esperando al juego");
    pthread_join(thread, NULL);
    LOGI("VM: hilo del juego terminado con %d", call.result);
    return call.result;
}

struct probe_call {
    CreateJavaVM_t create;
    GetDefaultJavaVMInitArgs_t defaults;
    JavaVMInitArgs args;
    jint result;
    jint version;
};

/* Crea el JVM con JNI_CreateJavaVM, sin pasar por libjli. */
static void *probe_thread_main(void *arg) {
    struct probe_call *call = (struct probe_call *) arg;
    LOGI("Probe: hilo nativo llamando a JNI_CreateJavaVM (%d opciones)", call->args.nOptions);
    if (call->defaults != NULL) {
        jint rc = call->defaults(&call->args);
        LOGI("Probe: JNI_GetDefaultJavaVMInitArgs -> %d", rc);
    }
    JNIEnv *jenv = NULL;
    call->result = call->create(&probe_vm, (void **) &jenv, &call->args);
    LOGI("Probe: JNI_CreateJavaVM -> %d", call->result);
    if (call->result == 0 && jenv != NULL) {
        call->version = (*jenv)->GetVersion(jenv);
        LOGI("Probe: JNIEnv obtenido, versión 0x%x", call->version);
    }
    return NULL;
}

JNIEXPORT jint JNICALL
Java_com_zyrovent_glauncher_launch_InProcessLaunch_probeJvm(
        JNIEnv *env, jclass clazz, jstring jvmPath, jobjectArray optionsArray) {
    const char *path = (*env)->GetStringUTFChars(env, jvmPath, NULL);
    void *handle = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (handle == NULL) {
        LOGE("Probe: no se pudo cargar libjvm.so: %s", dlerror());
        (*env)->ReleaseStringUTFChars(env, jvmPath, path);
        return -100;
    }
    (*env)->ReleaseStringUTFChars(env, jvmPath, path);

    CreateJavaVM_t create = (CreateJavaVM_t) dlsym(handle, "JNI_CreateJavaVM");
    GetDefaultJavaVMInitArgs_t defaults =
        (GetDefaultJavaVMInitArgs_t) dlsym(handle, "JNI_GetDefaultJavaVMInitArgs");
    if (create == NULL) {
        LOGE("Probe: JNI_CreateJavaVM no está en libjvm.so");
        return -101;
    }

    int count = (int) (*env)->GetArrayLength(env, optionsArray);
    JavaVMOption *options = (JavaVMOption *) calloc((size_t) count + 1, sizeof(JavaVMOption));
    for (int i = 0; i < count; i++) {
        jstring element = (jstring) (*env)->GetObjectArrayElement(env, optionsArray, i);
        const char *utf = (*env)->GetStringUTFChars(env, element, NULL);
        options[i].optionString = strdup(utf != NULL ? utf : "");
        options[i].extraInfo = NULL;
        if (utf != NULL) (*env)->ReleaseStringUTFChars(env, element, utf);
        (*env)->DeleteLocalRef(env, element);
    }

    reset_signal_handlers();
    install_crash_loggers();

    struct probe_call call;
    memset(&call, 0, sizeof(call));
    call.create = create;
    call.defaults = defaults;
    call.args.version = JNI_VERSION_1_6;
    call.args.nOptions = count;
    call.args.options = options;
    call.args.ignoreUnrecognized = JNI_FALSE;
    call.result = -1;

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 16L * 1024 * 1024);
    pthread_t thread;
    int created = pthread_create(&thread, &attr, probe_thread_main, &call);
    pthread_attr_destroy(&attr);
    if (created != 0) {
        LOGE("Probe: no se pudo crear el hilo: %s", strerror(created));
        return -102;
    }
    pthread_join(thread, NULL);
    LOGI("Probe: terminado con %d", call.result);
    return call.result;
}

/*
 * La JVM se crea en un hilo nativo propio y no en el hilo de Java que llamó a
 * esta función: ese hilo ya está adjunto a ART, y adjuntarlo también a HotSpot
 * es justo lo que suele terminar con el proceso asesinado.
 */
static void *jli_thread_main(void *arg) {
    struct jli_call *call = (struct jli_call *) arg;
    LOGI("Hilo nativo listo: llamando a JLI_Launch");
    call->result = call->launch(
        call->argc, call->argv,
        0, NULL,
        0, NULL,
        FULL_VERSION, DOT_VERSION,
        call->argv[0], "openjdk",
        JNI_FALSE, JNI_TRUE, JNI_FALSE, 0);
    LOGI("JLI_Launch devolvió %d", call->result);
    return NULL;
}

/* ------------------------------------------------------------------- JNI */

JNIEXPORT jint JNICALL
Java_com_zyrovent_glauncher_launch_InProcessLaunch_preloadLibs(
        JNIEnv *env, jclass clazz, jobjectArray dirsArray) {
    preload_count = 0;
    jsize length = (*env)->GetArrayLength(env, dirsArray);
    for (jsize i = 0; i < length; i++) {
        jstring element = (jstring) (*env)->GetObjectArrayElement(env, dirsArray, i);
        const char *utf = (*env)->GetStringUTFChars(env, element, NULL);
        if (utf != NULL) {
            collect_dir(utf, 0);
            (*env)->ReleaseStringUTFChars(env, element, utf);
        }
        (*env)->DeleteLocalRef(env, element);
    }
    int total = load_collected();
    LOGI("Librerías del runtime precargadas: %d de %d", total, preload_count);
    return total;
}

JNIEXPORT jboolean JNICALL
Java_com_zyrovent_glauncher_launch_InProcessLaunch_redirectStdio(
        JNIEnv *env, jclass clazz, jint writeFd) {
    fflush(stdout);
    fflush(stderr);

    if (saved_out < 0) saved_out = dup(1);
    if (saved_err < 0) saved_err = dup(2);

    int target = (int) writeFd;
    if (dup2(target, 1) < 0 || dup2(target, 2) < 0) {
        LOGE("No se pudo redirigir stdio: %s", strerror(errno));
        return JNI_FALSE;
    }

    int devnull = open("/dev/null", O_RDONLY);
    if (devnull >= 0) {
        dup2(devnull, 0);
        if (devnull > 2) close(devnull);
    }
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_InProcessLaunch_restoreStdio(JNIEnv *env, jclass clazz) {
    fflush(stdout);
    fflush(stderr);
    if (saved_out >= 0) { dup2(saved_out, 1); close(saved_out); saved_out = -1; }
    if (saved_err >= 0) { dup2(saved_err, 2); close(saved_err); saved_err = -1; }
}

JNIEXPORT jboolean JNICALL
Java_com_zyrovent_glauncher_launch_InProcessLaunch_chdirTo(
        JNIEnv *env, jclass clazz, jstring path) {
    const char *utf = (*env)->GetStringUTFChars(env, path, NULL);
    if (utf == NULL) return JNI_FALSE;
    int result = chdir(utf);
    (*env)->ReleaseStringUTFChars(env, path, utf);
    if (result != 0) {
        LOGE("No se pudo cambiar al directorio de trabajo: %s", strerror(errno));
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_zyrovent_glauncher_launch_InProcessLaunch_launchJvm(
        JNIEnv *env, jclass clazz, jstring jliPath, jobjectArray argsArray) {
    if (argsArray == NULL || (*env)->GetArrayLength(env, argsArray) == 0) {
        LOGE("Sin argumentos para el JVM");
        return -1;
    }

    const char *path = (*env)->GetStringUTFChars(env, jliPath, NULL);
    void *libjli = dlopen(path, RTLD_LAZY | RTLD_GLOBAL);
    if (libjli == NULL) {
        LOGE("No se pudo cargar libjli.so: %s", dlerror());
        (*env)->ReleaseStringUTFChars(env, jliPath, path);
        return -1;
    }
    LOGI("libjli.so cargada: %s", path);
    (*env)->ReleaseStringUTFChars(env, jliPath, path);

    JLI_Launch_func launch = (JLI_Launch_func) dlsym(libjli, "JLI_Launch");
    if (launch == NULL) {
        LOGE("JLI_Launch no está en libjli.so: %s", dlerror());
        return -1;
    }

    reset_signal_handlers();
    install_crash_loggers();

    int argc = 0;
    char **argv = to_argv(env, argsArray, 0, &argc);
    LOGI("Llamando a JLI_Launch con %d argumentos: %s", argc, argv[0]);

    struct jli_call call;
    call.launch = launch;
    call.argc = argc;
    call.argv = argv;
    call.result = -1;

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 16L * 1024 * 1024);
    pthread_t thread;
    int created = pthread_create(&thread, &attr, jli_thread_main, &call);
    pthread_attr_destroy(&attr);
    if (created != 0) {
        LOGE("No se pudo crear el hilo de la JVM: %s", strerror(created));
        free_argv(argv);
        return -1;
    }
    LOGI("Hilo de la JVM creado (tid propio); esperando a que HotSpot arranque");
    pthread_join(thread, NULL);

    free_argv(argv);
    return call.result;
}

/* -------------------------------------------------- ventana del juego (EGL) */

/*
 * El puente gráfico de Pojav (egl_bridge.c) vive en libpojavexec.so. Se abre con
 * dlopen para no disparar su JNI_OnLoad en el proceso de la app: solo se usan
 * setupBridgeWindow/releaseBridgeWindow, que guardan y liberan la ANativeWindow
 * que el renderer necesita.
 */
static void *pojav_bridge(void) {
    static void *handle = NULL;
    if (handle != NULL) return handle;
    handle = dlopen("libpojavexec.so", RTLD_NOW | RTLD_GLOBAL);
    if (handle == NULL) {
        LOGE("No se pudo cargar libpojavexec.so: %s", dlerror());
    }
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_attachSurface(
        JNIEnv *env, jclass clazz, jobject surface) {
    if (surface == NULL) {
        LOGE("Surface nula: no se puede enlazar la ventana");
        return JNI_FALSE;
    }
    /* Diagnóstico: tamaño real del búfer que ve el renderer. */
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window == NULL) {
        LOGE("attachSurface: ANativeWindow nulo");
    } else {
        LOGI("Surface enlazada: %dx%d formato %d",
             ANativeWindow_getWidth(window),
             ANativeWindow_getHeight(window),
             ANativeWindow_getFormat(window));
        ANativeWindow_release(window);
    }

    void *handle = pojav_bridge();
    if (handle == NULL) {
        LOGE("attachSurface: handle de libpojavexec.so es NULL");
        return JNI_FALSE;
    }

    void (*setup)(JNIEnv *, jclass, jobject) =
        (void (*)(JNIEnv *, jclass, jobject)) dlsym(
            handle, "Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow");
    if (setup == NULL) {
        LOGE("setupBridgeWindow no encontrado: %s", dlerror());
        return JNI_FALSE;
    }
    setup(env, clazz, surface);
    LOGI("Ventana del juego enlazada exitosamente al puente EGL");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_detachSurface(JNIEnv *env, jclass clazz) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    void (*release)(JNIEnv *, jclass) =
        (void (*)(JNIEnv *, jclass)) dlsym(
            handle, "Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow");
    if (release != NULL) release(env, clazz);
    LOGI("Ventana del juego desvinculada del puente EGL");
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSetWindowSize(
        JNIEnv *env, jclass clazz, jint width, jint height) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;

    void (*send_screen_size)(jint, jint) =
        (void (*)(jint, jint)) dlsym(handle, "critical_send_screen_size");
    if (send_screen_size != NULL) {
        send_screen_size(width, height);
        LOGI("critical_send_screen_size aplicado: %dx%d", (int)width, (int)height);
    }
}

/*
 * ¿Tiene el juego capturado el ratón? Lo marca el propio Minecraft a través de
 * nativeSetGrabbing. La capa de controles lo consulta para decidir si un toque
 * debe mover el puntero (menús) o solo girar la cámara (en partida).
 */
JNIEXPORT jboolean JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeIsGrabbing(JNIEnv *env, jclass clazz) {
    (void) env; (void) clazz;
    void *handle = pojav_bridge();
    if (handle == NULL) return JNI_FALSE;
    jboolean (*is_grabbing)(void) =
        (jboolean (*)(void)) dlsym(handle, "glauncher_is_grabbing");
    if (is_grabbing == NULL) return JNI_FALSE;
    return is_grabbing();
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendChar(
        JNIEnv *env, jclass clazz, jchar codepoint) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    jboolean (*send_char)(jchar) =
        (jboolean (*)(jchar)) dlsym(handle, "critical_send_char");
    if (send_char != NULL) send_char(codepoint);
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendScroll(
        JNIEnv *env, jclass clazz, jdouble xoffset, jdouble yoffset) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    void (*send_scroll)(jdouble, jdouble) =
        (void (*)(jdouble, jdouble)) dlsym(handle, "critical_send_scroll");
    if (send_scroll != NULL) send_scroll(xoffset, yoffset);
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSetInputReady(
        JNIEnv *env, jclass clazz, jboolean ready) {
    void *handle = pojav_bridge();
    if (handle == NULL) {
        LOGE("nativeSetInputReady: libpojavexec.so no cargada");
        return;
    }
    jboolean (*set_ready)(jboolean) =
        (jboolean (*)(jboolean)) dlsym(handle, "JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSetInputReady");
    if (set_ready == NULL) {
        LOGE("nativeSetInputReady no encontrado: %s", dlerror());
        return;
    }
    jboolean ok = set_ready(ready);
    LOGI("Puente de entrada %s (ok=%d)", ready ? "habilitado" : "deshabilitado", (int)ok);
}

/*
 * El puente descarta los eventos si isInputReady está en falso. El indicador se
 * pierde cuando LWJGL reinicia su estado, así que se reafirma antes de enviar
 * cada evento: es idempotente y así las teclas nunca se quedan sin efecto.
 */
static void bridge_ensure_input(void *handle) {
    jboolean (*set_ready)(jboolean) =
        (jboolean (*)(jboolean)) dlsym(handle, "JavaCritical_org_lwjgl_glfw_CallbackBridge_nativeSetInputReady");
    if (set_ready != NULL) set_ready(JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendKey(
        JNIEnv *env, jclass clazz, jint key, jint scancode, jint action, jint mods) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    static int avisado = 0;
    void (*send_key)(jint, jint, jint, jint) =
        (void (*)(jint, jint, jint, jint)) dlsym(handle, "critical_send_key");
    if (send_key == NULL) {
        if (!avisado) LOGE("critical_send_key no encontrado: %s", dlerror());
        avisado = 1;
        return;
    }
    bridge_ensure_input(handle);
    send_key(key, scancode, action, mods);
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendMouseButton(
        JNIEnv *env, jclass clazz, jint button, jint action, jint mods) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    void (*send_mb)(jint, jint, jint) =
        (void (*)(jint, jint, jint)) dlsym(handle, "critical_send_mouse_button");
    if (send_mb == NULL) {
        LOGE("critical_send_mouse_button no encontrado: %s", dlerror());
        return;
    }
    bridge_ensure_input(handle);
    send_mb(button, action, mods);
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendCursorPos(
        JNIEnv *env, jclass clazz, jfloat x, jfloat y) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    void (*send_pos)(jfloat, jfloat) =
        (void (*)(jfloat, jfloat)) dlsym(handle, "critical_send_cursor_pos");
    if (send_pos == NULL) {
        LOGE("critical_send_cursor_pos no encontrado: %s", dlerror());
        return;
    }
    bridge_ensure_input(handle);
    send_pos(x, y);
}
