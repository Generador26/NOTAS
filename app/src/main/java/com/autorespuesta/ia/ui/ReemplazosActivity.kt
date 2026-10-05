package com.autorespuesta.ia.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.Reemplazo
import com.google.android.material.floatingactionbutton.FloatingActionButton

/** Variables propias: %clave% en cualquier respuesta se cambia por su valor. */
class ReemplazosActivity : AppCompatActivity() {

    private lateinit var lista: ListView
    private lateinit var tvVacio: TextView
    private var reemplazos: List<Reemplazo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reemplazos)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        lista = findViewById(R.id.lvReemplazos)
        tvVacio = findViewById(R.id.tvVacio)
        findViewById<FloatingActionButton>(R.id.fabAgregar).setOnClickListener { editar(null) }
        lista.setOnItemClickListener { _, _, pos, _ -> editar(reemplazos[pos]) }
        lista.setOnItemLongClickListener { _, _, pos, _ ->
            val r = reemplazos[pos]
            AlertDialog.Builder(this)
                .setTitle("¿Borrar %${r.clave}%?")
                .setPositiveButton("Borrar") { _, _ ->
                    Almacen.guardarReemplazos(Almacen.reemplazos().filter { it.clave != r.clave })
                    cargar()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }
        cargar()
    }

    private fun cargar() {
        reemplazos = Almacen.reemplazos()
        lista.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1,
            reemplazos.map { "%${it.clave}%\n${it.valor.take(120)}" }
        )
        tvVacio.visibility = if (reemplazos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun editar(actual: Reemplazo?) {
        val vista = LayoutInflater.from(this).inflate(R.layout.dialogo_reemplazo, null)
        val etClave = vista.findViewById<EditText>(R.id.etClaveReemplazo)
        val etValor = vista.findViewById<EditText>(R.id.etValorReemplazo)
        if (actual != null) {
            etClave.setText(actual.clave)
            etValor.setText(actual.valor)
        }
        val dialogo = AlertDialog.Builder(this)
            .setTitle(if (actual == null) "Nuevo reemplazo" else "Editar reemplazo")
            .setView(vista)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val clave = etClave.text.toString().trim().trim('%').lowercase().replace(Regex("\\s+"), "_")
                val valor = etValor.text.toString()
                val existentes = Almacen.reemplazos()
                if (!clave.matches(Regex("[a-z0-9_]{1,30}"))) {
                    etClave.error = "Usa solo letras, números o _"
                } else if (clave in RESERVADAS) {
                    etClave.error = "Ese nombre ya lo usa la app"
                } else if (actual?.clave != clave && existentes.any { it.clave == clave }) {
                    etClave.error = "Ya existe"
                } else {
                    val nuevos = existentes.filter { it.clave != actual?.clave && it.clave != clave }.toMutableList()
                    nuevos.add(Reemplazo(clave, valor))
                    Almacen.guardarReemplazos(nuevos.sortedBy { it.clave })
                    dialogo.dismiss()
                    cargar()
                }
            }
        }
        dialogo.show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private val RESERVADAS = setOf("nombre", "chat", "mensaje", "hora", "fecha", "dia", "saludo")
    }
}
