# Migración de Backups a SAF (Storage Access Framework)

**Fecha:** 2026-01-19
**Proyecto:** PsychoLogger
**Objetivo:** Eliminar `READ_EXTERNAL_STORAGE` para cumplir requisitos de IzzyOnDroid

---

## 📋 Resumen Ejecutivo

Migrar el sistema de backups de permisos tradicionales de storage a SAF (Storage Access Framework), permitiendo que los usuarios elijan dónde guardar sus backups sin requerir permisos invasivos.

**Resultado esperado:**
- Eliminar `READ_EXTERNAL_STORAGE` y `WRITE_EXTERNAL_STORAGE` del manifest
- Backup manual con SAF picker (usuario elige destino cada vez)
- Autobackup cada 12h con URI persistente (usuario configura carpeta una vez)
- Cifrado AES-256 sigue funcionando igual
- Toasts de progreso en todo el flujo

---

## 🧠 Conceptos PBL (Para Plaud Note)

### ¿Qué es SAF (Storage Access Framework)?

**Analogía:** Imagina que antes tu app tenía una "llave maestra" (READ_EXTERNAL_STORAGE) que abría cualquier carpeta del teléfono. SAF es como un "portero educado" que pregunta al usuario: "¿A qué carpeta específica quieres dar acceso?"

**Beneficios:**
- Sin permisos peligrosos en el manifest
- Usuario tiene control total sobre dónde van sus datos
- Funciona con Google Drive, OneDrive, SD cards, etc.
- Más privacidad y seguridad

### Tipos de SAF Intents

| Intent | Para qué sirve | Ejemplo |
|--------|----------------|---------|
| `ACTION_CREATE_DOCUMENT` | Crear un archivo nuevo | Guardar backup.zip |
| `ACTION_OPEN_DOCUMENT` | Abrir archivo existente | Restaurar backup |
| `ACTION_OPEN_DOCUMENT_TREE` | Elegir carpeta completa | Configurar destino autobackups |

### URI Persistente (takePersistableUriPermission)

**Problema:** Normalmente, cuando el usuario elige una carpeta via SAF, el permiso se pierde al cerrar la app.

**Solución:** `takePersistableUriPermission()` guarda el permiso permanentemente (hasta que el usuario lo revoque manualmente o desinstale la app).

```kotlin
// Después de que usuario elige carpeta:
val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
context.contentResolver.takePersistableUriPermission(uri, flags)
```

### DocumentFile vs File

| File (tradicional) | DocumentFile (SAF) |
|--------------------|--------------------|
| `File("/sdcard/backups/")` | `DocumentFile.fromTreeUri(context, uri)` |
| `file.createNewFile()` | `documentFile.createFile("application/zip", "name")` |
| `FileOutputStream(file)` | `contentResolver.openOutputStream(uri)` |
| Requiere permisos | No requiere permisos |

---

## 🏗️ Arquitectura

### Sistema Actual vs Nuevo

```
┌─────────────────────────────────────────────────────────────┐
│                    SISTEMA ACTUAL                           │
├─────────────────────────────────────────────────────────────┤
│  Backup Manual → ZIP en getExternalFilesDir → ShareSheet    │
│  Autobackup    → ZIP en getExternalFilesDir (cada 12h)      │
│  Permisos: READ_EXTERNAL_STORAGE                            │
└─────────────────────────────────────────────────────────────┘

                           ↓ MIGRACIÓN ↓

┌─────────────────────────────────────────────────────────────┐
│                    SISTEMA NUEVO (SAF)                      │
├─────────────────────────────────────────────────────────────┤
│  Backup Manual → ZIP en cacheDir → SAF picker → destino     │
│  Autobackup    → ZIP en cacheDir → URI persistido           │
│  Permisos: NINGUNO                                          │
└─────────────────────────────────────────────────────────────┘
```

### Flujo Backup Manual

```
Usuario abre "📦 Backup Total"
         ↓
Configura opciones (media, cifrado)
         ↓
Click "CREAR BACKUP"
         ↓
🔔 Toast: "🔄 Preparando backup..."
         ↓
ZIP se crea en cacheDir
         ↓
🔔 Toast: "✅ Backup creado. Selecciona dónde guardarlo..."
         ↓
SAF Picker (ACTION_CREATE_DOCUMENT)
         ↓
    ├─ Cancela → 🔔 "❌ Backup cancelado"
    │
    └─ Elige destino → 🔔 "💾 Guardando..."
                              ↓
                       Copiar a destino
                              ↓
                       Eliminar temporal
                              ↓
                       🔔 "✅ Backup guardado"
```

### Flujo Autobackup

**Configuración (una vez):**
```
Usuario → "📁 Configurar carpeta autobackups"
         ↓
SAF Directory Picker (ACTION_OPEN_DOCUMENT_TREE)
         ↓
takePersistableUriPermission()
         ↓
URI guardado en SharedPreferences
         ↓
🔔 "✅ Carpeta configurada"
```

