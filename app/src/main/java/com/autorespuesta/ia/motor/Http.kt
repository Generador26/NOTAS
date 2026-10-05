package com.autorespuesta.ia.motor

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Lee un texto de un JSON. En Android, optString() de un valor null devuelve el texto "null";
 * esta función devuelve "" en ese caso.
 */
fun JSONObject.texto(clave: String): String = if (isNull(clave)) "" else optString(clave)

/** Peticiones HTTP sencillas (se llaman siempre desde un hilo de fondo). */
object Http {

    class Respuesta(val codigo: Int, val cuerpo: String)

    /** Error devuelto por una API, con el código HTTP y el detalle original (para reintentos). */
    class ErrorApi(val codigo: Int, val detalle: String) : Exception(explicar(codigo, detalle))

    fun peticion(
        url: String,
        metodo: String = "GET",
        cabeceras: Map<String, String> = emptyMap(),
        cuerpo: String? = null,
        tipo: String = "application/json; charset=utf-8",
        lectura: Int = 90_000
    ): Respuesta {
        val c = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw Exception("La dirección no es válida: $url")
        }
        try {
            c.requestMethod = metodo
            c.connectTimeout = 15_000
            c.readTimeout = lectura
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "AutoRespuestaIA/1.0 (Android)")
            c.setRequestProperty("Accept", "application/json, text/plain, */*")
            cabeceras.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (cuerpo != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", tipo)
                c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
            }
            val codigo = c.responseCode
            val flujo = if (codigo in 200..299) c.inputStream else c.errorStream
            val texto = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return Respuesta(codigo, texto)
        } catch (e: UnknownHostException) {
            throw Exception("Sin conexión a internet (no se encontró ${URL(url).host})")
        } catch (e: SocketTimeoutException) {
            throw Exception("El servicio tardó demasiado en responder")
        } catch (e: javax.net.ssl.SSLException) {
            throw Exception("Error de conexión segura (SSL): ${e.message ?: e.javaClass.simpleName}")
        } catch (e: java.net.UnknownServiceException) {
            throw Exception("Android bloquea las direcciones http:// sin cifrar; usa https:// (${e.message ?: ""})")
        } catch (e: java.io.IOException) {
            throw Exception("No se pudo conectar con el servicio: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            c.disconnect()
        }
    }

    /** POST con JSON; lanza ErrorApi con un mensaje claro si la API responde con error. */
    fun postJson(url: String, cabeceras: Map<String, String>, cuerpo: JSONObject): JSONObject {
        val r = peticion(url, "POST", cabeceras, cuerpo.toString())
        if (r.codigo !in 200..299) throw ErrorApi(r.codigo, mensajeError(r.cuerpo))
        return try {
            JSONObject(r.cuerpo)
        } catch (e: Exception) {
            throw Exception("Respuesta no válida del servicio: ${r.cuerpo.take(150)}")
        }
    }

    /** Extrae el mensaje de error de las respuestas JSON de Anthropic, OpenAI, Google, etc. */
    fun mensajeError(texto: String): String {
        val detalle = runCatching {
            val o = JSONObject(texto)
            o.optJSONObject("error")?.texto("message")?.ifBlank { null }
                ?: o.texto("error_description").ifBlank { null }
                ?: (if (o.optJSONObject("error") == null) o.texto("error").ifBlank { null } else null)
                ?: o.texto("message").ifBlank { null }
        }.getOrNull()
        if (detalle != null) return detalle
        // Algunas APIs devuelven [ { "error": {...} } ]
        val enLista = runCatching {
            org.json.JSONArray(texto).optJSONObject(0)?.optJSONObject("error")?.texto("message")
        }.getOrNull()
        return enLista?.ifBlank { null } ?: texto.take(300)
    }

    /** Traduce el código HTTP a una explicación en español (el detalle original va entre paréntesis). */
    fun explicar(codigo: Int, detalle: String): String {
        val causa = when (codigo) {
            400 -> "Petición rechazada"
            401 -> "La API key no es válida"
            402 -> "Sin saldo o sin método de pago en tu cuenta de IA"
            403 -> "La API key no tiene permiso para este modelo o servicio"
            404 -> "Modelo o dirección no encontrados (revisa el nombre del modelo)"
            413 -> "El mensaje es demasiado largo"
            429 -> "Límite de uso alcanzado o saldo agotado; espera un momento o revisa tu plan"
            in 500..599 -> "El servicio de IA no está disponible en este momento"
            else -> "Error $codigo"
        }
        return if (detalle.isBlank()) causa else "$causa ($detalle)"
    }
}
