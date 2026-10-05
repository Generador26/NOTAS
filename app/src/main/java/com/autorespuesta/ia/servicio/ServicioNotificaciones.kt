package com.autorespuesta.ia.servicio

import android.app.Notification
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.EventoHistorial
import com.autorespuesta.ia.datos.MensajeEntrante
import com.autorespuesta.ia.datos.Regla
import com.autorespuesta.ia.motor.Motor
import com.autorespuesta.ia.motor.Texto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Escucha las notificaciones de WhatsApp. Cuando llega un mensaje que coincide con una regla,
 * responde usando el botón "Responder" de la propia notificación (igual que AutoResponder).
 */
class ServicioNotificaciones : NotificationListenerService() {

    companion object {
        private const val TAG = "AutoRespuestaIA"
        @Volatile var conectado = false
            private set
    }

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Texto del aviso fijo; se actualiza con lo último que pasó. */
    private var estadoTexto = "Esperando nuevos mensajes…"

    /** Últimos textos que enviamos por chat, para no respondernos a nosotros mismos. */
    private val enviadosRecientes = HashMap<String, MutableList<Pair<String, Long>>>()

    /** Respuestas en espera (retraso) por chat, para poder cancelarlas. */
    private val pendientes = HashMap<String, MutableList<Job>>()

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = Automatizacion.procesar(context, intent)
    }

    override fun onCreate() {
        super.onCreate()
        Ajustes.init(this)
        Almacen.init(this)
        Motor.init(this)
        Ajustes.alCambiarActivo = { actualizarPrimerPlano() }
        try {
            Automatizacion.registrar(this, receptor)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar el receptor de automatización", e)
        }
    }

    override fun onDestroy() {
        Ajustes.alCambiarActivo = null
        runCatching { unregisterReceiver(receptor) }
        alcance.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        conectado = true
        actualizarPrimerPlano()
    }

    override fun onListenerDisconnected() {
        conectado = false
        // Algunos teléfonos desconectan el servicio: se pide al sistema volver a conectarlo
        try {
            requestRebind(ComponentName(this, ServicioNotificaciones::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo pedir la reconexión", e)
        }
    }

    /**
     * Mientras la app está activa muestra el aviso fijo «Esperando nuevos mensajes…».
     * Es lo que impide que el sistema (sobre todo en Xiaomi, Oppo, Realme, Huawei…) mate el servicio.
     */
    private fun actualizarPrimerPlano() {
        try {
            if (Ajustes.activo) {
                val aviso = Avisos.notificacionEstado(this, estadoTexto)
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(Avisos.ID_ESTADO, aviso, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(Avisos.ID_ESTADO, aviso)
                }
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo mostrar el aviso fijo", e)
        }
    }

    private fun estado(texto: String) {
        val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        estadoTexto = "$texto · $hora"
        if (Ajustes.activo) actualizarPrimerPlano()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            procesar(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando notificación", e)
        }
    }

    private fun procesar(sbn: StatusBarNotification) {
        if (!Ajustes.activo) return
        val paquete = sbn.packageName
        if (paquete !in Ajustes.paquetes()) return

        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val accion = accionResponder(n) ?: return   // sin botón "Responder" no es un mensaje

        val extras = n.extras
        val estilo = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val tituloBase = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val titulo = limpiarTitulo(
            (estilo?.conversationTitle
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))?.toString() ?: tituloBase
        )

        // WhatsApp usa el "jid" (ej. 51987654321@s.whatsapp.net) como id del acceso directo
        val jid = listOf(n.shortcutId, sbn.tag).firstOrNull { it != null && it.contains("@") }
        val chatId = jid ?: n.shortcutId ?: sbn.tag ?: titulo.ifBlank { return }
        val esGrupo = estilo?.isGroupConversation == true ||
            extras.getBoolean("android.isGroupConversation", false) ||
            jid?.endsWith("@g.us") == true

        val ultimoTs = Almacen.ultimoTs(chatId)
        var maxTs = ultimoTs
        var remitente = titulo
        val textos = mutableListOf<String>()

        if (estilo != null && estilo.messages.isNotEmpty()) {
            val nombreUsuario = estilo.user.name?.toString()
            val nuevos = estilo.messages.filter { it.timestamp > ultimoTs }
            // La primera vez que vemos este chat, solo respondemos al último mensaje
            val candidatos = if (ultimoTs == 0L) nuevos.takeLast(1) else nuevos
            for (msg in candidatos) {
                maxTs = maxOf(maxTs, msg.timestamp)
                val persona = msg.person ?: continue           // sin remitente = mensaje propio
                val nombre = persona.name?.toString()
                if (nombre != null && nombre == nombreUsuario) continue
                val t = msg.text?.toString() ?: continue
                if (esEnviadoReciente(chatId, t)) continue
                textos += t
                if (!nombre.isNullOrBlank()) remitente = nombre
            }
        } else {
            val t = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
            val ts = n.`when`
            if (ts <= ultimoTs) return
            maxTs = ts
            if (!esEnviadoReciente(chatId, t)) textos += t
        }

        if (maxTs > ultimoTs) Almacen.guardarUltimoTs(chatId, maxTs)
        if (textos.isEmpty()) return

        val mensaje = MensajeEntrante(
            paquete = paquete,
            chatId = chatId,
            nombreChat = titulo,
            remitente = remitente,
            texto = textos.joinToString("\n"),
            esGrupo = esGrupo,
            jid = jid
        )

        // Palabras para dejar de recibir / volver a recibir respuestas automáticas
        val normal = Texto.normalizar(mensaje.texto, true)
        val ignorar = Texto.normalizar(Ajustes.palabraIgnorar, true)
        val reanudar = Texto.normalizar(Ajustes.palabraReanudar, true)
        if (ignorar.isNotEmpty() && normal == ignorar) {
            Almacen.cambiarIgnorado(chatId, true)
            registrarEvento(mensaje, "(ya no recibirá respuestas automáticas)", "Automatización", null)
            return
        }
        if (reanudar.isNotEmpty() && normal == reanudar) {
            Almacen.cambiarIgnorado(chatId, false)
            registrarEvento(mensaje, "(vuelve a recibir respuestas automáticas)", "Automatización", null)
            return
        }

        Almacen.contarMensaje(chatId)
        val regla = Motor.buscarRegla(mensaje)
        Almacen.marcarVisto(chatId)
        if (regla == null) {
            estado("Mensaje de ${mensaje.nombreChat}: ninguna regla coincide")
            Salida.registrar(alcance, mensaje, null, "", null)
            return
        }
        Almacen.marcarRegla(chatId, regla.id)
        if (regla.noResponder) {
            // Regla de bloqueo: coincide, pero no se envía nada
            registrarEvento(mensaje, "🚫 (no responder)", regla.nombre.ifBlank { "Regla" }, null)
            Salida.registrar(alcance, mensaje, regla, "", null)
            return
        }

        val lista = pendientes.getOrPut(chatId) { mutableListOf() }
        lista.removeAll { it.isCompleted }
        if (regla.cancelarRetrasados) {
            lista.forEach { it.cancel() }
            lista.clear()
        }
        lista += alcance.launch { responder(regla, mensaje, accion) }
    }

    private suspend fun responder(r: Regla, m: MensajeEntrante, accion: Notification.Action) {
        val min = r.delayMin.coerceAtLeast(0)
        val max = r.delayMax.coerceAtLeast(min)
        val espera = if (max > 0) Random.nextInt(min, max + 1) else 0
        if (espera > 0) delay(espera * 1000L)

        ejecutar(r, m, accion, encadenada = false)

        // Submenú / flujo de conversación: "Ir a regla"
        val destino = r.irARegla.takeIf { it.isNotBlank() }
            ?.let { id -> Almacen.reglas().firstOrNull { it.id == id && it.id != r.id } }
        if (destino != null) {
            Almacen.marcarRegla(m.chatId, destino.id)
            delay(1500)
            ejecutar(destino, m, accion, encadenada = true)
        }
    }

    private suspend fun ejecutar(r: Regla, m: MensajeEntrante, accion: Notification.Action, encadenada: Boolean) {
        val res = Motor.generar(r, m)
        var error = res.error
        res.textos.forEachIndexed { i, t ->
            if (i > 0) delay(1200)
            registrarEnviado(m.chatId, t)
            if (!enviarTexto(accion, t)) error = (error?.plus(" · ") ?: "") + "No se pudo enviar el texto"
        }
        if (res.textos.isNotEmpty()) {
            if (!encadenada) Almacen.agregarConversacion(m.chatId, "user", m.texto)
            Almacen.agregarConversacion(m.chatId, "assistant", res.textos.joinToString("\n"))
        }

        val nombresAdjuntos = mutableListOf<String>()
        for (clave in res.adjuntos) {
            val a = Almacen.archivo(clave) ?: continue
            if (m.jid == null) {
                error = (error?.plus(" · ") ?: "") + "No se pudo identificar el chat para enviar \"$clave\""
                continue
            }
            nombresAdjuntos += clave
            EnviadorArchivos.encolar(
                this,
                EnviadorArchivos.Trabajo(m.paquete, m.jid, m.nombreChat, Almacen.ficheroDe(a), a.mime, a.pie, clave)
            )
        }

        val resumen = buildString {
            append(res.textos.joinToString("\n"))
            if (nombresAdjuntos.isNotEmpty()) append("\n📎 ").append(nombresAdjuntos.joinToString(", "))
        }.ifBlank { "(sin respuesta)" }
        val nombreRegla = r.nombre.ifBlank { "Regla" }
        registrarEvento(m, resumen, nombreRegla, error)
        Salida.registrar(alcance, m, r, if (resumen == "(sin respuesta)") "" else resumen, error)

        if (res.textos.isNotEmpty() || nombresAdjuntos.isNotEmpty()) {
            estado("Respondido a ${m.nombreChat}")
            Avisos.notificar(this, m.nombreChat, "🤖 $resumen", r.notificacionPrioritaria)
            Automatizacion.avisarEnviada(this, m, resumen, nombreRegla)
        } else if (error != null) {
            Avisos.notificar(this, "No se pudo responder a ${m.nombreChat}", error ?: "", r.notificacionPrioritaria)
        }
    }

    private fun registrarEvento(m: MensajeEntrante, respuesta: String, regla: String, error: String?) {
        Almacen.agregarHistorial(EventoHistorial(System.currentTimeMillis(), m.nombreChat, m.texto, respuesta, regla, error))
    }

    /** "Juan (2 mensajes)" -> "Juan"; "Familia: Ana" se deja igual. */
    private fun limpiarTitulo(t: String): String =
        t.replace(
            Regex("\\s*\\(\\d+\\s+(nuevos\\s+)?(mensajes?|messages?|mensagens?|new messages?)\\)\\s*$", RegexOption.IGNORE_CASE),
            ""
        ).trim()

    /** Busca la acción de la notificación que tiene campo de texto (el botón "Responder"). */
    private fun accionResponder(n: Notification): Notification.Action? {
        n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }?.let { return it }
        return Notification.WearableExtender(n).actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
    }

    private fun enviarTexto(accion: Notification.Action, texto: String): Boolean = try {
        val entradas = accion.remoteInputs
        val intent = Intent()
        val datos = Bundle()
        entradas.forEach { datos.putCharSequence(it.resultKey, texto) }
        RemoteInput.addResultsToIntent(entradas, intent, datos)
        accion.actionIntent.send(this, 0, intent)
        true
    } catch (e: Exception) {
        Log.e(TAG, "No se pudo enviar la respuesta", e)
        false
    }

    private fun registrarEnviado(chatId: String, texto: String) {
        val lista = enviadosRecientes.getOrPut(chatId) { mutableListOf() }
        lista.add(texto.trim() to System.currentTimeMillis())
        if (lista.size > 10) lista.removeAt(0)
    }

    private fun esEnviadoReciente(chatId: String, texto: String): Boolean {
        val limite = System.currentTimeMillis() - 5 * 60_000
        return enviadosRecientes[chatId]?.any { it.second > limite && it.first == texto.trim() } == true
    }
}
