# Changelog

Todos los cambios notables de este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

---

## [1.3] - 2026-01-28

### ✨ Añadido
- **Enviado a IzzyOnDroid**
  - App en proceso de revisión para el repositorio F-Droid IzzyOnDroid

### 🐛 Corregido
- **Exportación CSV en Backup Avanzado**
  - La función exportToCSV() ahora funciona correctamente en el backup avanzado

- **Renderizado de Notas Markdown**
  - Mejora en bloques de código al seleccionar y formatear texto

### 🔧 Cambiado
- **Migración a SAF (Storage Access Framework)**
  - Selector de archivos migrado a SAF para mejor compatibilidad
  - Cumple requisitos de IzzyOnDroid

- **UI Responsiva en DateTime Picker**
  - Botones del selector de fecha/hora ahora se adaptan correctamente a pantallas móviles

---

## [1.2] - 2026-01-17

### ✨ Añadido
- **Selector de Fecha/Hora "Bitácora Temporal"**
  - Mini-calendario visual para seleccionar fechas pasadas
  - Botones rápidos: Ayer, Hace 2 días, Hace 1 hora, etc.
  - Ideal para registrar experiencias olvidadas

- **Drag-and-Drop para Sustancias**
  - Arrastra y suelta para reordenar tu lista de sustancias
  - El orden se guarda automáticamente

- **Archivar Sustancias (Función Secreta)**
  - Triple-tap en el ícono ⋮⋮ para archivar sustancias
  - Los registros se mantienen, solo se oculta de la lista principal
  - Recupera desde Ajustes > Sustancias Archivadas

- **Herramienta de Limpieza de Datos Corruptos**
  - Nueva herramienta en Ajustes > Datos
  - Detecta y limpia fragmentos de notas en campos incorrectos
  - Toasts informativos durante el escaneo

- **Modal de Confirmación para Reconstruir Opciones**
  - Ahora explica qué hace la función antes de ejecutarla
  - Advierte sobre posible copia de datos corruptos

- **Botón de Repositorio GitHub**
  - Nuevo botón en la sección "Acerca de" para acceder al código fuente

### 🐛 Corregido
- Modal de Sustancias Archivadas no renderizaba contenido (variable CSS no definida)
- Bug de hora 00:00 al editar registros
- Conflicto CSS entre calendario principal y mini-calendario del picker
- Selector de archivos CSV ahora acepta tipos MIME correctos
- Validación de nombre de archivo CSV usando ContentResolver

### 🔧 Cambiado
- Triple-tap reemplaza long-press para archivar (evita conflicto con drag-and-drop)
- Clases CSS separadas: `.calendar-*` (principal) vs `.picker-*` (mini-calendario)
- Selector de hora mejorado con pasos de 1 minuto e inputs editables

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

- **Formato Markdown en Notas**
  - Soporte para formato de texto enriquecido en notas de experiencias
  - Botones de formato para facilitar la edición

- **Tema Suave**
  - Modo claro/oscuro alternativo con colores menos saturados
  - Switch en ajustes para alternar entre temas

### 🐛 Corregido
- Condición de carrera en el bridge JavaScript-Kotlin que impedía abrir la pantalla de perfil
- Contexto de Compose no se resolvía correctamente para lanzar ShareSheet

### 🔧 Cambiado
- **Reconstruir Sugerencias mejorado**
  - Ahora añade emojis automáticos basados en patrones de texto
  - Detección de duplicados insensible a mayúsculas
  - Muestra contador de elementos añadidos
- El botón "Backup Manual" en la web ahora abre la pantalla nativa de backup avanzado
- Mejoras visuales en el diálogo de backup con descripción completa de contenidos
- Nuevo subtítulo: "Tu compañera vital para un consumo consciente y responsable"

---

## [1.0] - 2025-11-16

Primera versión pública de PsychoLogger.

### ✨ Añadido
- **Bitácora Psiconáutica Completa**
  - Registro de sustancias con nombre, color (16 colores) y emoji personalizable
  - Registro de entradas con dosis, unidad, fecha/hora, set, setting y notas
  - Calendario visual con días marcados por color de sustancia
  - Filtrado de calendario por sustancia

- **Notas de Voz**
  - Grabación y reproducción de notas de audio por entrada
  - Almacenamiento interno seguro
  - Compartir vía ShareSheet

- **Fotos por Entrada**
  - Captura desde cámara o selección de galería
  - Visualización dentro de cada registro
  - Incluidas en backups

- **Estadísticas y Gráficos**
  - Dashboard con métricas y rachas
  - Gráficos interactivos basados en Chart.js
  - Timeline, análisis de set y setting
  - Tooltips táctiles

- **Auto-Backup Periódico**
  - Backups automáticos cada 12 horas vía WorkManager
  - Rotación automática (máximo 7 backups)
  - Exportación de audios con cifrado AES-256 opcional

- **Bloqueo de App**
  - Huella dactilar para desbloqueo biométrico
  - PIN de acceso como método alternativo
  - Auto-lock configurable
  - Pantalla de bloqueo con animación

- **Exportación/Importación CSV**
  - Exportar historial completo en formato CSV
  - Importación con parser flexible (separadores mixtos)
  - ShareSheet automático tras exportación
  - Compatible con Excel, LibreOffice, etc.

- **Unidades Personalizables**
  - Añadir unidades de medida propias
  - Editar y eliminar unidades creadas

- **Panel de Recursos**
  - Enlaces a sitios de reducción de daños (DrugScience, etc.)
  - Libros recomendados con descripciones

- **Sección "Acerca de"**
  - Información del proyecto y desarrollador

---

## [0.x] Desarrollo Pre-Release

### 2025-10 a 2025-11: Preparación para Release
- Implementación de notas de voz y fotos por entrada
- AutoBackup con cifrado AES-256 para audios
- Dashboard de estadísticas con Chart.js (portado de versión web)
- Mejoras en importación CSV (parser flexible)
- Limpieza de assets obsoletos

### 2025-09: Pulido de UI/UX
- Panel de Libros recomendados con modal responsive
- Unidades de medida personalizables (añadir/editar/eliminar)
- ShareSheet automático tras exportar CSV
- Mejoras en selector de fecha/hora (colores adaptados)

### 2025-08: Features Core
- **Bloqueo biométrico y PIN** (25 agosto)
  - Implementación de `androidx.biometric` y `security-crypto`
  - Pantalla de seguridad con huella dactilar
  - PIN como método alternativo
  - Configuración de tiempo de auto-lock
- Paleta de colores ampliada a 16
- Edición de sustancias (nombre y color)
- Parámetros de Set y Setting expandidos
- Fix de bug de zona horaria UTC en calendario
- Exportación/Importación CSV funcional
- IDs únicos robustos para entradas
- Transiciones visuales (fade out) tras confirmar acciones

### 2025-07-29 a 2025-08-04: Fundamentos
- **Primer commit**: 29 julio 2025
- Migración de Jetpack Compose a WebView + HTML/JS
- Primera versión funcional del calendario
- Filtrado por sustancias en calendario
- Colores por sustancia en días del calendario
- Modales de agregar/editar entrada
- Funciones básicas de exportar/importar CSV
- Eliminación individual de sustancias
- Responsive del teclado para notas

---

## Enlaces

- **Repositorio**: [github.com/D4vRAM369/PsychoLogger](https://github.com/D4vRAM369/PsychoLogger)
- **Releases**: [github.com/D4vRAM369/PsychoLogger/releases](https://github.com/D4vRAM369/PsychoLogger/releases)
