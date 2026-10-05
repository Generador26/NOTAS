package com.autorespuesta.ia.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.MensajeEntrante
import com.autorespuesta.ia.datos.ProveedorIA
import com.autorespuesta.ia.datos.Regla
import com.autorespuesta.ia.motor.Motor
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.UUID

class PruebaActivity : AppCompatActivity() {

    /** Id de chat de prueba; cambia al empezar una conversación nueva. */
    private var sesion = UUID.randomUUID().toString().take(8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prueba)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etContacto = findViewById<EditText>(R.id.etContacto)
        val cbGrupo = findViewById<CheckBox>(R.id.cbGrupo)
        val etMensaje = findViewById<EditText>(R.id.etMensaje)
        val tvResultado = findViewById<TextView>(R.id.tvResultado)
        val btnProbar = findViewById<MaterialButton>(R.id.btnProbar)

        findViewById<MaterialButton>(R.id.btnReiniciarPrueba).setOnClickListener {
            sesion = UUID.randomUUID().toString().take(8)
            tvResultado.text = "Conversación de prueba nueva."
            Toast.makeText(this, "Conversación reiniciada", Toast.LENGTH_SHORT).show()
        }

        btnProbar.setOnClickListener {
            val contacto = etContacto.text.toString().trim().ifBlank { "Cliente de prueba" }
            val texto = etMensaje.text.toString().trim()
            if (texto.isEmpty()) {
                tvResultado.text = "Escribe un mensaje."
                return@setOnClickListener
            }
            val m = MensajeEntrante(
                paquete = "prueba",
                chatId = "prueba:$sesion",
                nombreChat = contacto,
                remitente = contacto,
                texto = texto,
                esGrupo = cbGrupo.isChecked,
                jid = null
            )

            val bloqueo = Motor.bloqueoGlobal(m)
            if (bloqueo != null) {
                tvResultado.text = "⛔ No se respondería: $bloqueo"
                return@setOnClickListener
            }
            val regla = Motor.buscarRegla(m, simulacion = true)
            if (regla == null) {
                tvResultado.text = explicarSinRegla(m)
                return@setOnClickListener
            }

            btnProbar.isEnabled = false
            tvResultado.text = "✅ Regla: ${regla.nombre}\n\n" +
                if (regla.proveedor != ProveedorIA.NINGUNO) "Consultando a ${regla.proveedor.etiqueta}…" else ""
            lifecycleScope.launch {
                val sb = StringBuilder()
                describir(sb, regla, m)
                Almacen.marcarRegla(m.chatId, regla.id)

                val destino = regla.irARegla.takeIf { it.isNotBlank() }
                    ?.let { id -> Almacen.reglas().firstOrNull { it.id == id && it.id != regla.id } }
                if (destino != null) {
                    sb.append("\n➜ Luego va a la regla:\n")
                    describir(sb, destino, m)
                    Almacen.marcarRegla(m.chatId, destino.id)
                }
                tvResultado.text = sb.toString().trim()
                btnProbar.isEnabled = true
            }
        }
    }

    private suspend fun describir(sb: StringBuilder, regla: Regla, m: MensajeEntrante) {
        val res = Motor.generar(regla, m)
        sb.append("✅ Regla: ${regla.nombre}\n")
        if (regla.noResponder) {
            sb.append("🚫 Esta regla está marcada como «No responder»: no se enviaría nada.\n")
            return
        }
        if (regla.delayMax > 0) sb.append("⏱ Esperaría entre ${regla.delayMin} y ${regla.delayMax} s\n")
        if (regla.usarProbabilidad) sb.append("🎲 Solo responde el ${regla.probabilidad}% de las veces\n")
        if (regla.notificacionPrioritaria) sb.append("🔔 Mostraría una notificación prioritaria\n")
        sb.append("\n")
        if (res.textos.isEmpty()) sb.append("(sin texto)\n\n")
        res.textos.forEachIndexed { i, t ->
            if (res.textos.size > 1) sb.append("Mensaje ${i + 1}:\n")
            sb.append(t).append("\n\n")
        }
        if (res.adjuntos.isNotEmpty()) sb.append("📎 Enviaría: ${res.adjuntos.joinToString(", ")}\n")
        if (res.error != null) sb.append("⚠ Error: ${res.error}\n(se usó la respuesta de respaldo si había)\n")
    }

    private fun explicarSinRegla(m: MensajeEntrante): String {
        val reglas = Almacen.reglas()
        if (reglas.isEmpty()) return "❌ No tienes reglas. Crea una con + en la pantalla principal."
        return buildString {
            append("❌ Ninguna regla responde a este mensaje:\n\n")
            reglas.forEachIndexed { i, r ->
                val nombre = r.nombre.ifBlank { "Regla ${i + 1}" }
                val motivo = if (!r.activa) "está desactivada" else Motor.motivoNoCumple(r, m, true) ?: "coincide"
                append("${i + 1}. $nombre: $motivo\n")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
