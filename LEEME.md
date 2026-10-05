# AutoRespuesta IA — respuestas automáticas de WhatsApp con IA

App Android (Kotlin) que responde tus mensajes de WhatsApp y WhatsApp Business de forma automática y en tiempo real, como AutoResponder (versión Premium), pero con IA integrada y sin anuncios ni marcas de agua.

| Función de AutoResponder | En esta app |
|---|---|
| Responder a todos los mensajes | ✅ |
| Coincidencia exacta / de similitud / de patrones / experta (RegEx) | ✅ (umbral de similitud ajustable) |
| Mensaje de bienvenida | ✅ |
| Respuesta única / múltiples / aleatorias / diarias | ✅ |
| Reglas ilimitadas | ✅ |
| Conectar con Google Gemini IA y ChatGPT (OpenAI) | ✅ + también **Claude** |
| Conectar con Google Sheets | ✅ |
| Procesar mensajes con Dialogflow ES | ✅ |
| Conectar con tu servidor web | ✅ (formato compatible con AutoResponder) |
| Esperar respuesta de Tasker | ✅ |
| Contactos específicos / ignorados | ✅ |
| Pausar regla por… (por conversación) | ✅ |
| Notificaciones prioritarias | ✅ |
| Submenú / flujo de conversación | ✅ |
| Retraso | ✅ (+ cancelar respuestas retrasadas) |
| Horas específicas | ✅ |
| Condiciones (pantalla apagada, cargando, silencio, No molestar, modo coche, probabilidad, regla previa) | ✅ |
| Reemplazos de respuesta personalizados | ✅ |
| Tasker / MacroDroid | ✅ |
| Importar / exportar reglas | ✅ |
| Exportar estadísticas | ✅ (CSV para Excel) |
| Sin anuncios / sin marcas de agua | ✅ |
| **Extra:** enviar imágenes y archivos (también decididos por la IA) | ✅ |

Además: evitar repetir reglas, «no responder si coincide un patrón», encabezado y pie, ignorar acentos, palabra para que un contacto deje de recibir respuestas (ej. STOP), probar reglas explicando por qué no respondió cada una, historial con errores.

---

## 1. Obtener el APK

No necesitas saber programar. Elige una opción:

### Opción A — GitHub (gratis, sin instalar nada en tu PC)

1. Crea una cuenta en <https://github.com> y luego un repositorio nuevo (**New repository**, puede ser privado).
2. Descomprime `AutoRespuestaIA.zip` en tu computadora.
3. En el repositorio pulsa **Add file → Upload files** y arrastra **todo el contenido** de la carpeta `AutoRespuestaIA` (incluida la carpeta oculta `.github`). Pulsa **Commit changes**.
   - Si tu sistema oculta la carpeta `.github`, actívala en "mostrar archivos ocultos".
4. Ve a la pestaña **Actions**. Se ejecuta "Compilar APK" (tarda ~5 minutos). Si no arranca solo, entra en "Compilar APK" → **Run workflow**.
5. Cuando termine (✅), abre la ejecución y descarga **AutoRespuestaIA-apk** (es un .zip con el `.apk` dentro).

### Opción B — Android Studio

1. Instala Android Studio y abre la carpeta `AutoRespuestaIA`.
2. Espera a que sincronice y pulsa **Build → Build APK(s)**.

## 2. Instalar y dar permisos

1. Pasa el `.apk` al teléfono e instálalo (permite "instalar apps desconocidas" si te lo pide).
2. **Android 13 o superior (importante):** como la app no viene de Play Store, Android bloquea los permisos sensibles. Ve a **Ajustes → Aplicaciones → AutoRespuesta IA → ⋮ (arriba a la derecha) → Permitir ajustes restringidos**.
3. Abre la app y toca cada botón de "Permisos pendientes":
   - **Acceso a notificaciones** (obligatorio): así lee los mensajes y responde.
   - **Accesibilidad** (solo para enviar archivos): pulsa "Enviar" dentro de WhatsApp por ti.
   - **Mostrar sobre otras apps** (para abrir WhatsApp al enviar archivos).
   - **Batería sin restricciones**: para que Android no la cierre. En Xiaomi/Redmi/Poco, Huawei, Oppo, Vivo activa también **Inicio automático**.
