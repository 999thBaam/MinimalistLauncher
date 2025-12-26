package com.minimalist.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
        private val iconImage: ImageView = itemView.findViewById(R.id.appIconImage)
        private val pinIcon: View = itemView.findViewById(R.id.pinIcon)

        fun bind(app: AppItem) {
            // App Name
            nameText.text = app.label
            
            // Text color: dimmed if unused
            val colorRes = if (app.isPinned || !app.isUnused) R.color.minimalist_text_primary else R.color.secondaryText
            nameText.setTextColor(itemView.context.getColor(colorRes))
            
            // Pin Indicator
            pinIcon.visibility = if (app.isPinned) View.VISIBLE else View.GONE
            
            // Set icon based on package name (curated mapping)
            val iconRes = getIconForPackage(app.packageName)
            iconImage.setImageResource(iconRes)
            
            // Click listeners
            itemView.setOnClickListener { onAppClick(app) }
            itemView.setOnLongClickListener { 
                onAppLongClick(app)
                true 
            }
        }
        
        /**
         * Maps common package names to Material-style icons.
         * Falls back to generic icon for unknown apps.
         */
        private fun getIconForPackage(packageName: String): Int {
            return when {
                // Google Apps
                packageName.contains("calendar") -> R.drawable.ic_calendar
                packageName.contains("camera") -> R.drawable.ic_camera
                packageName.contains("chrome") || packageName.contains("browser") -> R.drawable.ic_browser
                packageName.contains("clock") || packageName.contains("deskclock") -> R.drawable.ic_clock
                packageName.contains("contacts") -> R.drawable.ic_contacts
                packageName.contains("drive") -> R.drawable.ic_drive
                packageName.contains("files") || packageName.contains("filemanager") -> R.drawable.ic_folder
                packageName.contains("gmail") || packageName.contains("email") -> R.drawable.ic_mail
                packageName.contains("maps") -> R.drawable.ic_map
                packageName.contains("messages") || packageName.contains("mms") -> R.drawable.ic_chat
                packageName.contains("phone") || packageName.contains("dialer") -> R.drawable.ic_phone
                packageName.contains("photos") || packageName.contains("gallery") -> R.drawable.ic_photos
                packageName.contains("play") && packageName.contains("store") -> R.drawable.ic_store
                packageName.contains("settings") -> R.drawable.ic_settings
                packageName.contains("youtube") -> R.drawable.ic_video
                packageName.contains("music") || packageName.contains("spotify") -> R.drawable.ic_music
                packageName.contains("calculator") -> R.drawable.ic_calculator
                packageName.contains("notes") || packageName.contains("keep") -> R.drawable.ic_notes
                packageName.contains("weather") -> R.drawable.ic_weather
                else -> R.drawable.ic_apps_generic
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
