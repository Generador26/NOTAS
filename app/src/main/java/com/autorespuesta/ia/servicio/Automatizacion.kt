package com.autorespuesta.ia.servicio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.MensajeEntrante
import com.autorespuesta.ia.datos.Regla
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Integración con Tasker, MacroDroid, Automate, etc. mediante "intents" (broadcasts).
 *
 * Que la app ENVÍA:
 *  - com.autorespuesta.ia.MENSAJE_RECIBIDO  (reglas con fuente Tasker) extras: id, mensaje, remitente, chat, grupo, regla, app
 *  - com.autorespuesta.ia.RESPUESTA_ENVIADA (tras cada respuesta)      extras: mensaje, respuesta, remitente, chat, regla
 *
 * Que la app RECIBE:
 *  - com.autorespuesta.ia.RESPUESTA         extras: respuesta (texto), id (opcional)
 *  - com.autorespuesta.ia.ACTIVAR / DESACTIVAR
 *  - com.autorespuesta.ia.ACTIVAR_REGLA / DESACTIVAR_REGLA  extra: regla (nombre)
 */
object Automatizacion {
    const val ACCION_MENSAJE = "com.autorespuesta.ia.MENSAJE_RECIBIDO"
    const val ACCION_ENVIADA = "com.autorespuesta.ia.RESPUESTA_ENVIADA"
    const val ACCION_RESPUESTA = "com.autorespuesta.ia.RESPUESTA"
    const val ACCION_ACTIVAR = "com.autorespuesta.ia.ACTIVAR"
    const val ACCION_DESACTIVAR = "com.autorespuesta.ia.DESACTIVAR"
    const val ACCION_ACTIVAR_REGLA = "com.autorespuesta.ia.ACTIVAR_REGLA"
    const val ACCION_DESACTIVAR_REGLA = "com.autorespuesta.ia.DESACTIVAR_REGLA"

    private val pendientes = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /** Envía el mensaje a Tasker/MacroDroid y espera su respuesta (o null si no contesta a tiempo). */
    suspend fun preguntarTasker(ctx: Context, r: Regla, m: MensajeEntrante): String? {
        val id = UUID.randomUUID().toString()
        val espera = CompletableDeferred<String>()
        pendientes[id] = espera
        try {
            ctx.sendBroadcast(
                Intent(ACCION_MENSAJE)
                    .putExtra("id", id)
                    .putExtra("mensaje", m.texto)
                    .putExtra("remitente", m.remitente)
                    .putExtra("chat", m.nombreChat)
                    .putExtra("grupo", m.esGrupo)
                    .putExtra("regla", r.nombre)
                    .putExtra("app", m.paquete)
            )
            return withTimeoutOrNull(r.taskerEspera.coerceIn(1, 120) * 1000L) { espera.await() }
        } finally {
            pendientes.remove(id)
        }
    }

    fun avisarEnviada(ctx: Context, m: MensajeEntrante, respuesta: String, regla: String) {
        try {
            ctx.sendBroadcast(
                Intent(ACCION_ENVIADA)
                    .putExtra("mensaje", m.texto)
                    .putExtra("respuesta", respuesta)
                    .putExtra("remitente", m.remitente)
                    .putExtra("chat", m.nombreChat)
                    .putExtra("regla", regla)
            )
        } catch (e: Exception) {
            Log.w("AutoRespuestaIA", "No se pudo avisar a Tasker", e)
        }
    }

    fun filtro() = IntentFilter().apply {
        addAction(ACCION_RESPUESTA)
        addAction(ACCION_ACTIVAR)
        addAction(ACCION_DESACTIVAR)
        addAction(ACCION_ACTIVAR_REGLA)
        addAction(ACCION_DESACTIVAR_REGLA)
    }

    /** Registra el receptor en tiempo de ejecución (recibe también intents sin paquete de destino). */
    fun registrar(ctx: Context, receptor: BroadcastReceiver) {
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receptor, filtro(), Context.RECEIVER_EXPORTED)
        } else {
            ctx.registerReceiver(receptor, filtro())
        }
    }

    fun procesar(ctx: Context, intent: Intent) {
        Ajustes.init(ctx)
        Almacen.init(ctx)
        when (intent.action) {
            ACCION_RESPUESTA -> {
                val texto = intent.getStringExtra("respuesta") ?: intent.getStringExtra("mensaje") ?: return
                val id = intent.getStringExtra("id")
                val destino = if (id != null) pendientes[id] else pendientes.values.firstOrNull()
                destino?.complete(texto)
            }
            ACCION_ACTIVAR -> Ajustes.activo = true
            ACCION_DESACTIVAR -> Ajustes.activo = false
            ACCION_ACTIVAR_REGLA, ACCION_DESACTIVAR_REGLA -> {
                val nombre = intent.getStringExtra("regla")?.trim() ?: return
                val activar = intent.action == ACCION_ACTIVAR_REGLA
                val reglas = Almacen.reglas()
                var cambios = false
                reglas.filter { it.nombre.equals(nombre, ignoreCase = true) || it.id == nombre }.forEach {
                    it.activa = activar; cambios = true
                }
                if (cambios) Almacen.guardarReglas(reglas)
            }
        }
    }
}

/** Receptor declarado en el manifiesto (para intents con el paquete com.autorespuesta.ia indicado). */
class ReceptorAutomatizacion : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Automatizacion.procesar(context, intent)
}
