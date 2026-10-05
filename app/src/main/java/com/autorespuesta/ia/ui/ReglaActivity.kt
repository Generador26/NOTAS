package com.autorespuesta.ia.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.Destinatarios
import com.autorespuesta.ia.datos.ModoRespuesta
import com.autorespuesta.ia.datos.ModoSalida
import com.autorespuesta.ia.datos.ProveedorIA
import com.autorespuesta.ia.datos.Regla
import com.autorespuesta.ia.datos.TipoCoincidencia
import com.autorespuesta.ia.motor.ClienteIA
import com.autorespuesta.ia.motor.Horario
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Calendar
import java.util.UUID

/** Crear / editar una regla. Diseño al estilo de AutoResponder. */
class ReglaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "id_regla"
        const val EXTRA_PADRE = "id_regla_padre"
        private const val SIN_ARCHIVO = "(ninguno)"
        private const val MAX_RESPUESTAS = 20
        private val FACTORES = intArrayOf(1, 60, 3600, 86400, 0)
        private val UNIDADES = listOf("segundos", "minutos", "horas", "días", "hasta (hora)")
    }

    private lateinit var regla: Regla
    private var esNueva = true
    private var tipoActual = TipoCoincidencia.TODOS
    private var noResponder = false
    private var cargando = true

    private val camposRespuesta = mutableListOf<EditText>()
    private var campoEnfocado: EditText? = null
    private var destinoContacto: EditText? = null

    /** Claves de IA escritas en esta pantalla (se guardan en Ajustes al guardar). */
    private val clavesEditadas = HashMap<ProveedorIA, String>()
    private var proveedorDeLaClave: ProveedorIA? = null

    private lateinit var idsOtras: List<String>
    private lateinit var opcionesAdjunto: List<String>

    private val radiosTipo = linkedMapOf(
        R.id.rbExacta to TipoCoincidencia.EXACTA,
        R.id.rbContiene to TipoCoincidencia.CONTIENE,
        R.id.rbSimilitud to TipoCoincidencia.SIMILITUD,
        R.id.rbPatron to TipoCoincidencia.PATRON,
        R.id.rbRegex to TipoCoincidencia.REGEX,
        R.id.rbBienvenida to TipoCoincidencia.BIENVENIDA
    )
    private val conexiones = linkedMapOf(
        R.id.cbGemini to ProveedorIA.GEMINI,
        R.id.cbChatGPT to ProveedorIA.OPENAI,
        R.id.cbClaude to ProveedorIA.CLAUDE,
        R.id.cbSheets to ProveedorIA.SHEETS,
        R.id.cbDialogflow to ProveedorIA.DIALOGFLOW,
        R.id.cbServidor to ProveedorIA.SERVIDOR,
        R.id.cbTasker to ProveedorIA.TASKER
    )
    private val modos = mapOf(
        R.id.rbUna to ModoRespuesta.UNA, R.id.rbTodas to ModoRespuesta.TODAS,
        R.id.rbAleatoria to ModoRespuesta.ALEATORIA, R.id.rbDiaria to ModoRespuesta.DIARIA
    )
    private val modosSheets = mapOf(
        R.id.rbSheetsContiene to TipoCoincidencia.CONTIENE,
        R.id.rbSheetsExacta to TipoCoincidencia.EXACTA,
        R.id.rbSheetsSimilitud to TipoCoincidencia.SIMILITUD
    )
    private val destinatarios = mapOf(
        R.id.rbIndividuales to Destinatarios.INDIVIDUALES,
        R.id.rbGrupos to Destinatarios.GRUPOS,
        R.id.rbAmbos to Destinatarios.AMBOS
    )
    private val camposHorario = mapOf(
        R.id.etHorLun to Calendar.MONDAY, R.id.etHorMar to Calendar.TUESDAY, R.id.etHorMie to Calendar.WEDNESDAY,
        R.id.etHorJue to Calendar.THURSDAY, R.id.etHorVie to Calendar.FRIDAY, R.id.etHorSab to Calendar.SATURDAY,
        R.id.etHorDom to Calendar.SUNDAY
    )
    private val nombresDias = mapOf(
        Calendar.MONDAY to "lunes", Calendar.TUESDAY to "martes", Calendar.WEDNESDAY to "miércoles",
        Calendar.THURSDAY to "jueves", Calendar.FRIDAY to "viernes", Calendar.SATURDAY to "sábado",
        Calendar.SUNDAY to "domingo"
    )

    private val elegirContacto = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) agregarContacto(uri)
    }

    // Accesos cortos a las vistas
    private fun et(id: Int) = findViewById<EditText>(id)

    /** Con «hasta (hora)» el campo de pausa pide una hora; con las demás unidades, un número. */
    private fun ajustarCampoPausa() {
        val esHora = sp(R.id.spPausaUnidad).selectedItemPosition == 4
        val campo = et(R.id.etPausa)
        campo.inputType = if (esHora) android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME
        else android.text.InputType.TYPE_CLASS_NUMBER
        campo.hint = if (esHora) "HH:mm" else "---"
    }
    private fun cb(id: Int) = findViewById<CheckBox>(id)
    private fun rb(id: Int) = findViewById<RadioButton>(id)
    private fun rg(id: Int) = findViewById<RadioGroup>(id)
    private fun sp(id: Int) = findViewById<Spinner>(id)
    private fun vista(id: Int) = findViewById<View>(id)
    private fun texto(id: Int) = et(id).text.toString().trim()
    private fun numero(id: Int, defecto: Int) = texto(id).toIntOrNull() ?: defecto
    private fun mostrarSi(id: Int, visible: Boolean) {
        vista(id).visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_regla)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val id = intent.getStringExtra(EXTRA_ID)
        val padre = intent.getStringExtra(EXTRA_PADRE)
        val existente = id?.let { i -> Almacen.reglas().firstOrNull { it.id == i } }
        esNueva = existente == null
        regla = existente ?: Regla()
        if (esNueva && padre != null) {
            regla.condReglaPrevia = padre
            regla.tipo = TipoCoincidencia.EXACTA
            regla.condReglaPreviaSegundos = 300
        }
        title = when {
            !esNueva -> "Regla"
            padre != null -> "Nuevo submenú"
            else -> "Nueva regla"
        }

        configurarEventos()
        mostrar(regla)
        cargando = false
        actualizar()
    }

    override fun onResume() {
        super.onResume()
        if (::regla.isInitialized) actualizarSubmenus()
    }

    // ===================== Eventos =====================

    private fun configurarEventos() {
        findViewById<MaterialButton>(R.id.btnTodos).setOnClickListener {
            seleccionarTipo(if (tipoActual == TipoCoincidencia.TODOS) TipoCoincidencia.CONTIENE else TipoCoincidencia.TODOS)
        }
        radiosTipo.forEach { (vistaId, tipo) -> rb(vistaId).setOnClickListener { seleccionarTipo(tipo) } }

        conexiones.forEach { (vistaId, _) ->
            cb(vistaId).setOnCheckedChangeListener { _, marcado ->
                if (cargando) return@setOnCheckedChangeListener
                if (marcado) conexiones.keys.filter { it != vistaId }.forEach { cb(it).isChecked = false }
                actualizar()
            }
        }

        findViewById<ImageButton>(R.id.btnNoResponder).setOnClickListener {
            noResponder = !noResponder
            actualizar()
        }
        findViewById<ImageButton>(R.id.btnFormato).setOnClickListener { menuFormato(it) }
        findViewById<ImageButton>(R.id.btnReemplazos).setOnClickListener { elegirVariable() }
        findViewById<MaterialButton>(R.id.btnMas).setOnClickListener {
            if (camposRespuesta.size < MAX_RESPUESTAS) {
                agregarCampoRespuesta("").requestFocus()
                if (camposRespuesta.size > 1 && rg(R.id.rgModo).checkedRadioButtonId == R.id.rbUna) rb(R.id.rbAleatoria).isChecked = true
            }
        }
        findViewById<MaterialButton>(R.id.btnMenos).setOnClickListener {
            if (camposRespuesta.size > 1) {
                val ultimo = camposRespuesta.removeAt(camposRespuesta.lastIndex)
                findViewById<LinearLayout>(R.id.contenedorRespuestas).removeView(ultimo)
                if (campoEnfocado === ultimo) campoEnfocado = null
            }
        }

        findViewById<ImageButton>(R.id.btnContactoEspecifico).setOnClickListener {
            destinoContacto = et(R.id.etContactos); elegirContacto.launch(null)
        }
        findViewById<ImageButton>(R.id.btnContactoIgnorado).setOnClickListener {
            destinoContacto = et(R.id.etIgnorados); elegirContacto.launch(null)
        }

        listOf(R.id.cbHorario, R.id.cbReglaPrevia, R.id.cbProbabilidad, R.id.cbIrARegla).forEach { c ->
            cb(c).setOnCheckedChangeListener { _, _ -> if (!cargando) actualizar() }
        }
        et(R.id.etPrompt).setOnFocusChangeListener { v, f -> if (f) campoEnfocado = v as EditText }

        findViewById<ImageButton>(R.id.btnNuevoSubmenu).setOnClickListener {
            if (guardar(cerrar = false)) {
                startActivity(Intent(this, ReglaActivity::class.java).putExtra(EXTRA_PADRE, regla.id))
            }
        }
        findViewById<FloatingActionButton>(R.id.fabGuardar).setOnClickListener { guardar(cerrar = true) }

        configurarAyudas()
    }

    private fun seleccionarTipo(t: TipoCoincidencia) {
        tipoActual = t
        radiosTipo.forEach { (vistaId, tipo) -> rb(vistaId).isChecked = tipo == t }
        val patron = et(R.id.etPatron)
        val todos = findViewById<MaterialButton>(R.id.btnTodos)
        todos.text = if (t == TipoCoincidencia.TODOS) "✓ Todos" else "Todos"
        todos.alpha = if (t == TipoCoincidencia.TODOS) 1f else 0.85f
        patron.isEnabled = t != TipoCoincidencia.TODOS && t != TipoCoincidencia.BIENVENIDA
        patron.alpha = if (patron.isEnabled) 1f else 0.5f
        patron.hint = when (t) {
            TipoCoincidencia.TODOS -> "Se responderá a todos los mensajes"
            TipoCoincidencia.BIENVENIDA -> "Primer mensaje de cada contacto"
            TipoCoincidencia.REGEX -> "Ej.: ^(hola|buenas).*"
            TipoCoincidencia.PATRON -> "Ej.: hola*, *precio*"
            TipoCoincidencia.SIMILITUD -> "Ej.: cuánto cuesta, precio del envío"
            else -> "Ej.: hola, buenas, info"
        }
    }

    private fun proveedorActual(): ProveedorIA =
        conexiones.entries.firstOrNull { cb(it.key).isChecked }?.value ?: ProveedorIA.NINGUNO

    /** Muestra u oculta paneles según lo marcado. */
    private fun actualizar() {
        val prov = proveedorActual()

        // No responder
        findViewById<ImageButton>(R.id.btnNoResponder).setBackgroundResource(
            if (noResponder) R.drawable.fondo_boton_rojo else R.drawable.fondo_boton_verde
        )
        mostrarSi(R.id.contenedorRespuestas, !noResponder)
        mostrarSi(R.id.tvNoResponder, noResponder)
        listOf(R.id.lineaModo, R.id.grupoModo, R.id.lineaConexiones, R.id.grupoConexiones).forEach { mostrarSi(it, !noResponder) }

        // Conexiones
        mostrarSi(R.id.panelIA, prov.esIA)
        mostrarSi(R.id.panelSheets, prov == ProveedorIA.SHEETS)
        mostrarSi(R.id.panelDialogflow, prov == ProveedorIA.DIALOGFLOW)
        mostrarSi(R.id.panelServidor, prov == ProveedorIA.SERVIDOR)
        mostrarSi(R.id.panelTasker, prov == ProveedorIA.TASKER)
        if (prov.esIA) cambiarClaveVisible(prov)
        findViewById<TextView>(R.id.tvModeloAyuda).text = when (prov) {
            ProveedorIA.CLAUDE -> "Vacío = ${ClienteIA.MODELO_CLAUDE}. Otros: claude-sonnet-5-5"
            ProveedorIA.OPENAI -> "Vacío = ${ClienteIA.MODELO_OPENAI}. Otros: gpt-4.1, gpt-4o-mini"
            ProveedorIA.GEMINI -> "Vacío = ${ClienteIA.MODELO_GEMINI}. Otros: gemini-3.8-flash"
            else -> ""
        }
        val externo = prov != ProveedorIA.NINGUNO
        camposRespuesta.forEachIndexed { i, e ->
            val n = if (camposRespuesta.size > 1) " (${i + 1})" else ""
            e.hint = if (externo) "Respuesta de respaldo$n (opcional)" else "Mensaje que se enviará$n"
        }
        findViewById<TextView>(R.id.tvTituloRespuesta).text =
            if (prov.esIA) "Respuesta con ${prov.etiqueta}" else "Respuesta automática"

        // Condiciones y flujo
        mostrarSi(R.id.panelHorario, cb(R.id.cbHorario).isChecked)
        mostrarSi(R.id.panelReglaPrevia, cb(R.id.cbReglaPrevia).isChecked)
        mostrarSi(R.id.panelProbabilidad, cb(R.id.cbProbabilidad).isChecked)
        mostrarSi(R.id.panelIrARegla, cb(R.id.cbIrARegla).isChecked)
    }

    /** El campo "API key" muestra la clave del proveedor de IA elegido. */
    private fun cambiarClaveVisible(prov: ProveedorIA) {
        if (proveedorDeLaClave == prov) return
        proveedorDeLaClave?.let { clavesEditadas[it] = texto(R.id.etClaveIA) }
        val guardada = clavesEditadas[prov] ?: when (prov) {
            ProveedorIA.CLAUDE -> Ajustes.claveClaude
            ProveedorIA.OPENAI -> Ajustes.claveOpenAI
            ProveedorIA.GEMINI -> Ajustes.claveGemini
            else -> ""
        }
        et(R.id.etClaveIA).setText(guardada)
        et(R.id.etClaveIA).hint = when (prov) {
            ProveedorIA.CLAUDE -> "sk-ant-..."
            ProveedorIA.OPENAI -> "sk-..."
            else -> "AIza..."
        }
        proveedorDeLaClave = prov
    }

    private fun agregarCampoRespuesta(textoInicial: String): EditText {
        val contenedor = findViewById<LinearLayout>(R.id.contenedorRespuestas)
        val e = layoutInflater.inflate(R.layout.item_respuesta, contenedor, false) as EditText
        e.setText(textoInicial)
        e.setOnFocusChangeListener { v, f -> if (f) campoEnfocado = v as EditText }
        contenedor.addView(e)
        camposRespuesta.add(e)
        if (!cargando) actualizar()
        return e
    }

    private fun campoObjetivo(): EditText = campoEnfocado ?: camposRespuesta.first()

    private fun menuFormato(ancla: View) {
        val opciones = listOf("Negrita  *texto*" to "*", "Cursiva  _texto_" to "_", "Tachado  ~texto~" to "~", "Monoespaciado  ```texto```" to "```")
        val popup = PopupMenu(this, ancla)
        opciones.forEachIndexed { i, (t, _) -> popup.menu.add(0, i, i, t) }
        popup.setOnMenuItemClickListener { item ->
            val marca = opciones[item.itemId].second
            val e = campoObjetivo()
            val largo = e.text.length
            val ini = e.selectionStart.let { if (it < 0) largo else it }
            val fin = e.selectionEnd.let { if (it < 0) largo else it }.coerceAtLeast(ini)
            e.text.insert(fin, marca)
            e.text.insert(ini, marca)
            e.requestFocus()
            e.setSelection(ini + marca.length, fin + marca.length)
            true
        }
        popup.show()
    }

    private fun elegirVariable() {
        val variables = mutableListOf(
            "%nombre%" to "Nombre del contacto",
            "%saludo%" to "Buenos días / tardes / noches",
            "%mensaje%" to "Mensaje recibido",
            "%chat%" to "Nombre del chat o grupo",
            "%hora%" to "Hora actual",
            "%fecha%" to "Fecha actual",
            "%dia%" to "Día de la semana"
        )
        Almacen.reemplazos().forEach { variables += "%${it.clave}%" to it.valor.take(40) }
        val textos = variables.map { "${it.first}   ${it.second}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Insertar variable")
            .setItems(textos) { _, i ->
                val e = campoObjetivo()
                val pos = e.selectionStart.let { if (it < 0) e.text.length else it }
                e.text.insert(pos, variables[i].first)
                e.requestFocus()
            }
            .setNeutralButton("Mis reemplazos") { _, _ -> startActivity(Intent(this, ReemplazosActivity::class.java)) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun agregarContacto(uri: Uri) {
        val destino = destinoContacto ?: return
        try {
            contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nombre = c.getString(0)?.trim().orEmpty()
                    if (nombre.isNotEmpty()) {
                        val actual = destino.text.toString().trim().trimEnd(',')
                        destino.setText(if (actual.isEmpty()) nombre else "$actual, $nombre")
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo leer el contacto", Toast.LENGTH_SHORT).show()
        }
    }

    // ===================== Mostrar la regla =====================

    private fun <T> idDe(mapa: Map<Int, T>, valor: T): Int =
        mapa.entries.firstOrNull { it.value == valor }?.key ?: mapa.keys.first()

    private fun mostrar(r: Regla) {
        // Mensaje recibido
        et(R.id.etPatron).setText(r.patron)
        seleccionarTipo(r.tipo)

        // Respuesta
        noResponder = r.noResponder
        if (r.respuestas.isEmpty()) agregarCampoRespuesta("") else r.respuestas.forEach { agregarCampoRespuesta(it) }
        rg(R.id.rgModo).check(idDe(modos, r.modo))
        conexiones.forEach { (vistaId, prov) -> cb(vistaId).isChecked = r.proveedor == prov }
        et(R.id.etModelo).setText(r.modelo)
        et(R.id.etPrompt).setText(r.promptSistema)
        et(R.id.etTemperatura).setText(r.temperatura)
        et(R.id.etMaxTokens).setText(r.maxTokens.toString())
        et(R.id.etHistorial).setText(r.historial.toString())
        cb(R.id.cbIAArchivos).isChecked = r.iaPuedeEnviarArchivos
        et(R.id.etSheetsUrl).setText(r.sheetsUrl)
        et(R.id.etSheetsColBusqueda).setText(r.sheetsColBusqueda)
        et(R.id.etSheetsColRespuesta).setText(r.sheetsColRespuesta)
        et(R.id.etSheetsAlternativa).setText(r.sheetsAlternativa)
        rg(R.id.rgSheetsModo).check(idDe(modosSheets, r.sheetsModo))
        et(R.id.etDialogflowIdioma).setText(r.dialogflowIdioma)
        et(R.id.etServidorUrl).setText(r.servidorUrl)
        et(R.id.etTaskerEspera).setText(r.taskerEspera.toString())

        // Archivo
        opcionesAdjunto = listOf(SIN_ARCHIVO) + Almacen.biblioteca().map { it.clave }
        sp(R.id.spAdjunto).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesAdjunto)
        sp(R.id.spAdjunto).setSelection(opcionesAdjunto.indexOfFirst { it.equals(r.adjunto, true) }.coerceAtLeast(0))

        // Opcional
        et(R.id.etDelayMin).setText(r.delayMin.toString())
        et(R.id.etDelayMax).setText(r.delayMax.toString())
        cb(R.id.cbCancelarRetrasados).isChecked = r.cancelarRetrasados
        rg(R.id.rgDestinatarios).check(idDe(destinatarios, r.destinatarios))
        et(R.id.etContactos).setText(r.contactos)
        et(R.id.etIgnorados).setText(r.ignorados)

        cb(R.id.cbHorario).isChecked = r.usarHorario
        camposHorario.forEach { (vistaId, dia) -> et(vistaId).setText(r.horarioDias[dia].orEmpty()) }

        val otras = Almacen.reglas().filter { it.id != r.id }
        idsOtras = otras.map { it.id }
        val nombres = otras.mapIndexed { i, o -> o.nombre.ifBlank { "Regla ${i + 1}" } }.ifEmpty { listOf("(no hay otras reglas)") }
        sp(R.id.spReglaPrevia).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nombres)
        sp(R.id.spIrARegla).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nombres)
        cb(R.id.cbReglaPrevia).isChecked = r.condReglaPrevia.isNotBlank()
        sp(R.id.spReglaPrevia).setSelection(idsOtras.indexOf(r.condReglaPrevia).coerceAtLeast(0))
        et(R.id.etReglaPreviaSeg).setText(r.condReglaPreviaSegundos.toString())
        cb(R.id.cbProbabilidad).isChecked = r.usarProbabilidad
        et(R.id.etProbabilidad).setText(r.probabilidad.toString())
        cb(R.id.cbPantalla).isChecked = r.condPantallaApagada
        cb(R.id.cbCargando).isChecked = r.condCargando
        cb(R.id.cbSilencio).isChecked = r.condSilencio
        cb(R.id.cbNoMolestar).isChecked = r.condNoMolestar
        cb(R.id.cbCoche).isChecked = r.condModoCoche

        cb(R.id.cbPrioritaria).isChecked = r.notificacionPrioritaria

        val salidas = ModoSalida.values().map { it.etiqueta }
        sp(R.id.spSalidaSheets).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, salidas)
        sp(R.id.spSalidaWebhook).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, salidas)
        sp(R.id.spSalidaSheets).setSelection(r.salidaSheets.ordinal)
        sp(R.id.spSalidaWebhook).setSelection(r.salidaWebhook.ordinal)

        sp(R.id.spPausaUnidad).adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, UNIDADES)
        val unidad = r.pausaUnidad.coerceIn(0, 4)
        sp(R.id.spPausaUnidad).setSelection(unidad)
        sp(R.id.spPausaUnidad).onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = ajustarCampoPausa()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) = Unit
        }
        ajustarCampoPausa()
        et(R.id.etPausa).setText(
            when {
                unidad == 4 -> r.pausaHasta
                r.pausaSegundos > 0 -> (r.pausaSegundos / FACTORES[unidad]).toString()
                else -> ""
            }
        )
        et(R.id.etPausaMensajes).setText(if (r.pausaMensajes > 0) r.pausaMensajes.toString() else "")

        cb(R.id.cbIrARegla).isChecked = r.irARegla.isNotBlank()
        sp(R.id.spIrARegla).setSelection(idsOtras.indexOf(r.irARegla).coerceAtLeast(0))
        if (idsOtras.isEmpty()) {
            cb(R.id.cbIrARegla).isEnabled = false
            cb(R.id.cbReglaPrevia).isEnabled = r.condReglaPrevia.isNotBlank()
        }
        et(R.id.etNombre).setText(r.nombre)
        actualizarSubmenus()
    }

    private fun actualizarSubmenus() {
        val hijos = Almacen.reglas().filter { it.condReglaPrevia == regla.id }
        findViewById<TextView>(R.id.tvSubmenus).text = if (hijos.isEmpty()) {
            "Toca ⊕ para crear un submenú: una regla que solo responde después de esta (ej. opciones 1, 2, 3 de un menú)."
        } else {
            "Submenús de esta regla: " + hijos.joinToString(", ") { it.nombre.ifBlank { it.patron } }
        }
    }

    // ===================== Guardar =====================

    private fun error(mensaje: String): Boolean {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        return false
    }

    private fun guardar(cerrar: Boolean): Boolean {
        val r = regla
        val prov = proveedorActual()
        r.tipo = tipoActual
        r.patron = texto(R.id.etPatron)
        r.noResponder = noResponder
        r.respuestas = camposRespuesta.map { it.text.toString().trim() }.filter { it.isNotEmpty() }.toMutableList()
        r.modo = modos[rg(R.id.rgModo).checkedRadioButtonId] ?: ModoRespuesta.UNA
        r.proveedor = prov
        r.modelo = texto(R.id.etModelo)
        r.promptSistema = texto(R.id.etPrompt)
        r.temperatura = texto(R.id.etTemperatura)
        r.maxTokens = numero(R.id.etMaxTokens, 500).coerceIn(16, 8000)
        r.historial = numero(R.id.etHistorial, 6).coerceIn(0, 20)
        r.iaPuedeEnviarArchivos = cb(R.id.cbIAArchivos).isChecked
        r.sheetsUrl = texto(R.id.etSheetsUrl)
        r.sheetsColBusqueda = texto(R.id.etSheetsColBusqueda).uppercase().ifBlank { "A" }
        r.sheetsColRespuesta = texto(R.id.etSheetsColRespuesta).uppercase().ifBlank { "B" }
        r.sheetsAlternativa = texto(R.id.etSheetsAlternativa)
        r.sheetsModo = modosSheets[rg(R.id.rgSheetsModo).checkedRadioButtonId] ?: TipoCoincidencia.CONTIENE
        r.dialogflowIdioma = texto(R.id.etDialogflowIdioma).ifBlank { "es" }
        r.servidorUrl = texto(R.id.etServidorUrl)
        r.taskerEspera = numero(R.id.etTaskerEspera, 10).coerceIn(1, 120)
        r.adjunto = opcionesAdjunto.getOrNull(sp(R.id.spAdjunto).selectedItemPosition)?.takeIf { it != SIN_ARCHIVO } ?: ""
        r.delayMin = numero(R.id.etDelayMin, 0).coerceAtLeast(0)
        r.delayMax = numero(R.id.etDelayMax, r.delayMin).coerceAtLeast(r.delayMin)
        r.cancelarRetrasados = cb(R.id.cbCancelarRetrasados).isChecked
        r.destinatarios = destinatarios[rg(R.id.rgDestinatarios).checkedRadioButtonId] ?: Destinatarios.AMBOS
        r.contactos = texto(R.id.etContactos)
        r.ignorados = texto(R.id.etIgnorados)
        r.usarHorario = cb(R.id.cbHorario).isChecked
        r.horarioDias = camposHorario.entries.associate { (vistaId, dia) -> dia to texto(vistaId) }
            .filterValues { it.isNotBlank() }.toMutableMap()
        r.condReglaPrevia = if (cb(R.id.cbReglaPrevia).isChecked) idsOtras.getOrNull(sp(R.id.spReglaPrevia).selectedItemPosition) ?: "" else ""
        r.condReglaPreviaSegundos = numero(R.id.etReglaPreviaSeg, 300).coerceAtLeast(0)
        r.usarProbabilidad = cb(R.id.cbProbabilidad).isChecked
        r.probabilidad = numero(R.id.etProbabilidad, 50).coerceIn(0, 100)
        r.condPantallaApagada = cb(R.id.cbPantalla).isChecked
        r.condCargando = cb(R.id.cbCargando).isChecked
        r.condSilencio = cb(R.id.cbSilencio).isChecked
        r.condNoMolestar = cb(R.id.cbNoMolestar).isChecked
        r.condModoCoche = cb(R.id.cbCoche).isChecked
        r.notificacionPrioritaria = cb(R.id.cbPrioritaria).isChecked
        r.salidaSheets = ModoSalida.values().getOrElse(sp(R.id.spSalidaSheets).selectedItemPosition) { ModoSalida.GLOBAL }
        r.salidaWebhook = ModoSalida.values().getOrElse(sp(R.id.spSalidaWebhook).selectedItemPosition) { ModoSalida.GLOBAL }
        r.pausaUnidad = sp(R.id.spPausaUnidad).selectedItemPosition.coerceIn(0, 4)
        if (r.pausaUnidad == 4) {
            r.pausaSegundos = 0
            r.pausaHasta = texto(R.id.etPausa)
        } else {
            r.pausaHasta = ""
            r.pausaSegundos = (numero(R.id.etPausa, 0).coerceAtLeast(0).toLong() * FACTORES[r.pausaUnidad])
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        r.pausaMensajes = numero(R.id.etPausaMensajes, 0).coerceAtLeast(0)
        r.irARegla = if (cb(R.id.cbIrARegla).isChecked) idsOtras.getOrNull(sp(R.id.spIrARegla).selectedItemPosition) ?: "" else ""
        r.nombre = texto(R.id.etNombre)

        // Validaciones
        val necesitaPatron = r.tipo != TipoCoincidencia.TODOS && r.tipo != TipoCoincidencia.BIENVENIDA
        if (necesitaPatron && r.patron.isBlank()) return error("Escribe el mensaje que activa la regla, o pulsa TODOS")
        if (r.tipo == TipoCoincidencia.REGEX && runCatching { Regex(r.patron) }.isFailure) return error("La expresión regular no es válida")
        if (!r.noResponder) {
            if (prov == ProveedorIA.NINGUNO && r.respuestas.isEmpty() && r.adjunto.isBlank()) return error("Escribe el mensaje que se enviará o elige un archivo")
            if (prov == ProveedorIA.SHEETS && r.sheetsUrl.isBlank()) return error("Pega el enlace de tu hoja de Google Sheets")
            if (prov == ProveedorIA.SERVIDOR && !r.servidorUrl.startsWith("http")) return error("Escribe la URL de tu servidor (https://...)")
        }
        if (r.usarHorario) {
            if (r.horarioDias.isEmpty()) return error("Escribe las horas de al menos un día (por ejemplo 0-24)")
            for ((dia, horas) in r.horarioDias) {
                if (Horario.parsear(horas) == null) return error("Horas no válidas el ${nombresDias[dia]}: «$horas». Ejemplo: 8-12,14:30-18")
            }
        }
        if (r.pausaUnidad == 4 && r.pausaHasta.isNotBlank() && Horario.horaSimple(r.pausaHasta) == null) {
            return error("Escribe la hora de la pausa como HH:mm (por ejemplo 08:30)")
        }
        if (cb(R.id.cbReglaPrevia).isChecked && r.condReglaPrevia.isBlank()) return error("Elige la regla anterior")
        if (cb(R.id.cbIrARegla).isChecked && r.irARegla.isBlank()) return error("Elige la regla a la que ir")
        if (r.nombre.isBlank()) {
            r.nombre = when (r.tipo) {
                TipoCoincidencia.TODOS -> "Todos los mensajes"
                TipoCoincidencia.BIENVENIDA -> "Bienvenida"
                else -> r.patron.take(30)
            }
            et(R.id.etNombre).setText(r.nombre)
        }

        // Claves de IA escritas aquí
        proveedorDeLaClave?.let { clavesEditadas[it] = texto(R.id.etClaveIA) }
        clavesEditadas.forEach { (p, clave) ->
            when (p) {
                ProveedorIA.CLAUDE -> Ajustes.claveClaude = clave
                ProveedorIA.OPENAI -> Ajustes.claveOpenAI = clave
                ProveedorIA.GEMINI -> Ajustes.claveGemini = clave
                else -> Unit
            }
        }

        Almacen.guardarRegla(r)
        esNueva = false
        if (cerrar) {
            Toast.makeText(this, "Regla guardada", Toast.LENGTH_SHORT).show()
            finish()
        }
        return true
    }

    // ===================== Ayudas (botones "i") =====================

    private fun configurarAyudas() {
        val ayudas = mapOf(
            R.id.infoExacta to "Responde solo si el mensaje es exactamente igual a lo que escribas.\n\nIgnora mayúsculas y minúsculas y los espacios sobrantes en los extremos. Los acentos se ignoran si lo tienes activado en ⋮ → Ajustes. Los emojis y la puntuación sí cuentan.\n\nPuedes poner varias opciones separadas por comas: hola, buenas, buen día.\n\nCómo responder a fotos, audios y otros medios:\nWhatsApp muestra los medios en la notificación como texto, empezando con un emoji (por ejemplo «📷 Foto»). Envía un medio de prueba, mira cómo aparece en el Historial y usa ese texto en tu regla (mejor con «Contiene»).",
            R.id.infoContiene to "Responde si el mensaje contiene alguna de las palabras o frases. Ej.: «precio, costo» responde a «¿cuál es el precio del envío?».",
            R.id.infoSimilitud to "Responde a distintas variaciones de un mensaje, aunque tenga errores de tipeo o las palabras en otro orden.\n\nLa app calcula un porcentaje de parecido (0 a 100) entre el mensaje y tu texto. Responde si supera el umbral, que puedes cambiar en ⋮ → Ajustes → Umbral de similitud.\n\nSepara varios mensajes con comas.\n\nFunciona mejor con mensajes de longitud parecida. Si quieres responder a cualquier mensaje que contenga una palabra o frase, usa «Contiene» o «Patrón con *».",
            R.id.infoPatron to "Responde si el mensaje encaja con el patrón completo. El * significa «cualquier cosa».\n\nResponder si…\n…el mensaje empieza con «test»:  test*\n…termina con «test»:  *test\n…contiene «test»:  *test*\n\nUso:\nSepara varios patrones con comas; basta con que uno coincida.\n\nEjemplos:\nEh*estás  responde a «Eh, ¿cómo estás»\n*gracias  responde a «Gracias» y a «Muchas gracias»\n*prueba, prueba*  responde si el mensaje termina o empieza con «prueba»\n\nIgnora mayúsculas, y los acentos si lo tienes activado en Ajustes.",
            R.id.infoRegex to "Para usuarios avanzados: condiciones complejas con una expresión regular de Java. Busca en Internet «java regular expression» como referencia. No distingue mayúsculas de minúsculas. Basta con que el patrón aparezca en cualquier parte del mensaje; usa ^ al inicio y $ al final para exigir el mensaje completo.\n\nGrupos de captura: usa %1%, %2%… en la respuesta para insertar partes del mensaje recibido (%0% es toda la coincidencia). Ejemplo: el patrón pedido (\\d+) y la respuesta «Anoté el pedido %1%».\n\nEjemplos:\nhi+  responde a hi, hii, hiii…\n^.{10,}$  mensajes de 10 o más caracteres\n^[0-9]{4}$  cualquier PIN de 4 dígitos\nCo{2,}l  responde a Cool, Coool…",
            R.id.infoBienvenida to "Responde al primer mensaje que recibes de cada chat desde que instalaste la app (o desde que reiniciaste el estado en Ajustes).\n\nLa app no puede saber con quién chateaste antes de instalarla, así que también contestará a contactos que ya conocías. Si te agregan a un grupo nuevo, también puede saludar (elige «Grupos» o «Ambos» en Destinatarios).\n\nConsejo: para un saludo recurrente cada x días, usa «Cualquier mensaje» en lugar de «Bienvenida» y combínalo con «Pausar regla por…» (por ejemplo, 1 día).",
            R.id.infoGemini to "Gemini es el modelo de IA de Google. Con esta opción, responde por ti los mensajes de WhatsApp.\n\nPuedes indicarle en las instrucciones qué personalidad debe tener, el tono, el nivel de formalidad y a qué debe prestar atención.\n\nQué necesitas:\n• Una API key gratuita: aistudio.google.com/apikey (el nivel gratuito tiene límites de uso)\n• Modelo (opcional): si lo dejas vacío usa gemini-3.5-flash-lite.\n\nLa key se guarda solo en tu teléfono.",
            R.id.infoChatGPT to "ChatGPT es un modelo de lenguaje de OpenAI capaz de responder preguntas y mantener una conversación. Con esta opción, responde por ti los mensajes de WhatsApp.\n\nPuedes indicarle en las instrucciones qué personalidad debe tener (por ejemplo, la de un vendedor de celulares), el tono, el nivel de formalidad y a qué debe prestar atención. Es como tu asistente personal.\n\nQué necesitas:\n• Una cuenta y una API key: platform.openai.com/api-keys\n• Modelo (opcional): si lo dejas vacío usa gpt-4.1-mini. Lista de modelos: platform.openai.com/docs/models\n• Los demás parámetros normalmente no hace falta cambiarlos.\n\nLa key se guarda solo en tu teléfono. Cada respuesta se cobra según el uso de tu cuenta de OpenAI.",
            R.id.infoClaude to "Claude es el modelo de IA de Anthropic. Con esta opción, responde por ti los mensajes de WhatsApp.\n\nPuedes indicarle en las instrucciones qué personalidad debe tener, el tono, el nivel de formalidad y a qué debe prestar atención.\n\nQué necesitas:\n• Una API key: console.anthropic.com → API Keys (necesita saldo en la cuenta)\n• Modelo (opcional): si lo dejas vacío usa claude-haiku-4-5-20251001.\n\nLa key se guarda solo en tu teléfono. Cada respuesta se cobra según el uso de tu cuenta.",
            R.id.infoSheets to "Con Google Sheets puedes crear tu propia base de datos de respuestas. Cuando llega un mensaje, la app busca una coincidencia en una columna de tu hoja y responde con el contenido de otra columna de la misma fila.\n\nCómo funciona:\n1. Comparte la hoja como «Cualquier persona con el enlace» y pega el enlace.\n2. Define la columna de búsqueda (por defecto A) y la de respuesta (por defecto B).\n3. Elige cómo se compara el mensaje (contiene, exacta o similitud).\n4. Opcionalmente escribe una respuesta alternativa para cuando no haya coincidencia.\n\nEjemplo: la columna A tiene palabras clave (varias separadas por comas o //) y la B las respuestas. Cuando alguien envía «precio», la app encuentra «precio» en la A y responde con la B de esa fila.\n\nPersonalizar la respuesta: por defecto se envía el valor de la celda. Para incluirlo en un mensaje, escribe en el campo de respuesta de arriba el marcador %sheet_result%.\nEjemplo: «El precio es: %sheet_result%»\n\nLa app guarda la hoja 3 minutos para ir más rápido.",
            R.id.infoDialogflow to "Envía el mensaje a tu agente de Dialogflow ES y responde con su respuesta. Configura las credenciales en Ajustes.",
            R.id.infoServidor to "Envía el mensaje a tu servidor con un POST en JSON y responde con lo que devuelva.\n\nSolicitud: appPackageName, messengerPackageName, timestamp, query { sender, message, reply, isGroup, groupParticipant, chat, ruleId, isTestMessage }.\n\nRespuesta esperada: {\"replies\":[{\"message\":\"Hola\"}]}. También se aceptan {\"reply\":\"Hola\"} y texto plano.\n\nUsa una dirección https:// (Android bloquea http://). Si el servidor falla, se envía el texto fijo de la regla.",
            R.id.infoTasker to "Envía el mensaje a Tasker/MacroDroid y espera su respuesta. Los intents están explicados en Ajustes.",
            R.id.infoAdjunto to "Envía además una imagen o archivo de tu biblioteca. Para mandarlo, la app abre WhatsApp y pulsa Enviar (necesita el servicio de accesibilidad y el teléfono desbloqueado).",
            R.id.infoRetraso to "Espera un tiempo al azar entre el mínimo y el máximo antes de responder, para que parezca más natural.",
            R.id.infoCancelar to "Si esta regla se activa, cancela las respuestas de otras reglas que todavía estaban esperando para este contacto.",
            R.id.infoContactos to "Escribe el nombre completo de un contacto, grupo o participante tal como aparece en WhatsApp, o un número de teléfono con código de país. Separa varios con comas.\n\nComodín * soportado:\n+34* = todos los números de España\n+* = números sin guardar en tus contactos\nAna* = nombres que empiezan por Ana\n\nEn grupos puedes usar el nombre del grupo o el de un participante. También puedes limitar a ciertos participantes de un grupo así:\n\nnombre del grupo{participante 1, participante 2}\n\nSi lo dejas vacío, la regla vale para todos.",
            R.id.infoIgnorados to "Nunca responde a estos contactos o grupos.",
            R.id.infoHorario to "Establece las horas de respuesta de cada día. Un día en blanco no responde; escribe 0-24 para todo el día.\n\nSepara horas y minutos con . o :\nAñade un rango de tiempo con -\nSepara rangos con ,\n(Formato de 12 y 24 horas)\n\nNota: si la hora final es anterior a la inicial, el rango continúa al día siguiente. 22:30-5:00 el lunes responde desde el lunes a las 22:30 hasta el martes a las 5:00, aunque dejes el martes en blanco.\n\nEjemplos:\n22:30-5:00\n12am-9am,2.30pm-12am\n0-11:30,14:30-24",
            R.id.infoReglaPrevia to "La regla solo responde si, en el mismo chat, la regla elegida se activó hace menos de los segundos indicados.\n\nSe usa sobre todo con submenús: por ejemplo, para crear un tiempo de espera de la sesión o un cuestionario con tiempo limitado para responder.\n\nSi pones 0 segundos, no hay límite de tiempo.",
            R.id.infoProbabilidad to "La regla solo se activa con la probabilidad que indiques, en porcentaje (de 0 a 100). Ejemplo: 50 = más o menos la mitad de las veces.\n\nEs muy útil en submenús para guiar al contacto de forma aleatoria hacia una u otra opción.\n\nCuidado con varias reglas seguidas con probabilidad: si una regla no sale por azar, la app prueba la siguiente. Para que todas tengan la misma opción, la primera necesita una probabilidad menor y la última debe tener 100 %.\n\nCálculo: probabilidad objetivo de la regla actual ÷ la suma de la probabilidad objetivo de la regla actual y de las siguientes.\nEjemplo con 3 reglas iguales: 33 %, 50 % y 100 %.",
            R.id.infoPrioritaria to "Muestra una notificación con sonido, separada de los avisos normales de «Respondido a…», cada vez que esta regla se activa.\n\nPor ejemplo, para enterarte al instante cuando un cliente pide hablar con una persona o hace un pedido.\n\nPuedes activar o desactivar estas notificaciones para toda la app en ⋮ → Ajustes.",
            R.id.infoSalidaSheets to "Registra una fila en una hoja de Google Sheets cada vez que esta regla responde.\n\nLa app envía un POST en JSON a la URL de tu script de Google Apps Script con: fecha, app, chat, remitente, grupo, mensaje, respuesta, regla y error. Tú decides en el script en qué columnas guardarlos.\n\nLa URL se configura en ⋮ → Ajustes → Salida de mensajes. Aquí puedes elegir «Siempre» o «Nunca» solo para esta regla; «Usar configuración global» aplica lo que definiste en Ajustes.",
            R.id.infoSalidaWebhook to "Envía un POST en JSON a tu propio servidor cada vez que esta regla responde, para que puedas reenviar los mensajes a tus sistemas.\n\nIncluye: fecha, app, chat, remitente, grupo, mensaje, respuesta, regla y error.\n\nLa URL se configura en ⋮ → Ajustes → Salida de mensajes. Aquí puedes elegir «Siempre» o «Nunca» solo para esta regla; «Usar configuración global» aplica lo de Ajustes.\n\nUsa una dirección https://.",
            R.id.infoPausa to "No repitas esta regla dentro de x segundos (o minutos, horas, días) ni para los siguientes x mensajes de un contacto, después de que se haya activado la regla.\n\nElige «hasta (hora)» para pausar la regla hasta una hora fija del día (por ejemplo 08:30). Si esa hora ya pasó el día en que se activó la regla, sigue pausada hasta esa hora del día siguiente.\n\nLa pausa es por conversación: otros contactos no se ven afectados.",
            R.id.infoFlujo to "Las reglas del submenú solo responden después de que la regla superior se haya activado antes en ese mismo chat. La app guarda la última regla activada por cada contacto.\n\n⊕ crea una regla de submenú. «Ir a la regla»: después de responder, la app también envía la respuesta de la regla elegida y la deja como la última activada (útil para volver a un menú).\n\nConsejos:\n• Crea un menú que ofrezca opciones 1, 2, 3 y añade una regla de submenú por cada opción.\n• Añade una regla de submenú para «cualquier mensaje» con «Ir a la regla» apuntando al menú, así el contacto vuelve a elegir si se equivoca.\n• Deja una regla sin submenú para que el contacto pueda salir."
        )
        val titulos = mapOf(
            R.id.infoExacta to "Coincidencia exacta",
            R.id.infoSimilitud to "Coincidencia de similitud",
            R.id.infoPatron to "Coincidencia de patrones",
            R.id.infoRegex to "Coincidencia de patrones experta (RegEx)",
            R.id.infoBienvenida to "Mensaje de bienvenida",
            R.id.infoReglaPrevia to "Responder solo si la regla anterior se ejecutó en los últimos x segundos",
            R.id.infoProbabilidad to "Probabilidad",
            R.id.infoContactos to "Contactos",
            R.id.infoSheets to "Google Sheets",
            R.id.infoChatGPT to "OpenAI ChatGPT",
            R.id.infoGemini to "Google Gemini",
            R.id.infoClaude to "Anthropic Claude",
            R.id.infoHorario to "Horario",
            R.id.infoPrioritaria to "Notificaciones prioritarias",
            R.id.infoSalidaSheets to "Salida de Google Sheets",
            R.id.infoSalidaWebhook to "Enviar respuestas a tu servidor",
            R.id.infoServidor to "Servidor web",
            R.id.infoPausa to "Pausar regla por… (por conversación)",
            R.id.infoFlujo to "Submenú / flujo de conversación"
        )
        val enlaces = mapOf(
            R.id.infoChatGPT to ("OpenAI API" to "https://platform.openai.com/api-keys"),
            R.id.infoGemini to ("Google AI Studio" to "https://aistudio.google.com/apikey"),
            R.id.infoClaude to ("Anthropic API" to "https://console.anthropic.com/settings/keys")
        )
        ayudas.forEach { (vistaId, texto) ->
            findViewById<ImageView>(vistaId).setOnClickListener {
                val dialogo = AlertDialog.Builder(this).setTitle(titulos[vistaId]).setMessage(texto)
                    .setPositiveButton("Entendido", null)
                enlaces[vistaId]?.let { (etiqueta, url) ->
                    dialogo.setNeutralButton(etiqueta) { _, _ ->
                        try {
                            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(this, "No se pudo abrir el navegador", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                dialogo.show()
            }
        }
    }

    // ===================== Menú =====================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!esNueva) menuInflater.inflate(R.menu.menu_regla, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.m_duplicar -> {
                Almacen.guardarRegla(regla.copy(id = UUID.randomUUID().toString(), nombre = regla.nombre + " (copia)"))
                Toast.makeText(this, "Regla duplicada", Toast.LENGTH_SHORT).show()
                finish()
            }
            R.id.m_eliminar -> AlertDialog.Builder(this)
                .setTitle("¿Eliminar esta regla?")
                .setPositiveButton("Eliminar") { _, _ -> Almacen.borrarRegla(regla.id); finish() }
                .setNegativeButton("Cancelar", null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
