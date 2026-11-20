# Compilación de referencia · GLauncher (Dell Vostro 3700, Windows 11, JDK 17)

## Herramientas

- **OS**: Windows 11 Pro 64 bits · **CPU**: Intel Core i5-7200U · **RAM**: 16 GB DDR4
- **JDK**: Eclipse Temurin **17.0.13+11** (`C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot`)
- **Android SDK**: `C:\Users\ZYROVENT\android-sdk` (compileSdk 34, build-tools **34.0.0**)
- **Gradle**: wrapper **8.6** + Android Gradle Plugin **8.3.2**
- **adb**: `C:\Users\ZYROVENT\android-sdk\platform-tools\adb.exe`
- **NDK**: `21.4.7075529` (`android.ndkVersion`, side-by-side)
- **CMake**: 3.22.1

## Cadena de compilación

```
.\gradlew.bat :app:assembleDebug
```

## Salidas

| Artefacto | Ruta |
|---|---|
| APK de depuración | `app\build\outputs\apk\debug\app-debug.apk` |
| APK de producción | `app\build\outputs\apk\release\app-release.apk` |
| APK de depuración con simbólicos (para crash tombstones) | `app\build\outputs\native-debug-symbols\debug\obj\arm64-v8a\libglauncher.so` |

## Pasos de compilación que ejecutan los assets (task `:app:mergeDebugAssets`)

El task **sube y limpia** `app/build/generated/assets`:

1. Limpia `app/build/generated/assets/debug/assetTarball`
2. Crea `app/build/generated/assets/debug/assetTarball` (vacío)
3. Crea `app/build/generated/assets/debug/out`
4. Copia `app/src/main/cpp/third_party/tarball/` → `app/build/generated/assets/debug/out`
5. Copia `app/src/main/assets/` → `app/build/generated/assets/debug/out`

Resultado tras el paso 5:

```
app/build/generated/assets/debug/out/
├── components/
├── gl4es.so            (13 431 304 B · gl4es 1.1.6.so)
├── launchericon.png    (4 623 B)
└── libtagoff.so        (3 951 158 B · sin símbolos)
```

Y el paso 6 (`:app:mergeDebugAssets`) lo fusiona en:

```
app/build/generated/assets/debug/merged/debug/assets/
├── components/
├── gl4es.so            (13 431 304 B)
├── launchericon.png    (4 623 B)
└── libtagoff.so        (3 951 158 B)
```

Ese `merged/.../assets/` es el que se comprime en el APK.

## Orden completo de tareas

```
:app:mergeDebugAssets
:app:mergeDebugJniLibFolders          ← copia las .so de src/main/jniLibs
:app:javaPreCompileDebug
:app:compileDebugKotlin
:app:mergeDebugJavaResource
:app:mergeDebugNativeLibs             ← fusiona jniLibs + externalNativeBuild
:app:desugarDebugFileDependencies
:app:mergeDexDebug
:app:packageDebug
:app:assembleDebug
```

## Saqueo previo

- `libhwui.so` (13,4 MB) → `dev/` (compilado 2025-11-28, propiedad de `root:root`)
- `egl32sw.so` (610 KB) → `dev/egl32sw.so` (Android 11, stub en APK → **eliminado** de la APK por limpieza)
- `libagl.so` → (37,5 KB) → `dev/libagl.so` (stub en APK → **eliminado**)
- `libGLES_android.so` → **eliminado**
- `egl.cfg` → **eliminado** (47 B)

## Salidas del build exitoso

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/debug/app-debug.apk-unaligned
```

## Signado (release)

Keystore en `key.properties`:

- `storeFile=zyrovent.p12`
- `keyAlias=zyrovent`
- `storeType=pkcs12`
- `storePassword=0458200752`
- `keyPassword=0458200752`
- `digestAlg=SHA512withRSA`
- `sigAlg=SHA512withRSA`

## Comandos de prueba

```bash
# Ver la app en pantalla completa (sin bordes)
adb shell dumpsys window displays | grep -E 'mCurrentFocus|imeLayeringTarget'

# Comprobar el scroll horizontal
adb shell uiautomator dump
adb shell cat /sdcard/window_dump.xml

# Lanzar la app
adb shell am start -n com.zyrovent.glauncher/.MainActivity
```