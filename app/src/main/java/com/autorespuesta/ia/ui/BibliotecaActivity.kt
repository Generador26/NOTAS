package com.autorespuesta.ia.ui

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.ArchivoBiblioteca
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BibliotecaActivity : AppCompatActivity() {

    private lateinit var lista: ListView
    private lateinit var tvVacio: TextView
    private var archivos: List<ArchivoBiblioteca> = emptyList()

    private val elegir = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pedirDatos(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_biblioteca)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lista = findViewById(R.id.lvArchivos)
        tvVacio = findViewById(R.id.tvVacio)
        findViewById<FloatingActionButton>(R.id.fabAgregar).setOnClickListener {
            elegir.launch(arrayOf("*/*"))
        }
        lista.setOnItemLongClickListener { _, _, pos, _ ->
            val a = archivos[pos]
            AlertDialog.Builder(this)
                .setTitle("¿Borrar «${a.clave}»?")
                .setMessage("Las reglas que lo usan dejarán de enviarlo.")
                .setPositiveButton("Borrar") { _, _ ->
                    Almacen.ficheroDe(a).parentFile?.deleteRecursively()
                    Almacen.guardarBiblioteca(Almacen.biblioteca().filter { it.clave != a.clave })
                    cargar()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }
        cargar()
    }

    private fun cargar() {
        archivos = Almacen.biblioteca()
        val filas = archivos.map { a ->
            val tamano = Almacen.ficheroDe(a).length() / 1024
            "📎 ${a.clave}  ·  ${File(a.ruta).name} (${tamano} KB)\n${a.descripcion.ifBlank { a.mime }}"
        }
        lista.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filas)
        tvVacio.visibility = if (archivos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun nombreDe(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getString(0)
                if (!n.isNullOrBlank()) return n
            }
        }
        return "archivo"
    }

    private fun pedirDatos(uri: Uri) {
        val nombre = nombreDe(uri)
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        val vista = LayoutInflater.from(this).inflate(R.layout.dialogo_archivo, null)
        vista.findViewById<TextView>(R.id.tvArchivo).text = "Archivo: $nombre"
        val etClave = vista.findViewById<EditText>(R.id.etClave)
        val etDescripcion = vista.findViewById<EditText>(R.id.etDescripcion)
        val etPie = vista.findViewById<EditText>(R.id.etPieArchivo)
        etClave.setText(nombre.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9_-]+"), "_").trim('_').take(30))

        val dialogo = AlertDialog.Builder(this)
            .setTitle("Agregar a la biblioteca")
            .setView(vista)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val clave = etClave.text.toString().trim().lowercase().replace(Regex("\\s+"), "_")
                if (!clave.matches(Regex("[a-z0-9_-]{1,40}"))) {
                    etClave.error = "Usa solo letras, números, - o _"
                } else if (Almacen.archivo(clave) != null) {
                    etClave.error = "Ya existe un archivo con esa clave"
                } else {
                    // Copiar en segundo plano: los videos o PDF grandes no congelan la pantalla
                    val boton = dialogo.getButton(DialogInterface.BUTTON_POSITIVE)
                    boton.isEnabled = false
                    boton.text = "Copiando…"
                    val descripcion = etDescripcion.text.toString().trim()
                    val pie = etPie.text.toString().trim()
                    lifecycleScope.launch {
                        val error = withContext(Dispatchers.IO) {
                            runCatching { copiar(uri, nombre, clave, mime, descripcion, pie) }.exceptionOrNull()
                        }
                        if (error == null) {
                            Toast.makeText(this@BibliotecaActivity, "Archivo agregado", Toast.LENGTH_SHORT).show()
                            dialogo.dismiss()
                            cargar()
                        } else {
                            Toast.makeText(this@BibliotecaActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                            boton.isEnabled = true
                            boton.text = "Guardar"
                        }
                    }
                }
            }
        }
        dialogo.show()
    }

    /** Copia el archivo a la carpeta privada de la app (se llama en un hilo de fondo). */
    private fun copiar(uri: Uri, nombre: String, clave: String, mime: String, descripcion: String, pie: String) {
        val carpeta = UUID.randomUUID().toString()
        val nombreSeguro = nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dir = File(Almacen.carpetaBiblioteca, carpeta).apply { mkdirs() }
        val destino = File(dir, nombreSeguro)
        try {
            contentResolver.openInputStream(uri)?.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            } ?: throw Exception("No se pudo leer el archivo")
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
        synchronized(Almacen) {
            val nuevos = Almacen.biblioteca()
            nuevos.add(ArchivoBiblioteca(clave, "$carpeta/$nombreSeguro", mime, descripcion, pie))
            Almacen.guardarBiblioteca(nuevos)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
