package com.autorespuesta.ia.ui

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.Estadisticas
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EstadisticasActivity : AppCompatActivity() {

    private val exportar = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) escribir(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estadisticas)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<MaterialButton>(R.id.btnExportar).setOnClickListener {
            exportar.launch("estadisticas_autorespuesta.csv")
        }
    }

    override fun onResume() {
        super.onResume()
        val eventos = Almacen.historial()
        findViewById<TextView>(R.id.tvResumen).text = Estadisticas.resumen(eventos).substringBefore("\nPor regla")

        val porRegla = eventos.groupingBy { it.regla }.eachCount().entries.sortedByDescending { it.value }
        findViewById<TextView>(R.id.tvPorRegla).text =
            if (porRegla.isEmpty()) "Sin datos todavía."
            else porRegla.joinToString("\n") { "• ${it.key}: ${it.value}" }

        // Barras de los últimos 7 días
        val formato = SimpleDateFormat("EEE dd", Locale("es"))
        val claveDia = SimpleDateFormat("yyyyMMdd", Locale.US)
        val conteo = eventos.groupingBy { claveDia.format(java.util.Date(it.fecha)) }.eachCount()
        val cal = Calendar.getInstance()
        val filas = (6 downTo 0).map { atras ->
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -atras)
            formato.format(c.time).padEnd(7) to (conteo[claveDia.format(c.time)] ?: 0)
        }
        val maximo = filas.maxOf { it.second }.coerceAtLeast(1)
        findViewById<TextView>(R.id.tvDias).text = filas.joinToString("\n") { (dia, n) ->
            "$dia ${"█".repeat((n * 16 + maximo - 1) / maximo)} $n"
        }
    }

    private fun escribir(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(Estadisticas.csv(Almacen.historial()).toByteArray()) }
            Toast.makeText(this, "Estadísticas exportadas", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
