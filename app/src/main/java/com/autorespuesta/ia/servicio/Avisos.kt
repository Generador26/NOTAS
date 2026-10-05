package com.autorespuesta.ia.servicio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.ui.HistorialActivity
import com.autorespuesta.ia.ui.MainActivity
import java.util.concurrent.atomic.AtomicInteger

/** Notificaciones de la app: "respondí a X" (normales) y prioritarias por regla. */
object Avisos {
    private const val CANAL_NORMAL = "respuestas"
    private const val CANAL_PRIORITARIO = "prioritarias"
    private const val CANAL_ESTADO = "estado"
    const val ID_ESTADO = 1
    private val contador = AtomicInteger(1000)

    fun crearCanales(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CANAL_NORMAL, "Respuestas enviadas", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aviso discreto cada vez que la app responde un chat"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_ESTADO, "Estado del servicio", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aviso fijo que mantiene la app atenta a los mensajes nuevos"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_PRIORITARIO, "Notificaciones prioritarias", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos con sonido de las reglas marcadas como prioritarias"
            }
        )
    }

    /** Aviso fijo («Hola, soy tu AutoRespuesta. Esperando nuevos mensajes…») que mantiene vivo el servicio. */
    fun notificacionEstado(ctx: Context, texto: String): Notification {
        crearCanales(ctx)
        val abrir = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(ctx, CANAL_ESTADO)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle("Hola, soy tu AutoRespuesta.")
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun puedeNotificar(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notificar(ctx: Context, titulo: String, texto: String, prioritaria: Boolean) {
        val usarPrioritaria = prioritaria && Ajustes.notificacionesPrioritarias
        if (!usarPrioritaria && !Ajustes.notificaciones) return
        if (!puedeNotificar(ctx)) return
        crearCanales(ctx)
        val abrir = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, HistorialActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, if (usarPrioritaria) CANAL_PRIORITARIO else CANAL_NORMAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java)?.notify(contador.incrementAndGet(), n)
    }
}
