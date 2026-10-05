package com.autorespuesta.ia.servicio

import android.util.Log
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.MensajeEntrante
import com.autorespuesta.ia.datos.ModoSalida
import com.autorespuesta.ia.datos.Regla
import com.autorespuesta.ia.motor.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Salida de mensajes": registra cada mensaje (y su respuesta) en Google Sheets
 * (mediante un script de Google Apps Script) y/o en un webhook, con un POST en JSON.
 */
object Salida {

    /** Valores de "Cuándo registrar" globales. */
    const val NUNCA = 0
    const val RESPONDIDOS = 1
    const val TODOS = 2

    private fun debeRegistrar(global: Int, porRegla: ModoSalida?, regla: Regla?, respondido: Boolean): Boolean =
        when (porRegla) {
            ModoSalida.SIEMPRE -> true
            ModoSalida.NUNCA -> false
            else -> global == TODOS || (global == RESPONDIDOS && regla != null && respondido)
        }

    fun registrar(alcance: CoroutineScope, m: MensajeEntrante, regla: Regla?, respuesta: String, error: String?) {
        val respondido = regla != null && !regla.noResponder && respuesta.isNotBlank()
        val destinos = mutableListOf<String>()
        val urlSheets = Ajustes.salidaSheetsUrl
        if (urlSheets.startsWith("http") &&
            debeRegistrar(Ajustes.salidaSheetsCuando, regla?.salidaSheets, regla, respondido)
        ) destinos += urlSheets
        val urlWebhook = Ajustes.salidaWebhookUrl
        if (urlWebhook.startsWith("http") &&
            debeRegistrar(Ajustes.salidaWebhookCuando, regla?.salidaWebhook, regla, respondido)
        ) destinos += urlWebhook
        if (destinos.isEmpty()) return

        val cuerpo = JSONObject()
            .put("fecha", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .put("app", if (m.paquete.endsWith("w4b")) "WhatsApp Business" else "WhatsApp")
            .put("chat", m.nombreChat)
            .put("remitente", m.remitente)
            .put("grupo", m.esGrupo)
            .put("mensaje", m.texto)
            .put("respuesta", respuesta)
            .put("regla", regla?.nombre ?: "")
            .put("error", error ?: "")
            .toString()

        alcance.launch {
            withContext(Dispatchers.IO) {
                for (url in destinos) {
                    try {
                        val r = Http.peticion(url, "POST", cuerpo = cuerpo, lectura = 20_000)
                        if (r.codigo !in 200..399) Log.w("AutoRespuestaIA", "Salida $url respondió ${r.codigo}")
                    } catch (e: Exception) {
                        Log.w("AutoRespuestaIA", "No se pudo registrar en $url", e)
                    }
                }
            }
        }
    }
}
