package com.autorespuesta.ia.motor

import java.text.Normalizer

object Texto {
    private val marcas = Regex("\\p{Mn}+")
    private val noLetras = Regex("[^\\p{L}\\p{N}]+")

    /** Minúsculas, sin espacios extremos y (opcionalmente) sin acentos. */
    fun normalizar(s: String, sinAcentos: Boolean): String {
        var t = s.trim().lowercase()
        if (sinAcentos) t = Normalizer.normalize(t, Normalizer.Form.NFD).replace(marcas, "")
        return t
    }

    /** "a, b ,c" -> [a, b, c] */
    fun listaComas(s: String): List<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Patrones separados por «//» o por comas (ambas formas valen). */
    fun listaPatrones(s: String): List<String> = s.split("//", ",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Contactos separados por comas; las comas dentro de «grupo{persona 1, persona 2}» no separan. */
    fun listaContactos(s: String): List<String> {
        val salida = mutableListOf<String>()
        val actual = StringBuilder()
        var nivel = 0
        for (c in s) {
            when {
                c == '{' -> { nivel++; actual.append(c) }
                c == '}' -> { if (nivel > 0) nivel--; actual.append(c) }
                c == ',' && nivel == 0 -> { salida += actual.toString().trim(); actual.clear() }
                else -> actual.append(c)
            }
        }
        salida += actual.toString().trim()
        return salida.filter { it.isNotEmpty() }
    }

    private val simbolos = Regex("[^\\p{L}\\p{N}\\s]")
    private val espacios = Regex("\\s+")

    /** Quita emojis, puntuación y caracteres especiales, y deja un solo espacio entre palabras. */
    fun sinSimbolos(s: String): String = s.replace(simbolos, "").replace(espacios, " ").trim()

    /**
     * Coincidencia exacta: igual al patrón. Si el patrón no usa emojis ni signos,
     * también se ignoran los del mensaje («¡Hola!» coincide con «hola»).
     */
    fun exacta(patron: String, texto: String): Boolean {
        if (patron == texto) return true
        val p = patron.replace(espacios, " ").trim()
        if (p.isEmpty()) return false
        return sinSimbolos(p) == p && sinSimbolos(texto) == p
    }

    /** Coincidencia con comodines: "hola*" coincide con "hola, ¿cómo estás?". */
    fun comodin(patron: String, texto: String): Boolean {
        val regex = patron.split('*').joinToString(".*") { Regex.escape(it) }
        return Regex(regex, RegexOption.DOT_MATCHES_ALL).matches(texto)
    }

    /** Patrón global: con * se usa como comodín, sin * basta con que el mensaje lo contenga. */
    fun coincidePatronSimple(patron: String, texto: String): Boolean =
        if (patron.contains('*')) comodin(patron, texto) else patron.isNotEmpty() && texto.contains(patron)

    /**
     * Similitud entre 0 y 100. Combina la distancia de edición (errores de tipeo)
     * con las palabras en común (orden distinto), y se queda con la mayor.
     */
    fun similitud(a: String, b: String): Int {
        val x = a.replace(noLetras, " ").trim()
        val y = b.replace(noLetras, " ").trim()
        if (x.isEmpty() || y.isEmpty()) return if (x == y) 100 else 0
        if (x == y) return 100
        val distancia = levenshtein(x, y)
        val porEdicion = (1.0 - distancia.toDouble() / maxOf(x.length, y.length)) * 100
        val px = x.split(' ').filter { it.isNotEmpty() }.toSet()
        val py = y.split(' ').filter { it.isNotEmpty() }.toSet()
        val comunes = px.count { p -> py.any { q -> p == q || (p.length >= 4 && q.length >= 4 && levenshtein(p, q) <= 1) } }
        val porPalabras = 2.0 * comunes / (px.size + py.size) * 100
        return maxOf(porEdicion, porPalabras).toInt().coerceIn(0, 100)
    }

    fun levenshtein(a: String, b: String): Int {
        var previo = IntArray(b.length + 1) { it }
        var actual = IntArray(b.length + 1)
        for (i in 1..a.length) {
            actual[0] = i
            for (j in 1..b.length) {
                val costo = if (a[i - 1] == b[j - 1]) 0 else 1
                actual[j] = minOf(actual[j - 1] + 1, previo[j] + 1, previo[j - 1] + costo)
            }
            val t = previo; previo = actual; actual = t
        }
        return previo[b.length]
    }

    /** Lee un CSV (con comillas, comas y saltos de línea dentro de celdas). */
    fun leerCsv(texto: String): List<List<String>> {
        val filas = mutableListOf<List<String>>()
        var fila = mutableListOf<String>()
        val celda = StringBuilder()
        var entreComillas = false
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < texto.length && texto[i + 1] == '"') { celda.append('"'); i++ } else entreComillas = false
                } else celda.append(c)
            } else when (c) {
                '"' -> entreComillas = true
                ',' -> { fila.add(celda.toString()); celda.clear() }
                '\r' -> Unit
                '\n' -> { fila.add(celda.toString()); celda.clear(); filas.add(fila); fila = mutableListOf() }
                else -> celda.append(c)
            }
            i++
        }
        if (celda.isNotEmpty() || fila.isNotEmpty()) { fila.add(celda.toString()); filas.add(fila) }
        return filas
    }
}
