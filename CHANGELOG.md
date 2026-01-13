# Changelog

Todos los cambios notables de este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

---

## [1.1] - 2026-01-13

### ✨ Añadido
- **Backup Avanzado con Cifrado AES-256-GCM**
  - Modal de configuración con opciones de cifrado y contraseña
  - Inclusión opcional de multimedia (audios y fotos)
  - Derivación de clave segura con PBKDF2 (120.000 iteraciones)

- **ShareSheet Nativo**
  - Compartir backups directamente a Google Drive, Telegram, Email, etc.
  - Integración con `FileProvider` para acceso seguro a archivos

- **Restauración Inteligente**
  - Detección automática de backups cifrados
  - Solicitud de contraseña al importar archivos protegidos
  - Restauración completa de datos, audios y fotos

- **Pantalla de Perfil Nativa**
  - Nuevo UI para gestión de datos y backups
  - Accesible desde la web y desde el FAB de configuración

- **Historial de Accesos**
  - Registro automático de cada desbloqueo (biométrico/PIN)
  - Visualización de últimos 50 accesos con fecha y hora
  - Opción para limpiar historial

### 🐛 Corregido
- Condición de carrera en el bridge JavaScript-Kotlin que impedía abrir la pantalla de perfil
- Contexto de Compose no se resolvía correctamente para lanzar ShareSheet

### 🔧 Cambiado
- **Reconstruir Sugerencias mejorado**
  - Ahora añade emojis automáticos basados en patrones de texto
  - Detección de duplicados insensible a mayúsculas
  - Muestra contador de elementos añadidos
### 🔧 Cambiado
- El botón "Backup Manual" en la web ahora abre la pantalla nativa de backup avanzado
- Mejoras visuales en el diálogo de backup con descripción completa de contenidos

---

## [1.0] - 2025-12-XX

### ✨ Añadido
- **Bitácora Psiconáutica Completa**
  - Registro de sustancias con nombre, color y emoji personalizable
  - Registro de entradas con dosis, unidad, fecha/hora, set, setting y notas

- **Notas de Voz**
  - Grabación y reproducción de notas de audio por entrada
  - Almacenamiento interno seguro

- **Fotos por Entrada**
  - Captura desde cámara o selección de galería
  - Visualización dentro de cada registro

- **Estadísticas y Gráficos**
  - Visualización de patrones de uso
  - Gráficos interactivos basados en Chart.js

- **Auto-Backup Periódico**
  - Backups automáticos cada 12 horas vía WorkManager
  - Rotación automática (máximo 7 backups)

- **Bloqueo de App**
  - PIN de acceso con auto-lock configurable
  - Pantalla de bloqueo con animación

- **Exportación CSV**
  - Exportar historial completo en formato CSV
  - Compatible con Excel, LibreOffice, etc.

- **Tema Suave**
  - Modo claro/oscuro alternativo con colores menos saturados

---

## Enlaces

- **Repositorio**: [github.com/D4vRAM369/PsychoLogger](https://github.com/D4vRAM369/PsychoLogger)
- **Releases**: [github.com/D4vRAM369/PsychoLogger/releases](https://github.com/D4vRAM369/PsychoLogger/releases)
