# GLauncher Mobile

<div align="center">
  <img src="icons/favicon.png" width="96" alt="GLauncher icon"/>
  <br/>
  <strong>Launcher de Minecraft Java Edition para Android</strong><br/>
  <sub>Hecho con Kotlin + Jetpack Compose · Diseño estilo Bedrock UI</sub>
</div>

---

## 📋 Info del proyecto

| Campo | Valor |
|-------|-------|
| **Paquete** | `com.zyrovent.glauncher` |
| **minSdk** | 24 (Android 7.0 Nougat) |
| **targetSdk / compileSdk** | 34 (Android 14) |
| **Lenguaje** | Kotlin + Jetpack Compose |
| **Backend** | Supabase · `glauncher-api.onrender.com` |
| **APK de debug** | `app/build/outputs/apk/debug/app-debug.apk` |

---

## ✨ Características

### 🎮 Launcher
- **Catálogo de versiones** – Descarga el manifest oficial de Mojang (`version_manifest_v2.json`), lo cachea en disco y funciona sin red con la última copia guardada. Filtros por Release/Snapshot y búsqueda por ID.
- **Perfiles offline** – Nombre validado (3–16 caracteres, `[A-Za-z0-9_]`), UUID derivado con `UUID.nameUUIDFromBytes`, persistidos en SharedPreferences.
- **Descarga y verificación** – Obtiene metadatos de la versión, descarga `client.jar` con barra de progreso y **valida SHA-1** antes de marcar la instalación como completa.
- **Pipeline de arranque** – `JvmCommandBuilder` construye la línea de comando completa, visible en el panel de Registro.
- **Ajustes** – RAM asignada, renderer, argumentos JVM extra, volumen de música/efectos y fondo de pantalla, todo persistido.

### 👤 Cuentas (Mi Cuenta)
- **Login en un paso** – Usuario + contraseña + código de seguridad (PIN de 6 dígitos). Se conecta a `glauncher-api.onrender.com/api/auth/login` y guarda el token de sesión.
- **Registro** – Redirige a `glauncher.vercel.app/src/html/register` en el navegador del dispositivo.
- **Perfil online** – Muestra avatar, nombre y saldo de GCoins al iniciar sesión.
- **Perfiles offline** – Gestión local de varios perfiles sin necesidad de cuenta.

### 💬 GChat
- **Chat en vivo** con amigos registrados en la plataforma GLauncher.
- **Búsqueda de usuarios** y sistema de solicitudes de amistad.
- **Responder mensajes** con cita del mensaje original.
- **GIFs via Giphy** – Selector integrado con búsqueda en tiempo real.
- **Refresco automático** cada 5 segundos mientras la conversación está abierta.

### 🎵 GMusic
- Reproductor de música integrado con listas de reproducción.
- Mini-player persistente visible en todas las pantallas.

### 🛒 Tienda
- Catálogo de cosméticos y artículos de GLauncher.

---

## 🏗️ Estructura del proyecto

```
app/src/main/java/com/zyrovent/glauncher/
├── MainActivity.kt              Activity principal Compose
├── LauncherViewModel.kt         Estado global (UiState) y acciones del launcher
├── LauncherApp.kt               Application class
├── audio/
│   └── MusicService.kt          Servicio en segundo plano para GMusic
├── data/
│   ├── AuthApi.kt               Cliente REST de glauncher-api (login, amigos, GChat)
│   ├── Models.kt                Modelos de datos (Account, Version, Settings…)
│   ├── SessionStore.kt          Persistencia del token de sesión
│   ├── Downloader.kt            Descarga de archivos con progreso y SHA-1
│   ├── MojangRepository.kt      Manifest y metadatos de versiones de Minecraft
│   ├── SupabaseApi.kt           Integración con Supabase
│   ├── RuntimeManager.kt        Detección y gestión del runtime Java (JRE ARM64)
│   └── ...                      Otros repositorios y utilidades
├── launch/
│   ├── LaunchPipeline.kt        Orquestador de instalación y arranque
│   ├── GameInstaller.kt         Descarga y verificación de assets
│   ├── JvmCommandBuilder.kt     Construcción del comando de inicio de JVM
│   └── ...                      DeviceTuning, GpuProfiles, RenderTuning…
├── gss/
│   └── GssEngine.kt             Motor de scripts GSS
├── ui/
│   ├── components/              Componentes reutilizables (McButton, McCompactField…)
│   ├── screens/
│   │   ├── AccountsScreen.kt    Pantalla Mi Cuenta + modal de login + GChat
│   │   ├── SessionViewModel.kt  ViewModel de autenticación y GChat
│   │   ├── HomeScreen.kt        Pantalla de inicio
│   │   ├── VersionsScreen.kt    Catálogo y selector de versiones
│   │   ├── ModsScreen.kt        Gestión de mods
│   │   ├── SettingsScreen.kt    Ajustes del launcher
│   │   ├── MusicScreen.kt       Reproductor GMusic
│   │   ├── ShopScreen.kt        Tienda GLauncher
│   │   └── ChatScreen.kt        Pantalla de GChat
│   └── theme/                   Colores, tipografía y tema Bedrock
└── util/
    └── Sfx.kt                   Efectos de sonido
```

