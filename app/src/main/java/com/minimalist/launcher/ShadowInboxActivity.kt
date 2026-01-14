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
            messages = mutableListOf(),
            onItemClick = { message -> showMessageDetail(message) },
            onOpenInApp = { message -> confirmOpenInApp(message) },
            context = this
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Swipe to dismiss
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val message = adapter.getItem(position)
                message?.let {
                    NotificationBatchManager.clearThread(it.conversationKey)
                    // Remove from adapter immediately needed? loadMessages re-fetches.
                    // But for animation smoothness we might want to remove.
                    // Actually loadMessages is better to sync.
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
        
        adapter.updateMessages(messages)
        
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
                NotificationBatchManager.moveThread(message.conversationKey, !showImportantOnly)
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
 * Adapter for Shadow Inbox RecyclerView
 */
class ShadowInboxAdapter(
    private var messages: MutableList<ShadowMessage>,
    private val onItemClick: (ShadowMessage) -> Unit,
    private val onOpenInApp: (ShadowMessage) -> Unit,
    private val context: android.content.Context
) : RecyclerView.Adapter<ShadowInboxAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.shadow_item_icon)
        val sender: TextView = view.findViewById(R.id.shadow_item_sender)
        val preview: TextView = view.findViewById(R.id.shadow_item_preview)
        val time: TextView = view.findViewById(R.id.shadow_item_time)
        val count: TextView = view.findViewById(R.id.shadow_item_count)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shadow_message, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        
        // Sender/Group name
        holder.sender.text = message.senderDisplayName ?: message.groupName ?: "Unknown"
        
        // Preview (latest message)
        val latestMsg = message.messages.firstOrNull()
        holder.preview.text = latestMsg?.text ?: ""
        
        // Time
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.time.text = timeFormat.format(Date(message.lastTimestamp))
        
        // Unread count
        val count = message.messages.size
        if (count > 1) {
            holder.count.visibility = View.VISIBLE
            holder.count.text = count.toString()
        } else {
            holder.count.visibility = View.GONE
        }
        
        // App icon
        try {
            val icon = context.packageManager.getApplicationIcon(message.packageName)
            holder.appIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            holder.appIcon.setImageResource(R.drawable.ic_launcher_foreground)
        }
        
        // Click handlers
        holder.itemView.setOnClickListener { onItemClick(message) }
        holder.itemView.setOnLongClickListener { 
            onOpenInApp(message)
            true
        }
    }
    
    override fun getItemCount() = messages.size
    
    fun getItem(position: Int): ShadowMessage? = messages.getOrNull(position)
    
    fun updateMessages(newMessages: List<ShadowMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}
