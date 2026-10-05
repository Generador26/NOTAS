package com.autorespuesta.ia.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.Estadisticas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistorialActivity : AppCompatActivity() {

    private lateinit var tv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tv = findViewById(R.id.tvHistorial)
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        val eventos = Almacen.historial()
        if (eventos.isEmpty()) {
            tv.text = "Todavía no hay respuestas. Cuando la app responda un mensaje aparecerá aquí."
            return
        }
        val formato = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val rojo = ContextCompat.getColor(this, R.color.rojo)
        val verde = ContextCompat.getColor(this, R.color.verde)
        val sb = SpannableStringBuilder()
        val iniResumen = sb.length
        sb.append("📊 ").append(Estadisticas.resumen(eventos)).append("\n\n")
        sb.setSpan(StyleSpan(Typeface.BOLD), iniResumen, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        for (e in eventos) {
            val inicio = sb.length
            sb.append("${formato.format(Date(e.fecha))} · ${e.chat}")
            sb.setSpan(StyleSpan(Typeface.BOLD), inicio, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("  [${e.regla}]\n")
            sb.append("⬅ ${e.mensaje.take(300)}\n")
            val ini2 = sb.length
            sb.append("➡ ${e.respuesta.take(500)}\n")
            sb.setSpan(ForegroundColorSpan(verde), ini2, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (e.error != null) {
                val ini3 = sb.length
                sb.append("⚠ ${e.error}\n")
                sb.setSpan(ForegroundColorSpan(rojo), ini3, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            sb.append("\n")
        }
        tv.text = sb
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_historial, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.m_borrar -> { Almacen.limpiarHistorial(); cargar() }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