4. En WhatsApp, deja activadas las notificaciones con vista previa.
5. Si la app te pide permiso para mostrar notificaciones, acéptalo (lo usa para avisarte de cada respuesta y para las notificaciones prioritarias).

## 3. Configurar la IA

Menú **⋮ → Ajustes y API keys**:

| IA | Dónde sacar la key | Modelo por defecto |
|---|---|---|
| Claude | console.anthropic.com → API Keys | `claude-haiku-4-5-20251001` |
| ChatGPT | platform.openai.com → API keys | `gpt-4.1-mini` |
| Gemini | aistudio.google.com → Get API key (tiene nivel gratuito) | `gemini-3.5-flash-lite` |

Notas sobre los modelos (revisado en octubre de 2026):
- **Gemini:** Google limita los modelos 2.5 a cuentas que ya los usaban; por eso el predeterminado es `gemini-3.5-flash-lite`. Los Gemini 3 siempre "piensan" un poco antes de responder; la app les pide pensar poco y les deja margen de tokens para que la respuesta no salga vacía.
- **ChatGPT:** si eliges un modelo de razonamiento (o3, o4, gpt-5…), la app ajusta sola los parámetros que esos modelos no aceptan.
- Si algo falla, el **Historial** muestra el motivo en español (API key no válida, sin saldo, modelo no encontrado, sin internet…).

Las keys se guardan solo en tu teléfono y se envían únicamente al proveedor elegido. Cada proveedor cobra según su uso (Gemini tiene un nivel gratuito con límites).

Puedes cambiar el modelo en cada regla. En ChatGPT también puedes cambiar la URL base para usar servicios compatibles (Groq, DeepSeek, OpenRouter).

## Pantalla principal
- **☰ (menú lateral):** Reglas, Reemplazos de respuesta, Prueba tus reglas, Historial de respuestas, Estadísticas, Biblioteca de archivos, Configuración, Automatización, Acerca de la aplicación, Invitar a un amigo.
- **Interruptor de la barra:** enciende o apaga todas las respuestas automáticas (la primera vez te pide el acceso a notificaciones).
- **Burbuja con ojo:** Prueba tus reglas.
- **⋮:** Buscar, Importar/Exportar, Exportar estadísticas, ¿No funciona?, Ayuda.
- **+ verde:** crear una regla. Mantén pulsada una regla para subirla, bajarla, duplicarla o eliminarla.

## 4. Crear reglas

1. Pulsa **+**.
2. **¿Cuándo responder?** elige el disparador.
3. **Respuesta:** texto fijo o una IA. En "Instrucciones para la IA" describe tu negocio, horarios, precios y el tono. Ejemplo:
   > Eres el asistente de mi tienda. Atendemos de lunes a sábado de 9:00 a 19:00. Si piden el catálogo envía [[archivo:catalogo]]. Responde breve y amable.
4. **Imagen o archivo:** elige uno de la biblioteca para que se envíe siempre.
5. Guarda y enciende el interruptor de la barra superior. Por defecto la regla responde **apenas llega el mensaje** (retraso 0); si quieres que parezca más humano, pon un retraso de unos segundos.
6. Usa **⋮ → Probar reglas** para ver qué respondería, sin enviar nada.

Se usa la **primera regla activa que coincida**: pon las específicas arriba (mantén pulsada una regla → Subir/Bajar) y una regla general de IA al final.

### Enviar archivos con IA

1. **⋮ → Biblioteca de archivos → +**, elige el archivo y dale una clave (ej. `catalogo`) y una descripción (ej. "catálogo de productos en PDF").
2. En la regla con IA deja marcado "Permitir que la IA envíe imágenes/archivos".
3. Cuando un cliente pida algo que encaje, la IA escribe `[[archivo:catalogo]]`; la app quita esa etiqueta del texto y envía el archivo.

## 5. Funciones avanzadas

