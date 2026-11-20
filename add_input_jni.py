import os

additions = """
JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendKey(
        JNIEnv *env, jclass clazz, jint key, jint scancode, jint action, jint mods) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    void (*send_key)(jint, jint, jint, jint) =
        (void (*)(jint, jint, jint, jint)) dlsym(handle, "critical_send_key");
    if (send_key != NULL) send_key(key, scancode, action, mods);
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendMouseButton(
        JNIEnv *env, jclass clazz, jint button, jint action, jint mods) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    void (*send_mb)(jint, jint, jint) =
        (void (*)(jint, jint, jint)) dlsym(handle, "critical_send_mouse_button");
    if (send_mb != NULL) send_mb(button, action, mods);
}

JNIEXPORT void JNICALL
Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendCursorPos(
        JNIEnv *env, jclass clazz, jfloat x, jfloat y) {
    void *handle = pojav_bridge();
    if (handle == NULL) return;
    void (*send_pos)(jfloat, jfloat) =
        (void (*)(jfloat, jfloat)) dlsym(handle, "critical_send_cursor_pos");
    if (send_pos != NULL) send_pos(x, y);
}
"""

with open('app/src/main/cpp/inprocess_jvm.c', 'r', encoding='utf-8') as f:
    content = f.read()

if 'Java_com_zyrovent_glauncher_launch_GameWindow_nativeSendKey' not in content:
    content += additions
    with open('app/src/main/cpp/inprocess_jvm.c', 'w', encoding='utf-8') as f:
        f.write(content)
    print("Added native input bridges to inprocess_jvm.c")
else:
    print("Already added")
