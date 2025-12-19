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
 * Text-only RecyclerView adapter for app list.
 * No icons, no branding - pure text representation.
 */
class AppListAdapter(
    private val onAppClick: (AppItem) -> Unit,
    private val onUninstallClick: (AppItem) -> Unit,
    private val onAppLongClick: (AppItem) -> Unit
) : ListAdapter<AppItem, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        // Inflate constraint layout instead of just TextView
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
        private val uninstallButton: View = itemView.findViewById(R.id.uninstallButton)

        fun bind(app: AppItem) {
            // Add pin indicator if pinned
            val displayName = if (app.isPinned) "📌 ${app.label}" else app.label
            nameText.text = displayName
            
            // Text color: Primary if used, Secondary (dimmed) if unused
            // Pinned apps are always "used" conceptually, so stick to primary color? 
            // Or respect unused status even if pinned? 
            // Let's force Primary color for pinned apps to keep them distinct/active.
            val colorRes = if (app.isPinned || !app.isUnused) R.color.primaryText else R.color.secondaryText
            nameText.setTextColor(itemView.context.getColor(colorRes))
            
            // Uninstall button visibility (only show for decluttering suggestion on unused apps)
            // If pinned, we probably don't want to suggest uninstall.
            uninstallButton.visibility = if (app.isUnused && !app.isPinned) View.VISIBLE else View.GONE
            
            // Click listeners
            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener { 
                onAppLongClick(app)
                true 
            }
            uninstallButton.setOnClickListener { onUninstallClick(app) }
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