---

## 🗄️ Base de datos (Supabase)

El proyecto usa **Supabase** como backend principal con 66 tablas distribuidas en 6 esquemas:

| Esquema | Tablas principales |
|---------|--------------------|
| `public` | `users`, `friendships`, `messages`, `profiles`, `notifications`, `user_achievements`, `user_progression`, `user_cosmetics`, `cosmetic_catalog`, `streaks`, `launcher_releases`… |
| `auth` | `users`, `sessions`, `identities`, `refresh_tokens`, `mfa_factors`… |
| `storage` | `buckets`, `objects`, `s3_multipart_uploads`… |
| `realtime` | `messages` (con particiones por fecha) |
| `cron` | `job`, `job_run_details` |
| `vault` | `secrets` |

---

## 🎨 Assets

| Archivo | Uso |
|---------|-----|
| `icons/favicon.png` | Icono de la app |
| `icons/minecraft_title.png` | Logo en la cabecera de Inicio |
| `icons/version_vanilla.png` | Icono de versión vanilla |
| `images/default-avatar.png` | Avatar por defecto de perfiles |
| `images/fondo-1…4.jfif`, `images/ifondo.png` | Fondos de pantalla seleccionables |
| `fonts/MinecraftTen.ttf` | Fuente de títulos |
| `fonts/font.ttf` (Minecraftia) | Botones, etiquetas y texto UI |
| `fonts/Montserrat-Bold.ttf` | Texto de cuerpo |
| `sounds/notifications/acept.mp3` | Acciones correctas / crear perfil |
| `sounds/notifications/error.mp3` | Errores de descarga o versión |
| `sounds/sfx/cartoon-hammer.mp3` | Pulsar el botón Jugar |

---

## 🛠️ Compilar y ejecutar

### Requisitos
- **JDK 17+**
- **Android SDK 34** (configura la ruta en `local.properties`)
- Dispositivo Android 7.0+ o emulador con `minSdk 24`

### Compilar e instalar
```bash
# Debug (recomendado para desarrollo)
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# O directamente instalar en dispositivo conectado
./gradlew installDebug
```

### En Windows
```bat
gradlew.bat installDebug
```

---

## 📁 Datos en tiempo de ejecución

Los archivos del juego se guardan **sin permisos de almacenamiento externo**:

```
/Android/data/com.zyrovent.glauncher/files/glaucher/
├── instances/<version>/
│   ├── client.jar
│   └── version.json
├── assets/
├── libraries/
├── logs/
└── runtime/
    └── bin/java          ← JRE ARM64 (requerido para ejecutar el juego)
```

> **Nota:** Para ejecutar el juego se necesita un **runtime Java para Android (JRE ARM64)** colocado en `filesDir/runtime/bin/java`. Sin él, el launcher descarga y verifica los archivos pero detiene el pipeline con un mensaje claro.

---

## 🔗 Links

- 🌐 **Web:** [glauncher.vercel.app](https://glauncher.vercel.app)
- 📝 **Registro:** [glauncher.vercel.app/src/html/register](https://glauncher.vercel.app/src/html/register)
- ⚙️ **API:** `https://glauncher-api.onrender.com`

---

<div align="center">
  <sub>Hecho con ❤️ por el equipo GLauncher · 2026</sub>
</div>
