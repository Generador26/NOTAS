package com.autorespuesta.ia.ui

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.motor.Fuentes
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject

class AjustesActivity : AppCompatActivity() {

    private lateinit var tvDialogflowEstado: TextView

    private val cargarJson = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) cargarDialogflow(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ajustes)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val cbWhatsApp = findViewById<CheckBox>(R.id.cbWhatsApp)
        val cbBusiness = findViewById<CheckBox>(R.id.cbBusiness)
        val etClaveClaude = findViewById<EditText>(R.id.etClaveClaude)
        val etClaveOpenAI = findViewById<EditText>(R.id.etClaveOpenAI)
        val etUrlOpenAI = findViewById<EditText>(R.id.etUrlOpenAI)
        val etClaveGemini = findViewById<EditText>(R.id.etClaveGemini)
        val swAcentos = findViewById<SwitchMaterial>(R.id.swAcentos)
        val etIgnorados = findViewById<EditText>(R.id.etIgnoradosGlobales)
        val etEncabezado = findViewById<EditText>(R.id.etEncabezado)
        val etPie = findViewById<EditText>(R.id.etPie)
        val tvSimilitud = findViewById<TextView>(R.id.tvSimilitud)
        val sbSimilitud = findViewById<SeekBar>(R.id.sbSimilitud)
        val swEvitarRepetir = findViewById<SwitchMaterial>(R.id.swEvitarRepetir)
        val etEvitarRepetirSeg = findViewById<EditText>(R.id.etEvitarRepetirSeg)
        val etNoResponderSi = findViewById<EditText>(R.id.etNoResponderSi)
        val swNotificaciones = findViewById<SwitchMaterial>(R.id.swNotificaciones)
        val swPrioritarias = findViewById<SwitchMaterial>(R.id.swPrioritarias)
        val etPalabraIgnorar = findViewById<EditText>(R.id.etPalabraIgnorar)
        val etPalabraReanudar = findViewById<EditText>(R.id.etPalabraReanudar)
        tvDialogflowEstado = findViewById(R.id.tvDialogflowEstado)
        val etSalidaSheets = findViewById<EditText>(R.id.etSalidaSheets)
        val etSalidaWebhook = findViewById<EditText>(R.id.etSalidaWebhook)
        val spSalidaSheets = findViewById<Spinner>(R.id.spSalidaSheets)
        val spSalidaWebhook = findViewById<Spinner>(R.id.spSalidaWebhook)
        val opcionesSalida = listOf("Nunca", "Solo mensajes respondidos", "Todos los mensajes recibidos")
        spSalidaSheets.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSalida)
        spSalidaWebhook.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSalida)
        etSalidaSheets.setText(Ajustes.salidaSheetsUrl)
        etSalidaWebhook.setText(Ajustes.salidaWebhookUrl)
        spSalidaSheets.setSelection(Ajustes.salidaSheetsCuando.coerceIn(0, 2))
        spSalidaWebhook.setSelection(Ajustes.salidaWebhookCuando.coerceIn(0, 2))

        cbWhatsApp.isChecked = Ajustes.usarWhatsApp
        cbBusiness.isChecked = Ajustes.usarBusiness
        etClaveClaude.setText(Ajustes.claveClaude)
        etClaveOpenAI.setText(Ajustes.claveOpenAI)
        etUrlOpenAI.setText(Ajustes.urlOpenAI)
        etClaveGemini.setText(Ajustes.claveGemini)
        swAcentos.isChecked = Ajustes.ignorarAcentos
        etIgnorados.setText(Ajustes.ignoradosGlobales)
        etEncabezado.setText(Ajustes.encabezado)
        etPie.setText(Ajustes.pie)
        sbSimilitud.progress = Ajustes.umbralSimilitud
        tvSimilitud.text = "Umbral de similitud: ${Ajustes.umbralSimilitud} %"
        sbSimilitud.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, valor: Int, usuario: Boolean) {
                tvSimilitud.text = "Umbral de similitud: $valor %"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        swEvitarRepetir.isChecked = Ajustes.evitarRepetir
        etEvitarRepetirSeg.setText(Ajustes.evitarRepetirSegundos.toString())
        etNoResponderSi.setText(Ajustes.noResponderSi)
        swNotificaciones.isChecked = Ajustes.notificaciones
        swPrioritarias.isChecked = Ajustes.notificacionesPrioritarias
        etPalabraIgnorar.setText(Ajustes.palabraIgnorar)
        etPalabraReanudar.setText(Ajustes.palabraReanudar)
        mostrarEstadoDialogflow()
        findViewById<TextView>(R.id.tvIntents).text = TEXTO_INTENTS

        findViewById<MaterialButton>(R.id.btnCargarDialogflow).setOnClickListener {
            cargarJson.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        findViewById<MaterialButton>(R.id.btnQuitarDialogflow).setOnClickListener {
            Ajustes.dialogflowJson = ""
            mostrarEstadoDialogflow()
        }

        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener {
            if (!cbWhatsApp.isChecked && !cbBusiness.isChecked) {
                Toast.makeText(this, "Elige al menos una versión de WhatsApp", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            Ajustes.usarWhatsApp = cbWhatsApp.isChecked
            Ajustes.usarBusiness = cbBusiness.isChecked
            Ajustes.claveClaude = etClaveClaude.text.toString()
            Ajustes.claveOpenAI = etClaveOpenAI.text.toString()
            Ajustes.urlOpenAI = etUrlOpenAI.text.toString()
            Ajustes.claveGemini = etClaveGemini.text.toString()
            Ajustes.ignorarAcentos = swAcentos.isChecked
            Ajustes.ignoradosGlobales = etIgnorados.text.toString().trim()
            Ajustes.encabezado = etEncabezado.text.toString()
            Ajustes.pie = etPie.text.toString()
            Ajustes.umbralSimilitud = sbSimilitud.progress
            Ajustes.evitarRepetir = swEvitarRepetir.isChecked
            Ajustes.evitarRepetirSegundos = etEvitarRepetirSeg.text.toString().trim().toIntOrNull() ?: 0
            Ajustes.noResponderSi = etNoResponderSi.text.toString().trim()
            Ajustes.notificaciones = swNotificaciones.isChecked
            Ajustes.notificacionesPrioritarias = swPrioritarias.isChecked
            Ajustes.palabraIgnorar = etPalabraIgnorar.text.toString()
            Ajustes.palabraReanudar = etPalabraReanudar.text.toString()
            Ajustes.salidaSheetsUrl = etSalidaSheets.text.toString()
            Ajustes.salidaWebhookUrl = etSalidaWebhook.text.toString()
            Ajustes.salidaSheetsCuando = spSalidaSheets.selectedItemPosition.coerceAtLeast(0)
            Ajustes.salidaWebhookCuando = spSalidaWebhook.selectedItemPosition.coerceAtLeast(0)
            Fuentes.limpiarCacheSheets()
            Toast.makeText(this, "Ajustes guardados", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<MaterialButton>(R.id.btnBorrarMemoria).setOnClickListener {
            confirmar("¿Borrar la memoria de todas las conversaciones?") {
                Almacen.borrarConversaciones()
                Toast.makeText(this, "Memoria borrada", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnReiniciar).setOnClickListener {
            confirmar(
                "Todos los contactos volverán a recibir el mensaje de bienvenida, se quitarán las pausas " +
                    "y los contactos que enviaron la palabra para dejar de recibir respuestas volverán a recibirlas. ¿Continuar?"
            ) {
                Almacen.reiniciarEstado()
                Toast.makeText(this, "Estado reiniciado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarEstadoDialogflow() {
        val json = Ajustes.dialogflowJson
        tvDialogflowEstado.text = if (json.isBlank()) {
            "Sin configurar. Necesitas el archivo JSON de una cuenta de servicio de Google Cloud con el rol " +
                "«Cliente de la API de Dialogflow»."
        } else {
            val o = runCatching { JSONObject(json) }.getOrNull()
            "✅ Configurado · proyecto: ${o?.optString("project_id") ?: "?"}\n${o?.optString("client_email") ?: ""}"
        }
    }

    private fun cargarDialogflow(uri: Uri) {
        try {
            val texto = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val o = JSONObject(texto)
            if (o.optString("private_key").isBlank() || o.optString("client_email").isBlank() || o.optString("project_id").isBlank()) {
                Toast.makeText(this, "Ese JSON no es de una cuenta de servicio (faltan datos)", Toast.LENGTH_LONG).show()
                return
            }
            Ajustes.dialogflowJson = texto
            mostrarEstadoDialogflow()
            Toast.makeText(this, "Credenciales de Dialogflow cargadas", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Archivo no válido: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmar(mensaje: String, accion: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(mensaje)
            .setPositiveButton("Sí") { _, _ -> accion() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val TEXTO_INTENTS =
            "Intents que puedes usar en Tasker/MacroDroid («Enviar intent», destino: Broadcast; " +
                "si te lo pide, paquete com.autorespuesta.ia):\n\n" +
                "• com.autorespuesta.ia.ACTIVAR / DESACTIVAR → enciende o apaga las respuestas\n" +
                "• com.autorespuesta.ia.ACTIVAR_REGLA / DESACTIVAR_REGLA + extra regla:NOMBRE\n" +
                "• com.autorespuesta.ia.RESPUESTA + extras id y respuesta → contesta a una regla «Esperar respuesta de Tasker»\n\n" +
                "Eventos que envía la app (Intent recibido):\n" +
                "• com.autorespuesta.ia.MENSAJE_RECIBIDO (id, mensaje, remitente, chat, grupo, regla)\n" +
                "• com.autorespuesta.ia.RESPUESTA_ENVIADA (mensaje, respuesta, remitente, chat, regla)"
    }
}
