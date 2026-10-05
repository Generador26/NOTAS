package com.autorespuesta.ia.motor

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.BatteryManager
import android.os.PowerManager
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.Destinatarios
import com.autorespuesta.ia.datos.MensajeEntrante
import com.autorespuesta.ia.datos.ModoRespuesta
import com.autorespuesta.ia.datos.ProveedorIA
import com.autorespuesta.ia.datos.Regla
import com.autorespuesta.ia.datos.TipoCoincidencia
import com.autorespuesta.ia.servicio.Automatizacion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** Decide qué regla aplica a un mensaje y genera la respuesta. */
object Motor {

    data class Resultado(
        val textos: List<String>,
        val adjuntos: List<String>,
        val error: String?
    )

    private lateinit var ctx: Context
    private val etiquetaArchivo = Regex("\\[\\[\\s*archivo\\s*:\\s*([^\\]]+?)\\s*\\]\\]", RegexOption.IGNORE_CASE)
    private val es = Locale("es")
    private const val MARCA_SHEETS = "%sheet_result%"

    fun init(c: Context) {
        if (!::ctx.isInitialized) ctx = c.applicationContext
    }

    // ===================== Elegir la regla =====================

    /** Motivo global por el que no se responde a este chat, o null. */
    fun bloqueoGlobal(m: MensajeEntrante): String? {
        if (m.chatId in Almacen.ignoradosDinamicos()) return "El contacto pidió no recibir respuestas automáticas"
        if (coincideContacto(Ajustes.ignoradosGlobales, m)) return "El contacto está en «Contactos ignorados» (Ajustes)"
        val texto = Texto.normalizar(m.texto, Ajustes.ignorarAcentos)
        val bloqueo = Texto.listaPatrones(Ajustes.noResponderSi)
            .firstOrNull { Texto.coincidePatronSimple(Texto.normalizar(it, Ajustes.ignorarAcentos), texto) }
        if (bloqueo != null) return "El mensaje coincide con «No responder si…» ($bloqueo)"
        return null
    }

    /** Devuelve la primera regla activa que coincide (el orden de la lista importa). */
    fun buscarRegla(m: MensajeEntrante, simulacion: Boolean = false): Regla? {
        if (bloqueoGlobal(m) != null) return null
        val regla = Almacen.reglas().firstOrNull { it.activa && motivoNoCumple(it, m, simulacion) == null } ?: return null
        if (!simulacion && Ajustes.evitarRepetir) {
            val ultima = Almacen.ultimaRegla(m.chatId)
            if (ultima != null && ultima.first == regla.id) {
                val limite = Ajustes.evitarRepetirSegundos
                if (limite == 0 || System.currentTimeMillis() - ultima.second < limite * 1000L) return null
            }
        }
        return regla
    }

    /** null si la regla se cumple; si no, el motivo (se muestra en «Probar reglas»). */
    fun motivoNoCumple(r: Regla, m: MensajeEntrante, simulacion: Boolean): String? {
        when (r.destinatarios) {
            Destinatarios.INDIVIDUALES -> if (m.esGrupo) return "solo responde a personas, no a grupos"
            Destinatarios.GRUPOS -> if (!m.esGrupo) return "solo responde a grupos"
            Destinatarios.AMBOS -> Unit
        }
        if (r.contactos.isNotBlank() && !coincideContacto(r.contactos, m)) return "el contacto no está en «Solo estos contactos»"
        if (coincideContacto(r.ignorados, m)) return "el contacto está en «Ignorar estos contactos»"
        if (r.usarHorario && !enHorario(r)) return "fuera del horario configurado"
        if (r.condReglaPrevia.isNotBlank()) {
            val ultima = Almacen.ultimaRegla(m.chatId)
            val dentro = ultima != null && ultima.first == r.condReglaPrevia &&
                (r.condReglaPreviaSegundos <= 0 ||
                    System.currentTimeMillis() - ultima.second <= r.condReglaPreviaSegundos * 1000L)
            if (!dentro) {
                val nombre = Almacen.reglas().firstOrNull { it.id == r.condReglaPrevia }?.nombre ?: "otra regla"
                return "es un submenú: antes debe activarse «$nombre»"
            }
        }
        if (!simulacion) {
            val ultimaVez = Almacen.ultimaVezRegla(m.chatId, r.id)
            if (ultimaVez > 0) {
                val ahora = System.currentTimeMillis()
                if (r.pausaSegundos > 0 && ahora - ultimaVez < r.pausaSegundos * 1000L) {
                    return "la regla está en pausa para este chat"
                }
                if (r.pausaHasta.isNotBlank()) {
                    val fin = finPausaHasta(r.pausaHasta, ultimaVez)
                    if (fin != null && ahora < fin) return "la regla está en pausa hasta las ${r.pausaHasta}"
                }
                if (r.pausaMensajes > 0 && Almacen.mensajesDesdeRegla(m.chatId, r.id) <= r.pausaMensajes) {
                    return "la regla está en pausa por los siguientes ${r.pausaMensajes} mensajes de este chat"
                }
            }
        }
        if (r.condPantallaApagada && !pantallaApagadaOBloqueada()) return "la pantalla no está apagada ni bloqueada"
        if (r.condCargando && !cargando()) return "el teléfono no se está cargando"
        if (r.condSilencio && !timbreEnSilencio()) return "el timbre no está en silencio"
        if (r.condNoMolestar && !noMolestarActivo()) return "«No molestar» no está activo"
        if (r.condModoCoche && !modoCoche()) return "el modo coche no está activo"
        if (!coincideTexto(r, m)) return "el texto no coincide"
        if (!simulacion && r.usarProbabilidad && Random.nextInt(100) >= r.probabilidad.coerceIn(0, 100)) {
            return "no salió en la probabilidad del ${r.probabilidad}%"
        }
        return null
    }

