package com.autorespuesta.ia.motor

import android.util.Base64
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.MensajeEntrante
import com.autorespuesta.ia.datos.Regla
import com.autorespuesta.ia.datos.TipoCoincidencia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/** Fuentes de respuesta externas: Google Sheets, Dialogflow ES y tu propio servidor web. */
object Fuentes {

    // ======================= Google Sheets =======================

    private const val CACHE_SHEETS_MS = 3 * 60_000L
    private val cacheSheets = HashMap<String, Pair<Long, List<List<String>>>>()
    private val encabezados = setOf("mensaje", "mensajes", "pregunta", "preguntas", "palabras", "palabras clave", "clave", "entrada")

    /** Convierte el enlace normal de la hoja en un enlace de descarga CSV. */
    fun urlCsv(url: String): String {
        val u = url.trim()
        val gid = Regex("[#&?]gid=(\\d+)").find(u)?.groupValues?.get(1)
        Regex("/spreadsheets/d/e/([A-Za-z0-9_-]+)").find(u)?.let {
            return "https://docs.google.com/spreadsheets/d/e/${it.groupValues[1]}/pub?output=csv" +
                (gid?.let { g -> "&gid=$g" } ?: "")
        }
        Regex("/spreadsheets/d/([A-Za-z0-9_-]+)").find(u)?.let {
            return "https://docs.google.com/spreadsheets/d/${it.groupValues[1]}/export?format=csv" +
                (gid?.let { g -> "&gid=$g" } ?: "")
        }
        return u   // ya es un enlace CSV directo
    }

    private fun filasSheets(url: String): List<List<String>> {
        if (url.isBlank()) throw Exception("Falta el enlace de Google Sheets en la regla")
        synchronized(cacheSheets) {
            cacheSheets[url]?.let { (ts, filas) -> if (System.currentTimeMillis() - ts < CACHE_SHEETS_MS) return filas }
        }
        val r = Http.peticion(urlCsv(url), lectura = 30_000)
        if (r.codigo !in 200..299 || r.cuerpo.trimStart().startsWith("<")) {
            throw Exception("No se pudo leer la hoja. Compártela como «Cualquier persona con el enlace» (lector).")
        }
        val filas = Texto.leerCsv(r.cuerpo)
        synchronized(cacheSheets) { cacheSheets[url] = System.currentTimeMillis() to filas }
        return filas
    }

    fun limpiarCacheSheets() = synchronized(cacheSheets) { cacheSheets.clear() }

    /**
     * Columna A: palabras o frases (separadas por comas). Columna B: respuesta.
     * Devuelve la respuesta de la primera fila que coincide (o la más parecida en modo similitud).
     */
    suspend fun sheets(r: Regla, m: MensajeEntrante): String? = withContext(Dispatchers.IO) {
        val filas = filasSheets(r.sheetsUrl)
        buscarEnFilas(
            filas, m.texto, r.sheetsModo, Ajustes.ignorarAcentos, Ajustes.umbralSimilitud,
            indiceColumna(r.sheetsColBusqueda, 0), indiceColumna(r.sheetsColRespuesta, 1)
        )
    }

    /** «A» → 0, «B» → 1, «AA» → 26; también acepta números (1 = primera columna). */
    fun indiceColumna(texto: String, defecto: Int): Int {
        val t = texto.trim().uppercase()
        if (t.isEmpty()) return defecto
        t.toIntOrNull()?.let { return (it - 1).coerceAtLeast(0) }
        if (!t.all { it in 'A'..'Z' }) return defecto
        var n = 0
        for (c in t) n = n * 26 + (c - 'A' + 1)
        return n - 1
    }

    fun buscarEnFilas(
        filas: List<List<String>>,
        mensaje: String,
        modo: TipoCoincidencia,
        sinAcentos: Boolean,
        umbral: Int,
        colBusqueda: Int = 0,
        colRespuesta: Int = 1
    ): String? {
        val texto = Texto.normalizar(mensaje, sinAcentos)
        var mejor: String? = null
        var mejorPuntaje = -1
        filas.forEachIndexed { i, fila ->
            val celdaBusqueda = fila.getOrNull(colBusqueda) ?: return@forEachIndexed
            val respuesta = fila.getOrNull(colRespuesta)?.trim().orEmpty()
            if (i == 0 && Texto.normalizar(celdaBusqueda, true) in encabezados) return@forEachIndexed
            val claves = Texto.listaPatrones(celdaBusqueda).map { Texto.normalizar(it, sinAcentos) }
            if (claves.isEmpty() || respuesta.isEmpty()) return@forEachIndexed
            when (modo) {
                TipoCoincidencia.EXACTA -> if (claves.any { Texto.exacta(it, texto) }) return respuesta
                TipoCoincidencia.PATRON -> if (claves.any { Texto.comodin(it, texto) }) return respuesta
                TipoCoincidencia.SIMILITUD -> {
                    val p = claves.maxOf { Texto.similitud(it, texto) }
                    if (p >= umbral && p > mejorPuntaje) { mejor = respuesta; mejorPuntaje = p }
                }
                else -> if (claves.any { it.isNotEmpty() && texto.contains(it) }) return respuesta
            }
        }
        return mejor
    }

    // ======================= Dialogflow ES =======================

    private var tokenDialogflow: String? = null
    private var expiraToken = 0L
    private var correoToken = ""

