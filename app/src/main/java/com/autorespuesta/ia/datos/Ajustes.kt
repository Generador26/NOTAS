package com.autorespuesta.ia.datos

import android.content.Context
import android.content.SharedPreferences

/** Ajustes generales de la app (se guardan solo en el teléfono). */
object Ajustes {
    const val PAQUETE_WHATSAPP = "com.whatsapp"
    const val PAQUETE_BUSINESS = "com.whatsapp.w4b"
    const val URL_OPENAI_DEFECTO = "https://api.openai.com/v1"

    private lateinit var p: SharedPreferences

    fun init(ctx: Context) {
        if (!::p.isInitialized) {
            p = ctx.applicationContext.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        }
    }

    /** Lo registra el servicio de notificaciones para mostrar/quitar el aviso fijo «Esperando nuevos mensajes…». */
    @Volatile var alCambiarActivo: (() -> Unit)? = null

    var activo: Boolean
        get() = p.getBoolean("activo", false)
        set(v) {
            p.edit().putBoolean("activo", v).apply()
            alCambiarActivo?.invoke()
        }

    var usarWhatsApp: Boolean
        get() = p.getBoolean("usarWhatsApp", true)
        set(v) = p.edit().putBoolean("usarWhatsApp", v).apply()

    var usarBusiness: Boolean
        get() = p.getBoolean("usarBusiness", true)
        set(v) = p.edit().putBoolean("usarBusiness", v).apply()

    var claveClaude: String
        get() = p.getString("claveClaude", "") ?: ""
        set(v) = p.edit().putString("claveClaude", v.trim()).apply()

    var claveOpenAI: String
        get() = p.getString("claveOpenAI", "") ?: ""
        set(v) = p.edit().putString("claveOpenAI", v.trim()).apply()

    var claveGemini: String
        get() = p.getString("claveGemini", "") ?: ""
        set(v) = p.edit().putString("claveGemini", v.trim()).apply()

    /** URL base compatible con OpenAI (sirve también para Groq, DeepSeek, OpenRouter...). */
    var urlOpenAI: String
        get() = (p.getString("urlOpenAI", URL_OPENAI_DEFECTO) ?: URL_OPENAI_DEFECTO).ifBlank { URL_OPENAI_DEFECTO }
        set(v) = p.edit().putString("urlOpenAI", v.trim()).apply()

    var ignorarAcentos: Boolean
        get() = p.getBoolean("ignorarAcentos", true)
        set(v) = p.edit().putBoolean("ignorarAcentos", v).apply()

    var ignoradosGlobales: String
        get() = p.getString("ignoradosGlobales", "") ?: ""
        set(v) = p.edit().putString("ignoradosGlobales", v).apply()

    var encabezado: String
        get() = p.getString("encabezado", "") ?: ""
        set(v) = p.edit().putString("encabezado", v).apply()

    var pie: String
        get() = p.getString("pie", "") ?: ""
        set(v) = p.edit().putString("pie", v).apply()

    /** Umbral (0-100) para la coincidencia de similitud. */
    var umbralSimilitud: Int
        get() = p.getInt("umbralSimilitud", 50)
        set(v) = p.edit().putInt("umbralSimilitud", v.coerceIn(0, 100)).apply()

    /** No enviar otra respuesta si el mismo contacto activa la misma regla seguida. */
    var evitarRepetir: Boolean
        get() = p.getBoolean("evitarRepetir", false)
        set(v) = p.edit().putBoolean("evitarRepetir", v).apply()

    /** Duración en segundos de "evitar repetir" (0 = sin límite). */
    var evitarRepetirSegundos: Int
        get() = p.getInt("evitarRepetirSegundos", 0)
        set(v) = p.edit().putInt("evitarRepetirSegundos", v.coerceAtLeast(0)).apply()

    /** Patrones (separados por comas) que bloquean cualquier respuesta. */
    var noResponderSi: String
        get() = p.getString("noResponderSi", "") ?: ""
        set(v) = p.edit().putString("noResponderSi", v).apply()

    var notificaciones: Boolean
        get() = p.getBoolean("notificaciones", true)
        set(v) = p.edit().putBoolean("notificaciones", v).apply()

    var notificacionesPrioritarias: Boolean
        get() = p.getBoolean("notificacionesPrioritarias", true)
        set(v) = p.edit().putBoolean("notificacionesPrioritarias", v).apply()

    /** Si un contacto envía este mensaje, deja de recibir respuestas automáticas. */
    var palabraIgnorar: String
        get() = p.getString("palabraIgnorar", "") ?: ""
        set(v) = p.edit().putString("palabraIgnorar", v.trim()).apply()

    /** Si un contacto ignorado envía este mensaje, vuelve a recibir respuestas. */
    var palabraReanudar: String
        get() = p.getString("palabraReanudar", "") ?: ""
        set(v) = p.edit().putString("palabraReanudar", v.trim()).apply()

    /** JSON de la cuenta de servicio de Google Cloud para Dialogflow ES. */
    var dialogflowJson: String
        get() = p.getString("dialogflowJson", "") ?: ""
        set(v) = p.edit().putString("dialogflowJson", v.trim()).apply()

    /** Salida de mensajes: URL de Google Apps Script (Sheets) y de webhook. */
    var salidaSheetsUrl: String
        get() = p.getString("salidaSheetsUrl", "") ?: ""
        set(v) = p.edit().putString("salidaSheetsUrl", v.trim()).apply()

    var salidaWebhookUrl: String
        get() = p.getString("salidaWebhookUrl", "") ?: ""
        set(v) = p.edit().putString("salidaWebhookUrl", v.trim()).apply()

    /** 0 = nunca, 1 = solo mensajes respondidos, 2 = todos los mensajes recibidos. */
    var salidaSheetsCuando: Int
        get() = p.getInt("salidaSheetsCuando", 1)
        set(v) = p.edit().putInt("salidaSheetsCuando", v).apply()

    var salidaWebhookCuando: Int
        get() = p.getInt("salidaWebhookCuando", 1)
        set(v) = p.edit().putInt("salidaWebhookCuando", v).apply()

    fun paquetes(): Set<String> = buildSet {
        if (usarWhatsApp) add(PAQUETE_WHATSAPP)
        if (usarBusiness) add(PAQUETE_BUSINESS)
    }
}