**Cada 12 horas:**
```
BackupWorker inicia
         ↓
¿Autobackup habilitado? ─NO→ Skip
         ↓ SÍ
¿Hay URI persistido? ─NO→ 🔔 "❌ No hay carpeta configurada"
         ↓ SÍ
¿Tenemos permisos? ─NO→ 🔔 "❌ Permisos revocados"
         ↓ SÍ
🔔 "🔄 Iniciando backup automático..."
         ↓
Crear ZIP en cacheDir
         ↓
🔔 "💾 Guardando backup..."
         ↓
Crear archivo en carpeta SAF
         ↓
Copiar contenido
         ↓
Rotación (mantener 7)
         ↓
🔔 "✅ Backup automático completado"
```

---

## 📁 Archivos a Modificar

### AndroidManifest.xml
**Cambio:** Eliminar permisos de storage
```xml
<!-- ELIMINAR estas líneas -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

### BackupManager.kt
**Cambios:**
- Nuevo: `createBackupInCache()` - crear ZIP en caché temporal
- Nuevo: `writeBackupToUri()` - escribir a URI de SAF
- Nuevo: `cleanOldBackupsInSaf()` - rotación en carpeta SAF
- Deprecar: `createBackup()` - el que escribe directo
- Deprecar: `getBackupsDirectory()` - ya no usamos directorio fijo

### BackupWorker.kt
**Cambios:**
- Usar BackupPreferences para obtener URI
- Verificar permisos antes de escribir
- Crear archivo via DocumentFile
- Toasts de progreso (si app visible)

### ProfileSettingsScreen.kt
**Cambios:**
- Nuevo launcher: `CreateDocument` para backup manual
- Nuevo launcher: `OpenDocumentTree` para configurar autobackup
- Nueva UI: Sección de configuración de autobackup
- Toasts de progreso en todo el flujo
- Eliminar ShareSheet

---

## 📁 Archivos a Crear

### BackupPreferences.kt
```kotlin
class BackupPreferences(context: Context) {
    // Gestión de:
    // - URI de carpeta de autobackups
    // - Estado on/off de autobackup
    // - Timestamp último backup
}
```

---

## 🎨 Nueva UI en ProfileSettingsScreen

```
┌─────────────────────────────────────────┐
│  Gestión de Datos                       │
├─────────────────────────────────────────┤
│  📊 Exportar Historial (CSV)            │
├─────────────────────────────────────────┤
│  📦 Backup Total (Media + Cifrado)      │
├─────────────────────────────────────────┤
│  📁 Configurar Carpeta Autobackup       │
│     Estado: ✅ Activo / ❌ No config.   │
│     Carpeta: [nombre carpeta]           │
├─────────────────────────────────────────┤
│  🔄 Autobackup cada 12h    [Switch]     │
├─────────────────────────────────────────┤
│  📁 Seleccionar CSV para Importar       │
├─────────────────────────────────────────┤
│  🗑️ Limpiar Todos los Datos            │
└─────────────────────────────────────────┘
```

---

## ⚠️ Casos Edge

| Situación | Comportamiento |
|-----------|----------------|
| Usuario revoca permisos SAF | Autobackup falla, se desactiva, pedir reconfigurar |
| Carpeta eliminada/movida | Mismo comportamiento |
| Sin espacio en destino | Toast "❌ Sin espacio suficiente" |
| Usuario cancela SAF picker | Toast "❌ Cancelado", eliminar temporal |
| App en background | Sin Toast, solo log |
| Primer uso | Autobackup desactivado por defecto |

---

## 📦 Dependencias

Verificar en `build.gradle.kts`:
```kotlin
implementation("androidx.documentfile:documentfile:1.0.1")
```

---

## ✅ Checklist de Implementación

- [x] Crear `BackupPreferences.kt`
- [x] Modificar `BackupManager.kt` con métodos SAF
- [x] Modificar `BackupWorker.kt` para usar URI persistido
- [x] Modificar `ProfileSettingsScreen.kt` con nueva UI y launchers
- [x] Eliminar permisos en `AndroidManifest.xml`
- [x] Añadir dependencia `documentfile` en `build.gradle.kts`
- [x] Build debug exitoso
- [ ] Probar backup manual con cifrado
- [ ] Probar backup manual sin cifrado
- [ ] Probar configuración de carpeta autobackup
- [ ] Probar autobackup automático
- [ ] Probar restauración de backup
- [ ] Verificar que audios y fotos siguen funcionando
- [ ] Build release y verificar que no hay errores

---

## 📝 Notas para IzzyOnDroid

Después de implementar, el scanner debería mostrar:
- ✅ Sin READ_EXTERNAL_STORAGE
- ✅ Sin WRITE_EXTERNAL_STORAGE
- ✅ Backups via SAF (user-controlled location)
