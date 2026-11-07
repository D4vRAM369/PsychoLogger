/*
 * i18n.js — traducción del WebView.
 * La app está escrita en español (idioma fuente). Si el idioma del dispositivo es
 * español no se hace nada; en cualquier otro se traduce al inglés por diccionario
 * (clave = texto en español con espacios colapsados). Traduce nodos de texto y
 * atributos, también los que se añadan después (MutationObserver), y expone t().
 * Los datos guardados (CSV/backups) siguen en español: solo se traduce al mostrar.
 */
(function () {
    'use strict';
    var lang = 'es', tag = '';
    try {
        if (window.Android && Android.getLanguage) { lang = Android.getLanguage(); tag = Android.getLocaleTag(); }
        else { tag = navigator.language || 'es'; lang = tag; }
    } catch (e) { /* sin puente: español */ }
    lang = String(lang || 'es').slice(0, 2).toLowerCase();
    window.I18N_LANG = lang;
    window.I18N_LOCALE = lang === 'es' ? 'es-ES' : (tag || 'en-US');
    if (lang === 'es') { window.t = function (s) { return s; }; return; }

    var debug = false;
    try { debug = !!(window.Android && Android.isDebug && Android.isDebug()); } catch (e) {}

    var PAIRS = [
        // ---- meses / días
        ['Enero', 'January'], ['Febrero', 'February'], ['Marzo', 'March'], ['Abril', 'April'], ['Mayo', 'May'], ['Junio', 'June'],
        ['Julio', 'July'], ['Agosto', 'August'], ['Septiembre', 'September'], ['Octubre', 'October'], ['Noviembre', 'November'], ['Diciembre', 'December'],
        ['Dom', 'Sun'], ['Lun', 'Mon'], ['Mar', 'Tue'], ['Mié', 'Wed'], ['Jue', 'Thu'], ['Vie', 'Fri'], ['Sáb', 'Sat'],
        // ---- navegación y pantallas
        ['Calendario', 'Calendar'], ['Añadir', 'Add'], ['Stats', 'Stats'], ['Recursos', 'Resources'], ['Datos', 'Data'],
        ['Saltar', 'Skip'], ['Siguiente', 'Next'], ['Empezar', 'Start'], ['Cerrar', 'Close'], ['Cancelar', 'Cancel'], ['CANCELAR', 'CANCEL'],
        ['Aceptar', 'OK'], ['Guardar', 'Save'], ['Eliminar', 'Delete'], ['BORRAR', 'DELETE'], ['Editar', 'Edit'], ['Sí', 'Yes'], ['No', 'No'],
        ['Tu compañera para un consumo consciente', 'Your companion for mindful use'],
        ['Tu bitácora personal de experiencias con sustancias.', 'Your personal log of experiences with substances.'],
        ['Privada y sin conexión', 'Private and offline'],
        [': todo se guarda cifrado en tu móvil y nada sale de él.', ': everything is stored encrypted on your phone and nothing leaves it.'],
        ['Sustancias', 'Substances'], ['SUSTANCIAS', 'SUBSTANCES'], ['Experiencias registradas', 'Logged experiences'],
        ['+ Añadir Sustancia', '+ Add Substance'], ['Añadir Sustancia', 'Add Substance'], ['Añadir Nueva Sustancia', 'Add New Substance'],
        ['➕ Añadir Nueva Sustancia', '➕ Add New Substance'], ['Editar sustancia', 'Edit substance'], ['Eliminar sustancia', 'Delete substance'],
        ['Archivar', 'Archive'], ['Archivar una sustancia', 'Archive a substance'], ['Triple tap para archivar', 'Triple tap to archive'],
        ['Ver y restaurar archivadas', 'View and restore archived'], ['No hay sustancias archivadas', 'No archived substances'],
        ['Aquí están las sustancias archivadas con triple-tap en ⋮⋮. No se han borrado, solo están archivadas.', 'Here are the substances archived with a triple tap on ⋮⋮. They have not been deleted, only archived.'],
        ['Nuevo Registro', 'New Entry'], ['Editar Registro', 'Edit Entry'], ['Sustancia *', 'Substance *'], ['Dosis *', 'Dose *'], ['Unidad *', 'Unit *'],
        ['Fecha y Hora *', 'Date and Time *'], ['Establecer fecha y hora', 'Set date and time'], ['Selecciona una sustancia...', 'Select a substance...'],
        ['Seleccionar unidad...', 'Select unit...'], ['Seleccionar...', 'Select...'], ['Set (Estado mental)', 'Set (Mindset)'], ['Setting (Entorno)', 'Setting (Environment)'],
        ['Notas adicionales', 'Additional notes'], ['ROA (vía de administración), efectos esperados, compañía, música, etc...', 'ROA (route of administration), expected effects, company, music, etc...'],
        ['Nombre', 'Name'], ['Cantidad', 'Amount'], ['Adjuntos', 'Attachments'], ['Color Identificativo', 'Identifying Color'],
        ['Emoji Personalizado (opcional)', 'Custom Emoji (optional)'], ['SMILES de la molécula (opcional)', 'Molecule SMILES (optional)'], ['SMILES', 'SMILES'],
        ['💡 Deja vacío para auto-detectar', '💡 Leave empty to auto-detect'], ['Si lo dejas vacío se usa la lista incluida o el emoji', 'If left empty, the built-in list or the emoji is used'],
        ['Estilo de iconos', 'Icon style'], ['Moléculas y SMILES', 'Molecules and SMILES'], ['Sin nombre', 'No name'], ['Sin sustancia', 'No substance'], ['Sin set', 'No set'], ['Sin setting', 'No setting'],
        // ---- unidades / vías
        ['Comprimidos', 'Tablets'], ['Gotas', 'Drops'], ['Polvo', 'Powder'], ['Líquido', 'Liquid'], ['Vapor', 'Vapor'], ['Cristal', 'Crystal'], ['Píldoras', 'Pills'],
        ['1/2 pastilla', '1/2 pill'], ['1/4 pastilla', '1/4 pill'], ['1/2 seta', '1/2 mushroom'], ['1/2 tripi', '1/2 tab'], ['1/4 tripi', '1/4 tab'], ['1/8 tripi', '1/8 tab'],
        ['aplicación', 'application'], ['inhalación', 'inhalation'], ['raya/línea', 'line'], ['Café frío', 'Cold coffee'],
        ['Oral', 'Oral'], ['Nasal', 'Nasal'], ['Inhalado', 'Inhaled'], ['Inyectado', 'Injected'], ['Sublingual', 'Sublingual'],
        ['PSICODÉLICOS', 'PSYCHEDELICS'], ['ESTIMULANTES/MDMA', 'STIMULANTS/MDMA'], ['HONGOS/PLANTAS', 'MUSHROOMS/PLANTS'], ['LÍQUIDOS', 'LIQUIDS'], ['POLVO/CRISTAL', 'POWDER/CRYSTAL'], ['OTRAS', 'OTHER'],
        // ---- sustancias comunes
        ['Anfetamina', 'Amphetamine'], ['Cafeína', 'Caffeine'], ['Café', 'Coffee'], ['Cocaína', 'Cocaine'], ['Codeína', 'Codeine'], ['Fentanilo', 'Fentanyl'], ['Hachís', 'Hashish'],
        ['Heroina', 'Heroin'], ['Heroína', 'Heroin'], ['Ketamina', 'Ketamine'], ['Marihuana', 'Marijuana'], ['Mescalina', 'Mescaline'], ['Morfina', 'Morphine'], ['Nicotina', 'Nicotine'],
        ['Opio', 'Opium'], ['Psilocibina', 'Psilocybin'], ['Setas', 'Mushrooms'], ['Benzodiazepinas', 'Benzodiazepines'], ['Meth', 'Meth'],
        // ---- sets / settings
        ['Aburrido', 'Bored'], ['Ansioso', 'Anxious'], ['Bueno', 'Good'], ['Creativo', 'Creative'], ['Curioso', 'Curious'], ['Desanimado', 'Down'], ['Emocionado', 'Excited'],
        ['Estresado', 'Stressed'], ['Excelente', 'Excellent'], ['Introspectivo', 'Introspective'], ['Neutral', 'Neutral'], ['Triste', 'Sad'], ['Colocado', 'High'], ['Viaje', 'Trip'],
        ['Ceremonia', 'Ceremony'], ['Con amigos', 'With friends'], ['Concierto', 'Concert'], ['En casa', 'At home'], ['En pareja', 'With partner'], ['Festival', 'Festival'],
        ['Fiesta/Evento', 'Party/Event'], ['Meditación/Yoga', 'Meditation/Yoga'], ['Naturaleza', 'Nature'], ['Paseo/Calle', 'Walk/Street'], ['Programando/Estudiando', 'Coding/Studying'],
        ['Seriefilo, pelis o música', 'Series, movies or music'], ['Solo/a', 'Alone'],
        ['Añadir entorno personalizado...', 'Add custom environment...'], ['Añadir estado mental personalizado...', 'Add custom mindset...'], ['Añadir unidad personalizada...', 'Add custom unit...'],
        ['Editar entorno', 'Edit environment'], ['Editar entorno:', 'Edit environment:'], ['Editar estado mental', 'Edit mindset'], ['Editar estado mental:', 'Edit mindset:'], ['Editar unidad', 'Edit unit'],
        ['Eliminar entorno', 'Delete environment'], ['Eliminar estado mental', 'Delete mindset'], ['Eliminar unidad', 'Delete unit'],
        ['El nombre de la unidad no puede exceder 25 caracteres.', 'The unit name cannot exceed 25 characters.'], ['El nombre del entorno no puede exceder 25 caracteres.', 'The environment name cannot exceed 25 characters.'],
        ['El nombre del estado mental no puede exceder 25 caracteres.', 'The mindset name cannot exceed 25 characters.'],
        ['Esta unidad personalizada ya existe en esta categoría.', 'This custom unit already exists in this category.'], ['Este entorno personalizado ya existe.', 'This custom environment already exists.'],
        ['Este estado mental personalizado ya existe.', 'This custom mindset already exists.'], ['Esta sustancia ya existe', 'This substance already exists'],
        ['Ya existe un entorno con ese nombre.', 'An environment with that name already exists.'], ['Ya existe un estado mental con ese nombre.', 'A mindset with that name already exists.'],
        ['Ya existe una unidad con ese nombre en esta categoría.', 'A unit with that name already exists in this category.'],
        ['Por favor ingresa el nombre de la unidad personalizada.', 'Please enter the custom unit name.'], ['Por favor ingresa el nombre del entorno personalizado.', 'Please enter the custom environment name.'],
        ['Por favor ingresa el nombre del estado mental personalizado.', 'Please enter the custom mindset name.'],
        ['Por favor, introduce un nombre para la sustancia', 'Please enter a name for the substance'], ['Por favor, introduce una dosis válida mayor a 0', 'Please enter a valid dose greater than 0'],
        ['Por favor, selecciona una fecha y hora válida', 'Please select a valid date and time'], ['Por favor, selecciona una sustancia válida', 'Please select a valid substance'],
        ['Por favor, selecciona una unidad válida', 'Please select a valid unit'], ['⚠️ Dosis Inválida', '⚠️ Invalid Dose'], ['⚠️ Primero escribe el nombre de la sustancia', '⚠️ Type the substance name first'],
        ['✅ Sustancia Añadida', '✅ Substance Added'], ['❌ Sustancia no encontrada', '❌ Substance not found'], ['❌ Registro no encontrado', '❌ Entry not found'],
        ['❌ Error: Registro no encontrado para actualizar.', '❌ Error: Entry to update not found.'],
        ['No hay registros para este día. ¿Quieres añadir uno?', 'There are no entries for this day. Do you want to add one?'], ['📅 Día sin Registros', '📅 Day with No Entries'],
        ['➕ Añadir Registro para este Día', '➕ Add Entry for this Day'], ['🗑️ Confirmar Eliminación', '🗑️ Confirm Deletion'],
        ['\n\n⚠️ Esta sustancia tiene registros asociados. Si la eliminas, también se eliminarán todos sus registros.', '⚠️ This substance has associated entries. If you delete it, all its entries will also be deleted.'],
        ['⚠️ Esta sustancia tiene registros asociados. Si la eliminas, también se eliminarán todos sus registros.', '⚠️ This substance has associated entries. If you delete it, all its entries will also be deleted.'],
        ['¿Estás seguro de que quieres eliminar este registro?', 'Are you sure you want to delete this entry?'],
        // ---- notas de voz / fotos
        ['Nota de voz', 'Voice note'], ['🎤 Nota de voz', '🎤 Voice note'], ['🎤 Grabar nota de voz', '🎤 Record voice note'], ['Nota de voz lista', 'Voice note ready'], ['🎤 Nota de voz lista', '🎤 Voice note ready'],
        ['🎤 Nota de voz lista para este registro', '🎤 Voice note ready for this entry'], ['Sin nota de voz', 'No voice note'], ['🎤 Sin nota de voz', '🎤 No voice note'], ['🗑️ Nota de voz eliminada', '🗑️ Voice note deleted'],
        ['Grabando...', 'Recording...'], ['Pulsa para grabar o escuchar', 'Tap to record or listen'], ['Pulsa para escuchar o eliminar', 'Tap to listen or delete'],
        ['Graba, escucha o elimina la nota de voz asociada a este registro.', 'Record, listen to or delete the voice note linked to this entry.'], ['¿Eliminar esta nota de voz?', 'Delete this voice note?'],
        ['No hay ninguna nota de voz para guardar.', 'There is no voice note to save.'], ['Audio grabado:', 'Recorded audio:'], ['Solicitando permiso de micrófono...', 'Requesting microphone permission...'],
        ['Permiso de micrófono concedido, iniciando grabación...', 'Microphone permission granted, starting recording...'], ['Permiso de cámara concedido, abriendo cámara...', 'Camera permission granted, opening camera...'],
        ['Foto / Imagen', 'Photo / Image'], ['Foto adjunta', 'Attached photo'], ['📷 Foto', '📷 Photo'], ['Sin imagen adjunta', 'No attached image'], ['Imagen adjunta lista', 'Attached image ready'],
        ['📷 Imagen lista para este registro', '📷 Image ready for this entry'], ['Pulsa para verla o cambiarla', 'Tap to view or change it'], ['Captura o selecciona una imagen', 'Take or select an image'],
        ['🖼️ Elegir de galería', '🖼️ Choose from gallery'], ['No hay imagen adjunta todavía', 'No attached image yet'], ['No hay ninguna imagen adjunta todavía.', 'There is no attached image yet.'],
        ['No hay ninguna imagen adjunta que compartir.', 'There is no attached image to share.'], ['No hay ninguna imagen adjunta que eliminar.', 'There is no attached image to delete.'],
        ['No hay ninguna imagen para guardar.', 'There is no image to save.'], ['¿Eliminar la imagen adjunta?', 'Delete the attached image?'], ['No se pudo eliminar la foto.', 'Could not delete the photo.'],
        ['No se pudo cargar la vista previa', 'Could not load the preview'], ['❌ Error al capturar foto', '❌ Error capturing photo'],
        ['Añade una foto para complementar este registro. Puedes tomarla ahora o elegirla desde tu galería.', 'Add a photo to complement this entry. You can take it now or choose it from your gallery.'],
        // ---- stats
        ['ene', 'Jan'], ['feb', 'Feb'], ['mar', 'Mar'], ['abr', 'Apr'], ['may', 'May'], ['jun', 'Jun'], ['jul', 'Jul'], ['ago', 'Aug'], ['sep', 'Sep'], ['oct', 'Oct'], ['nov', 'Nov'], ['dic', 'Dec'], ['Estadísticas y Análisis', 'Statistics and Analysis'], ['Registra una experiencia', 'Log an experience'], ['Racha actual', 'Current streak'], ['Mejor racha', 'Best streak'],
        ['Días activos', 'Active days'], ['Días sin consumo', 'Days without use'], ['días consecutivos', 'consecutive days'], ['récord histórico', 'all-time record'], ['desde último registro', 'since last entry'],
        ['Sin datos suficientes', 'Not enough data'], ['No hay registros para analizar.', 'There are no entries to analyze.'], ['❌ No hay registros para analizar', '❌ There are no entries to analyze'],
        ['📊 Frecuencia de consumo por sustancia', '📊 Use frequency by substance'], ['📈 Timeline mensual de registros', '📈 Monthly timeline of entries'],
        ['🧠 Distribución de Set (estado mental)', '🧠 Set (mindset) distribution'], ['🌐 Distribución de Setting (entorno)', '🌐 Setting (environment) distribution'],
        ['Registra experiencias para ver tendencias por sustancia, set, setting y frecuencia mensual.', 'Log experiences to see trends by substance, set, setting and monthly frequency.'],
        ['REGISTROS', 'ENTRIES'], ['BITÁCORA TEMPORAL', 'TIMELINE'], ['Bitácora Temporal', 'Timeline'],
        // ---- datos / backup
        ['Gestión de Datos', 'Data Management'], ['Backup y restauración', 'Backup and restore'], ['Para mover o recuperar toda la app', 'To move or recover the whole app'],
        ['💾 Backup Manual + Auto-Backup', '💾 Manual Backup + Auto-Backup'], ['Crear backup completo con o sin cifrado, configurar Auto-Backup y elegir directorio.', 'Create a full backup with or without encryption, set up Auto-Backup and choose a folder.'],
        ['⚙️ Abrir Backup Avanzado', '⚙️ Open Advanced Backup'], ['Abrir Backup Avanzado', 'Open Advanced Backup'], ['📥 Importar Backup', '📥 Import Backup'], ['Importar Backup', 'Import Backup'],
        ['Restaura datos, audios y fotos desde un ZIP previamente exportado.', 'Restore data, audio and photos from a previously exported ZIP.'], ['Exportación e importación manual', 'Manual export and import'],
        ['CSV y archivos adjuntos por separado', 'CSV and attachments separately'], ['Descarga registros, sustancias, sets, settings y sustancias archivadas en CSV.', 'Download entries, substances, sets, settings and archived substances as CSV.'],
        ['Carga datos desde un CSV compatible con PsychoLogger.', 'Load data from a PsychoLogger-compatible CSV.'], ['Exporta todas las notas de voz en ZIP.', 'Export all voice notes as a ZIP.'],
        ['Descarga todas las imágenes adjuntas en un ZIP.', 'Download all attached images as a ZIP.'], ['🗂️ Exportar Fotos', '🗂️ Export Photos'],
        ['Exporta todas tus notas de voz en un archivo ZIP. Opcionalmente puedes cifrarlo con contraseña (AES-256).', 'Export all your voice notes in a ZIP file. You can optionally encrypt it with a password (AES-256).'],
        ['📦 Exportar Notas de Voz', '📦 Export Voice Notes'], ['🔒 Cifrar con contraseña (AES-256)', '🔒 Encrypt with password (AES-256)'], ['Contraseña (mínimo 8 caracteres)', 'Password (minimum 8 characters)'],
        ['Introduce una contraseña fuerte', 'Enter a strong password'], ['⚠️ Guarda esta contraseña en un lugar seguro. Sin ella no podrás desencriptar el archivo.', '⚠️ Keep this password in a safe place. Without it you will not be able to decrypt the file.'],
        ['Exporta todas tus fotos en un archivo ZIP. Opcionalmente puedes cifrarlo con contraseña (AES-256).', 'Export all your photos in a ZIP file. You can optionally encrypt it with a password (AES-256).'], ['Exporta todas tus notas de voz en un archivo ZIP. Opcionalmente puedes cifrarlo con contraseña (AES-256).', 'Export all your voice notes in a ZIP file. You can optionally encrypt it with a password (AES-256).'],
        ['La contraseña debe tener al menos 8 caracteres', 'Password must be at least 8 characters'], ['Preparando archivos…', 'Preparing files…'], ['Preparando y cifrando archivos…', 'Preparing and encrypting files…'],
        ['Exportación lista. Elige dónde guardarla o compartirla.', 'Export ready. Choose where to save or share it.'], ['No hay archivos para exportar.', 'There are no files to export.'], ['Ya hay una exportación en curso', 'An export is already in progress'],
        ['Sin memoria suficiente para exportar. Cierra otras apps e inténtalo de nuevo.', 'Not enough memory to export. Close other apps and try again.'], ['No se pudo completar la exportación.', 'The export could not be completed.'],
        ['⚠️ La contraseña debe tener al menos 8 caracteres', '⚠️ Password must be at least 8 characters'], ['Ordena tus datos antes de borrar nada', 'Tidy your data before deleting anything'],
        ['Limpia texto mal guardado, fusiona Sets/Settings duplicados y recupera tus sugerencias desde el historial.', 'Clean badly saved text, merge duplicate Sets/Settings and recover your suggestions from history.'],
        ['Reconstruyendo sugerencias con emojis...', 'Rebuilding suggestions with emojis...'], ['¿Qué hace la limpieza?', 'What does the cleanup do?'],
        ['Se fusionarán por nombre normalizado. Se conservará como nombre final la variante con más apariciones en estadísticas.', 'They will be merged by normalized name. The variant with the most occurrences in the statistics will be kept as the final name.'],
        ['Los campos corruptos se vaciarán (quedarán como "Sin sustancia", "Sin set", "Sin setting"). Los registros NO se eliminan.', 'Corrupted fields will be emptied (they will become "No substance", "No set", "No setting"). Entries are NOT deleted.'],
        ['¿Desea corregirlos antes de exportar?', 'Do you want to fix them before exporting?'], ['✅ Análisis completado: Sin problemas', '✅ Analysis complete: No problems'], ['🔍 Iniciando análisis...', '🔍 Starting analysis...'],
        ['🗑️ Eliminar Todos los Datos', '🗑️ Delete All Data'], ['Limpiar Todo', 'Clear All'], ['Elimina todos los datos almacenados. Haz un backup antes de usarlo.', 'Deletes all stored data. Make a backup before using it.'],
        ['¿Estás seguro de que quieres eliminar todos los datos? Esta acción no se puede deshacer.', 'Are you sure you want to delete all data? This action cannot be undone.'],
        ['Todos los datos han sido eliminados', 'All data has been deleted'], ['Cuidado de tus datos', 'Taking care of your data'],
        ['Aún no se ha generado un backup.', 'No backup has been generated yet.'], ['Backup creado:', 'Backup created:'], ['Automático', 'Automatic'], ['Manual', 'Manual'],
        ['No hay datos para exportar', 'There is no data to export'], ['✅ Exportación Completa', '✅ Export Complete'], ['❌ El archivo está vacío', '❌ The file is empty'], ['❌ Error al leer archivo', '❌ Error reading file'], ['📥 Importación completada', '📥 Import completed'],
        ['¿Quieres importar un archivo de backup ahora?', 'Do you want to import a backup file now?'], ['Importar backup solo está disponible en Android.', 'Importing a backup is only available on Android.'],
        ['La función de backup manual solo está disponible en Android.', 'Manual backup is only available on Android.'], ['Esta función solo está disponible en Android.', 'This feature is only available on Android.'],
        ['ℹ️ Información', 'ℹ️ Information'], ['❌ Error al importar', '❌ Import error'], ['❌ Error al importar backup', '❌ Error importing backup'], ['❌ Error al leer el archivo', '❌ Error reading the file'],
        ['❌ No se seleccionó ningún archivo', '❌ No file selected'], ['❌ Solo se permiten archivos .csv', '❌ Only .csv files are allowed'], ['No se pudo importar el backup seleccionado.', 'Could not import the selected backup.'],
        ['No se pudo aplicar el backup:', 'Could not apply the backup:'], ['Estructura de datos incompleta', 'Incomplete data structure'],
        ['Esta acción reemplazará tus datos actuales (sustancias, registros, adjuntos).', 'This action will replace your current data (substances, entries, attachments).'],
        ['Te recomendamos crear un backup manual antes de continuar.', 'We recommend creating a manual backup before continuing.'], ['¿Qué hacer con los datos actuales?', 'What do you want to do with the current data?'],
        ['❌ Error de Inicialización', '❌ Initialization Error'], ['❌ Error del Sistema', '❌ System Error'], ['Error al cargar lista', 'Error loading list'],
        ['No se pudo abrir el modal. Intenta recargar la app.', 'Could not open the dialog. Try reloading the app.'],
        ['ERROR:', 'ERROR:'], ['Error:', 'Error:'], ['Error al grabar:', 'Error recording:'], ['Error al detener:', 'Error stopping:'], ['Error al reproducir:', 'Error playing:'], ['Error al eliminar:', 'Error deleting:'],
        ['Error al importar:', 'Error importing:'], ['Error al crear backup:', 'Error creating backup:'], ['Error en backup:', 'Backup error:'], ['Error al exportar audios:', 'Error exporting audio:'],
        ['Error exportando fotos:', 'Error exporting photos:'], ['Error guardando datos:', 'Error saving data:'], ['Error aplicando backup:', 'Error applying backup:'],
        ['Contraseña muy corta', 'Password too short'], ['Archivo no existe', 'File does not exist'], ['Archivo no encontrado', 'File not found'], ['Nombre de archivo inválido', 'Invalid file name'],
        ['No se pudo crear el backup', 'Could not create the backup'], ['No se pudo crear el ZIP', 'Could not create the ZIP'], ['No se pudo crear el ZIP de fotos', 'Could not create the photos ZIP'],
        ['No se pudo crear el ZIP cifrado', 'Could not create the encrypted ZIP'], ['No se pudo reproducir', 'Could not play'],
        ['No se pudo guardar la foto', 'Could not save the photo'], ['Ya hay una operación de imagen en curso', 'An image operation is already in progress'],
        ['No se pudo iniciar la cámara', 'Could not start the camera'], ['No se pudo abrir la galería', 'Could not open the gallery'], ['Error al importar imagen', 'Error importing image'],
        // ---- recursos
        ['Recursos y Harm Reduction', 'Resources and Harm Reduction'], ['Biblioteca Recomendada', 'Recommended Library'], ['Biblioteca', 'Library'], ['Enciclopedia', 'Encyclopedia'], ['Investigación', 'Research'],
        ['Interacciones', 'Interactions'], ['Introducción', 'Introduction'], ['Base de datos', 'Database'], ['Evidencia', 'Evidence'], ['Cuando quieras repasarlo', 'Whenever you want to review it'], ['Ver introducción', 'View introduction'],
        ['Bibliografía para comprender desde la ciencia, la historia y la reducción de riesgos.', 'Bibliography to understand through science, history and risk reduction.'],
        ['Bibliografía esencial sobre psiconáuticos, neurociencia y reducción de riesgos', 'Essential bibliography on psychonautics, neuroscience and risk reduction'],
        ['Fuentes externas para contrastar información, revisar riesgos y ampliar contexto antes de tomar decisiones.', 'External sources to cross-check information, review risks and broaden context before making decisions.'],
        ['Base de datos completa sobre sustancias psicoactivas, experiencias y efectos', 'Complete database on psychoactive substances, experiences and effects'],
        ['Enciclopedia científica de sustancias psicoactivas y sus efectos', 'Scientific encyclopedia of psychoactive substances and their effects'],
        ['Información sobre interacciones, dosificación y asistencia en tiempo real', 'Information on interactions, dosing and real-time assistance'],
        ['Análisis científico objetivo sobre drogas y políticas.', 'Objective scientific analysis of drugs and policy.'], ['No todas las drogas son iguales', 'Not all drugs are the same'],
        ['Organización independiente líder en información y educación científica sobre drogas, que ofrece recursos y datos basados en evidencia para promover decisiones informadas y políticas sensatas', 'Independent organization leading in scientific drug information and education, offering evidence-based resources and data to promote informed decisions and sensible policies'],
        ['Organización sin ánimo de lucro que investiga los potenciales usos médicos, legales y culturales de los psicodélicos', 'Non-profit organization researching the potential medical, legal and cultural uses of psychedelics'],
        ['Obra fundamental sobre feniletilaminas y experiencias psicodélicas.', 'Fundamental work on phenethylamines and psychedelic experiences.'], ['Segunda parte dedicada a triptaminas y sus efectos.', 'Second part dedicated to tryptamines and their effects.'],
        ['Plantas psicoactivas y evolución humana.', 'Psychoactive plants and human evolution.'], ['Drogas enteógenas, fuentes vegetales e historia.', 'Entheogenic drugs, plant sources and history.'],
        ['Viajes seguros y terapéuticos.', 'Safe and therapeutic journeys.'], ['Tratamiento para depresión resistente.', 'Treatment for resistant depression.'], ['Ketamina: el nuevo milagro médico', 'Ketamine: the new medical miracle'],
        ['Guía del explorador psicodélico', 'The Psychedelic Explorer\'s Guide'], ['LSD, MDMA, Psilocibina...', 'LSD, MDMA, Psilocybin...'],
        ['💡 Estos libros son recomendaciones educativas para comprender mejor el tema desde una perspectiva científica, objetiva y de conocimiento, y no como una incitación al consumo.', '💡 These books are educational recommendations to better understand the subject from a scientific, objective and knowledge-based perspective, and not as an incitement to use.'],
        // ---- acerca de / proyecto
        ['Acerca del proyecto', 'About the project'], ['Autor:', 'Author:'], ['Ver Repositorio', 'View Repository'], ['Función secreta', 'Secret feature'], ['Hecho con amor por D4vRAM', 'Made with love by D4vRAM'],
        ['Esta aplicación ha sido desarrollada por', 'This app was developed by'], ['Aprendizaje Basado en Proyectos · Comunidad open source · IA como profesora y copiloto', 'Project-Based Learning · Open source community · AI as teacher and copilot'],
        ['siguiendo un método de Aprendizaje Basado en Proyectos (ABP). Nació como proyecto personal y de exploración constante, iterando con la IA como guía y compañera de código para compartir una herramienta útil con la comunidad psiconáutica responsable. Es software libre y se mantiene con cariño por quienes creen en la cultura abierta ❤️', 'following a Project-Based Learning (PBL) method. It started as a personal project of constant exploration, iterating with AI as a guide and coding partner to share a useful tool with the responsible psychonaut community. It is free software, lovingly maintained by those who believe in open culture ❤️'],
        ['Si este proyecto te resulta valioso y quieres apoyar su evolución, puedes invitarme a un café o hacer el donativo que quieras/puedas. Cada aporte ayuda a seguir mejorando PsychoLogger, documentar nuevas funciones y crear más recursos para la comunidad.', 'If you find this project valuable and want to support its evolution, you can buy me a coffee or donate whatever you want/can. Every contribution helps keep improving PsychoLogger, document new features and create more resources for the community.'],
        ['Cada sustancia muestra su molécula. Si la tuya no sale, busca su', 'Each substance shows its molecule. If yours does not appear, look up its'],
        ['(el texto que describe la molécula) en PubChem desde el navegador, cópialo y pégalo al editar la sustancia. En Ajustes puedes volver a los emojis con', '(the text describing the molecule) on PubChem in your browser, copy it and paste it when editing the substance. In Settings you can go back to emojis with'],
        ['Cópialo de PubChem u otra fuente (búscalo fuera de la app).', 'Copy it from PubChem or another source (look it up outside the app).'],
        ['No se pudo dibujar ese SMILES (se usará el emoji)', 'Could not draw that SMILES (the emoji will be used)'],
        [', y añade tus notas. Después la verás en el calendario y en las estadísticas.', ', and add your notes. Then you will see it in the calendar and in the statistics.'],
        ['Elige la sustancia, la dosis, el', 'Choose the substance, the dose, the'], ['Abre el', 'Open the'], ['Toca', 'Tap'],
        ['el icono ⋮⋮ de una sustancia. Desaparece del calendario, las estadísticas, el contador y el CSV. Tus registros', 'the ⋮⋮ icon of a substance. It disappears from the calendar, the statistics, the counter and the CSV. Your entries'],
        ['se conservan', 'are kept'], ['en las copias de seguridad.', 'in backups.'], ['de Ajustes y pulsa', 'in Settings and tap'], ['junto con sus registros', 'together with its entries'], ['con registros', 'with entries'],
        ['5 veces seguidas', '5 times in a row'], ['Viernes, 17 de enero 2026', 'Friday, January 17, 2026'], ['Enero 2025', 'January 2025'], ['Enero 2026', 'January 2026'],
        ['Hace 2 días', '2 days ago'], ['📆 Hace 2 días', '📆 2 days ago'], ['Ver y restaurar archivadas', 'View and restore archived'], ['Sin nota de voz', 'No voice note'],
        ['⏱️ Ahora', '⏱️ Now'], ['📆 Ayer', '📆 Yesterday'], ['↩️ Hace 1h', '↩️ 1h ago'], ['🗑️ Borrado completo', '🗑️ Full delete'],
        ['📤 Compartir', '📤 Share'], ['🧹 Datos Corruptos Encontrados', '🧹 Corrupted Data Found'], ['⏹️ Detener', '⏹️ Stop'], ['🗑️ Eliminar', '🗑️ Delete'],
        ['✓ ESTABLECER', '✓ SET'], ['📋 Experiencias registradas', '📋 Logged experiences'], ['📤 Exportar', '📤 Export'], ['🎤 Exportar Audios', '🎤 Export Audio'],
        ['📤 Exportar CSV', '📤 Export CSV'], ['📸 Exportar Fotos', '📸 Export Photos'], ['📦 Exportar ZIP', '📦 Export ZIP'], ['📷 Foto / Imagen', '📷 Photo / Image'],
        ['💾 Guardar', '💾 Save'], ['💾 Guardar Registro', '💾 Save Entry'], ['Guardar Registro', 'Save Entry'], ['📁 Importar CSV', '📁 Import CSV'],
        ['📚 Libros Recomendados', '📚 Recommended Books'], ['🧹 Limpiar Ahora', '🧹 Clean Now'], ['🔍 Revisar', '🔍 Review'], ['🔎 Revisar datos', '🔎 Review data'],
        ['📁 Seleccionar CSV', '📁 Select CSV'], ['📅 Seleccionar Fecha y Hora', '📅 Select Date and Time'], ['💡 Sugerir', '💡 Suggest'],
        ['📦 Sustancias Archivadas', '📦 Archived Substances'], ['📸 Tomar foto', '📸 Take photo'], ['👁️ Ver', '👁️ View'],
        ['💡 Tip: Incluye ROA (oral, sublingual, nasal, etc.), testing, pureza estimada, etc.', '💡 Tip: Include ROA (oral, sublingual, nasal, etc.), testing, estimated purity, etc.'],
        ['«Acerca del proyecto»', '“About the project”'],
        ['el chip «Acerca del proyecto». Se abre la lista de archivadas; al restaurar una vuelve a funcionar como cualquier otra.', 'the “About the project” chip. The archived list opens; once you restore one it works like any other again.'],
        ['g (gramos)', 'g (grams)'], ['mg (miligramos)', 'mg (milligrams)'], ['ml (mililitros)', 'ml (milliliters)'], ['μg (microgramos)', 'μg (micrograms)'],
        ['pastilla completa', 'whole pill'], ['seta completa', 'whole mushroom'], ['tripi completo', 'whole tab'], ['gotas', 'drops'], ['raya', 'line'], ['dosis', 'dose'],
        ['punto/bomba', 'dab/bomb'], ['tirito', 'small line'], ['vial', 'vial'], ['calada', 'puff'], ['cookie', 'cookie'], ['cookies', 'cookies'], ['engranaje', 'gear'],
        ['set', 'set'], ['setting', 'setting'], ['tres veces seguidas', 'three times in a row'],
        ['Rastreo:', 'Tracking:']
    ];

    var D = Object.create(null), OUT = Object.create(null);
    function norm(s) { return String(s).replace(/\s+/g, ' ').trim(); }
    PAIRS.forEach(function (p) { D[norm(p[0])] = p[1]; OUT[norm(p[1])] = 1; });

    // Textos con datos variables: [regex sobre texto normalizado, función->inglés]
    var RULES = [
        [/^(\d+) sustancias?$/, function (m) { return m[1] + ' substance' + (m[1] === '1' ? '' : 's'); }],
        [/^(\d+) registros?$/, function (m) { return m[1] + ' entr' + (m[1] === '1' ? 'y' : 'ies'); }],
        [/^(\d+) días?$/, function (m) { return m[1] + ' day' + (m[1] === '1' ? '' : 's'); }],
        [/^Hace (\d+) días?$/, function (m) { return m[1] + ' day' + (m[1] === '1' ? '' : 's') + ' ago'; }],
        [/^✅ Importado: (\d+) sustancias?, (\d+) registros?$/, function (m) { return '✅ Imported: ' + m[1] + ' substance' + (m[1] === '1' ? '' : 's') + ', ' + m[2] + ' entr' + (m[2] === '1' ? 'y' : 'ies'); }],
        [/^❌ Error: (.+)$/, function (m) { return '❌ Error: ' + tr(m[1]); }],
        [/^❌ Error al (leer archivo|exportar): (.+)$/, function (m) { return '❌ Error ' + (m[1] === 'exportar' ? 'exporting' : 'reading file') + ': ' + tr(m[2]); }],
        [/^📁 Leyendo archivo: (.+)$/, function (m) { return '📁 Reading file: ' + m[1]; }],
        [/^Error: (.+)$/, function (m) { return 'Error: ' + tr(m[1]); }],
        [/^ERROR: (.+)$/, function (m) { return 'ERROR: ' + tr(m[1]); }],
        [/^OK: (.+)$/, function (m) { return 'OK: ' + m[1]; }]
    ];

    var missing = Object.create(null);

    function tr(s) {
        if (!s) return s;
        var m = /^(\s*)([\s\S]*?)(\s*)$/.exec(s);
        var key = norm(m[2]);
        if (!key) return s;
        var v = D[key];
        if (v === undefined) {
            for (var i = 0; i < RULES.length; i++) {
                var r = RULES[i][0].exec(key);
                if (r) { v = RULES[i][1](r); break; }
            }
        }
        if (v === undefined) {
            // "🔥 Racha actual": prefijo de emoji/símbolos + texto traducible
            var ep = /^([^\p{L}\p{N}]+?)\s+(\S[\s\S]*)$/u.exec(key);
            if (ep) { var rest = tr(ep[2]); if (rest !== ep[2]) v = ep[1] + ' ' + rest; }
        }
        if (v === undefined) {
            if (debug && /[A-Za-zÁ-úñ]{3}/.test(key) && !OUT[key] && !missing[key]) {
                missing[key] = 1; console.log('[i18n-missing] ' + key);
            }
            return s;
        }
        return m[1] + v + m[3];
    }
    window.t = tr;

    var ATTRS = ['placeholder', 'title', 'aria-label', 'alt', 'data-tooltip'];
    var SKIP = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, CODE: 1, PRE: 1 };

    function translateElementAttrs(el) {
        for (var i = 0; i < ATTRS.length; i++) {
            var a = ATTRS[i];
            if (el.hasAttribute && el.hasAttribute(a)) {
                var o = el.getAttribute(a), n = tr(o);
                if (n !== o) el.setAttribute(a, n);
            }
        }
    }

    function walk(root) {
        if (!root) return;
        if (root.nodeType === 3) { var n = tr(root.nodeValue); if (n !== root.nodeValue) root.nodeValue = n; return; }
        if (root.nodeType !== 1 && root.nodeType !== 9 && root.nodeType !== 11) return;
        if (root.nodeType === 1) { translateElementAttrs(root); if (SKIP[root.tagName]) return; }
        var kids = root.childNodes;
        for (var i = 0; i < kids.length; i++) walk(kids[i]);
    }

    var busy = false;
    function start() {
        document.title = tr(document.title);
        document.documentElement.lang = lang;
        walk(document.body);
        new MutationObserver(function (muts) {
            if (busy) return; busy = true;
            try {
                muts.forEach(function (mu) {
                    if (mu.type === 'childList') mu.addedNodes.forEach(walk);
                    else if (mu.type === 'characterData') walk(mu.target);
                    else if (mu.type === 'attributes') translateElementAttrs(mu.target);
                });
            } finally { busy = false; }
        }).observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRS });
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();

    // alert/confirm/prompt nativos del WebView
    ['alert', 'confirm', 'prompt'].forEach(function (fn) {
        var orig = window[fn];
        window[fn] = function (msg, def) { return orig.call(window, tr(String(msg)), def); };
    });

    // Toasts nativos enviados desde JS (Android.showToast): traducir antes de cruzar al puente
    try {
        var A = window.Android;
        if (A && A.showToast) {
            var origToast = A.showToast.bind(A);
            A.showToast = function (msg) { return origToast(tr(String(msg))); };
        }
    } catch (e) { /* ponytail: si el puente es de solo lectura, el toast queda en español */ }
})();
