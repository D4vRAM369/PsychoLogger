# PsychoLogger 🧠📊

## ¿Qué es PsychoLogger?

PsychoLogger es tu bitácora personal para experiencias psiconáuticas. Una app Android diseñada para quienes buscan documentar y entender sus experiencias con sustancias psicoactivas de manera responsable y segura. 

## ¿Por qué usar PsychoLogger?

### 🔒 Privacidad Total
- Todos tus datos quedan en tu dispositivo - nada se sube a internet
- Protección con huella dactilar o PIN
- Cifrado de grado militar para tus registros más sensibles

### 📝 Registro Completo
- Anota sustancia, dosis, fecha y hora
- Documenta tu "set" (estado mental) y "setting" (ambiente)
- Agrega notas personales para cada experiencia, y/o dale un uso complementario de "diaro psiconáutico"
- Categoriza tus sustancias con colores y emojis

### 📈 Visualiza tus Patrones 
- Ve tu historial en un calendario interactivo
- Estadísticas para entender mejor tus hábitos

### 🔗 Recursos anexos sobre RdR (Reducción de Riesgos) e información ### 

Con enlaces en el Panel de Recursos a web muy conocidas sobre información, reducción de riesgos y demás información útil y valiosa. Por el momento se encuentran adjuntadas:

- Erowid: Base de datos completa sobre sustancias psicoactivas, experiencias y efectos.

- TripSit: Información sobre interacciones, dosificación y asistencia en tiempo real.
- MAPS *(Multidisciplinary Association for Psychedelic Studies)*: Organización sin ánimo de lucro que investiga los potenciales usos médicos, legales y culturales de los psicodélicos.
- PsychonautWiki: Enciclopedia científica de sustancias psicoactivas y sus efectosz

## Características Principales

### 🏠 Pantalla Principal
La app combina lo mejor de Android nativo con una interfaz web fluida. Tu seguridad está garantizada desde el momento en que abres la app.

### 💊 Gestión de Sustancias
Viene con sustancias predefinidas (LSD, Ketamina, Opio) pero puedes agregar las tuyas:
- Psicodélicos 🍄
- Estimulantes/MDMA ⚡  
- Disociativos 🌀
- Depresores 😴

### 📊 Tus Datos, Tu Control
- **Exporta** todo a CSV cuando quieras
- **Importa** datos de otros formatos
- **Migra** fácilmente entre dispositivos
- **Respalda** tu información de forma segura

### 🛡️ Seguridad que Importa
- **Biometría:** Huella, cara, lo que tengas habilitado
- **PIN de respaldo:** Por si falla la biometría  
- **Auto-bloqueo:** Se cierra automáticamente para protegerte
- **Punto de entrada seguro:** Verificación antes de acceder

## Instalación

### Lo que Necesitas
- Android 7.0 o más reciente
- Unos 50MB de espacio
- Sensor biométrico (recomendado pero no obligatorio)

### Cómo Instalar
1. Descarga el APK o clona este repo
2. Si compilas desde código: Android Studio + Gradle
3. Instala en tu dispositivo
4. Configura tu PIN y biometría en el primer uso

## Cómo Usar PsychoLogger

### Primera Vez
1. **Configura tu seguridad** - Elige PIN y activa biometría
2. **Agrega tus sustancias** - Personaliza con colores y emojis  
3. **Ajusta preferencias** - Tiempo de auto-bloqueo, etc.

### Uso Diario
1. **Abre la app** - Autentícate con huella o PIN
2. **Registra tu experiencia** - Sustancia, dosis, contexto, notas
3. **Revisa tus datos** - Calendar, gráficos, estadísticas
4. **Exporta** cuando necesites respaldo

## Tecnología

Construida con tecnologías modernas para Android:
- **Kotlin** como lenguaje principal
- **Jetpack Compose** para interfaces nativas fluidas
- **Material 3** para un diseño limpio y familiar
- **Cifrado AndroidX** para máxima seguridad
- **WebView híbrido** para la interfaz principal

## Importante: Reducción de Daños

PsychoLogger está pensada para:
- ✅ **Educación** sobre sustancias psicoactivas
- ✅ **Reducción de riesgos** mediante documentación
- ✅ **Autoconocimiento** y patrones personales
- ✅ **Investigación responsable**

❌ **NO promovemos el uso recreativo** de ninguna sustancia

❌ **NO sustituye asesoramiento médico** profesional

❌ **NO es para menores de edad**

## Datos Técnicos

### Versión Actual: 1.0
- **Package:** com.d4vram.psychologger
- **Objetivo:** Android 14 (API 36)
- **Mínimo:** Android 7.0 (API 24)
- **Tamaño:** ~25MB instalada

### Estructura de Datos
Tus registros se guardan de forma estructurada:
```
📁 Sustancias (nombre, color, emoji, fecha)
📁 Entradas (sustancia, dosis, fecha, set, setting, notas)
📁 Preferencias (configuración personal)
```

## Privacidad & Seguridad

### Tu Privacidad Es Sagrada
- **Cero telemetría** - No enviamos datos a ningún servidor
- **Local únicamente** - Todo queda en tu teléfono
- **Sin conexiones externas** - La app funciona completamente offline
- **Cifrado robusto** - AES256 para datos sensibles

### Controles de Seguridad
- **Autenticación multicapa** (biométrica + PIN)
- **Hash seguro** de PINs (SHA-256)
- **Auto-lock inteligente** configurable
- **Verificación de integridad** en cada arranque

## Contribuir

¿Quieres mejorar PsychoLogger? Las contribuciones son bienvenidas:
1. Fork del repositorio
2. Crea una rama para tu feature
3. Sigue las convenciones de Kotlin/Android
4. Envía tu PR con descripción detallada

## Soporte

¿Problemas? ¿Sugerencias? ¿Bugs?
- Abre un issue en GitHub
- Contacta al desarrollador
- Revisa la documentación técnica

---

**Recuerda:** Esta herramienta está diseñada para fomentar el uso responsable y la reducción de daños. Siempre infórmate adecuadamente y considera los riesgos antes de experimentar con cualquier sustancia psicoactiva.

*Desarrollado con ❤️ para la comunidad psiconáutica responsable.*