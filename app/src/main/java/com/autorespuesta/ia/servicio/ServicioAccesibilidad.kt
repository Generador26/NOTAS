package com.autorespuesta.ia.servicio

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Solo actúa dentro de WhatsApp y solo cuando hay un archivo pendiente de enviar:
 * busca el botón "Enviar" de la pantalla de vista previa y lo pulsa.
 */
class ServicioAccesibilidad : AccessibilityService() {

    companion object {
        @Volatile var instancia: ServicioAccesibilidad? = null
            private set
        val activo: Boolean get() = instancia != null

        private val TEXTOS_ENVIAR = setOf("enviar", "send", "enviar a", "envoyer", "enviar archivo")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instancia = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instancia = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instancia = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val t = EnviadorArchivos.actual ?: return
        if (event?.packageName?.toString() != t.paquete) return
        val raiz = rootInActiveWindow ?: return
        if (raiz.packageName?.toString() != t.paquete) return
        val boton = buscarBotonEnviar(raiz, t.paquete) ?: return
        if (pulsar(boton)) EnviadorArchivos.completado()
    }

    private fun buscarBotonEnviar(raiz: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val ids = listOf("$pkg:id/send", "$pkg:id/send_media_btn", "$pkg:id/send_btn", "$pkg:id/media_send_button")
        for (id in ids) {
            raiz.findAccessibilityNodeInfosByViewId(id)
                .firstOrNull { it.isVisibleToUser && it.isEnabled }
                ?.let { return it }
        }
        // Diálogo de confirmación al enviar documentos ("¿Enviar a ...?")
        raiz.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull { it.isVisibleToUser }?.let { b ->
            val tx = b.text?.toString()?.trim()?.lowercase() ?: ""
            if (tx.contains("enviar") || tx.contains("send")) return b
        }
        return buscarPorTexto(raiz, 0)
    }

    private fun buscarPorTexto(n: AccessibilityNodeInfo, profundidad: Int): AccessibilityNodeInfo? {
        if (profundidad > 40) return null
        val desc = n.contentDescription?.toString()?.trim()?.lowercase()
        val tx = n.text?.toString()?.trim()?.lowercase()
        val coincide = (desc != null && desc in TEXTOS_ENVIAR) || (tx != null && tx in TEXTOS_ENVIAR)
        if (n.isVisibleToUser && coincide) return n
        for (i in 0 until n.childCount) {
            val hijo = n.getChild(i) ?: continue
            buscarPorTexto(hijo, profundidad + 1)?.let { return it }
        }
        return null
    }

    private fun pulsar(nodo: AccessibilityNodeInfo): Boolean {
        var x: AccessibilityNodeInfo? = nodo
        while (x != null) {
            if (x.isClickable && x.isEnabled) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            x = x.parent
        }
        return false
    }
}
