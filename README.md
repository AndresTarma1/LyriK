# Melodist ---> LyriK

> **Versión actual:** v0.1.3 (Beta) · Desarrollado con Compose Multiplatform

Reproductor de música de escritorio con streaming desde YouTube Music, motor de audio MPV nativo y interfaz Material Design 3 con temas dinámicos basados en carátulas.

---

## Estado por Plataforma

| Plataforma | Estado | Notas                                          |
|------------|--------|------------------------------------------------|
| Windows | ✓ Disponible | Plataforma principal, probada en Windows 10/11 |
| Android | [Metrolist](https://github.com/MetrolistGroup/Metrolist) | Usa Metrolist como alternativa funcional       |
| macOS | Pendiente | Falta por adaptar                              |
| Linux | Pendiente | Falta por adaptar |
| iOS | Pendiente | Falta por adaptar |

---

## Características

- Streaming de YouTube Music con reproducción de canciones, álbumes y playlists (WebM/Opus)
- Motor de audio MPV nativo vía `libmpv` — alta calidad, bajo consumo
- Interfaz Material Design 3 con temas dinámicos extraídos de carátulas
- Búsqueda integrada con historial persistente (SQLDelight / SQLite)
- Descargas y caché de canciones para ahorro de ancho de banda
- Atajos de teclado globales y controles desde la bandeja del sistema (System Tray)
- Soporte de cookies para contenido personalizado y playlists privadas de YouTube

---

## Proceso de Desarrollo

### Arquitectura

El proyecto sigue Clean Architecture con tres módulos principales:

```
LyriK/
├── composeApp/     # UI Desktop, pantallas, navegación, componentes compartidos
├── shared/         # Lógica de negocio, ViewModels, repositorios, base de datos
├── innertube/      # Cliente para la API de YouTube Music (NewPipe + parsing custom)
└── mpv-resources/  # Binarios de MPV para Windows (libmpv-2.dll)
```

### Stack Técnico

- **UI:** Compose Multiplatform + Material 3
- **Navegación:** Decompose
- **Inyección de dependencias:** Koin
- **Base de datos:** SQLDelight (SQLite)
- **Audio:** MPV vía JNA
- **Red:** Ktor Client / OkHttp
- **Serialización:** Kotlinx Serialization

### Cómo Compilar

Requisitos previos:
- JDK 21 o superior
- Windows 10/11 (plataforma de desarrollo activa)

```powershell
# Ejecutar en modo desarrollo
.\gradlew :composeApp:run

# Compilar Kotlin
.\gradlew compileKotlinJvm --quiet

# Ejecutar tests
.\gradlew test
```

### Rutas de Datos

La aplicación almacena sus datos en `%LOCALAPPDATA%\Tarma\LyriK\`:

| Contenido | Ruta |
|-----------|------|
| Base de datos | `%LOCALAPPDATA%\Tarma\LyriK\melodist.db` |
| Configuración | `%LOCALAPPDATA%\Tarma\LyriK\data\` |
| Caché de canciones | `%LOCALAPPDATA%\Tarma\LyriK\cache\songs\` |
| Descargas | `%LOCALAPPDATA%\Tarma\LyriK\downloads\` |
| Logs | `%LOCALAPPDATA%\Tarma\LyriK\logs\` |

---

## Trabajo Pendiente

- Soporte multiplataforma (macOS, Linux, iOS) 
- Mejorar cobertura de tests unitarios y de UI
- Estabilizar módulo `server` (Ktor, experimental)
- Unificar preferencias de crossfade actualmente sin uso
- Añadir localización (actualmente todo en español)
- Migrar `Route` y `ScreenConfig` duplicados a una fuente única

---

## Notas

- Este proyecto utiliza herramientas de IA para lógica y diseño de interfaz
- Inspirado en [Metrolist](https://github.com/MetrolistGroup/Metrolist)
