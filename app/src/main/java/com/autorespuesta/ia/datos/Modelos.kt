package com.autorespuesta.ia.datos

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID
import com.autorespuesta.ia.motor.Horario

enum class TipoCoincidencia(val etiqueta: String) {
    TODOS("Cualquier mensaje"),
    EXACTA("Exacta"),
    CONTIENE("Contiene"),
    SIMILITUD("Similitud"),
    PATRON("Patrón con *"),
    REGEX("Expresión regular"),
    BIENVENIDA("Bienvenida")
}

/** De dónde sale la respuesta de la regla. */
enum class ProveedorIA(val etiqueta: String) {
    NINGUNO("Texto fijo"),
    CLAUDE("Claude"),
    OPENAI("ChatGPT"),
    GEMINI("Gemini"),
    SHEETS("Google Sheets"),
    DIALOGFLOW("Dialogflow ES"),
    SERVIDOR("Servidor web"),
    TASKER("Tasker/MacroDroid");

    val esIA: Boolean get() = this == CLAUDE || this == OPENAI || this == GEMINI
}

enum class ModoRespuesta { UNA, TODAS, ALEATORIA, DIARIA }

enum class Destinatarios { INDIVIDUALES, GRUPOS, AMBOS }

/** Cuándo registrar los mensajes en Google Sheets / webhook. */
enum class ModoSalida(val etiqueta: String) {
    GLOBAL("Usar configuración global"),
    SIEMPRE("Siempre"),
    NUNCA("Nunca")
}

