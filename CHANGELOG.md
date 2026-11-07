# Changelog

Todos los cambios notables de este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

---

## [1.4] - 2026-11-07

### ✨ Añadido
- **Interfaz Bilingüe Completa (Español e Inglés)**
  - Localización completa tanto en pantallas nativas Compose (`values/` y `values-es/`) como en la interfaz WebView (`i18n.js` reactivo para vistas, modales, gráficos, meses y notificaciones)
- **Iconos Moleculares 2D y Soporte SMILES**
  - Biblioteca integrada con 86 estructuras esqueléticas 2D oficiales de PubChem (`assets/mol/*.svg`) con orientación de libro
  - Campo opcional `SMILES` al crear o editar cualquier sustancia para dibujar su estructura en tiempo real con `smiles-drawer` (incluido como 9.ª columna opcional en exportación/importación CSV)
  - Selector en *Ajustes → Apariencia* para alternar entre **Moléculas** y **Emojis clásicos**
- **Nuevo Icono Neón, Cabecera Animada, Splash Inteligente y Onboarding**
  - Nuevo icono adaptativo neón y cabecera compacta con moléculas doradas alternas y título con degradado fluido
  - Pantalla de inicio (*splash*) neón que se muestra durante las primeras 4 aperturas y se desactiva sola a partir de la 5.ª (reactivable en cualquier momento desde *Ajustes → Apariencia*)
  - Onboarding interactivo deslizable de 6 pantallas en el primer arranque y botón *"Ver introducción"* en Ajustes
- **Cifrado por Bloques `AES-256-GCM` (`chunked-v2`) sin Límite de Memoria**
  - Cifrado y descifrado en streaming por bloques de 1 MiB con IV determinista por bloque y metadatos autenticados (AAD), eliminando los cierres por falta de memoria (`OutOfMemoryError`) en bibliotecas grandes de fotos y audios
  - Retrocompatibilidad total para restaurar backups cifrados con versiones anteriores
  - Icono de ojo (mostrar/ocultar contraseña) y validación de mínimo 8 caracteres en los diálogos de cifrado y restauración
- **Exportación Asíncrona Unificada de Audios y Fotos con Cifrado Opcional**
  - Nuevo flujo en segundo plano (`Dispatchers.IO`) con barra de progreso en vivo que evita congelar la interfaz al comprimir cientos de megabytes de imágenes o notas de voz
  - Opción de cifrar con contraseña (`AES-256-GCM`) tanto el ZIP de audios como el de fotos
  - Limpieza automática de archivos ZIP temporales previos en `cacheDir`
  - Script `decrypt_psychologger.py` actualizado para desencriptar y extraer tanto `audios/` como `photos/` (formato `chunked-v2` y legacy)
- **Autobackup Configurable (Frecuencia y Copias Recientes)**
  - Selector de frecuencia en *Backup Automático* para elegir cada cuánto se ejecuta (`6h`, `12h`, `24h`, `48h` o `7d`) con reprogramación inmediata en `WorkManager`
  - Selector para decidir cuántos autobackups recientes conservar (`3`, `5`, `7`, `10` o `15`), rotando y eliminando automáticamente los más antiguos en almacenamiento interno y carpeta SAF
- **Sustancias Archivadas Rediseñadas y Comportamiento Transparente**
  - Acceso rápido con 5 toques sobre el chip *"Acerca del proyecto"* en la pestaña Datos
  - Nuevo modal de Sustancias Archivadas con estética neón, icono molecular/emoji, barra de color de la sustancia, fecha de archivado y botón de restauración
  - Una sustancia archivada desaparece del calendario, de las estadísticas, del contador de *"Experiencias registradas"* y del CSV exportado, conservando sus registros intactos en las copias de seguridad

### 🔄 Cambiado
- **Desbloqueo Biométrico + PIN de la App**: el prompt biométrico solicita exclusivamente huella dactilar (`BIOMETRIC_WEAK`) con botón directo *"Usar PIN de la app"*, y las pantallas de PIN ocultan los dígitos con asteriscos y permiten scroll con el teclado abierto
- El calendario empieza la semana en lunes
- Acciones de la sección **Gestión de Datos** ordenadas según su propósito/riesgo, con mayor separación visual entre los textos descriptivos y los botones de acción
- Estado vacío explícito en Estadísticas cuando aún no hay datos y gráficos más compactos cuando hay muchas categorías
- Navegación inferior más cómoda al tocar, tarjetas de Recursos etiquetadas por categoría y editor de notas mejorado para entradas largas
- Chip de la sustancia activa más legible con superficie tenue y contador en píldora

### 🐛 Corregido
- Corregido el bloqueo (~10 s) del WebView al exportar galerías grandes de fotos en ZIP
- Corregido el colapso del modal de Libros Recomendados por el uso de `100vh` y respetada la barra de gestos inferior
- Al renombrar una sustancia, todos sus registros actualizan ahora su nombre automáticamente
- Los diálogos del WebView ya no muestran el encabezado *"localhost dice…"*
- Las notas conservan los saltos de línea al renderizarse
- Etiquetas de *set* y *setting* duplicadas o con ruido se unifican en la herramienta de revisión de datos sin marcar frases normales como corruptas
- Las copias de seguridad y CSV conservan las sustancias archivadas, el campo SMILES y el contexto del usuario al restaurar

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