    private fun coincideTexto(r: Regla, m: MensajeEntrante): Boolean {
        val sinAcentos = Ajustes.ignorarAcentos
        val texto = Texto.normalizar(m.texto, sinAcentos)
        val patrones = Texto.listaPatrones(r.patron).map { Texto.normalizar(it, sinAcentos) }
        return when (r.tipo) {
            TipoCoincidencia.TODOS -> true
            TipoCoincidencia.EXACTA -> patrones.any { Texto.exacta(it, texto) }
            TipoCoincidencia.CONTIENE -> patrones.any { it.isNotEmpty() && texto.contains(it) }
            TipoCoincidencia.SIMILITUD -> patrones.any { Texto.similitud(it, texto) >= Ajustes.umbralSimilitud }
            TipoCoincidencia.PATRON -> patrones.any { Texto.comodin(it, texto) }
            TipoCoincidencia.REGEX -> runCatching {
                Regex(r.patron, RegexOption.IGNORE_CASE).containsMatchIn(m.texto)
            }.getOrDefault(false)
            TipoCoincidencia.BIENVENIDA -> !Almacen.contactoVisto(m.chatId)
        }
    }

    /**
     * Contactos separados por comas. Cada elemento puede ser:
     *  - un nombre (o parte, como palabra completa) de contacto, grupo o participante,
     *  - un número con código de país (51987654321),
     *  - un comodín: «+34*» = todos los números de España, «+*» = números sin guardar, «Ana*» = nombres que empiezan por Ana,
     *  - «nombre del grupo{participante 1, participante 2}» = solo esas personas dentro de ese grupo.
     */
    fun coincideContacto(lista: String, m: MensajeEntrante): Boolean {
        val chat = Texto.normalizar(m.nombreChat, true)
        val remitente = Texto.normalizar(m.remitente, true)
        return Texto.listaContactos(lista).any { coincideItemContacto(it, m, chat, remitente) }
    }

    private fun nombreCoincide(patron: String, texto: String): Boolean =
        if (patron.contains('*')) Texto.comodin(patron, texto) else contienePalabra(texto, patron)

    private fun coincideItemContacto(item: String, m: MensajeEntrante, chat: String, remitente: String): Boolean {
        val llave = item.indexOf('{')
        if (llave > 0 && item.endsWith("}")) {
            val grupo = Texto.normalizar(item.substring(0, llave), true)
            val participantes = item.substring(llave + 1, item.length - 1).split(',')
                .map { Texto.normalizar(it, true) }.filter { it.isNotEmpty() }
            return m.esGrupo && grupo.isNotEmpty() && nombreCoincide(grupo, chat) &&
                (participantes.isEmpty() || participantes.any { nombreCoincide(it, remitente) })
        }
        val x = Texto.normalizar(item, true)
        if (x.isEmpty()) return false
        val digitos = item.filter { it.isDigit() }
        val esNumero = digitos.length >= 6 && item.all { it.isDigit() || it in "+ -()" }
        val esNumeroComodin = item.startsWith("+") && item.contains('*') && item.all { it.isDigit() || it in "+ -()*" }
        return when {
            esNumero -> m.jid?.contains(digitos) == true || chat.filter { it.isDigit() }.contains(digitos)
            esNumeroComodin -> {
                val patron = item.filter { it.isDigit() || it == '*' }
                if (patron == "*") {
                    // «+*»: WhatsApp muestra el número como nombre cuando el contacto no está guardado
                    chat.startsWith("+") || remitente.startsWith("+")
                } else {
                    val delJid = m.jid?.substringBefore('@')?.filter { it.isDigit() } ?: ""
                    val delChat = if (chat.startsWith("+")) chat.filter { it.isDigit() } else ""
                    val delRemitente = if (remitente.startsWith("+")) remitente.filter { it.isDigit() } else ""
                    listOf(delJid, delChat, delRemitente).any { it.isNotEmpty() && Texto.comodin(patron, it) }
                }
            }
            else -> nombreCoincide(x, chat) || nombreCoincide(x, remitente)
        }
    }