### Google Sheets
Crea una hoja con dos columnas: **A** = palabras o frases del cliente (separadas por comas), **B** = respuesta. Compártela como «Cualquier persona con el enlace» y pega el enlace en la regla (fuente «Google Sheets»). Elige buscar por *Contiene*, *Exacta* o *Similitud*. La hoja se vuelve a leer cada 3 minutos, así que puedes editar respuestas desde tu PC. En la columna B puedes poner `[[archivo:clave]]` para enviar un archivo.

| A (mensaje) | B (respuesta) |
|---|---|
| precio, costo, cuánto cuesta | El precio es %precio% 😊 |
| horario, atienden | Atendemos de lunes a sábado de 9:00 a 19:00 |
| catálogo, productos | Te envío el catálogo [[archivo:catalogo]] |

### Dialogflow ES
En Google Cloud crea una cuenta de servicio con el rol «Cliente de la API de Dialogflow», descarga su clave JSON y cárgala en ⋮ → Ajustes → Dialogflow ES. En la regla elige la fuente «Dialogflow ES» y el idioma del agente (ej. `es`).

### Tu servidor web
La app envía un POST con JSON (mismo formato que AutoResponder):
```json
{"appPackageName":"com.autorespuesta.ia","messengerPackageName":"com.whatsapp",
 "query":{"sender":"Juan","message":"hola","isGroup":false,"groupParticipant":"","ruleId":"..."}}
```
Tu servidor responde `{"replies":[{"message":"¡Hola Juan!"}]}` (o texto plano).

### Tasker / MacroDroid
- Encender/apagar: intents `com.autorespuesta.ia.ACTIVAR` y `com.autorespuesta.ia.DESACTIVAR`.
- Activar/desactivar una regla: `com.autorespuesta.ia.ACTIVAR_REGLA` / `DESACTIVAR_REGLA` con el extra `regla` = nombre de la regla.
- **Esperar respuesta de Tasker:** la app envía `com.autorespuesta.ia.MENSAJE_RECIBIDO` (extras `id`, `mensaje`, `remitente`, `chat`, `grupo`). Tu tarea contesta enviando `com.autorespuesta.ia.RESPUESTA` con los extras `id` (el mismo) y `respuesta`.
- Tras cada respuesta la app envía `com.autorespuesta.ia.RESPUESTA_ENVIADA`, útil como disparador.
- Si tu app de automatización pide un paquete de destino, usa `com.autorespuesta.ia`.

### Submenú / flujo de conversación (menús 1, 2, 3)
1. Regla **«Menú»** (cualquier mensaje o «hola»): responde «1) Precios 2) Horario 0) Volver».
2. Reglas **«1»** y **«2»** (coincidencia exacta): en «Submenú» elige «Responder solo si antes se activó: Menú». Ponlas **arriba** de la regla «Menú».
3. Regla **«0»**: en «Después de responder, ir a la regla» elige «Menú» para volver al inicio.
Prueba todo el flujo en ⋮ → Probar reglas enviando varios mensajes seguidos.

### Reemplazos de respuesta
En ⋮ → Reemplazos de respuesta crea tus variables (ej. `precio` = `S/ 25`). Escribe `%precio%` en cualquier respuesta, hoja o instrucción de IA, y se cambia en todas a la vez.

### Salida de mensajes (registrar en Google Sheets o webhook)
Cada mensaje (y su respuesta) puede guardarse en una hoja de Google Sheets o enviarse a un webhook.

**Google Sheets:**
1. Crea una hoja nueva → menú **Extensiones → Apps Script**.
2. Borra lo que haya y pega:
```javascript
function doPost(e) {
  var d = JSON.parse(e.postData.contents);
  var hoja = SpreadsheetApp.getActiveSpreadsheet().getSheets()[0];
  if (hoja.getLastRow() === 0) {
    hoja.appendRow(['Fecha', 'App', 'Chat', 'Remitente', 'Grupo', 'Mensaje', 'Respuesta', 'Regla', 'Error']);
  }
  hoja.appendRow([d.fecha, d.app, d.chat, d.remitente, d.grupo ? 'Sí' : 'No',
                  d.mensaje, d.respuesta, d.regla, d.error]);
  return ContentService.createTextOutput('ok');
}
```
3. **Implementar → Nueva implementación → Aplicación web**. Ejecutar como: *yo*. Quién tiene acceso: *cualquier usuario*. Copia la URL (`https://script.google.com/macros/s/.../exec`).
4. Pégala en ⋮ → Ajustes → Salida de mensajes y elige cuándo registrar (nunca, solo respondidos o todos los mensajes).

