package com.minimalist.launcher.ui.notifications

import android.graphics.Canvas
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.service.MinimalistNotificationListener

class NotificationSwipeCallback(
    private val adapter: NotificationAdapter
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false // No drag-and-drop
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        
        val adapter = (viewHolder.bindingAdapter as? NotificationAdapter) ?: return
        val item = adapter.currentList[position] // Now returns NotificationUiModel
        
        when (direction) {
            ItemTouchHelper.LEFT -> {
                // Dismiss: Remove entire stack from Vault + System
                MinimalistNotificationListener.dismissNotification(item.childrenKeys)
            }
            ItemTouchHelper.RIGHT -> {
                // Open: Launch Content Intent of the Representative (Latest)
                try {
                    item.sbn.notification.contentIntent?.send()
                    MinimalistNotificationListener.dismissNotification(item.childrenKeys) // Cleanup stack
                } catch (e: Exception) {
                    e.printStackTrace()
                    adapter.notifyItemChanged(position)
                }
            }
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        // Optional: Custom drawing for background (e.g. Red for delete, Green for open)
        // For minimalist design, we keep it transparent or subtle.
        
        // Simple alpha fade based on swipe distance
        val alpha = 1.0f - Math.abs(dX) / viewHolder.itemView.width.toFloat()
        viewHolder.itemView.alpha = alpha
        viewHolder.itemView.translationX = dX
        
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
