# Melodist (Beta)

> **Estado:** En desarrollo (v0.1.1) · **Solo compatible con Windows** · Basado en Metrolist.

Melodist es un reproductor de música de escritorio orientado a Windows que utiliza el stack de Compose Multiplatform. Está enfocado en ofrecer una experiencia de streaming ligera y fluida, integrándose directamente con YouTube Music y utilizando MPV como motor de audio nativo.

Este proyecto ha sido desarrollado con un uso intensivo de herramientas de IA para agilizar la lógica y el diseño de la interfaz.

## Características

- 🎵 **Streaming de YouTube Music:** Reproducción de canciones, álbumes y playlists (soporte WebM/Opus).
- 🔊 **Motor MPV Nativo:** Audio de alta calidad y bajo consumo mediante `libmpv` (sin depender de VLC).
- 🎨 **Interfaz Dinámica:** Material Design 3 con temas que se adaptan automáticamente a los colores de la carátula.
- 🔍 **Búsqueda e Historial:** Búsqueda integrada con persistencia local mediante SQLDelight.
- ⬇️ **Descargas y Caché:** Sistema de descargas reanudables y caché de canciones para ahorro de ancho de banda.
- ⌨️ **Controles Globales:** Soporte para teclas multimedia del teclado y controles en la bandeja del sistema (System Tray).
- 🍪 **Soporte de Cookies:** Permite cargar contenido personalizado y playlists privadas de YouTube.

## Requisitos

- **Sistema Operativo:** Windows 10/11 (Única plataforma probada y soportada).
- **JDK:** Java 21 o superior.
- **MPV:** El proyecto ya incluye los binarios necesarios (`libmpv-2.dll`) en `mpv-resources`.

## Estructura del Proyecto

```text
Melodist/
├── composeApp/     # Interfaz de usuario (Desktop), pantallas y navegación.
├── shared/         # Lógica de negocio, ViewModels, base de datos y reproductores.
├── innertube/      # Cliente interno para la API de YouTube Music (No modificar).
├── server/         # Servidor Ktor (Funcionalidad experimental).
└── mpv-resources/  # Binarios y cabeceras de MPV para Windows.
```

## Tecnologías (Stack)

- **UI:** Compose Multiplatform + Material 3.
- **Navegación:** Decompose.
- **DI:** Koin.
- **Base de Datos:** SQLDelight (SQLite).
- **Audio:** MPV vía JNA.
- **Red:** Ktor Client / OkHttp.
- **Serialización:** Kotlinx Serialization.

## Configuración y Ejecución

### Desarrollo
Para ejecutar la aplicación en modo desarrollo:
```powershell
.\gradlew.bat :composeApp:run
```

### Tests
El proyecto incluye pruebas unitarias básicas. Para ejecutarlas:
```powershell
.\gradlew.bat test
```

### Construcción (Build)
Para generar un instalador (MSI/EXE) se utiliza **Conveyor**:
```powershell
conveyor make windows-msi
```
*Nota: La configuración de empaquetado está definida en `conveyor.conf`.*

## Variables de Entorno y Rutas

La aplicación utiliza la variable de entorno estándar de Windows `%LOCALAPPDATA%` para almacenar sus datos.

| Contenido | Ruta aproximada |
|-----------|------|
| **Base de Datos** | `%LOCALAPPDATA%/Melodist/melodist.db` |
| **Configuración** | `%LOCALAPPDATA%/Melodist/data/` |
| **Caché de Música** | `%LOCALAPPDATA%/Melodist/cache/songs/` |
| **Logs** | `%LOCALAPPDATA%/Melodist/logs/` |

## Notas de Desarrollo

- **TODO:** Implementar soporte para otras plataformas (actualmente bloqueado por dependencias nativas de MPV y JNativeHook configuradas para Windows).
- **TODO:** Mejorar la cobertura de tests unitarios y de UI.
- **TODO:** Estabilizar el módulo `server`.

## Licencia

Este proyecto está bajo la licencia [LICENSE.md](LICENSE.md) (si no existe, consultar con los autores). Inspirado en el proyecto Metrolist.