    @Synchronized
    private fun token(cuenta: JSONObject): String {
        val correo = cuenta.optString("client_email")
        val actual = tokenDialogflow
        if (actual != null && correo == correoToken && System.currentTimeMillis() < expiraToken - 60_000) return actual

        val pem = cuenta.optString("private_key")
        if (correo.isBlank() || pem.isBlank()) throw Exception("El JSON de Dialogflow no tiene client_email o private_key")
        val ahora = System.currentTimeMillis() / 1000
        val cabecera = b64(JSONObject().put("alg", "RS256").put("typ", "JWT").toString().toByteArray())
        val datos = b64(
            JSONObject()
                .put("iss", correo)
                .put("scope", "https://www.googleapis.com/auth/dialogflow")
                .put("aud", "https://oauth2.googleapis.com/token")
                .put("iat", ahora)
                .put("exp", ahora + 3600)
                .toString().toByteArray()
        )
        val sinFirma = "$cabecera.$datos"
        val claveBase64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")
        val clave = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(claveBase64, Base64.DEFAULT)))
        val firma = Signature.getInstance("SHA256withRSA").run {
            initSign(clave)
            update(sinFirma.toByteArray())
            sign()
        }
        val jwt = "$sinFirma.${b64(firma)}"
        val cuerpo = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") +
            "&assertion=" + jwt
        val r = Http.peticion("https://oauth2.googleapis.com/token", "POST", cuerpo = cuerpo, tipo = "application/x-www-form-urlencoded")
        if (r.codigo !in 200..299) throw Exception("Dialogflow (autenticación): ${Http.mensajeError(r.cuerpo)}")
        val o = JSONObject(r.cuerpo)
        val nuevo = o.getString("access_token")
        tokenDialogflow = nuevo
        correoToken = correo
        expiraToken = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000
        return nuevo
    }

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    suspend fun dialogflow(r: Regla, m: MensajeEntrante): List<String> = withContext(Dispatchers.IO) {
        val json = Ajustes.dialogflowJson
        if (json.isBlank()) throw Exception("Carga el archivo JSON de Dialogflow en ⋮ → Ajustes")
        val cuenta = JSONObject(json)
        val proyecto = cuenta.optString("project_id").ifBlank { throw Exception("El JSON no tiene project_id") }
        val sesion = m.chatId.replace(Regex("[^A-Za-z0-9_-]"), "_").takeLast(36)
        val cuerpo = JSONObject().put(
            "queryInput", JSONObject().put(
                "text", JSONObject()
                    .put("text", m.texto.take(256))
                    .put("languageCode", r.dialogflowIdioma.ifBlank { "es" })
            )
        )
        val resp = Http.postJson(
            "https://dialogflow.googleapis.com/v2/projects/$proyecto/agent/sessions/$sesion:detectIntent",
            mapOf("Authorization" to "Bearer ${token(cuenta)}"),
            cuerpo
        )
        val q = resp.optJSONObject("queryResult") ?: return@withContext emptyList()
        val textos = mutableListOf<String>()
        val mensajes = q.optJSONArray("fulfillmentMessages")
        if (mensajes != null) {
            for (i in 0 until mensajes.length()) {
                val lista = mensajes.optJSONObject(i)?.optJSONObject("text")?.optJSONArray("text") ?: continue
                for (j in 0 until lista.length()) {
                    if (!lista.isNull(j)) lista.optString(j).takeIf { it.isNotBlank() }?.let { textos += it }
                }
            }
        }
        if (textos.isEmpty()) q.texto("fulfillmentText").takeIf { it.isNotBlank() }?.let { textos += it }
        textos
    }

    // ======================= Servidor web =======================

    /**
     * Envía el mensaje a tu servidor (formato compatible con AutoResponder):
     * {"appPackageName","messengerPackageName","query":{"sender","message","isGroup","groupParticipant","ruleId"}}
     * Respuesta esperada: {"replies":[{"message":"..."}]}  (también acepta {"reply":"..."} o texto plano).
     */
    suspend fun servidor(r: Regla, m: MensajeEntrante): List<String> = withContext(Dispatchers.IO) {
        if (r.servidorUrl.isBlank()) throw Exception("Falta la URL del servidor en la regla")
        val cuerpo = JSONObject()
            .put("appPackageName", "com.autorespuesta.ia")
            .put("messengerPackageName", m.paquete)
            .put("timestamp", System.currentTimeMillis())
            .put(
                "query", JSONObject()
                    .put("sender", m.remitente)
                    .put("message", m.texto)
                    .put("reply", r.respuestas.firstOrNull { it.isNotBlank() } ?: "")
                    .put("isGroup", m.esGrupo)
                    .put("groupParticipant", if (m.esGrupo) m.remitente else "")
                    .put("chat", m.nombreChat)
                    .put("ruleId", r.id)
                    .put("isTestMessage", false)
            )
        val resp = Http.peticion(r.servidorUrl.trim(), "POST", cuerpo = cuerpo.toString(), lectura = 30_000)
        if (resp.codigo !in 200..299) throw Exception("Servidor: error ${resp.codigo} ${resp.cuerpo.take(200)}")
        leerRespuestaServidor(resp.cuerpo)
    }

    fun leerRespuestaServidor(texto: String): List<String> {
        val t = texto.trim()
        if (t.isEmpty()) return emptyList()
        if (!t.startsWith("{") && !t.startsWith("[")) return listOf(t)
        return try {
            val arr: JSONArray? = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray("replies")
            if (arr != null) {
                List(arr.length()) { i ->
                    val obj = arr.optJSONObject(i)
                    when {
                        obj != null -> obj.texto("message")
                        arr.isNull(i) -> ""
                        else -> arr.optString(i)
                    }
                }.filter { it.isNotBlank() }
            } else {
                val o = JSONObject(t)
                listOfNotNull(
                    (o.texto("reply").ifBlank { null } ?: o.texto("message").ifBlank { null })
                )
            }
        } catch (e: Exception) {
            listOf(t)
        }
    }
}
