package com.autorespuesta.ia.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.autorespuesta.ia.R
import com.autorespuesta.ia.datos.Regla
import com.google.android.material.switchmaterial.SwitchMaterial

class ReglasAdapter(
    private val alTocar: (Regla) -> Unit,
    private val alMantener: (Regla, View) -> Unit,
    private val alCambiar: (Regla, Boolean) -> Unit
) : RecyclerView.Adapter<ReglasAdapter.Vista>() {

    private var reglas: List<Regla> = emptyList()

    fun actualizar(nuevas: List<Regla>) {
        reglas = nuevas
        notifyDataSetChanged()
    }

    class Vista(v: View) : RecyclerView.ViewHolder(v) {
        val nombre: TextView = v.findViewById(R.id.tvNombre)
        val resumen: TextView = v.findViewById(R.id.tvResumen)
        val interruptor: SwitchMaterial = v.findViewById(R.id.swRegla)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vista =
        Vista(LayoutInflater.from(parent.context).inflate(R.layout.item_regla, parent, false))

    override fun getItemCount() = reglas.size

    override fun onBindViewHolder(h: Vista, position: Int) {
        val r = reglas[position]
        h.nombre.text = "${position + 1}. ${r.nombre.ifBlank { "Regla sin nombre" }}"
        h.resumen.text = r.resumen()
        h.interruptor.setOnCheckedChangeListener(null)
        h.interruptor.isChecked = r.activa
        h.interruptor.setOnCheckedChangeListener { _, activa -> alCambiar(r, activa) }
        h.itemView.alpha = if (r.activa) 1f else 0.55f
        h.itemView.setOnClickListener { alTocar(r) }
        h.itemView.setOnLongClickListener { alMantener(r, it); true }
    }
}
