package com.minimalist.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.R
import com.minimalist.launcher.data.AppItem

/**
 * Bottom-Heavy App Launcher - RecyclerView adapter.
 * Shows app name with a circular icon container.
 */
class AppListAdapter(
    private val onAppClick: (AppItem) -> Unit,
    private val onUninstallClick: (AppItem) -> Unit,
    private val onAppLongClick: (AppItem) -> Unit
) : ListAdapter<AppItem, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app)
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.appNameText)
        private val pinIcon: View = itemView.findViewById(R.id.pinIcon)

        fun bind(app: AppItem) {
            // App Name
            nameText.text = app.label
            
            // Text color: dimmed if unused
            val colorRes = if (app.isPinned || !app.isUnused) R.color.minimalist_text_primary else R.color.secondaryText
            nameText.setTextColor(itemView.context.getColor(colorRes))
            
            // Pin Indicator
            pinIcon.visibility = if (app.isPinned) View.VISIBLE else View.GONE
            
            // Click listeners
            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener { 
                onAppLongClick(app)
                true 
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem == newItem
        }
    }
}
