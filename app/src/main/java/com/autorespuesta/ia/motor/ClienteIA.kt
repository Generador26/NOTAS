package com.autorespuesta.ia.motor

import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.ProveedorIA
import com.autorespuesta.ia.datos.Regla
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Llama a Claude (Anthropic), ChatGPT (OpenAI o API compatible) o Gemini (Google).
 *
 * Formatos verificados con la documentación oficial (octubre 2026):
 *  - Claude:  POST https://api.anthropic.com/v1/messages, cabeceras x-api-key + anthropic-version: 2023-06-01
 *  - OpenAI:  POST https://api.openai.com/v1/chat/completions, Authorization: Bearer
 *  - Gemini:  POST https://generativelanguage.googleapis.com/v1beta/openai/chat/completions (compatible con OpenAI)
 */
object ClienteIA {
    const val MODELO_CLAUDE = "claude-haiku-4-5-20251001"
    const val MODELO_OPENAI = "gpt-4.1-mini"
    /** Google limita los modelos 2.5 a usuarios antiguos; para claves nuevas se usa la serie 3.x. */
    const val MODELO_GEMINI = "gemini-3.5-flash-lite"
    private const val URL_CLAUDE = "https://api.anthropic.com/v1/messages"
    private const val URL_GEMINI = "https://generativelanguage.googleapis.com/v1beta/openai"

    /** Margen extra de tokens para modelos que "piensan" antes de responder (no se cobra si no se usa). */
    private const val MARGEN_RAZONAMIENTO = 4096

    suspend fun responder(
        regla: Regla,
        sistema: String,
        historial: List<Pair<String, String>>,
        mensaje: String
    ): String = withContext(Dispatchers.IO) {
        val mensajes = ordenar(historial + ("user" to mensaje))
        when (regla.proveedor) {
            ProveedorIA.CLAUDE -> claude(regla, sistema, mensajes)
            ProveedorIA.OPENAI -> {
                val base = Ajustes.urlOpenAI.trimEnd('/')
                val esOpenAI = base.contains("api.openai.com")
                if (Ajustes.claveOpenAI.isBlank() && esOpenAI) {
                    throw Exception("Falta la API key de OpenAI (ponla en la regla o en Configuración)")
                }
                val modelo = regla.modelo.ifBlank { MODELO_OPENAI }
                chatCompatible(
                    base = base, clave = Ajustes.claveOpenAI, modelo = modelo, r = regla,
                    sistema = sistema, mensajes = mensajes, nombre = "ChatGPT",
                    // OpenAI usa max_completion_tokens (max_tokens está obsoleto y los modelos de
                    // razonamiento lo rechazan); otros servicios compatibles siguen usando max_tokens.
                    usarMaxCompletion = esOpenAI,
                    razonamiento = esOpenAI && esModeloRazonamientoOpenAI(modelo)
                )
            }
            ProveedorIA.GEMINI -> {
                if (Ajustes.claveGemini.isBlank()) throw Exception("Falta la API key de Gemini (ponla en la regla o en Configuración)")
                chatCompatible(
                    base = URL_GEMINI, clave = Ajustes.claveGemini, modelo = regla.modelo.ifBlank { MODELO_GEMINI },
                    r = regla, sistema = sistema, mensajes = mensajes, nombre = "Gemini",
                    usarMaxCompletion = false,
                    // Los Gemini 3.x siempre "piensan" y esos tokens cuentan en el límite
                    razonamiento = true
                )
            }
            else -> throw IllegalStateException("La regla no usa IA")
        }
    }

    /** Modelos de OpenAI que razonan (o1, o3, o4, gpt-5…): no aceptan temperature y gastan tokens pensando. */
    private fun esModeloRazonamientoOpenAI(modelo: String): Boolean {
        val m = modelo.lowercase()
        return !(m.startsWith("gpt-4") || m.startsWith("gpt-3") || m.startsWith("chatgpt-4"))
    }

    /** Las APIs exigen que la conversación empiece por "user" y alterne roles. */
    fun ordenar(lista: List<Pair<String, String>>): List<Pair<String, String>> {
        val salida = mutableListOf<Pair<String, String>>()
        for ((rol, texto) in lista) {
            if (texto.isBlank()) continue
            if (salida.isEmpty() && rol != "user") continue
            if (salida.isNotEmpty() && salida.last().first == rol) {
                salida[salida.lastIndex] = rol to (salida.last().second + "\n" + texto)
            } else {
                salida.add(rol to texto)
            }
        }
        return salida
    }

    private fun temperatura(r: Regla, maximo: Double): Double? =
        r.temperatura.replace(',', '.').trim().toDoubleOrNull()?.coerceIn(0.0, maximo)

    private fun claude(r: Regla, sistema: String, mensajes: List<Pair<String, String>>): String {
        val clave = Ajustes.claveClaude
        if (clave.isBlank()) throw Exception("Falta la API key de Claude (ponla en la regla o en Configuración)")

        val arr = JSONArray()
        mensajes.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val cuerpo = JSONObject()
            .put("model", r.modelo.ifBlank { MODELO_CLAUDE })
            .put("max_tokens", r.maxTokens.coerceIn(16, 8000))
            .put("system", sistema)
            .put("messages", arr)
        temperatura(r, 1.0)?.let { cuerpo.put("temperature", it) }

        val resp = postConReintento(URL_CLAUDE, mapOf("x-api-key" to clave, "anthropic-version" to "2023-06-01"), cuerpo)
        return leerRespuestaClaude(resp)
    }

