package com.autorespuesta.ia.datos

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda reglas, biblioteca, historial y el estado de cada conversación. */
object Almacen {
    private const val MAX_HISTORIAL = 500
    private const val MAX_CONVERSACION = 40

    private lateinit var datos: SharedPreferences
    private lateinit var estado: SharedPreferences
    private lateinit var conversaciones: SharedPreferences
    lateinit var carpetaBiblioteca: File
        private set

    fun init(ctx: Context) {
        if (::datos.isInitialized) return
        val app = ctx.applicationContext
        datos = app.getSharedPreferences("datos", Context.MODE_PRIVATE)
        estado = app.getSharedPreferences("estado", Context.MODE_PRIVATE)
        conversaciones = app.getSharedPreferences("conversaciones", Context.MODE_PRIVATE)
        carpetaBiblioteca = File(app.filesDir, "biblioteca").apply { mkdirs() }
    }

    // ---------- Reglas ----------

    @Synchronized
    fun reglas(): MutableList<Regla> {
        val a = JSONArray(datos.getString("reglas", "[]"))
        return MutableList(a.length()) { Regla.desdeJson(a.getJSONObject(it)) }
    }

    @Synchronized
    fun guardarReglas(lista: List<Regla>) {
        val a = JSONArray()
        lista.forEach { a.put(it.aJson()) }
        datos.edit().putString("reglas", a.toString()).apply()
    }

    @Synchronized
    fun guardarRegla(r: Regla) {
        val lista = reglas()
        val i = lista.indexOfFirst { it.id == r.id }
        if (i >= 0) lista[i] = r else lista.add(r)
        guardarReglas(lista)
    }

    @Synchronized
    fun borrarRegla(id: String) = guardarReglas(reglas().filter { it.id != id })

    @Synchronized
    fun moverRegla(id: String, arriba: Boolean) {
        val lista = reglas()
        val i = lista.indexOfFirst { it.id == id }
        val j = if (arriba) i - 1 else i + 1
        if (i < 0 || j !in lista.indices) return
        val tmp = lista[i]; lista[i] = lista[j]; lista[j] = tmp
        guardarReglas(lista)
    }

    // ---------- Biblioteca de archivos ----------

    @Synchronized
    fun biblioteca(): MutableList<ArchivoBiblioteca> {
        val a = JSONArray(datos.getString("biblioteca", "[]"))
        return MutableList(a.length()) { ArchivoBiblioteca.desdeJson(a.getJSONObject(it)) }
    }

    @Synchronized
    fun guardarBiblioteca(lista: List<ArchivoBiblioteca>) {
        val a = JSONArray()
        lista.forEach { a.put(it.aJson()) }
        datos.edit().putString("biblioteca", a.toString()).apply()
    }

    fun archivo(clave: String): ArchivoBiblioteca? =
        biblioteca().firstOrNull { it.clave.equals(clave.trim(), ignoreCase = true) }

    fun ficheroDe(a: ArchivoBiblioteca): File = File(carpetaBiblioteca, a.ruta)

    // ---------- Reemplazos personalizados ----------

    @Synchronized
    fun reemplazos(): MutableList<Reemplazo> {
        val a = JSONArray(datos.getString("reemplazos", "[]"))
        return MutableList(a.length()) { Reemplazo.desdeJson(a.getJSONObject(it)) }
    }

    @Synchronized
    fun guardarReemplazos(lista: List<Reemplazo>) {
        val a = JSONArray()
        lista.forEach { a.put(it.aJson()) }
        datos.edit().putString("reemplazos", a.toString()).apply()
    }

    // ---------- Historial ----------

    @Synchronized
    fun historial(): List<EventoHistorial> {
        val a = JSONArray(datos.getString("historial", "[]"))
        return List(a.length()) { EventoHistorial.desdeJson(a.getJSONObject(it)) }
    }

    @Synchronized
    fun agregarHistorial(e: EventoHistorial) {
        val lista = historial().toMutableList()
        lista.add(0, e)
        val a = JSONArray()
        lista.take(MAX_HISTORIAL).forEach { a.put(it.aJson()) }
        datos.edit().putString("historial", a.toString()).apply()
    }

    @Synchronized
    fun limpiarHistorial() = datos.edit().putString("historial", "[]").apply()

    // ---------- Conversaciones (memoria para la IA) ----------

    @Synchronized
    fun conversacion(chatId: String): List<Pair<String, String>> {
        val a = JSONArray(conversaciones.getString(chatId, "[]"))
        return List(a.length()) {
            val o = a.getJSONObject(it)
            o.optString("rol") to o.optString("texto")
        }
    }

    @Synchronized
    fun agregarConversacion(chatId: String, rol: String, texto: String) {
        val lista = conversacion(chatId).toMutableList()
        lista.add(rol to texto)
        val a = JSONArray()
        lista.takeLast(MAX_CONVERSACION).forEach { a.put(JSONObject().put("rol", it.first).put("texto", it.second)) }
        conversaciones.edit().putString(chatId, a.toString()).apply()
    }

    @Synchronized
    fun borrarConversaciones() = conversaciones.edit().clear().apply()

    // ---------- Estado por chat ----------

    fun ultimoTs(chatId: String): Long = estado.getLong("ts_$chatId", 0L)
    fun guardarUltimoTs(chatId: String, ts: Long) = estado.edit().putLong("ts_$chatId", ts).apply()

    fun contactoVisto(chatId: String): Boolean = estado.getBoolean("visto_$chatId", false)
    fun marcarVisto(chatId: String) = estado.edit().putBoolean("visto_$chatId", true).apply()

    fun ultimaVezRegla(chatId: String, reglaId: String): Long = estado.getLong("regla_${reglaId}_$chatId", 0L)
    /** Guarda que la regla se activó en este chat (para pausas, submenús y "evitar repetir"). */
    fun marcarRegla(chatId: String, reglaId: String) {
        val ahora = System.currentTimeMillis()
        estado.edit()
            .putLong("regla_${reglaId}_$chatId", ahora)
            .putLong("msg_${reglaId}_$chatId", estado.getLong("total_$chatId", 0L))
            .putString("ultima_$chatId", reglaId)
            .putLong("ultimats_$chatId", ahora)
            .apply()
    }

    /** Cuenta un mensaje recibido de este chat (para «no repetir en los siguientes x mensajes»). */
    fun contarMensaje(chatId: String) =
        estado.edit().putLong("total_$chatId", estado.getLong("total_$chatId", 0L) + 1).apply()

    /** Mensajes recibidos del chat desde que se activó la regla (0 = el mismo mensaje que la activó). */
    fun mensajesDesdeRegla(chatId: String, reglaId: String): Long =
        estado.getLong("total_$chatId", 0L) - estado.getLong("msg_${reglaId}_$chatId", 0L)

    /** Última regla que se activó en el chat y cuándo (ms), o null. */
    fun ultimaRegla(chatId: String): Pair<String, Long>? {
        val id = estado.getString("ultima_$chatId", null) ?: return null
        return id to estado.getLong("ultimats_$chatId", 0L)
    }

    // ---------- Contactos que pidieron no recibir respuestas ----------

    fun ignoradosDinamicos(): Set<String> = estado.getStringSet("ignorados_dinamicos", emptySet()) ?: emptySet()

    @Synchronized
    fun cambiarIgnorado(chatId: String, ignorar: Boolean) {
        val s = ignoradosDinamicos().toMutableSet()
        if (ignorar) s.add(chatId) else s.remove(chatId)
        estado.edit().putStringSet("ignorados_dinamicos", s).apply()
    }

    fun reiniciarEstado() = estado.edit().clear().apply()
}
