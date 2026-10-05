package com.autorespuesta.ia

import android.app.Application
import com.autorespuesta.ia.datos.Ajustes
import com.autorespuesta.ia.datos.Almacen
import com.autorespuesta.ia.motor.Motor
import com.autorespuesta.ia.servicio.Avisos

class AppAutoRespuesta : Application() {
    override fun onCreate() {
        super.onCreate()
        Ajustes.init(this)
        Almacen.init(this)
        Motor.init(this)
        Avisos.crearCanales(this)
    }
}