    /** Une los bloques de texto de la respuesta de Claude. */
    fun leerRespuestaClaude(resp: JSONObject): String {
        val bloques = resp.optJSONArray("content") ?: throw Exception("Claude devolvió una respuesta vacía")
        val sb = StringBuilder()
        for (i in 0 until bloques.length()) {
            val b = bloques.getJSONObject(i)
            if (b.texto("type") == "text") sb.append(b.texto("text"))
        }
        val texto = sb.toString().trim()
        if (texto.isEmpty()) {
            val motivo = resp.texto("stop_reason")
            throw Exception(
                if (motivo == "max_tokens") "Claude se quedó sin tokens: sube «Máx. tokens» en la regla"
                else "Claude no devolvió texto (motivo: ${motivo.ifBlank { "desconocido" }})"
            )
        }
        return texto
    }

    /** Formato "chat/completions" de OpenAI; Gemini ofrece un endpoint compatible. */
    private fun chatCompatible(
        base: String,
        clave: String,
        modelo: String,
        r: Regla,
        sistema: String,
        mensajes: List<Pair<String, String>>,
        nombre: String,
        usarMaxCompletion: Boolean,
        razonamiento: Boolean
    ): String {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", sistema))
        mensajes.forEach { arr.put(JSONObject().put("role", it.first).put("content", it.second)) }

        val limite = r.maxTokens.coerceIn(16, 8000) + if (razonamiento) MARGEN_RAZONAMIENTO else 0
        val cuerpo = JSONObject()
            .put("model", modelo)
            .put("messages", arr)
            .put(if (usarMaxCompletion) "max_completion_tokens" else "max_tokens", limite)
        // Pensar poco: respuestas de chat rápidas y baratas
        if (razonamiento) cuerpo.put("reasoning_effort", "low")
        // Los modelos de razonamiento de OpenAI solo aceptan la temperatura por defecto
        if (!(razonamiento && usarMaxCompletion)) temperatura(r, 2.0)?.let { cuerpo.put("temperature", it) }

        val cabeceras = if (clave.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $clave")
        val resp = postConReintento("$base/chat/completions", cabeceras, cuerpo)
        return leerRespuestaChat(resp, nombre)
    }

    /** Lee choices[0].message.content (formato OpenAI / Gemini compatible). */
    fun leerRespuestaChat(resp: JSONObject, nombre: String): String {
        val opciones = resp.optJSONArray("choices")
        if (opciones == null || opciones.length() == 0) throw Exception("$nombre devolvió una respuesta vacía")
        val opcion = opciones.getJSONObject(0)
        val mensaje = opcion.optJSONObject("message")
        val texto = mensaje?.texto("content")?.trim().orEmpty()
        if (texto.isEmpty()) {
            val motivo = opcion.texto("finish_reason")
            val rechazo = mensaje?.texto("refusal").orEmpty()
            throw Exception(
                when {
                    rechazo.isNotBlank() -> "$nombre rechazó responder: $rechazo"
                    motivo == "length" -> "$nombre se quedó sin tokens: sube «Máx. tokens» en la regla"
                    motivo == "content_filter" -> "$nombre bloqueó la respuesta por su filtro de contenido"
                    else -> "$nombre no devolvió texto (motivo: ${motivo.ifBlank { "desconocido" }})"
                }
            )
        }
        return texto
    }

    /**
     * Envía la petición. Si la API rechaza un parámetro concreto (p. ej. un modelo nuevo que no acepta
     * "temperature" o "max_tokens"), lo ajusta y reintenta automáticamente (máximo 3 intentos).
     */
    fun postConReintento(url: String, cabeceras: Map<String, String>, cuerpo: JSONObject): JSONObject {
        var ultimo: Exception? = null
        for (intento in 1..3) {
            try {
                return Http.postJson(url, cabeceras, cuerpo)
            } catch (e: Http.ErrorApi) {
                ultimo = e
                if (e.codigo != 400 && e.codigo != 422) throw e
                if (!ajustarParametro(e.detalle.lowercase(), cuerpo, url)) throw e
            }
        }
        throw ultimo ?: Exception("No se pudo contactar con la IA")
    }

    /** Quita o renombra el parámetro que la API dice no aceptar. Devuelve true si cambió algo. */
    fun ajustarParametro(error: String, cuerpo: JSONObject, url: String): Boolean = when {
        "temperature" in error && cuerpo.has("temperature") -> { cuerpo.remove("temperature"); true }
        "reasoning_effort" in error && cuerpo.has("reasoning_effort") -> { cuerpo.remove("reasoning_effort"); true }
        "max_completion_tokens" in error && cuerpo.has("max_completion_tokens") ->
            { cuerpo.put("max_tokens", cuerpo.remove("max_completion_tokens")); true }
        "max_tokens" in error && cuerpo.has("max_tokens") && !url.contains("anthropic.com") ->
            { cuerpo.put("max_completion_tokens", cuerpo.remove("max_tokens")); true }
        else -> false
    }
}
