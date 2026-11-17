# Muse (nativo)

Reproductor de música offline para Android. Kotlin + Jetpack Compose + Media3.

## Compilar el APK
1. Sube esta carpeta como raíz de un repositorio de GitHub (rama `main`).
2. Ve a **Actions → Build Muse APK → Run workflow** (o haz un push).
3. Al terminar, descarga el artefacto **muse-apk** (`app-release.apk`, ya firmado).

La app usa el mismo `applicationId` que la versión Capacitor: desinstala la anterior antes de instalar esta.

## Estructura
- `data/` biblioteca (MediaStore), portadas, persistencia (JSON)
- `player/` servicio Media3 (notificación, pantalla de bloqueo), ecualizador
- `widget/` widget de escritorio
- `ui/` pantallas Compose, 98 temas en `Themes.kt`
- `branding/` logo (SVG y PNG)