/** Una regla de respuesta automática. */
data class Regla(
    var id: String = UUID.randomUUID().toString(),
    var nombre: String = "",
    var activa: Boolean = true,
    // Mensaje recibido
    var patron: String = "",
    var tipo: TipoCoincidencia = TipoCoincidencia.TODOS,
    // Respuesta fija (también se usa como respaldo si la fuente externa falla)
    var respuestas: MutableList<String> = mutableListOf(),
    var modo: ModoRespuesta = ModoRespuesta.UNA,
    // Fuente de la respuesta
    var proveedor: ProveedorIA = ProveedorIA.NINGUNO,
    var modelo: String = "",
    var promptSistema: String = PROMPT_DEFECTO,
    var temperatura: String = "0.7",
    var maxTokens: Int = 500,
    var historial: Int = 6,
    var iaPuedeEnviarArchivos: Boolean = true,
    var sheetsUrl: String = "",
    var sheetsModo: TipoCoincidencia = TipoCoincidencia.CONTIENE,
    var sheetsColBusqueda: String = "A",      // columna donde se busca el mensaje
    var sheetsColRespuesta: String = "B",     // columna con la respuesta
    var sheetsAlternativa: String = "",       // respuesta si no hay coincidencia (opcional)
    var dialogflowIdioma: String = "es",
    var servidorUrl: String = "",
    var taskerEspera: Int = 10,
    // Archivo adjunto fijo (clave de la biblioteca)
    var adjunto: String = "",
    // Retraso
    var delayMin: Int = 0,   // 0 = responder apenas llega el mensaje
    var delayMax: Int = 0,
    var cancelarRetrasados: Boolean = false,
    // Destinatarios
    var destinatarios: Destinatarios = Destinatarios.INDIVIDUALES,
    var contactos: String = "",
    var ignorados: String = "",
    // Horario
    var usarHorario: Boolean = false,
    var dias: MutableSet<Int> = mutableSetOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    ),
    var horaInicio: String = "08:00",
    var horaFin: String = "20:00",
    // Horas específicas por día (Calendar.MONDAY… -> "8-12,14-18"); día ausente o vacío = no responde
    var horarioDias: MutableMap<Int, String> = mutableMapOf(),
    // Condiciones
    var condReglaPrevia: String = "",
    var condReglaPreviaSegundos: Int = 300,
    var usarProbabilidad: Boolean = false,
    var probabilidad: Int = 50,
    var condPantallaApagada: Boolean = false,
    var condCargando: Boolean = false,
    var condSilencio: Boolean = false,
    var condNoMolestar: Boolean = false,
    var condModoCoche: Boolean = false,
    // Otros
    var notificacionPrioritaria: Boolean = false,
    var pausaSegundos: Int = 0,
    var pausaUnidad: Int = 0,          // 0 segundos, 1 minutos, 2 horas, 3 días, 4 hasta (hora)
    var pausaHasta: String = "",       // «HH:mm»: pausa hasta esa hora del día (unidad 4)
    var pausaMensajes: Int = 0,        // y no repetir en los siguientes x mensajes del contacto
    var irARegla: String = "",
    var noResponder: Boolean = false,  // si coincide, no se envía nada (bloquea las reglas de abajo)
    var salidaSheets: ModoSalida = ModoSalida.GLOBAL,
    var salidaWebhook: ModoSalida = ModoSalida.GLOBAL
) {
    fun aJson(): JSONObject = JSONObject().apply {
        put("id", id); put("nombre", nombre); put("activa", activa)
        put("patron", patron); put("tipo", tipo.name)
        put("respuestas", JSONArray(respuestas)); put("modo", modo.name)
        put("proveedor", proveedor.name); put("modelo", modelo); put("promptSistema", promptSistema)
        put("temperatura", temperatura); put("maxTokens", maxTokens); put("historial", historial)
        put("iaPuedeEnviarArchivos", iaPuedeEnviarArchivos)
        put("sheetsUrl", sheetsUrl); put("sheetsModo", sheetsModo.name)
        put("sheetsColBusqueda", sheetsColBusqueda); put("sheetsColRespuesta", sheetsColRespuesta)
        put("sheetsAlternativa", sheetsAlternativa)
        put("dialogflowIdioma", dialogflowIdioma); put("servidorUrl", servidorUrl); put("taskerEspera", taskerEspera)
        put("adjunto", adjunto)
        put("delayMin", delayMin); put("delayMax", delayMax); put("cancelarRetrasados", cancelarRetrasados)
        put("destinatarios", destinatarios.name); put("contactos", contactos); put("ignorados", ignorados)
        put("usarHorario", usarHorario); put("dias", JSONArray(dias.toList()))
        put("horaInicio", horaInicio); put("horaFin", horaFin)
        put("horarioDias", JSONObject().also { j -> horarioDias.forEach { (dia, texto) -> j.put(dia.toString(), texto) } })
        put("condReglaPrevia", condReglaPrevia); put("condReglaPreviaSegundos", condReglaPreviaSegundos)
        put("usarProbabilidad", usarProbabilidad); put("probabilidad", probabilidad)
        put("condPantallaApagada", condPantallaApagada); put("condCargando", condCargando)
        put("condSilencio", condSilencio); put("condNoMolestar", condNoMolestar); put("condModoCoche", condModoCoche)
        put("notificacionPrioritaria", notificacionPrioritaria)
        put("pausaSegundos", pausaSegundos); put("pausaUnidad", pausaUnidad); put("pausaHasta", pausaHasta); put("pausaMensajes", pausaMensajes)
        put("irARegla", irARegla)
        put("noResponder", noResponder); put("salidaSheets", salidaSheets.name); put("salidaWebhook", salidaWebhook.name)
    }

    /** Texto corto que se muestra en la lista de reglas. */
    fun resumen(): String {
        val disparador = when (tipo) {
            TipoCoincidencia.TODOS -> "Cualquier mensaje"
            TipoCoincidencia.BIENVENIDA -> "Primer mensaje (bienvenida)"
            else -> "${tipo.etiqueta}: \"$patron\""
        }
        val respuesta = when {
            noResponder -> "🚫 No responder"
            proveedor == ProveedorIA.NINGUNO -> respuestas.firstOrNull()?.take(40) ?: "(sin texto)"
            proveedor.esIA -> "🤖 ${proveedor.etiqueta}"
            else -> "🔗 ${proveedor.etiqueta}"
        }
        val extras = buildString {
            if (adjunto.isNotBlank()) append("  📎 $adjunto")
            if (condReglaPrevia.isNotBlank()) append("  ↳ submenú")
            if (irARegla.isNotBlank()) append("  ➜ ir a regla")
            if (notificacionPrioritaria) append("  🔔")
        }
        return "$disparador  →  $respuesta$extras"
    }

    companion object {
        const val PROMPT_DEFECTO =
            "Eres un asistente amable que responde mensajes de WhatsApp en nombre del dueño de este número. " +
                "Responde siempre en español, de forma breve, clara y cordial, como en un chat. " +
                "Si no sabes algo, dilo con honestidad y ofrece que el dueño responderá personalmente más tarde. " +
                "No inventes precios, datos ni compromisos."

        private inline fun <reified T : Enum<T>> enumDe(valor: String, defecto: T): T =
            runCatching { enumValueOf<T>(valor) }.getOrDefault(defecto)

        fun desdeJson(o: JSONObject): Regla {
            val d = Regla()
            return Regla(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                nombre = o.optString("nombre"),
                activa = o.optBoolean("activa", true),
                patron = o.optString("patron"),
                tipo = enumDe(o.optString("tipo"), TipoCoincidencia.TODOS),
                respuestas = mutableListOf<String>().apply {
                    val a = o.optJSONArray("respuestas")
                    if (a != null) for (i in 0 until a.length()) add(a.optString(i))
                },
                modo = enumDe(o.optString("modo"), ModoRespuesta.UNA),
                proveedor = enumDe(o.optString("proveedor"), ProveedorIA.NINGUNO),
                modelo = o.optString("modelo"),
                promptSistema = o.optString("promptSistema", PROMPT_DEFECTO),
                temperatura = o.optString("temperatura", d.temperatura),
                maxTokens = o.optInt("maxTokens", d.maxTokens),
                historial = o.optInt("historial", d.historial),
                iaPuedeEnviarArchivos = o.optBoolean("iaPuedeEnviarArchivos", true),
                sheetsUrl = o.optString("sheetsUrl"),
                sheetsModo = enumDe(o.optString("sheetsModo"), TipoCoincidencia.CONTIENE),
                sheetsColBusqueda = o.optString("sheetsColBusqueda", "A").ifBlank { "A" },
                sheetsColRespuesta = o.optString("sheetsColRespuesta", "B").ifBlank { "B" },
                sheetsAlternativa = o.optString("sheetsAlternativa"),
                dialogflowIdioma = o.optString("dialogflowIdioma", "es"),
                servidorUrl = o.optString("servidorUrl"),
                taskerEspera = o.optInt("taskerEspera", d.taskerEspera),
                adjunto = o.optString("adjunto"),
                delayMin = o.optInt("delayMin", d.delayMin),
                delayMax = o.optInt("delayMax", d.delayMax),
                cancelarRetrasados = o.optBoolean("cancelarRetrasados", false),
                destinatarios = enumDe(o.optString("destinatarios"), Destinatarios.INDIVIDUALES),
                contactos = o.optString("contactos"),
                ignorados = o.optString("ignorados"),
                usarHorario = o.optBoolean("usarHorario", false),
                dias = o.optJSONArray("dias")?.let { a ->
                    MutableList(a.length()) { a.optInt(it) }.toMutableSet()
                } ?: d.dias,
                horaInicio = o.optString("horaInicio", d.horaInicio),
                horaFin = o.optString("horaFin", d.horaFin),
                horarioDias = o.optJSONObject("horarioDias")?.let { h ->
                    val mapa = mutableMapOf<Int, String>()
                    h.keys().forEach { k -> k.toIntOrNull()?.let { dia -> mapa[dia] = h.optString(k) } }
                    mapa
                } ?: Horario.desdeAntiguo(
                    o.optJSONArray("dias")?.let { a -> MutableList(a.length()) { a.optInt(it) }.toSet() } ?: d.dias,
                    o.optString("horaInicio", d.horaInicio), o.optString("horaFin", d.horaFin)
                ),
                condReglaPrevia = o.optString("condReglaPrevia"),
                condReglaPreviaSegundos = o.optInt("condReglaPreviaSegundos", d.condReglaPreviaSegundos),
                usarProbabilidad = o.optBoolean("usarProbabilidad", false),
                probabilidad = o.optInt("probabilidad", d.probabilidad),
                condPantallaApagada = o.optBoolean("condPantallaApagada", false),
                condCargando = o.optBoolean("condCargando", false),
                condSilencio = o.optBoolean("condSilencio", false),
                condNoMolestar = o.optBoolean("condNoMolestar", false),
                condModoCoche = o.optBoolean("condModoCoche", false),
                notificacionPrioritaria = o.optBoolean("notificacionPrioritaria", false),
                pausaSegundos = o.optInt("pausaSegundos", 0),
                pausaUnidad = o.optInt("pausaUnidad", 0),
                pausaHasta = o.optString("pausaHasta"),
                pausaMensajes = o.optInt("pausaMensajes", 0),
                irARegla = o.optString("irARegla"),
                noResponder = o.optBoolean("noResponder", false),
                salidaSheets = enumDe(o.optString("salidaSheets"), ModoSalida.GLOBAL),
                salidaWebhook = enumDe(o.optString("salidaWebhook"), ModoSalida.GLOBAL)
            )
        }
    }
}