    /** true si [frase] aparece en [texto] como palabra(s) completa(s). */
    fun contienePalabra(texto: String, frase: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(frase) + "(?![\\p{L}\\p{N}])").containsMatchIn(texto)

    private fun enHorario(r: Regla): Boolean = Horario.activoAhora(r.horarioDias)

    /** Momento (ms) hasta el que está pausada la regla: la primera vez que llega «HH:mm» después de [desde]. */
    private fun finPausaHasta(hhmm: String, desde: Long): Long? {
        val minutos = Horario.horaSimple(hhmm) ?: return null
        if (minutos >= 1440) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = desde
            set(Calendar.HOUR_OF_DAY, minutos / 60)
            set(Calendar.MINUTE, minutos % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= desde) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    // ===================== Condiciones del teléfono =====================

    private fun pantallaApagadaOBloqueada(): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val km = ctx.getSystemService(KeyguardManager::class.java)
        return pm?.isInteractive == false || km?.isKeyguardLocked == true
    }

    private fun cargando(): Boolean = ctx.getSystemService(BatteryManager::class.java)?.isCharging == true

    private fun timbreEnSilencio(): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        return am.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    private fun noMolestarActivo(): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return false
        val filtro = nm.currentInterruptionFilter
        return filtro != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filtro != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun modoCoche(): Boolean =
        ctx.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR

    // ===================== Generar la respuesta =====================

    /** Genera las respuestas de texto y la lista de archivos a enviar. */
    suspend fun generar(r: Regla, m: MensajeEntrante): Resultado {
        if (r.noResponder) return Resultado(emptyList(), emptyList(), null)
        val adjuntos = mutableListOf<String>()
        if (r.adjunto.isNotBlank()) adjuntos += r.adjunto
        val textosFijos = r.respuestas.filter { it.isNotBlank() }

        // Texto fijo
        if (r.proveedor == ProveedorIA.NINGUNO) {
            return Resultado(elegir(textosFijos, r.modo).map { cierre(it, r, m) }, adjuntos.distinct(), null)
        }

        var error: String? = null
        val externos: List<String> = try {
            when (r.proveedor) {
                ProveedorIA.CLAUDE, ProveedorIA.OPENAI, ProveedorIA.GEMINI -> {
                    val historial = if (r.historial > 0) {
                        Almacen.conversacion(m.chatId).takeLast(r.historial * 2)
                    } else emptyList()
                    listOf(ClienteIA.responder(r, sistema(r, m), historial, m.texto))
                }
                ProveedorIA.SHEETS -> {
                    val hallado = Fuentes.sheets(r, m)
                    val plantilla = textosFijos.firstOrNull { MARCA_SHEETS in it.lowercase() }
                    when {
                        hallado == null -> listOfNotNull(r.sheetsAlternativa.trim().ifEmpty { null })
                        plantilla != null -> listOf(plantilla.replace(MARCA_SHEETS, hallado, ignoreCase = true))
                        else -> listOf(hallado)
                    }
                }
                ProveedorIA.DIALOGFLOW -> Fuentes.dialogflow(r, m)
                ProveedorIA.SERVIDOR -> Fuentes.servidor(r, m)
                ProveedorIA.TASKER -> {
                    val t = Automatizacion.preguntarTasker(ctx, r, m)
                    if (t == null) error = "Tasker/MacroDroid no respondió en ${r.taskerEspera} s"
                    listOfNotNull(t)
                }
                ProveedorIA.NINGUNO -> emptyList()
            }
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            emptyList()
        }

        val permitirArchivos = !r.proveedor.esIA || r.iaPuedeEnviarArchivos
        val textos = mutableListOf<String>()
        for (crudo in externos) {
            val claves = etiquetaArchivo.findAll(crudo).map { it.groupValues[1].trim() }.toList()
            if (permitirArchivos) adjuntos += claves.filter { Almacen.archivo(it) != null }
            val limpio = crudo.replace(etiquetaArchivo, "").replace(Regex("\\n{3,}"), "\n\n").trim()
            if (limpio.isNotBlank()) textos += limpio
        }
        // Si la fuente no dio respuesta (error o sin coincidencia), se usa el texto fijo como respaldo
        // (con Sheets, un texto que contiene %sheet_result% es una plantilla, no un respaldo)
        val respaldo = if (r.proveedor == ProveedorIA.SHEETS) textosFijos.filterNot { MARCA_SHEETS in it.lowercase() } else textosFijos
        val finales = textos.ifEmpty { elegir(respaldo, r.modo) }
        return Resultado(finales.map { cierre(it, r, m) }, adjuntos.distinct(), error)
    }

