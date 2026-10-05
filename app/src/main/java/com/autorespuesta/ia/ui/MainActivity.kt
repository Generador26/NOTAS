package com.autorespuesta.ia.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.Estadisticas
import com.autorespuesta.ia.datos.Regla
import com.autorespuesta.ia.motor.Texto
import com.autorespuesta.ia.servicio.Avisos
import com.autorespuesta.ia.servicio.ServicioAccesibilidad
import com.autorespuesta.ia.servicio.ServicioNotificaciones
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONArray
import java.util.UUID

/** Pantalla principal: lista de reglas, interruptor general y menú lateral (estilo AutoResponder). */
class MainActivity : AppCompatActivity() {

    private lateinit var adaptador: ReglasAdapter
    private lateinit var cajon: DrawerLayout
    private lateinit var conmutador: ActionBarDrawerToggle
    private lateinit var cardPermisos: MaterialCardView
    private lateinit var btnNotificaciones: MaterialButton
    private lateinit var btnAccesibilidad: MaterialButton
    private lateinit var btnSuperponer: MaterialButton
    private lateinit var btnBateria: MaterialButton
    private var swActivo: SwitchMaterial? = null
    private var filtro = ""

    private val exportar = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportarA(uri)
    }
    private val importar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importarDe(uri)
    }
    private val exportarEstadisticas = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) escribirEstadisticas(uri)
    }
    private val pedirNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cajon = findViewById(R.id.cajon)
        conmutador = ActionBarDrawerToggle(this, cajon, R.string.abrir_menu, R.string.cerrar_menu)
        cajon.addDrawerListener(conmutador)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        conmutador.syncState()

        val menuLateral = findViewById<NavigationView>(R.id.menuLateral)
        menuLateral.setCheckedItem(R.id.n_reglas)
        menuLateral.setNavigationItemSelectedListener { item ->
            cajon.closeDrawer(GravityCompat.START)
            abrirOpcionLateral(item.itemId)
            item.itemId == R.id.n_reglas
        }

        cardPermisos = findViewById(R.id.cardPermisos)
        btnNotificaciones = findViewById(R.id.btnNotificaciones)
        btnAccesibilidad = findViewById(R.id.btnAccesibilidad)
        btnSuperponer = findViewById(R.id.btnSuperponer)
        btnBateria = findViewById(R.id.btnBateria)

        adaptador = ReglasAdapter(
            alTocar = { abrirRegla(it.id) },
            alMantener = { r, v -> menuRegla(r, v) },
            alCambiar = { r, activa ->
                r.activa = activa
                Almacen.guardarRegla(r)
                cargarReglas()
            }
        )
        findViewById<RecyclerView>(R.id.rvReglas).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adaptador
        }
        findViewById<FloatingActionButton>(R.id.fabNueva).setOnClickListener { abrirRegla(null) }

        btnNotificaciones.setOnClickListener { abrirAjustesNotificaciones() }
        btnAccesibilidad.setOnClickListener { explicarAccesibilidad() }
        btnSuperponer.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btnBateria.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        if (Build.VERSION.SDK_INT >= 33 && !Avisos.puedeNotificar(this)) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<NavigationView>(R.id.menuLateral).setCheckedItem(R.id.n_reglas)
        cargarReglas()
        actualizarEstado()
        if (tieneAccesoNotificaciones()) {
            // Algunos teléfonos desconectan el servicio; esto le pide al sistema reconectarlo
            NotificationListenerService.requestRebind(ComponentName(this, ServicioNotificaciones::class.java))
        }
    }

    @Deprecated("Se mantiene por compatibilidad con versiones antiguas de Android")
    override fun onBackPressed() {
        if (cajon.isDrawerOpen(GravityCompat.START)) cajon.closeDrawer(GravityCompat.START)
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun cargarReglas() {
        val todas = Almacen.reglas()
        val f = Texto.normalizar(filtro, true)
        val visibles = if (f.isEmpty()) todas else todas.filter { r ->
            listOf(r.nombre, r.patron, r.respuestas.joinToString(" "))
                .any { Texto.normalizar(it, true).contains(f) }
        }
        adaptador.actualizar(visibles)
        findViewById<View>(R.id.vacio).visibility = if (visibles.isEmpty()) View.VISIBLE else View.GONE
        findViewById<android.widget.TextView>(R.id.tvVacio).text =
            if (todas.isEmpty()) "Toca + para crear una nueva regla de respuesta automática que uses para definir tus respuestas automáticas."
            else "Ninguna regla coincide con «$filtro»."
    }

    // ---------------- Interruptor general y permisos ----------------

    private fun tieneAccesoNotificaciones() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun tieneAccesibilidad(): Boolean {
        val habilitados = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return habilitados.contains("$packageName/") || ServicioAccesibilidad.activo
    }

    private fun puedeSuperponer() = Settings.canDrawOverlays(this)

    private fun sinRestriccionBateria(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(packageName) == true
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        conmutador.syncState()
    }

    private fun cambiarActivo(activo: Boolean) {
        if (activo && !tieneAccesoNotificaciones()) {
            actualizarEstado()   // vuelve a dejar el interruptor apagado sin disparar avisos
            AlertDialog.Builder(this)
                .setTitle("Falta un permiso")
                .setMessage(
                    "Para responder automáticamente, AutoRespuesta IA necesita leer las notificaciones de WhatsApp.\n\n" +
                        "En la siguiente pantalla busca «AutoRespuesta IA» y actívalo.\n\n" +
                        "Si aparece en gris: Ajustes → Apps → AutoRespuesta IA → ⋮ → «Permitir ajustes restringidos»."
                )
                .setPositiveButton("Activar") { _, _ -> abrirAjustesNotificaciones() }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }
        Ajustes.activo = activo
        Toast.makeText(
            this,
            if (activo) "AutoRespuesta activada" else "AutoRespuesta desactivada",
            Toast.LENGTH_SHORT
        ).show()
        actualizarEstado()
    }

    private fun actualizarEstado() {
        val notif = tieneAccesoNotificaciones()
        val acces = tieneAccesibilidad()
        val superp = puedeSuperponer()
        val bateria = sinRestriccionBateria()

        btnNotificaciones.visibility = if (notif) View.GONE else View.VISIBLE
        btnAccesibilidad.visibility = if (acces) View.GONE else View.VISIBLE
        btnSuperponer.visibility = if (superp) View.GONE else View.VISIBLE
        btnBateria.visibility = if (bateria) View.GONE else View.VISIBLE
        cardPermisos.visibility = if (notif && acces && superp && bateria) View.GONE else View.VISIBLE

        if (!notif && Ajustes.activo) Ajustes.activo = false
        swActivo?.let { sw ->
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = Ajustes.activo
            sw.setOnCheckedChangeListener { _, activo -> cambiarActivo(activo) }
        }
        val activas = Almacen.reglas().count { it.activa }
        supportActionBar?.subtitle = when {
            !notif -> "Falta el acceso a notificaciones"
            !Ajustes.activo -> "Desactivado"
            !ServicioNotificaciones.conectado -> "Activo · conectando con las notificaciones…"
            else -> "Activo · $activas regla(s)"
        }
    }

    private fun abrirAjustesNotificaciones() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        Toast.makeText(this, "Busca «AutoRespuesta IA» y actívalo", Toast.LENGTH_LONG).show()
    }

    private fun explicarAccesibilidad() {
        AlertDialog.Builder(this)
            .setTitle("Servicio de accesibilidad")
            .setMessage(
                "WhatsApp no permite adjuntar archivos desde la notificación. Para enviar imágenes y archivos, " +
                    "la app abre el chat con el archivo y este servicio pulsa «Enviar» por ti.\n\n" +
                    "Solo actúa dentro de WhatsApp y solo cuando una regla envía un archivo.\n\n" +
                    "En la siguiente pantalla entra en «Apps instaladas» (o «Servicios descargados») → " +
                    "AutoRespuesta IA → actívalo.\n\n" +
                    "Si aparece en gris («ajuste restringido»): ve a Ajustes → Apps → AutoRespuesta IA → ⋮ → " +
                    "«Permitir ajustes restringidos» y vuelve a intentarlo."
            )
            .setPositiveButton("Abrir ajustes") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    // ---------------- Reglas ----------------

    private fun abrirRegla(id: String?) {
        val i = Intent(this, ReglaActivity::class.java)
        if (id != null) i.putExtra(ReglaActivity.EXTRA_ID, id)
        startActivity(i)
    }

    private fun menuRegla(r: Regla, ancla: View) {
        val popup = PopupMenu(this, ancla)
        popup.menu.add(0, 1, 0, "Subir")
        popup.menu.add(0, 2, 1, "Bajar")
        popup.menu.add(0, 3, 2, "Duplicar")
        popup.menu.add(0, 4, 3, "Eliminar")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> Almacen.moverRegla(r.id, true)
                2 -> Almacen.moverRegla(r.id, false)
                3 -> Almacen.guardarRegla(r.copy(id = UUID.randomUUID().toString(), nombre = r.nombre + " (copia)"))
                4 -> confirmarBorrado(r)
            }
            cargarReglas()
            true
        }
        popup.show()
    }

    private fun confirmarBorrado(r: Regla) {
        AlertDialog.Builder(this)
            .setTitle("¿Eliminar la regla?")
            .setMessage(r.nombre.ifBlank { "Regla sin nombre" })
            .setPositiveButton("Eliminar") { _, _ -> Almacen.borrarRegla(r.id); cargarReglas(); actualizarEstado() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------- Menú lateral ----------------

    private fun abrirOpcionLateral(id: Int) {
        when (id) {
            R.id.n_reglas -> Unit
            R.id.n_reemplazos -> startActivity(Intent(this, ReemplazosActivity::class.java))
            R.id.n_probar -> startActivity(Intent(this, PruebaActivity::class.java))
            R.id.n_historial -> startActivity(Intent(this, HistorialActivity::class.java))
            R.id.n_estadisticas -> startActivity(Intent(this, EstadisticasActivity::class.java))
            R.id.n_biblioteca -> startActivity(Intent(this, BibliotecaActivity::class.java))
            R.id.n_ajustes -> startActivity(Intent(this, AjustesActivity::class.java))
            R.id.n_automatizacion -> AlertDialog.Builder(this)
                .setTitle("Automatización (Tasker / MacroDroid)")
                .setMessage(AjustesActivity.TEXTO_INTENTS)
                .setPositiveButton("Entendido", null)
                .setNeutralButton("Configuración") { _, _ -> startActivity(Intent(this, AjustesActivity::class.java)) }
                .show()
            R.id.n_acerca -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(
                    "Versión 1.0\n\nResponde automáticamente tus mensajes de WhatsApp y WhatsApp Business con " +
                        "reglas, inteligencia artificial (Claude, ChatGPT, Gemini), Google Sheets, Dialogflow, " +
                        "tu servidor o Tasker, y envía imágenes y archivos.\n\n" +
                        "Tus reglas y API keys se guardan solo en este teléfono."
                )
                .setPositiveButton("Cerrar", null)
                .show()
            R.id.n_invitar -> startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                        Intent.EXTRA_TEXT,
                        "Uso AutoRespuesta IA para responder WhatsApp automáticamente con inteligencia artificial. ¡Pruébala! 🤖"
                    ),
                    "Invitar a un amigo"
                )
            )
        }
    }

    // ---------------- Barra superior ----------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_principal, menu)
        swActivo = menu.findItem(R.id.m_activo)?.actionView?.findViewById(R.id.swActivoBarra)
        actualizarEstado()

        val buscador = menu.findItem(R.id.m_buscar)?.actionView as? SearchView
        buscador?.queryHint = "Buscar reglas"
        buscador?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filtro = newText.orEmpty()
                cargarReglas()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (conmutador.onOptionsItemSelected(item)) return true
        when (item.itemId) {
            R.id.m_probar -> startActivity(Intent(this, PruebaActivity::class.java))
            R.id.m_exportar -> exportar.launch("reglas_autorespuesta.json")
            R.id.m_importar -> importar.launch(arrayOf("application/json", "text/plain", "*/*"))
            R.id.m_estadisticas -> exportarEstadisticas.launch("estadisticas_autorespuesta.csv")
            R.id.m_no_funciona -> mostrarNoFunciona()
            R.id.m_ayuda -> mostrarAyuda()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun exportarA(uri: Uri) {
        try {
            val a = JSONArray()
            Almacen.reglas().forEach { a.put(it.aJson()) }
            contentResolver.openOutputStream(uri)?.use { it.write(a.toString(2).toByteArray()) }
            Toast.makeText(this, "Reglas exportadas (las API keys no se incluyen)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun escribirEstadisticas(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(Estadisticas.csv(Almacen.historial()).toByteArray()) }
            Toast.makeText(this, "Estadísticas exportadas", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun importarDe(uri: Uri) {
        try {
            val texto = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val a = JSONArray(texto)
            val existentes = Almacen.reglas()
            val ids = existentes.map { it.id }.toSet()
            var n = 0
            for (i in 0 until a.length()) {
                val r = Regla.desdeJson(a.getJSONObject(i))
                if (r.id in ids) r.id = UUID.randomUUID().toString()
                existentes.add(r); n++
            }
            Almacen.guardarReglas(existentes)
            cargarReglas()
            Toast.makeText(this, "$n regla(s) importada(s)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Archivo no válido: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun mostrarNoFunciona() {
        AlertDialog.Builder(this)
            .setTitle("¿No funciona?")
            .setMessage(
                "• La app solo ve los mensajes que generan notificación: si tienes el chat abierto en " +
                    "WhatsApp, o el chat está silenciado/archivado sin notificación, no responderá.\n\n" +
                    "• Activa las notificaciones de WhatsApp con vista previa.\n\n" +
                    "• Quita la restricción de batería y, en Xiaomi/Huawei/Oppo/Vivo, activa el «Inicio automático».\n\n" +
                    "• Si dejó de funcionar, desactiva y vuelve a activar el acceso a notificaciones.\n\n" +
                    "• Usa «Prueba tus reglas» (icono de arriba): te dice qué regla respondería y por qué las demás no.\n\n" +
                    "• Revisa «Historial de respuestas» en el menú: ahí aparecen los errores de la IA.\n\n" +
                    "• Los archivos solo se envían con el teléfono desbloqueado o sin bloqueo de pantalla."
            )
            .setPositiveButton("Entendido", null)
            .setNeutralButton("Acceso a notificaciones") { _, _ -> abrirAjustesNotificaciones() }
            .show()
    }

    private fun mostrarAyuda() {
        AlertDialog.Builder(this)
            .setTitle("Cómo funciona")
            .setMessage(
                "1. Enciende el interruptor de arriba (la primera vez te pedirá el acceso a notificaciones).\n\n" +
                    "2. Crea reglas con +. Se usa la PRIMERA regla activa que coincida, así que pon las más " +
                    "específicas arriba (mantén pulsada una regla → Subir/Bajar).\n\n" +
                    "3. Para IA: en la regla marca Gemini, ChatGPT o Claude y pega tu API key.\n\n" +
                    "4. Para enviar fotos o PDFs: súbelos en ☰ → Biblioteca de archivos y activa la accesibilidad.\n\n" +
                    "5. Para menús (1, 2, 3…): usa «Submenú/Flujo de conversación» dentro de cada regla.\n\n" +
                    "Aviso: las respuestas automáticas no son una función oficial de WhatsApp. Úsala con " +
                    "moderación (no envíes spam) para no arriesgar tu cuenta."
            )
            .setPositiveButton("Entendido", null)
            .show()
    }
}