/** Un archivo guardado en la biblioteca, que las reglas o la IA pueden enviar. */
data class ArchivoBiblioteca(
    val clave: String,
    val ruta: String,        // ruta relativa dentro de files/biblioteca
    val mime: String,
    val descripcion: String,
    val pie: String          // texto que acompaña a la imagen (caption)
) {
    fun aJson(): JSONObject = JSONObject()
        .put("clave", clave).put("ruta", ruta).put("mime", mime)
        .put("descripcion", descripcion).put("pie", pie)

    companion object {
        fun desdeJson(o: JSONObject) = ArchivoBiblioteca(
            o.optString("clave"), o.optString("ruta"), o.optString("mime", "application/octet-stream"),
            o.optString("descripcion"), o.optString("pie")
        )
    }
}

/** Un reemplazo personalizado: %clave% se sustituye por valor en las respuestas. */
data class Reemplazo(val clave: String, val valor: String) {
    fun aJson(): JSONObject = JSONObject().put("clave", clave).put("valor", valor)

    companion object {
        fun desdeJson(o: JSONObject) = Reemplazo(o.optString("clave"), o.optString("valor"))
    }
}

/** Una entrada del historial de respuestas. */
data class EventoHistorial(
    val fecha: Long,
    val chat: String,
    val mensaje: String,
    val respuesta: String,
    val regla: String,
    val error: String?
) {
    fun aJson(): JSONObject = JSONObject()
        .put("fecha", fecha).put("chat", chat).put("mensaje", mensaje)
        .put("respuesta", respuesta).put("regla", regla).put("error", error ?: "")

    companion object {
        fun desdeJson(o: JSONObject) = EventoHistorial(
            o.optLong("fecha"), o.optString("chat"), o.optString("mensaje"),
            o.optString("respuesta"), o.optString("regla"), o.optString("error").ifBlank { null }
        )
    }
}

/** Un mensaje que llegó por WhatsApp. */
data class MensajeEntrante(
    val paquete: String,
    val chatId: String,
    val nombreChat: String,
    val remitente: String,
    val texto: String,
    val esGrupo: Boolean,
    val jid: String?
)
