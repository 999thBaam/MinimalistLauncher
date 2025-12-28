package com.minimalist.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.R
import com.minimalist.launcher.data.AppItem

/**
 * Adapter for the real app pinning demo in onboarding.
 * Shows real apps and allows users to pin/unpin via long-press.
 */
class PinDemoAdapter(
    private val apps: MutableList<AppItem>,
    private val onPinToggle: (AppItem, Int) -> Unit
) : RecyclerView.Adapter<PinDemoAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.appNameText)
        private val pinIndicator: TextView = itemView.findViewById(R.id.pinIndicator)

        fun bind(app: AppItem, position: Int) {
            nameText.text = app.label
            pinIndicator.visibility = if (app.isPinned) View.VISIBLE else View.GONE
            
            // Long press to toggle pin
            itemView.setOnLongClickListener {
                onPinToggle(app, position)
                true
            }
            
            // Also allow single tap
            itemView.setOnClickListener {
                onPinToggle(app, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pin_demo_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position], position)
    }

    override fun getItemCount(): Int = apps.size

    fun updateItem(position: Int, app: AppItem) {
        apps[position] = app
        notifyItemChanged(position)
    }
}
