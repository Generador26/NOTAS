package com.autorespuesta.ia.motor

import java.util.Calendar

/**
 * Horarios por día, igual que «Horas específicas» de AutoResponder.
 *
 *  - Un campo por día; vacío = no responde ese día; «0-24» = todo el día.
 *  - Separa horas y minutos con «.» o «:»  ·  rango con «-»  ·  varios rangos con «,»
 *  - Formato de 12 y 24 horas: «22:30-5:00», «12am-9am,2.30pm-12am», «0-11:30,14:30-24».
 *  - Si la hora final es anterior a la inicial, el rango continúa al día siguiente
 *    (22:30-5:00 el lunes responde hasta el martes a las 5:00, aunque el martes esté en blanco).
 */
object Horario {

    /** Rangos (inicio, fin) en minutos; lista vacía si no hay texto; null si el formato no es válido. */
    fun parsear(texto: String): List<Pair<Int, Int>>? {
        val t = texto.trim()
        if (t.isEmpty()) return emptyList()
        val rangos = mutableListOf<Pair<Int, Int>>()
        for (parte in t.split(',', ';')) {
            val p = parte.trim()
            if (p.isEmpty()) continue
            val lados = p.split('-', '–', '—')
            if (lados.size != 2) return null
            val ini = hora(lados[0], esFin = false) ?: return null
            val fin = hora(lados[1], esFin = true) ?: return null
            if (ini == fin) return null
            rangos += ini to fin
        }
        return rangos
    }

    /** «8», «8:30», «2.30pm», «12am» → minutos desde las 00:00 (24 solo vale como hora final). */
    fun hora(s: String, esFin: Boolean): Int? {
        var t = s.trim().lowercase().replace(" ", "")
        var sufijo = ""
        if (t.endsWith("am") || t.endsWith("pm")) {
            sufijo = t.takeLast(2)
            t = t.dropLast(2)
        }
        if (t.isEmpty()) return null
        val partes = t.split('.', ':')
        if (partes.size > 2) return null
        var h = partes[0].toIntOrNull() ?: return null
        val m = if (partes.size == 2) (partes[1].toIntOrNull() ?: return null) else 0
        if (h < 0 || m !in 0..59) return null
        if (sufijo.isNotEmpty()) {
            if (h !in 1..12) return null
            h = if (sufijo == "am") (if (h == 12) 0 else h) else (if (h == 12) 12 else h + 12)
        } else if (h == 24) {
            return if (m == 0 && esFin) 1440 else null
        } else if (h > 23) {
            return null
        }
        return h * 60 + m
    }

    /** Hora suelta «HH:mm» (o «8.30», «8pm») en minutos, para «Pausar hasta (hora)». */
    fun horaSimple(s: String): Int? = hora(s, esFin = false)

    fun activoAhora(horarios: Map<Int, String>, cal: Calendar = Calendar.getInstance()): Boolean =
        activoEn(horarios, cal.get(Calendar.DAY_OF_WEEK), cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))

    /** [dia] = Calendar.MONDAY… SUNDAY; [minuto] = minutos desde las 00:00. */
    fun activoEn(horarios: Map<Int, String>, dia: Int, minuto: Int): Boolean {
        val ayer = if (dia == Calendar.SUNDAY) Calendar.SATURDAY else dia - 1
        parsear(horarios[dia].orEmpty())?.forEach { (ini, fin) ->
            if (ini < fin) {
                if (minuto in ini until fin) return true
            } else if (minuto >= ini) return true
        }
        // La parte de madrugada de un rango que empezó el día anterior
        parsear(horarios[ayer].orEmpty())?.forEach { (ini, fin) ->
            if (ini > fin && minuto < fin) return true
        }
        return false
    }

    /** Convierte el horario antiguo (días marcados + un solo rango) al formato por día. */
    fun desdeAntiguo(dias: Set<Int>, inicio: String, fin: String): MutableMap<Int, String> {
        val texto = "${inicio.trim()}-${fin.trim()}".let { if (parsear(it).isNullOrEmpty()) "0-24" else it }
        return dias.associateWith { texto }.toMutableMap()
    }
}
