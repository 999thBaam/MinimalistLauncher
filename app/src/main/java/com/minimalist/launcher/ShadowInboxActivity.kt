package com.minimalist.launcher

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.minimalist.launcher.data.ShadowMessage
import com.minimalist.launcher.service.NotificationBatchManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ShadowInboxActivity: Ghost Mode UI
 * 
 * Read messages without triggering "Seen" or read receipts.
 * 
 * GUARANTEES:
 * - Reading here does NOT mark messages as read in source apps
 * - Does NOT clear notifications from source apps
 * - Does NOT notify senders
 * 
 * LIMITATION (clearly communicated):
 * - Replying requires opening the source app, which may trigger read receipts
 */
class ShadowInboxActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var titleView: TextView
    private lateinit var adapter: ShadowInboxAdapter
    
    private var showImportantOnly = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shadow_inbox)
        
        showImportantOnly = intent.getBooleanExtra("SHOW_IMPORTANT", true)
        
        setupViews()
        loadMessages()
    }
    
    override fun onResume() {
        super.onResume()
        loadMessages()  // Refresh on return
    }
    
    private fun setupViews() {
        recyclerView = findViewById(R.id.shadow_inbox_recycler)
        emptyView = findViewById(R.id.shadow_inbox_empty)
        titleView = findViewById(R.id.shadow_inbox_title)
        
        // Set title based on filter
        titleView.text = if (showImportantOnly) "Important Messages" else "Notifications"
        
        adapter = ShadowInboxAdapter(
            items = mutableListOf(),
            onItemClick = { message -> showMessageDetail(message) },
            onOpenInApp = { message -> confirmOpenInApp(message) },
            context = this
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Swipe to dismiss
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                // Disable swipe for headers
                if (viewHolder is ShadowInboxAdapter.HeaderViewHolder) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.getItem(position)
                
                if (item is ShadowInboxItem.MessageItem) {
                    val message = item.message
                    NotificationBatchManager.clearThread(message.conversationKey)
                    loadMessages()
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
        
        // Back button
        findViewById<View>(R.id.shadow_inbox_back)?.setOnClickListener { finish() }
    }
    
    private fun loadMessages() {
        val messages = if (showImportantOnly) {
            NotificationBatchManager.getImportantMessages()
        } else {
            NotificationBatchManager.getUnimportantMessages()
        }
        
        val items = mutableListOf<ShadowInboxItem>()
        
        if (showImportantOnly) {
            // Partition into Urgent vs Regular
            val (urgent, regular) = messages.partition { it.isUrgent }
            
            if (urgent.isNotEmpty()) {
                items.add(ShadowInboxItem.HeaderItem("Urgent"))
                items.addAll(urgent.map { createItem(it) })
            }
            
            if (regular.isNotEmpty()) {
                // Only show header if we have an Urgent section too, or just always nice to have?
                // Request said: "top part for urgent and bottom for not urgent"
                // If we have urgent, we label the bottom "Everything Else" or "Important"
                if (urgent.isNotEmpty()) {
                    items.add(ShadowInboxItem.HeaderItem("Everything Else"))
                }
                items.addAll(regular.map { createItem(it) })
            }
        } else {
            // Unimportant / regular view
            if (messages.isNotEmpty()) {
                items.addAll(messages.map { createItem(it) })
            }
        }
        
        adapter.updateItems(items)
        
        if (messages.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            emptyView.text = if (showImportantOnly) 
                "No important messages.\nPeace and quiet." 
            else 
                "No notifications.\nAll caught up."
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    private fun createItem(msg: ShadowMessage): ShadowInboxItem {
        return if (msg.isMessaging) {
            ShadowInboxItem.MessageItem(msg)
        } else {
            ShadowInboxItem.NotificationItem(msg)
        }
    }
    
    private fun showMessageDetail(message: ShadowMessage) {
        // Show all messages in thread
        val content = message.messages.joinToString("\n\n") { msg ->
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            val sender = if (message.isGroup && msg.senderName != null) "${msg.senderName}: " else ""
            "[$time] $sender${msg.text}"
        }
        
        val actionTitle = if (showImportantOnly) "Demote to Unimportant" else "Promote to Important"
        
        AlertDialog.Builder(this)
            .setTitle(message.senderDisplayName ?: message.groupName ?: "Message")
            .setMessage(content)
            .setPositiveButton("Open in App") { _, _ -> confirmOpenInApp(message) }
            .setNeutralButton(actionTitle) { _, _ ->
                // Promote/Demote
                val newIsImportant = !showImportantOnly
                NotificationBatchManager.moveThread(message.conversationKey, newIsImportant)
                
                // Log for ML Training
                val action = if (newIsImportant) 
                    com.minimalist.launcher.data.NotificationLogger.ACTION_PROMOTED 
                else 
                    com.minimalist.launcher.data.NotificationLogger.ACTION_DEMOTED
                    
                com.minimalist.launcher.data.NotificationLogger.log(
                    this, 
                    message.packageName, 
                    message.category, 
                    message.isOngoing, 
                    action
                )
                
                loadMessages()
                android.widget.Toast.makeText(this, 
                    if (showImportantOnly) "Demoted" else "Promoted", 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    /**
     * Confirm before opening app (warning about read receipts)
     */
    private fun confirmOpenInApp(message: ShadowMessage) {
        val appName = NotificationBatchManager.getAppName(message.packageName, this)
        
        AlertDialog.Builder(this)
            .setTitle("Open in $appName?")
            .setMessage("Opening will mark messages as read in $appName.\n\nThe sender may see that you've read their message.")
            .setPositiveButton("Open") { _, _ -> openSourceApp(message) }
            .setNegativeButton("Stay Here", null)
            .show()
    }
    
    private fun openSourceApp(message: ShadowMessage) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(message.packageName)
            launchIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
                
                // Clear this thread since user is engaging
                NotificationBatchManager.clearThread(message.conversationKey)
            }
        } catch (e: Exception) {
            // Fallback: open app info
        }
    }
}

/**
 * Sealed class for heterogeneous list items
 */
sealed class ShadowInboxItem {
    data class MessageItem(val message: ShadowMessage) : ShadowInboxItem()
    data class NotificationItem(val message: ShadowMessage) : ShadowInboxItem()
    data class HeaderItem(val title: String) : ShadowInboxItem()
}

/**
 * Adapter for Shadow Inbox RecyclerView
 */
class ShadowInboxAdapter(
    private var items: MutableList<ShadowInboxItem>,
    private val onItemClick: (ShadowMessage) -> Unit,
    private val onOpenInApp: (ShadowMessage) -> Unit,
    private val context: android.content.Context
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    companion object {
        const val TYPE_MESSAGE = 0
        const val TYPE_HEADER = 1
        const val TYPE_NOTIFICATION = 2
    }
    
    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.shadow_item_icon)
        val sender: TextView = view.findViewById(R.id.shadow_item_sender)
        val preview: TextView = view.findViewById(R.id.shadow_item_preview)
        val time: TextView = view.findViewById(R.id.shadow_item_time)
        val count: TextView = view.findViewById(R.id.shadow_item_count)
    }

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.card_icon)
        val title: TextView = view.findViewById(R.id.card_title)
        val text: TextView = view.findViewById(R.id.card_text)
        val time: TextView = view.findViewById(R.id.card_time)
    }
    
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.header_title)
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ShadowInboxItem.MessageItem -> TYPE_MESSAGE
            is ShadowInboxItem.NotificationItem -> TYPE_NOTIFICATION
            is ShadowInboxItem.HeaderItem -> TYPE_HEADER
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_shadow_header, parent, false)
                HeaderViewHolder(view)
            }
            TYPE_NOTIFICATION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_shadow_notification, parent, false)
                NotificationViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_shadow_message, parent, false)
                MessageViewHolder(view)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ShadowInboxItem.HeaderItem -> {
                (holder as HeaderViewHolder).title.text = item.title
            }
            is ShadowInboxItem.MessageItem -> {
                bindMessage(holder as MessageViewHolder, item.message)
            }
            is ShadowInboxItem.NotificationItem -> {
                bindNotification(holder as NotificationViewHolder, item.message)
            }
        }
    }
    
    private fun bindMessage(vh: MessageViewHolder, message: ShadowMessage) {
        // Sender/Group name
        vh.sender.text = message.senderDisplayName ?: message.groupName ?: "Unknown"
        
        // Preview (latest message)
        val latestMsg = message.messages.firstOrNull()
        vh.preview.text = latestMsg?.text ?: ""
        
        // Time
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        vh.time.text = timeFormat.format(Date(message.lastTimestamp))
        
        // Unread count
        val count = message.messages.size
        if (count > 1) {
            vh.count.visibility = View.VISIBLE
            vh.count.text = count.toString()
        } else {
            vh.count.visibility = View.GONE
        }
        
        // App icon
        bindIcon(vh.appIcon, message.packageName)
        
        // Urgent visuals? Maybe color tint the card or sender name?
        // Minimalist design: Urgency is conveyed by position (Header).
        // But let's add bold or color if strictly needed. 
        // For now, sorting is enough.
        
        // Click handlers
        vh.itemView.setOnClickListener { onItemClick(message) }
        vh.itemView.setOnLongClickListener { 
            onOpenInApp(message)
            true
        }
    }

    private fun bindNotification(vh: NotificationViewHolder, message: ShadowMessage) {
        // Title (App Name or Title)
        vh.title.text = message.senderDisplayName ?: NotificationBatchManager.getAppName(message.packageName, context)
        
        // Text
        val latestMsg = message.messages.firstOrNull()
        vh.text.text = latestMsg?.text ?: ""
        
        // Time
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        vh.time.text = timeFormat.format(Date(message.lastTimestamp))
        
        // App icon
        bindIcon(vh.appIcon, message.packageName)
        
        // Click handlers
        vh.itemView.setOnClickListener { onItemClick(message) }
        vh.itemView.setOnLongClickListener { 
            onOpenInApp(message)
            true
        }
    }
    
    private fun bindIcon(view: ImageView, packageName: String) {
        try {
            val icon = context.packageManager.getApplicationIcon(packageName)
            view.setImageDrawable(icon)
        } catch (e: Exception) {
            view.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }
    
    override fun getItemCount() = items.size
    
    fun getItem(position: Int): ShadowInboxItem? = items.getOrNull(position)
    
    fun updateItems(newItems: List<ShadowInboxItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