**Webhook:** pon tu URL; recibirá un POST con el mismo JSON: `fecha, app, chat, remitente, grupo, mensaje, respuesta, regla, error`.

En cada regla, «Cuándo registrar» puede usar la configuración global, o forzar *Siempre* / *Nunca*.

### Botones de la tarjeta «Respuesta automática»
- **Aa**: formato de WhatsApp (*negrita*, _cursiva_, ~tachado~, ```monoespaciado```).
- **🚫 No responder**: la regla coincide pero no envía nada (sirve para excluir mensajes o contactos; las reglas de abajo no se aplican). Se pone rojo cuando está activo.
- **Etiqueta +**: inserta variables (%nombre%, %saludo%…) y tus reemplazos.
- **− / +**: quita o agrega respuestas (con «Aleatorio» se elige una al azar; con «Todos» se envían todas; con «Diaria» una distinta cada día).
- **Botón TODOS**: responde a cualquier mensaje.
- En **Contactos específicos / ignorados**, el ícono de persona abre tu agenda para elegir un contacto.
- En **Submenú/Flujo**, el **⊕** crea directamente una regla hija que solo responde después de esta.

### Condiciones
En cada regla puedes exigir: pantalla apagada o bloqueada, teléfono cargando, timbre en silencio, «No molestar» activo, modo coche, o responder solo con cierta probabilidad (ej. 50 %).

## 6. Cómo funciona por dentro (y sus límites)

- **Texto:** igual que AutoResponder, responde con el botón "Responder" de la notificación de WhatsApp. No necesita que abras WhatsApp ni que la pantalla esté encendida.
- **Archivos:** WhatsApp no permite adjuntar desde la notificación, así que la app abre el chat con el archivo y el servicio de accesibilidad pulsa "Enviar", luego vuelve al inicio. Para eso **el teléfono debe estar desbloqueado o sin bloqueo de pantalla**; si está bloqueado, espera y reintenta durante ~10 minutos.
- Solo ve mensajes que generan notificación: si tienes ese chat abierto en WhatsApp, o está silenciado sin notificación, no responderá.
- Si WhatsApp cambia su diseño, el botón "Enviar" podría no encontrarse; el historial lo mostrará como error.

## Aviso

Las respuestas automáticas no son una función oficial de WhatsApp y van contra sus términos de uso si se abusa (spam, envíos masivos). Úsala para atender a quienes te escriben, con moderación, para no arriesgar tu número. Para uso comercial a gran escala existe la API oficial de WhatsApp Business.

## Estructura del proyecto

```
app/src/main/java/com/autorespuesta/ia/
├── datos/      Reglas, ajustes, almacenamiento
├── motor/      Coincidencias, similitud, variables, IA (Claude, ChatGPT, Gemini),
│               Google Sheets, Dialogflow ES y servidor web
├── servicio/   Lector de notificaciones, envío de archivos, accesibilidad,
│               notificaciones y Tasker/MacroDroid
└── ui/         Pantallas
```

## Novedades (octubre 2026)
- **Horas específicas por día**: un campo por día; vacío = no responde, `0-24` = todo el día; varios rangos con coma; formato 12 y 24 horas; los rangos que cruzan medianoche continúan al día siguiente.
- **Pausar regla por…**: ahora también «hasta (hora)» y «no repetir en los siguientes x mensajes».
- **Contactos**: comodín `*` (`+34*`, `+*`, `Ana*`) y `grupo{participante 1, participante 2}`.
- **Google Sheets**: columna de búsqueda y de respuesta configurables, marcador `%sheet_result%` y respuesta alternativa.
- **RegEx**: grupos de captura `%1%`, `%2%`… en la respuesta.
- **Coincidencias**: patrones separables con `//` o comas; la coincidencia exacta ignora emojis y signos si el patrón no los usa.
- **Servidor**: el JSON enviado incluye `timestamp`, `reply` e `isTestMessage`.
