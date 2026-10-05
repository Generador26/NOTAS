package com.autorespuesta.ia.datos

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resumen y exportación CSV del historial de respuestas. */
object Estadisticas {

    private fun celda(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    fun csv(eventos: List<EventoHistorial>): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder("﻿")  // BOM para que Excel lea bien los acentos
        sb.append("fecha,chat,regla,mensaje_recibido,respuesta,error\n")
        for (e in eventos.sortedBy { it.fecha }) {
            sb.append(listOf(f.format(Date(e.fecha)), e.chat, e.regla, e.mensaje, e.respuesta, e.error ?: "")
                .joinToString(",") { celda(it) }).append("\n")
        }
        return sb.toString()
    }

    fun resumen(eventos: List<EventoHistorial>): String {
        if (eventos.isEmpty()) return "Sin datos todavía."
        val ahora = System.currentTimeMillis()
        val hoy = eventos.count { ahora - it.fecha < 24 * 3600_000L }
        val semana = eventos.count { ahora - it.fecha < 7 * 24 * 3600_000L }
        val errores = eventos.count { it.error != null }
        val chats = eventos.map { it.chat }.distinct().size
        val porRegla = eventos.groupingBy { it.regla }.eachCount().entries.sortedByDescending { it.value }.take(8)
        return buildString {
            append("Respuestas registradas: ${eventos.size}  ·  últimas 24 h: $hoy  ·  7 días: $semana\n")
            append("Chats distintos: $chats  ·  con error: $errores\n")
            append("Por regla: ")
            append(porRegla.joinToString("  ·  ") { "${it.key} (${it.value})" })
        }
    }
}