    /** Variables, encabezado y pie, y grupos de captura de la expresión regular (%1%, %2%… y %0% = todo). */
    private fun cierre(t: String, r: Regla, m: MensajeEntrante): String {
        val texto = final(t, m)
        if (r.tipo != TipoCoincidencia.REGEX) return texto
        val grupos = runCatching { Regex(r.patron, RegexOption.IGNORE_CASE).find(m.texto)?.groupValues }.getOrNull()
            ?: return texto
        var s = texto
        for (i in grupos.indices) s = s.replace("%$i%", grupos[i])
        return s
    }

    private fun elegir(lista: List<String>, modo: ModoRespuesta): List<String> = when {
        lista.isEmpty() -> emptyList()
        modo == ModoRespuesta.TODAS -> lista
        modo == ModoRespuesta.ALEATORIA -> listOf(lista.random())
        modo == ModoRespuesta.DIARIA -> {
            val cal = Calendar.getInstance()
            val dia = cal.get(Calendar.YEAR) * 366 + cal.get(Calendar.DAY_OF_YEAR)
            listOf(lista[dia % lista.size])
        }
        else -> listOf(lista.first())
    }

    /** Variables, reemplazos personalizados, encabezado y pie. */
    private fun final(t: String, m: MensajeEntrante): String {
        val enc = Ajustes.encabezado.trim()
        val pie = Ajustes.pie.trim()
        val cuerpo = buildString {
            if (enc.isNotEmpty()) append(enc).append("\n")
            append(t)
            if (pie.isNotEmpty()) append("\n").append(pie)
        }
        return variables(cuerpo, m)
    }

    private fun sistema(r: Regla, m: MensajeEntrante): String = buildString {
        append(variables(r.promptSistema.ifBlank { Regla.PROMPT_DEFECTO }, m))
        append("\n\nContexto: estás respondiendo por WhatsApp")
        append(if (m.esGrupo) " en el grupo \"${m.nombreChat}\" a ${m.remitente}." else " a ${m.remitente}.")
        append(" Fecha y hora actual: ${fecha("EEEE dd/MM/yyyy HH:mm")}.")
        append(" Escribe en texto plano apto para WhatsApp (puedes usar *negrita*), sin encabezados Markdown.")
        if (r.iaPuedeEnviarArchivos) {
            val biblioteca = Almacen.biblioteca()
            if (biblioteca.isNotEmpty()) {
                append("\n\nPuedes enviar archivos al usuario escribiendo exactamente [[archivo:CLAVE]] en tu respuesta ")
                append("(la etiqueta no se mostrará). Hazlo solo cuando sea útil o te lo pidan. Archivos disponibles:\n")
                biblioteca.forEach { append("- ${it.clave}: ${it.descripcion.ifBlank { it.mime }}\n") }
            }
        }
    }

    fun variables(t: String, m: MensajeEntrante): String {
        var s = t
        // Reemplazos personalizados primero (pueden contener variables integradas)
        for (rep in Almacen.reemplazos()) {
            if (rep.clave.isNotBlank()) s = s.replace("%${rep.clave}%", rep.valor, ignoreCase = true)
        }
        val hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val saludo = when (hora) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        return s
            .replace("%nombre%", m.remitente, ignoreCase = true)
            .replace("%chat%", m.nombreChat, ignoreCase = true)
            .replace("%mensaje%", m.texto, ignoreCase = true)
            .replace("%hora%", fecha("HH:mm"), ignoreCase = true)
            .replace("%fecha%", fecha("dd/MM/yyyy"), ignoreCase = true)
            .replace("%dia%", fecha("EEEE"), ignoreCase = true)
            .replace("%saludo%", saludo, ignoreCase = true)
    }

    private fun fecha(formato: String) = SimpleDateFormat(formato, es).format(Date())
}
