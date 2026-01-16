package com.minimalist.launcher.ui.notifications

import android.app.Notification
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.service.notification.StatusBarNotification
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.databinding.ItemMinimalistMediaBinding
import com.minimalist.launcher.databinding.ItemMinimalistNotificationBinding
import com.minimalist.launcher.model.NotificationUiModel
import com.minimalist.launcher.service.MinimalistNotificationListener

class NotificationAdapter : ListAdapter<NotificationUiModel, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_DEFAULT = 0
        private const val VIEW_TYPE_MEDIA = 1
        
        private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable?): android.graphics.Bitmap? {
            if (drawable == null) return null
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                return drawable.bitmap
            }
            val bitmap = android.graphics.Bitmap.createBitmap(
                if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1,
                if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
    }

    override fun getItemViewType(position: Int): Int {
        val model = getItem(position)
        val sbn = model.sbn
        val extras = sbn.notification.extras
        
        // Robust Check for Media
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            return VIEW_TYPE_MEDIA
        }
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        if (template != null && template.contains("MediaStyle")) {
            return VIEW_TYPE_MEDIA
        }
        
        return VIEW_TYPE_DEFAULT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_MEDIA) {
            val binding = ItemMinimalistMediaBinding.inflate(inflater, parent, false)
            MediaViewHolder(binding)
        } else {
            val binding = ItemMinimalistNotificationBinding.inflate(inflater, parent, false)
            NotificationViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is MediaViewHolder -> holder.bind(item)
            is NotificationViewHolder -> holder.bind(item)
        }
    }

    // --- Media Player ViewHolder ---
    class MediaViewHolder(private val binding: ItemMinimalistMediaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(model: NotificationUiModel) {
            val sbn = model.sbn
            val notification = sbn.notification
            val extras = notification.extras
            
            val title = extras.getCharSequence(Notification.EXTRA_TITLE) ?: "Unknown Track"
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT) ?: "Unknown Artist"
            
            var bitmap: android.graphics.Bitmap? = null
            try {
                val iconObj = extras.get(Notification.EXTRA_LARGE_ICON)
                bitmap = when (iconObj) {
                    is android.graphics.Bitmap -> iconObj
                    is android.graphics.drawable.Icon -> {
                        val drawable = iconObj.loadDrawable(binding.root.context)
                        drawableToBitmap(drawable)
                    }
                    else -> {
                        val icon = notification.getLargeIcon()
                        val drawable = icon?.loadDrawable(binding.root.context)
                        drawableToBitmap(drawable)
                    }
                }
            } catch (e: Exception) {}

            binding.trackTitle.text = title
            binding.trackArtist.text = artist

            if (bitmap != null) {
                binding.albumArt.setImageBitmap(bitmap)
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                binding.albumArt.colorFilter = ColorMatrixColorFilter(matrix)
            } else {
                binding.albumArt.setImageDrawable(null)
                binding.albumArt.setBackgroundColor(Color.DKGRAY)
            }

            binding.mediaControls.removeAllViews()
            val actions = notification.actions ?: return
            actions.take(3).forEach { action ->
                val title = action.title?.toString()?.lowercase() ?: ""
                val btnText = when {
                    title.contains("prev") || title.contains("rewind") || title.contains("back") -> "|<<"
                    title.contains("next") || title.contains("forward") || title.contains("skip") -> ">>|"
                    title.contains("play") || title.contains("resume") || title.contains("start") -> "▶"
                    title.contains("pause") || title.contains("stop") -> "||"
                    else -> "•"
                }
                val btn = TextView(binding.root.context).apply {
                    text = btnText
                    textSize = 24f
                    setTextColor(Color.WHITE)
                    setPadding(0, 0, 64, 0)
                    setOnClickListener {
                        try { action.actionIntent.send() } catch (e: Exception) {}
                    }
                }
                binding.mediaControls.addView(btn)
            }
            
            binding.root.setOnClickListener {
                try { notification.contentIntent?.send() } catch (e: Exception) {}
            }
        }
    }

    // --- Regular Notification ViewHolder ---
    class NotificationViewHolder(private val binding: ItemMinimalistNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(model: NotificationUiModel) {
            val sbn = model.sbn
            val notification = sbn.notification
            val extras = notification.extras
            val context = binding.root.context
            
            val pm = context.packageManager
            try {
                val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString().uppercase()
                
                if (model.isGroup) {
                    binding.appName.text = "$label • ${model.count}"
                } else {
                    binding.appName.text = label
                }
                
                binding.appIcon.setImageDrawable(pm.getApplicationIcon(appInfo).apply {
                    setTint(Color.WHITE) 
                })
            } catch (e: Exception) {
                binding.appName.text = sbn.packageName
                binding.appIcon.setImageDrawable(null)
            }
            
            // Content
            if (model.isGroup) {
                binding.titleText.text = "STACK"
                binding.titleText.visibility = View.GONE
                binding.contentPreview.text = model.summaryText
                binding.contentPreview.maxLines = 3
            } else {
                val title = extras.getCharSequence(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT) ?: ""
                
                binding.titleText.text = title
                binding.contentPreview.text = text
                
                binding.titleText.visibility = if (title.isNotEmpty()) View.VISIBLE else View.GONE
                binding.contentPreview.maxLines = 2
            }
            binding.contentPreview.visibility = View.VISIBLE

            // Time
            val time = sbn.postTime
            val timeStr = if (time > 0) {
                DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
            } else { "Now" }
            binding.timeText.text = timeStr
            
            // Actions
            val actions = notification.actions
            if (!actions.isNullOrEmpty() && !model.isGroup) {
                val primary = actions[0]
                binding.primaryAction.text = "[ ${primary.title} ]"
                binding.primaryAction.setOnClickListener {
                    try { primary.actionIntent.send() } catch (e: Exception) {}
                }
                binding.primaryAction.visibility = View.VISIBLE
            } else {
                binding.primaryAction.visibility = View.GONE
            }

            // Click Body
            binding.root.setOnClickListener {
                if (model.isGroup) {
                    // EXPAND the stack
                    MinimalistNotificationListener.togglePackageExpansion(sbn.packageName)
                } else {
                    // Open & Dismiss
                    try { 
                        notification.contentIntent?.send()
                        
                        // IF it's not ongoing, we treat it as an Inbox item and dismiss it
                        if (!sbn.isOngoing) {
                             MinimalistNotificationListener.dismissNotification(model.childrenKeys)
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NotificationUiModel>() {
        override fun areItemsTheSame(oldItem: NotificationUiModel, newItem: NotificationUiModel): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(oldItem: NotificationUiModel, newItem: NotificationUiModel): Boolean {
            return oldItem.count == newItem.count && 
                   oldItem.sbn.postTime == newItem.sbn.postTime &&
                   oldItem.summaryText == newItem.summaryText &&
                   oldItem.childrenKeys == newItem.childrenKeys
        }
    }
}
