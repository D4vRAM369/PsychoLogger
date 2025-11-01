# 📚 Sesión Completa: Implementación de Audio y Backups en PsychoLogger

**Fecha**: 15 de Enero de 2025
**Objetivo**: Añadir grabación/reproducción de audio + sistema de backups automáticos
**Metodología**: Project-Based Learning (PBL)

---

## 📑 Índice

1. [Implementación de Audio](#1-implementación-de-audio)
   - [1.1 Permisos de Audio](#11-permisos-de-audio)
   - [1.2 AudioRecorder.kt](#12-audiorecorderkt)
   - [1.3 AudioPlayer.kt](#13-audioplayerkt)
   - [1.4 WebAppInterface - Bridge Android-JS](#14-webappinterface---bridge-android-js)
   - [1.5 UI de Grabación/Reproducción](#15-ui-de-grabaciónreproducción)
   - [1.6 Integración con Modelo de Datos](#16-integración-con-modelo-de-datos)

2. [Sistema de Backups](#2-sistema-de-backups)
   - [2.1 Dependencia WorkManager](#21-dependencia-workmanager)
   - [2.2 BackupManager.kt](#22-backupmanagerkt)
   - [2.3 BackupWorker.kt](#23-backupworkerkt)
   - [2.4 Métodos en WebAppInterface](#24-métodos-en-webappinterface)
   - [2.5 UI de Backups y Exportación](#25-ui-de-backups-y-exportación)

3. [Conceptos Aprendidos](#3-conceptos-aprendidos)
4. [Resumen de Archivos](#4-resumen-de-archivos)
5. [Cómo Usar las Funcionalidades](#5-cómo-usar-las-funcionalidades)

---

## 1. Implementación de Audio

### 1.1 Permisos de Audio

**Archivo**: `app/src/main/AndroidManifest.xml`

**Concepto**: Los permisos peligrosos en Android (como RECORD_AUDIO) deben:
1. Declararse en el Manifest
2. Solicitarse en tiempo de ejecución (runtime permissions)

**Código añadido**:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

**Ubicación**: Línea 10, junto a los demás permisos.

**¿Por qué?**:
- Sin este permiso, MediaRecorder crasheará
- Es obligatorio desde Android 6.0 (API 23)
- El usuario debe aprobarlo manualmente

---

### 1.2 AudioRecorder.kt

**Archivo**: `app/src/main/java/com/d4vram/psychologger/AudioRecorder.kt` (237 líneas)

**Propósito**: Wrapper para MediaRecorder que simplifica la grabación de audio.

**Conceptos clave**:

#### **A. Estados de MediaRecorder**
```
[Idle] → setAudioSource() → [Initialized] → setOutputFormat() →
setAudioEncoder() → prepare() → [Prepared] → start() → [Recording] →
stop() → [Idle] → release() → [Released]
```

**¡IMPORTANTE!**: Este orden es obligatorio. Si lo alteras → crash.

#### **B. Configuración de Audio**
```kotlin
MediaRecorder().apply {
    setAudioSource(MediaRecorder.AudioSource.MIC)      // 1. Micrófono
    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)  // 2. Contenedor .m4a
    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)     // 3. Codec AAC
    setAudioEncodingBitRate(128_000)                    // 4. 128 kbps
    setAudioSamplingRate(44_100)                        // 5. 44.1 kHz (calidad CD)
    setOutputFile(file.absolutePath)                    // 6. Dónde guardar
    prepare()                                            // 7. Inicializar hardware
    start()                                              // 8. Empezar
}
```

**Calidad de audio**:
- **Bitrate 128 kbps**: Equilibrio calidad/tamaño (música = 128-320 kbps)
- **Sample Rate 44.1 kHz**: Calidad CD, captura todas las frecuencias audibles humanas
- **Codec AAC**: 30% más eficiente que MP3, estándar moderno

#### **C. Almacenamiento de Archivos**

**filesDir vs cacheDir**:
| Directorio | Persistencia | Cuándo usar |
|------------|--------------|-------------|
| `filesDir` | Permanente | Datos importantes (nuestro caso) |
| `cacheDir` | Temporal | Archivos temporales |

**Ubicación**: `/data/data/com.d4vram.psychologger/files/audio_notes/`

**Nombres**: `audio_{UUID}.m4a` (ejemplo: `audio_a1b2c3d4.m4a`)

#### **D. Gestión de Recursos**

**Patrón finally para cleanup**:
```kotlin
try {
    recorder.stop()
    return RecordingResult(file, duration)
} finally {
    // SIEMPRE se ejecuta, incluso si stop() falla
    recorder.release()  // ¡CRÍTICO! Libera el micrófono
}
```

**Si no llamas `release()`**:
- El micrófono queda bloqueado
- Otras apps no pueden usarlo
- Tu app no puede grabar de nuevo

#### **E. Métodos públicos**

```kotlin
fun startRecording(): File
fun stopRecording(): RecordingResult
fun cancelRecording()
fun isRecording(): Boolean
fun getCurrentDuration(): Long
```

**Data class**:
```kotlin
data class RecordingResult(
    val file: File,
    val durationMillis: Long
)
```

---

### 1.3 AudioPlayer.kt

**Archivo**: `app/src/main/java/com/d4vram/psychologger/AudioPlayer.kt` (241 líneas)

**Propósito**: Wrapper para MediaPlayer con callbacks y actualización de progreso.

**Conceptos clave**:

#### **A. Estados de MediaPlayer**
```
[Idle] → setDataSource() → [Initialized] → prepare() → [Prepared] →
start() → [Playing] → pause() → [Paused] → stop() → [Stopped] →
release() → [End]
```

**Más flexible que MediaRecorder**: Permite pause/resume nativo.

#### **B. Sistema de Callbacks**

**¿Por qué callbacks?**
MediaPlayer reproduce en background, pero necesitamos actualizar la UI.

**Solución**: Patrón Observer con lambdas.

```kotlin
// Definir callback
private var onProgressUpdate: ((Float, Int, Int) -> Unit)? = null

// Configurar (desde WebAppInterface)
audioPlayer.setProgressCallback { progress, current, total ->
    // Kotlin ejecuta esto y notifica a JavaScript
    activity.executeJavaScript("""
        window.onAudioProgressUpdate($progress, $current, $total);
    """)
}

// Invocar
onProgressUpdate?.invoke(0.5f, 7500, 15000)
```

**Ventaja**: Comunicación asíncrona sin bloquear threads.

#### **C. Handler + Runnable para Timer**

**Problema**: Necesitamos actualizar el progreso cada 100ms.

**Solución**: Handler con Runnable recursivo.

```kotlin
private fun startProgressUpdates() {
    progressUpdateHandler = Handler(Looper.getMainLooper())

    progressUpdateRunnable = object : Runnable {
        override fun run() {
            // Obtener progreso
            val progress = player.currentPosition / player.duration.toFloat()
            onProgressUpdate?.invoke(progress, current, total)

            // RECURSIÓN: Auto-programarse en 100ms
            progressUpdateHandler?.postDelayed(this, 100)
        }
    }

    progressUpdateHandler?.post(progressUpdateRunnable!!)
}
```

**¿Por qué 100ms?**:
- 10 actualizaciones por segundo
- Suficiente para animaciones suaves
- Bajo consumo de CPU

**¡IMPORTANTE! Detener el Handler**:
```kotlin
private fun stopProgressUpdates() {
    progressUpdateHandler?.removeCallbacks(progressUpdateRunnable!!)
}
```

Sin esto → **memory leak** (el Runnable sigue ejecutándose).

#### **D. Listeners de MediaPlayer**

```kotlin
setOnCompletionListener {
    // Audio terminó naturalmente
    stopProgressUpdates()
    onCompletion?.invoke()
}

setOnErrorListener { mp, what, extra ->
    // Error durante reproducción
    onError?.invoke("Error: what=$what")
    true  // "Manejé el error, no crashees"
}
```

**Códigos de error comunes**:
- `MEDIA_ERROR_UNKNOWN (1)`: Error genérico
- `MEDIA_ERROR_SERVER_DIED (100)`: MediaPlayer crasheó
- `MEDIA_ERROR_IO (-1004)`: Error leyendo archivo

#### **E. prepare() vs prepareAsync()**

```kotlin
prepare()       // Síncrono: bloquea hasta estar listo
prepareAsync()  // Asíncrono: continúa inmediatamente
```

**Cuándo usar cada uno**:
- `prepare()`: Archivos locales pequeños (**nuestro caso**)
- `prepareAsync()`: Streaming, archivos grandes

---

### 1.4 WebAppInterface - Bridge Android-JS

**Archivo**: `app/src/main/java/com/d4vram/psychologger/MainActivity.kt`

**Concepto**: Comunicación bidireccional entre JavaScript (WebView) y Kotlin.

#### **A. @JavascriptInterface**

**¿Qué hace?**: Expone métodos Kotlin a JavaScript.

**Ejemplo**:
```kotlin
@JavascriptInterface
fun startRecording(): String {
    val file = audioRecorder.startRecording()
    return file.name  // Retorna "audio_uuid.m4a"
}
```

**Desde JavaScript**:
```javascript
const filename = Android.startRecording();
console.log(filename); // "audio_uuid.m4a"
```

**⚠️ LIMITACIONES**:
- Solo retorna tipos primitivos: `String`, `Int`, `Boolean`
- Para objetos complejos → retornar JSON como String

**Ejemplo JSON**:
```kotlin
@JavascriptInterface
fun stopRecording(): String {
    val result = audioRecorder.stopRecording()
    return """{"filename": "${result.file.name}", "duration": ${result.durationMillis}}"""
}
```

```javascript
const jsonString = Android.stopRecording();
const data = JSON.parse(jsonString);
console.log(data.duration); // 15000
```

#### **B. Kotlin → JavaScript (evaluateJavascript)**

**Problema**: Kotlin necesita notificar a JavaScript de eventos.

**Solución**: `executeJavaScript()` ejecuta código JS desde Kotlin.

```kotlin
activity.executeJavaScript("""
    if (window.onAudioCompleted) {
        window.onAudioCompleted();
    }
""")
```

**JavaScript define el callback**:
```javascript
window.onAudioCompleted = function() {
    isPlaying = false;
    updatePlayButton('▶️');
};
```

#### **C. Threading: runOnUiThread**

**Problema**: Algunas operaciones Android requieren el UI thread.

```kotlin
@JavascriptInterface
fun shareAudio(filename: String) {
    activity.runOnUiThread {
        // Intent.ACTION_SEND requiere UI thread
        val intent = Intent(Intent.ACTION_SEND)
        activity.startActivity(intent)
    }
}
```

**Cuándo usar**:
- Iniciar Activities
- Mostrar Dialogs
- Modificar Views

#### **D. API Completa de Audio**

**Grabación**:
```javascript
Android.startRecording()          // → "audio_uuid.m4a"
Android.stopRecording()           // → JSON
Android.cancelRecording()
Android.isRecording()             // → "true"/"false"
Android.getRecordingDuration()    // → "15000"
```

**Reproducción**:
```javascript
Android.playAudio("audio_uuid.m4a")  // → "OK"
Android.pauseAudio()
Android.resumeAudio()
Android.stopAudio()
Android.isPlayingAudio()             // → "true"/"false"
```

**Gestión**:
```javascript
Android.deleteAudio("audio_uuid.m4a")  // → "OK"
Android.shareAudio("audio_uuid.m4a")   // Abre ShareSheet
```

**Callbacks invocados por Kotlin**:
```javascript
window.onAudioProgressUpdate = function(progress, currentMs, totalMs) { ... }
window.onAudioCompleted = function() { ... }
window.onAudioError = function(message) { ... }
```

---

### 1.5 UI de Grabación/Reproducción

**Archivo**: `app/src/main/assets/index.html`

**Ubicación**: Dentro del formulario de entrada, después de "Notas adicionales".

#### **A. Estructura HTML (3 estados)**

**Estado 1: Inicial (sin audio)**
```html
<button id="btnStartRecording" class="btn-audio-record">
    🎤 Grabar nota de voz
</button>
```

**Estado 2: Grabando**
```html
<div id="recordingCard" class="audio-recording-card">
    <div class="recording-pulse"></div>  <!-- Animación pulsante -->
    <div>Grabando...</div>
    <div id="recordingTimer">00:15</div>
    <button id="btnStopRecording">⏹️ Detener</button>
</div>
```

**Estado 3: Reproductor (audio listo)**
```html
<div id="audioPlayerCard" class="audio-player-card">
    <div>🎤 Nota de voz</div>
    <button id="btnShareAudio">📤</button>
    <button id="btnDeleteAudio">🗑️</button>

    <!-- Barra de progreso -->
    <div class="audio-progress-container">
        <div id="audioProgressBar" class="audio-progress-bar"></div>
    </div>

    <!-- Control play/pause -->
    <button id="btnPlayPause">▶️</button>

    <div id="audioDuration">00:15 / 01:23</div>
</div>
```

#### **B. Estilos CSS (158 líneas)**

**Animación de pulso**:
```css
.recording-pulse {
    width: 12px;
    height: 12px;
    background: var(--danger);
    border-radius: 50%;
    animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.4; transform: scale(1.3); }
}
```

**Card de grabación**:
```css
.audio-recording-card {
    background: linear-gradient(135deg,
        rgba(239, 68, 68, 0.1),
        rgba(248, 113, 113, 0.1));
    border: 2px solid var(--danger);
    border-radius: 12px;
    padding: 16px;
}
```

**Barra de progreso**:
```css
.audio-progress-bar {
    height: 100%;
    background: linear-gradient(90deg, var(--primary), var(--secondary));
    width: 0%;  /* JavaScript actualiza esto */
    transition: width 0.1s linear;
}
```

#### **C. JavaScript (288 líneas)**

**Variables globales**:
```javascript
let currentAudioFilename = null;
let recordingTimerInterval = null;
let isPlaying = false;
```

**Función: startRecording()**
```javascript
function startRecording() {
    const result = Android.startRecording();

    if (!result.startsWith('ERROR:')) {
        currentAudioFilename = result;
        updateAudioUI('recording');
        startRecordingTimer();
    }
}
```

**Función: Timer de grabación**
```javascript
function startRecordingTimer() {
    let startTime = Date.now();

    recordingTimerInterval = setInterval(() => {
        const elapsed = Date.now() - startTime;
        const timerEl = document.getElementById('recordingTimer');
        timerEl.textContent = formatTime(elapsed);
    }, 1000);  // Cada 1 segundo
}

function formatTime(ms) {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}
```

**Función: togglePlayPause()**
```javascript
function togglePlayPause() {
    if (isPlaying) {
        Android.pauseAudio();
        isPlaying = false;
        updatePlayButton('▶️');
    } else {
        const result = Android.playAudio(currentAudioFilename);
        if (result === 'OK') {
            isPlaying = true;
            updatePlayButton('⏸️');
        }
    }
}
```

**Callbacks desde Kotlin**:
```javascript
window.onAudioProgressUpdate = function(progress, currentMs, totalMs) {
    updateAudioProgress(progress);  // Actualiza barra
    updateAudioDuration(currentMs, totalMs);  // "00:15 / 01:23"
};

window.onAudioCompleted = function() {
    isPlaying = false;
    updatePlayButton('▶️');
    updateAudioProgress(0);
};
```

**Gestión de estados UI**:
```javascript
function updateAudioUI(state) {
    const btnStart = document.getElementById('btnStartRecording');
    const recordingCard = document.getElementById('recordingCard');
    const playerCard = document.getElementById('audioPlayerCard');

    // Ocultar todo
    btnStart.style.display = 'none';
    recordingCard.style.display = 'none';
    playerCard.style.display = 'none';

    // Mostrar según estado
    if (state === 'initial') btnStart.style.display = 'block';
    else if (state === 'recording') recordingCard.style.display = 'block';
    else if (state === 'player') playerCard.style.display = 'block';
}
```

---

### 1.6 Integración con Modelo de Datos

**Archivo**: `app/src/main/assets/index.html`

#### **A. Actualizar modelo Entry**

**Añadir campo `audioPath`**:

```javascript
// syncDataFromStorage() - línea 3776
entries = entries.map(entry => ({
    id: entry.id || generateUniqueId(),
    substance: entry.substance || '',
    dose: entry.dose || 0,
    unit: entry.unit || '',
    date: entry.date || new Date().toISOString(),
    set: entry.set || '',
    setting: entry.setting || '',
    notes: entry.notes || '',
    audioPath: entry.audioPath || null,  // ← NUEVO
    createdAt: entry.createdAt || new Date().toISOString(),
    updatedAt: entry.updatedAt || ''
}));
```

#### **B. Guardar audio al crear entrada**

```javascript
// addEntry() - Modo CREACIÓN - línea 5313
const entry = {
    id: generateUniqueId(),
    substance: substance,
    dose: dose,
    unit: unit,
    date: dateTime,
    set: set || null,
    setting: setting || null,
    notes: notes || null,
    audioPath: currentAudioFilename || null,  // ← NUEVO
    createdAt: new Date().toISOString()
};
```

#### **C. Actualizar audio al editar entrada**

```javascript
// addEntry() - Modo EDICIÓN - línea 5290
entries[existingEntryIndex] = {
    ...entries[existingEntryIndex],
    substance: substance,
    dose: dose,
    unit: unit,
    date: dateTime,
    set: set || null,
    setting: setting || null,
    notes: notes || null,
    audioPath: currentAudioFilename || entries[existingEntryIndex].audioPath || null,  // ← NUEVO
    updatedAt: new Date().toISOString()
};
```

**Lógica**:
- Si hay `currentAudioFilename` → usar nuevo audio
- Si no → mantener audio existente
- Si ninguno → null

#### **D. Cargar audio al editar**

```javascript
// editEntryFromCalendar() - línea 4757
if (entry.audioPath) {
    currentAudioFilename = entry.audioPath;
    updateAudioUI('player');
} else {
    currentAudioFilename = null;
    updateAudioUI('initial');
}
```

#### **E. Eliminar archivo al borrar entrada**

```javascript
// deleteEntryFromCalendar() - línea 4788
if (entry.audioPath) {
    Android.deleteAudio(entry.audioPath);
}

entries = entries.filter(e => e.id !== entryId);
saveDataToStorage();
```

#### **F. Limpiar audio al resetear formulario**

```javascript
// resetEntryForm() - línea 4990
if (isPlaying) {
    Android.stopAudio();
    isPlaying = false;
}
currentAudioFilename = null;
updateAudioUI('initial');
```

---

## 2. Sistema de Backups

### 2.1 Dependencia WorkManager

**Archivo**: `app/build.gradle.kts`

```kotlin
dependencies {
    // WorkManager for periodic backups
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ... otras dependencias
}
```

**¿Qué es WorkManager?**
- API de Jetpack para tareas en background **garantizadas**
- Funciona aunque la app esté cerrada o el dispositivo reinicie
- Respeta Doze mode (ahorro de batería)
- Reintentos automáticos en caso de fallo

**Ventajas vs AlarmManager**:
| Característica | WorkManager | AlarmManager |
|----------------|-------------|--------------|
| Sobrevive reinicios | ✅ | ❌ |
| Respeta ahorro batería | ✅ | ❌ |
| Reintentos automáticos | ✅ | ❌ |
| API moderna | ✅ | ❌ (deprecated) |

---

### 2.2 BackupManager.kt

**Archivo**: `app/src/main/java/com/d4vram/psychologger/BackupManager.kt` (429 líneas)

**Propósito**: Gestión completa de backups y exportación de audios.

#### **A. Crear Backup Completo**

**Estructura del ZIP**:
```
backup_2025-01-15_14-30-00.zip
├── data.json              # localStorage serializado
└── audios/
    ├── audio_uuid1.m4a
    ├── audio_uuid2.m4a
    └── ...
```

**Código**:
```kotlin
fun createBackupWithData(localStorageData: String): File? {
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    val backupFile = File(backupsDir, "backup_$timestamp.zip")

    ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
        // 1. Añadir data.json
        val entry = ZipEntry("data.json")
        zipOut.putNextEntry(entry)
        zipOut.write(localStorageData.toByteArray())
        zipOut.closeEntry()

        // 2. Añadir audios
        addAudioFilesToZip(zipOut)
    }

    cleanOldBackups()  // Mantener solo últimos 7
    return backupFile
}
```

**¿Qué es ZipEntry?**
Representa un archivo/carpeta dentro del ZIP. Como "carpetas virtuales".

**Rotación de backups**:
```kotlin
private fun cleanOldBackups() {
    val backups = backupsDir.listFiles()
        ?.filter { it.name.startsWith("backup_") }
        ?.sortedBy { it.lastModified() }  // Más antiguo primero
        ?: return

    val toDelete = backups.take((backups.size - MAX_BACKUPS).coerceAtLeast(0))
    toDelete.forEach { it.delete() }
}
```

#### **B. Exportar Audios SIN Cifrar**

**Estructura**:
```
audios_2025-01-15_14-30-00.zip
└── audios/
    ├── audio_uuid1.m4a
    └── ...
```

**Código**:
```kotlin
fun exportAudioZip(password: String? = null): File? {
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    val zipName = if (password != null) {
        "audios_encrypted_$timestamp.zip"
    } else {
        "audios_$timestamp.zip"
    }

    val exportFile = File(context.cacheDir, zipName)

    if (password != null) {
        createEncryptedAudioZip(exportFile, password)
    } else {
        createPlainAudioZip(exportFile)
    }

    return exportFile
}
```

#### **C. Exportar Audios CON Cifrado AES-256**

**Estructura (Nested ZIP)**:
```
audios_encrypted_2025-01-15_14-30-00.zip
├── metadata.json          # { algorithm, salt, iv, iterations }
└── data.enc               # ZIP interno cifrado
```

**¿Por qué nested ZIP?**
1. Crear ZIP interno con audios
2. Cifrar ese ZIP completo
3. Meter el cifrado + metadata en ZIP externo

**Ventaja**: Metadata sin cifrar (puedes ver algoritmo usado).

**Código completo**:
```kotlin
private fun createEncryptedAudioZip(outputFile: File, password: String) {
    // 1. Generar salt e IV aleatorios
    val salt = generateRandomBytes(SALT_LENGTH)  // 16 bytes
    val iv = generateRandomBytes(IV_LENGTH)      // 12 bytes

    // 2. Derivar clave AES desde contraseña
    val key = deriveKey(password, salt)

    // 3. Crear ZIP temporal con audios
    val tempZip = File(context.cacheDir, "temp_audios.zip")
    createPlainAudioZip(tempZip)

    // 4. Cifrar el ZIP
    val cipher = Cipher.getInstance(AES_MODE)  // "AES/GCM/NoPadding"
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

    val encryptedData = File(context.cacheDir, "data.enc")
    CipherOutputStream(FileOutputStream(encryptedData), cipher).use { cipherOut ->
        FileInputStream(tempZip).use { it.copyTo(cipherOut) }
    }

    // 5. Crear ZIP final
    ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
        // Metadata
        val metadata = JSONObject().apply {
            put("algorithm", "AES-256-GCM")
            put("salt", bytesToHex(salt))
            put("iv", bytesToHex(iv))
            put("iterations", PBKDF2_ITERATIONS)
        }

        zipOut.putNextEntry(ZipEntry("metadata.json"))
        zipOut.write(metadata.toString(2).toByteArray())
        zipOut.closeEntry()

        // Datos cifrados
        zipOut.putNextEntry(ZipEntry("data.enc"))
        FileInputStream(encryptedData).use { it.copyTo(zipOut) }
        zipOut.closeEntry()
    }

    // 6. Limpiar temporales
    tempZip.delete()
    encryptedData.delete()
}
```

#### **D. PBKDF2: Derivación de Clave**

**¿Por qué no usar la contraseña directamente?**
- Contraseñas humanas son débiles ("password123")
- PBKDF2 convierte contraseña débil en clave fuerte de 256 bits
- 120,000 iteraciones hacen brute-force extremadamente lento

**Código**:
```kotlin
private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(
        password.toCharArray(),
        salt,
        PBKDF2_ITERATIONS,  // 120,000
        KEY_LENGTH          // 256 bits
    )
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val secret = factory.generateSecret(spec)
    return SecretKeySpec(secret.encoded, "AES")
}
```

**Parámetros**:
- **Salt**: 16 bytes aleatorios (evita rainbow tables)
- **Iterations**: 120,000 (recomendación OWASP 2024)
- **Key Length**: 256 bits (AES-256)

**¿Qué es rainbow table?**
Tabla precalculada de hashes comunes. El salt hace que sea único.

#### **E. AES-256-GCM**

**¿Por qué GCM?**
- **GCM = Galois/Counter Mode**
- Cifrado **autenticado** (detecta manipulación)
- Moderno, rápido, seguro
- Estándar en TLS 1.3, WhatsApp, Signal

**Alternativas**:
- **AES-CBC**: Más antiguo, requiere HMAC separado
- **AES-ECB**: ❌ INSEGURO, no usar nunca

**Código de cifrado**:
```kotlin
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(
    Cipher.ENCRYPT_MODE,
    key,
    GCMParameterSpec(128, iv)  // 128-bit auth tag
)

CipherOutputStream(output, cipher).use { cipherOut ->
    input.copyTo(cipherOut)
}
```

**IV (Initialization Vector)**:
- 12 bytes aleatorios
- **NUNCA reutilizar** con la misma clave
- Puede ser público (se incluye en metadata)

---

### 2.3 BackupWorker.kt

**Archivo**: `app/src/main/java/com/d4vram/psychologger/BackupWorker.kt` (66 líneas)

**Propósito**: Worker de WorkManager que ejecuta backups automáticos cada 12 horas.

**Código completo**:
```kotlin
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "periodic_backup"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("BackupWorker", "Iniciando backup automático...")

            val backupManager = BackupManager(applicationContext)
            val backupFile = backupManager.createBackup()

            if (backupFile != null) {
                Log.d("BackupWorker", "Backup completado: ${backupFile.name}")
                Result.success()
            } else {
                Log.e("BackupWorker", "Backup falló")
                Result.failure()
            }

        } catch (e: Exception) {
            Log.e("BackupWorker", "Error en backup", e)
            Result.failure()
        }
    }
}
```

**Conceptos**:

#### **A. CoroutineWorker vs Worker**

```kotlin
// Worker normal
class MyWorker : Worker() {
    override fun doWork(): Result {
        // Bloquea el thread
    }
}

// CoroutineWorker (mejor)
class MyWorker : CoroutineWorker() {
    override suspend fun doWork(): Result {
        // Usa coroutines (no bloquea)
    }
}
```

**Ventaja CoroutineWorker**: Puedes usar `suspend` functions, `withContext()`, etc.

#### **B. Dispatchers.IO**

```kotlin
override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    // ...
}
```

**¿Qué es Dispatcher?**
Define en qué thread pool se ejecuta el código.

**Tipos**:
- `Dispatchers.Main`: UI thread
- `Dispatchers.IO`: I/O operations (red, archivos)
- `Dispatchers.Default`: CPU-intensive (cálculos)

**Nuestro caso**: I/O (leer/escribir archivos ZIP).

#### **C. Result types**

```kotlin
Result.success()   // ✅ Trabajo completado
Result.failure()   // ❌ Falló, no reintentar
Result.retry()     // 🔄 Falló, reintentar según política
```

**WorkManager reintentará automáticamente** si retornas `Result.retry()`.

---

### 2.4 Métodos en WebAppInterface

**Archivo**: `app/src/main/java/com/d4vram/psychologger/MainActivity.kt`

#### **A. createManualBackup()**

```kotlin
@JavascriptInterface
fun createManualBackup(localStorageJson: String): String {
    return try {
        val backupFile = backupManager.createBackupWithData(localStorageJson)

        if (backupFile != null) {
            Toast.makeText(
                context,
                "✅ Backup creado: ${backupFile.name}",
                Toast.LENGTH_LONG
            ).show()
            "OK:${backupFile.name}"
        } else {
            "ERROR: No se pudo crear el backup"
        }
    } catch (e: Exception) {
        "ERROR: ${e.message}"
    }
}
```

**JavaScript**:
```javascript
function createManualBackup() {
    const backupData = {
        substances: substances,
        entries: entries,
        userProfile: userProfile,
        // ...
    };

    const jsonString = JSON.stringify(backupData, null, 2);
    const result = Android.createManualBackup(jsonString);

    if (result.startsWith('OK:')) {
        console.log('Backup creado:', result.substring(3));
    }
}
```

#### **B. exportAudiosZip() y exportAudiosZipEncrypted()**

**Sin cifrar**:
```kotlin
@JavascriptInterface
fun exportAudiosZip(): String {
    val zipFile = backupManager.exportAudioZip(password = null)

    if (zipFile != null) {
        activity.runOnUiThread {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(Intent.createChooser(shareIntent, "📦 Exportar audios"))
        }

        return "OK"
    } else {
        return "ERROR: No se pudo crear el ZIP"
    }
}
```

**Con cifrado**:
```kotlin
@JavascriptInterface
fun exportAudiosZipEncrypted(password: String): String {
    if (password.length < 8) {
        return "ERROR: Contraseña muy corta"
    }

    val zipFile = backupManager.exportAudioZip(password = password)

    if (zipFile != null) {
        // Similar a exportAudiosZip() pero con mensaje diferente
        activity.runOnUiThread {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT,
                    "⚠️ Este archivo está cifrado con AES-256.\\nGuarda la contraseña en un lugar seguro.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(Intent.createChooser(shareIntent, "🔒 Exportar audios cifrados"))
        }

        Toast.makeText(context, "✅ ZIP cifrado creado. Guarda la contraseña!", Toast.LENGTH_LONG).show()
        return "OK"
    } else {
        return "ERROR: No se pudo crear el ZIP cifrado"
    }
}
```

**FileProvider**: Permite compartir archivos privados (`filesDir`, `cacheDir`).

**Sin FileProvider**: Error "file:// URIs are not allowed" (Android 7+).

---

### 2.5 UI de Backups y Exportación

**Archivo**: `app/src/main/assets/index.html`

#### **A. Cards en data-view**

```html
<div class="resource-card">
    <h4>💾 Backup Manual</h4>
    <p>Crear backup completo (datos + audios)</p>
    <button class="btn-primary" onclick="createManualBackup()">
        🗄️ Crear Backup
    </button>
</div>

<div class="resource-card">
    <h4>🎤 Exportar Audios</h4>
    <p>Exportar todas las notas de voz en ZIP</p>
    <button class="btn-primary" onclick="openAudioExportModal()">
        📦 Exportar ZIP
    </button>
</div>
```

#### **B. Modal de Exportación**

```html
<div id="audioExportModal" class="modal">
    <div class="modal-content" style="max-width: 450px;">
        <h3>📦 Exportar Notas de Voz</h3>

        <p>Exporta todas tus notas de voz en un archivo ZIP.
           Opcionalmente puedes cifrarlo con contraseña (AES-256).</p>

        <!-- Checkbox de cifrado -->
        <label>
            <input type="checkbox" id="encryptAudioZip">
            🔒 Cifrar con contraseña (AES-256)
        </label>

        <!-- Campo de contraseña (oculto por defecto) -->
        <div id="passwordGroup" style="display: none;">
            <label>Contraseña (mínimo 8 caracteres)</label>
            <input type="password" id="audioZipPassword" minlength="8">
            <small>⚠️ Guarda esta contraseña en un lugar seguro.</small>
        </div>

        <button onclick="closeModal('audioExportModal')">Cancelar</button>
        <button onclick="exportAudiosZip()">📤 Exportar</button>
    </div>
</div>
```

#### **C. JavaScript**

**Función: openAudioExportModal()**
```javascript
function openAudioExportModal() {
    openModal('audioExportModal');

    const checkbox = document.getElementById('encryptAudioZip');
    const passwordGroup = document.getElementById('passwordGroup');

    // Mostrar/ocultar campo de contraseña
    checkbox.addEventListener('change', function() {
        passwordGroup.style.display = this.checked ? 'block' : 'none';
    });
}
```

**Función: exportAudiosZip()**
```javascript
function exportAudiosZip() {
    const encryptCheckbox = document.getElementById('encryptAudioZip');
    const passwordInput = document.getElementById('audioZipPassword');

    if (encryptCheckbox.checked) {
        // Exportar con cifrado
        const password = passwordInput.value.trim();

        if (password.length < 8) {
            Android.showToast('⚠️ La contraseña debe tener al menos 8 caracteres');
            return;
        }

        const result = Android.exportAudiosZipEncrypted(password);

        if (result === 'OK') {
            closeModal('audioExportModal');
            // Reset form
            encryptCheckbox.checked = false;
            passwordInput.value = '';
            passwordGroup.style.display = 'none';
        }

    } else {
        // Exportar sin cifrar
        const result = Android.exportAudiosZip();

        if (result === 'OK') {
            closeModal('audioExportModal');
            Android.showToast('✅ Audios exportados correctamente');
        }
    }
}
```

---

## 3. Conceptos Aprendidos

### Android/Kotlin

#### **A. MediaRecorder y MediaPlayer**
- Estados y ciclo de vida estrictos
- Configuración de calidad de audio
- Gestión de recursos con `release()`
- Listeners para eventos asíncronos

#### **B. WorkManager**
- `PeriodicWorkRequest`: Tareas periódicas
- `Constraints`: Condiciones para ejecutar
- `CoroutineWorker`: Workers con coroutines
- `Result.success/failure/retry()`

#### **C. Cifrado Criptográfico**
- **AES-256-GCM**: Cifrado autenticado moderno
- **PBKDF2**: Derivación de claves desde contraseñas
- **Salt e IV**: Unicidad y seguridad
- **SecureRandom**: Generador criptográfico

#### **D. Archivos y Almacenamiento**
- `filesDir` vs `cacheDir`
- `ZipOutputStream`: Crear ZIPs
- `FileProvider`: Compartir archivos privados
- Rotación de archivos (mantener N últimos)

#### **E. Threading**
- `Handler + Runnable`: Tareas periódicas
- `Looper.getMainLooper()`: UI thread
- `runOnUiThread`: Ejecutar en UI thread
- `Dispatchers.IO`: Thread pool para I/O

### JavaScript

#### **A. Bridge Android-JS**
- `@JavascriptInterface`: Exponer métodos
- `evaluateJavascript()`: Ejecutar JS desde Android
- Callbacks bidireccionales con `window.onXXX`
- Limitación: Solo tipos primitivos

#### **B. Gestión de Estados**
- State machine (initial/recording/player)
- `setInterval()` para timers
- `addEventListener()` para eventos
- LocalStorage serialization

#### **C. Manipulación del DOM**
- `getElementById()`, `querySelector()`
- `style.display` para mostrar/ocultar
- `classList.add/remove()` para estilos
- Event delegation

### Seguridad

#### **A. OWASP Recommendations**
- PBKDF2 con ≥120,000 iteraciones
- Salt único de 16+ bytes
- AES-256 (no AES-128)
- GCM mode (autenticación integrada)

#### **B. Gestión de Contraseñas**
- Validación mínimo 8 caracteres
- No almacenar contraseña en plaintext
- Wipe password array después de usar
- Advertir al usuario sobre pérdida

---

## 4. Resumen de Archivos

### Nuevos Archivos Creados

```
app/src/main/java/com/d4vram/psychologger/
├── AudioRecorder.kt           (237 líneas) ✨
├── AudioPlayer.kt             (241 líneas) ✨
├── BackupManager.kt           (429 líneas) ✨
└── BackupWorker.kt            (66 líneas)  ✨
```

### Archivos Modificados

```
app/
├── build.gradle.kts           (+1 línea)
└── src/main/
    ├── AndroidManifest.xml    (+1 línea)
    ├── assets/
    │   └── index.html         (+606 líneas)
    └── java/.../MainActivity.kt (+418 líneas)
```

### Estadísticas Totales

- **Líneas de código nuevo**: ~1,999
- **Archivos nuevos**: 4
- **Archivos modificados**: 4
- **Funcionalidades nuevas**: 2 (Audio + Backups)

---

## 5. Cómo Usar las Funcionalidades

### Grabación y Reproducción de Audio

#### **Grabar nota de voz**:
1. Abrir formulario de nueva entrada
2. Scroll hasta "Nota de voz (opcional)"
3. Click **"🎤 Grabar nota de voz"**
4. Hablar (timer muestra duración)
5. Click **"⏹️ Detener"**

#### **Reproducir**:
1. Click **"▶️"** en el reproductor
2. Ver barra de progreso actualizarse
3. Click **"⏸️"** para pausar

#### **Eliminar**:
1. Click **"🗑️"** en el reproductor
2. Confirmar

#### **Compartir**:
1. Click **"📤"** en el reproductor
2. Seleccionar app en ShareSheet

### Auto-Backup (Cada 12 horas)

**Automático**: Se programa al abrir la app por primera vez.

**Ubicación**: `/data/data/com.d4vram.psychologger/files/backups/`

**Formato**: `backup_YYYY-MM-DD_HH-mm-ss.zip`

**Rotación**: Mantiene últimos 7 backups.

### Backup Manual

1. Abrir pestaña **"Datos"** (💾)
2. Scroll hasta **"💾 Backup Manual"**
3. Click **"🗄️ Crear Backup"**
4. Toast confirma: `"✅ Backup creado: backup_2025-01-15_14-30-00.zip"`

### Exportar Audios en ZIP

#### **Sin cifrar**:
1. Abrir pestaña **"Datos"**
2. Click **"📦 Exportar ZIP"**
3. Dejar checkbox desmarcado
4. Click **"📤 Exportar"**
5. ShareSheet se abre → Compartir o guardar

#### **Con cifrado AES-256**:
1. Abrir pestaña **"Datos"**
2. Click **"📦 Exportar ZIP"**
3. Marcar **"🔒 Cifrar con contraseña"**
4. Introducir contraseña (≥8 caracteres)
5. **⚠️ GUARDAR LA CONTRASEÑA** (anotarla)
6. Click **"📤 Exportar"**
7. ShareSheet se abre
8. Archivo exportado: `audios_encrypted_2025-01-15_14-30-00.zip`

---

## 📚 Recursos Adicionales

### Documentación Oficial

- **MediaRecorder**: https://developer.android.com/reference/android/media/MediaRecorder
- **MediaPlayer**: https://developer.android.com/reference/android/media/MediaPlayer
- **WorkManager**: https://developer.android.com/topic/libraries/architecture/workmanager
- **Cipher (AES)**: https://developer.android.com/reference/javax/crypto/Cipher

### Conceptos Criptográficos

- **PBKDF2**: https://en.wikipedia.org/wiki/PBKDF2
- **AES-GCM**: https://en.wikipedia.org/wiki/Galois/Counter_Mode
- **OWASP Key Derivation**: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html

### Kotlin Coroutines

- **Coroutines Guide**: https://kotlinlang.org/docs/coroutines-guide.html
- **Dispatchers**: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/

---

## 🎯 Próximos Pasos Sugeridos

### Mejoras Futuras (Opcional)

1. **Restauración de Backups**: Implementar función para restaurar desde ZIP
2. **Visualización de Forma de Onda**: Mostrar waveform mientras graba
3. **Compresión de Audio**: Reducir tamaño con OPUS codec
4. **Cloud Backup**: Subir a Google Drive automáticamente
5. **Desencriptación**: UI para desencriptar ZIPs cifrados
6. **Notificaciones**: Notificar cuando se complete el backup automático
7. **Trim Audio**: Recortar inicio/final del audio
8. **Mostrar Icono**: Indicador visual en entradas con audio

---

## ✅ Checklist de Verificación

- [x] Permisos de audio en Manifest
- [x] AudioRecorder.kt compilado sin errores
- [x] AudioPlayer.kt con callbacks funcionales
- [x] WebAppInterface con 12+ métodos de audio
- [x] UI de grabación con 3 estados
- [x] Modelo Entry con campo audioPath
- [x] Eliminación de audio al borrar entrada
- [x] WorkManager configurado
- [x] BackupManager con rotación
- [x] BackupWorker programado cada 12h
- [x] UI de backup manual
- [x] Modal de exportación con cifrado opcional
- [x] Build exitoso sin warnings críticos

---

**Fin del documento**

---

*Este documento contiene el 100% de la sesión de desarrollo Project-Based Learning.*
*Guárdalo como referencia para futuras implementaciones similares.*
