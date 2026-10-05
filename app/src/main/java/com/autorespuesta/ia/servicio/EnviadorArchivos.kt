package com.autorespuesta.ia.servicio

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.FileProvider
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.datos.EventoHistorial
import java.io.File

/**
 * Envía imágenes y archivos: abre WhatsApp directamente en el chat con el archivo
 * (usando el "jid" del contacto) y el servicio de accesibilidad pulsa "Enviar".
 * Los envíos se hacen de uno en uno.
 */
object EnviadorArchivos {

    data class Trabajo(
        val paquete: String,
        val jid: String,
        val nombreChat: String,
        val archivo: File,
        val mime: String,
        val pie: String,
        val clave: String,
        var intentos: Int = 0
    )

    private const val TIEMPO_MAXIMO = 20_000L
    private const val ESPERA_DESBLOQUEO = 15_000L
    private const val MAX_INTENTOS_BLOQUEO = 40   // ~10 minutos

    private val manejador = Handler(Looper.getMainLooper())
    private val cola = ArrayDeque<Trabajo>()
    private var contexto: Context? = null
    private var esperandoDesbloqueo = false

    /** Trabajo en curso (lo consulta el servicio de accesibilidad). Solo se usa en el hilo principal. */
    var actual: Trabajo? = null
        private set

    fun encolar(ctx: Context, t: Trabajo) {
        manejador.post {
            contexto = ctx.applicationContext
            cola.addLast(t)
            siguiente()
        }
    }

    private fun siguiente() {
        if (actual != null || esperandoDesbloqueo) return
        val ctx = contexto ?: return
        val t = cola.firstOrNull() ?: return

        if (!t.archivo.exists()) {
            cola.removeFirst(); fallo(t, "El archivo ya no existe en la biblioteca"); siguiente(); return
        }
        if (!ServicioAccesibilidad.activo) {
            cola.removeFirst()
            fallo(t, "Activa el servicio de accesibilidad de AutoRespuesta IA para enviar archivos")
            siguiente(); return
        }

        despertarPantalla(ctx)
        val km = ctx.getSystemService(KeyguardManager::class.java)
        if (km != null && km.isKeyguardLocked) {
            if (t.intentos >= MAX_INTENTOS_BLOQUEO) {
                cola.removeFirst(); fallo(t, "La pantalla siguió bloqueada; no se pudo enviar"); siguiente(); return
            }
            t.intentos++
            esperandoDesbloqueo = true
            manejador.postDelayed({ esperandoDesbloqueo = false; siguiente() }, ESPERA_DESBLOQUEO)
            return
        }

        cola.removeFirst()
        actual = t
        try {
            abrirWhatsApp(ctx, t)
        } catch (e: Exception) {
            actual = null
            fallo(t, "No se pudo abrir WhatsApp: ${e.message}")
            siguiente(); return
        }
        manejador.postDelayed({
            if (actual === t) {
                actual = null
                fallo(t, "No se encontró el botón Enviar en WhatsApp")
                ServicioAccesibilidad.instancia?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                manejador.postDelayed({ siguiente() }, 1000)
            }
        }, TIEMPO_MAXIMO)
    }

    /** Lo llama el servicio de accesibilidad (hilo principal) tras pulsar "Enviar". */
    fun completado() {
        val t = actual ?: return
        actual = null
        Almacen.agregarHistorial(
            EventoHistorial(System.currentTimeMillis(), t.nombreChat, "(envío de archivo)", "📎 ${t.clave} enviado", "Archivo", null)
        )
        manejador.postDelayed({
            ServicioAccesibilidad.instancia?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            manejador.postDelayed({ siguiente() }, 1200)
        }, 1800)
    }

    private fun abrirWhatsApp(ctx: Context, t: Trabajo) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", t.archivo)
        ctx.grantUriPermission(t.paquete, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = t.mime
            setPackage(t.paquete)
            putExtra(Intent.EXTRA_STREAM, uri)
            if (t.pie.isNotBlank()) putExtra(Intent.EXTRA_TEXT, t.pie)
            putExtra("jid", t.jid)   // abre directamente el chat de ese contacto
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // El servicio de accesibilidad puede abrir pantallas aunque la app esté en segundo plano
        (ServicioAccesibilidad.instancia ?: ctx).startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun despertarPantalla(ctx: Context) {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return
        if (pm.isInteractive) return
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "autorespuesta:enviar"
        )
        wl.acquire(TIEMPO_MAXIMO + 5_000)
    }

    private fun fallo(t: Trabajo, motivo: String) {
        Almacen.agregarHistorial(
            EventoHistorial(System.currentTimeMillis(), t.nombreChat, "(envío de archivo)", "📎 ${t.clave}", "Archivo", motivo)
        )
    }
}
