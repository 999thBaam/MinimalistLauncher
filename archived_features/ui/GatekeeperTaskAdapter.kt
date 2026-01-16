package com.minimalist.launcher.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.minimalist.launcher.R
import com.minimalist.launcher.data.GatekeeperTask

/**
 * RecyclerView adapter for Gatekeeper task items.
 * Handles checkbox animations and task completion callbacks.
 */
class GatekeeperTaskAdapter(
    private val onTaskCompleted: (GatekeeperTask) -> Unit,
    private val onTaskUncompleted: (GatekeeperTask) -> Unit,
    private val onTaskDeleted: (GatekeeperTask) -> Unit,
    private val showCompleted: Boolean = false
) : ListAdapter<GatekeeperTask, GatekeeperTaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gatekeeper_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.taskItemContainer)
        private val checkbox: ImageView = itemView.findViewById(R.id.taskCheckbox)
        private val title: TextView = itemView.findViewById(R.id.taskTitle)
        private val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)

        fun bind(task: GatekeeperTask) {
            title.text = task.title
            
            // Update checkbox state
            if (task.completed) {
                checkbox.setImageResource(R.drawable.ic_checkbox_checked)
                title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                title.alpha = 0.5f
                deleteButton.visibility = if (showCompleted) View.VISIBLE else View.GONE
            } else {
                checkbox.setImageResource(R.drawable.ic_checkbox_unchecked)
                title.paintFlags = title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                title.alpha = 1f
                deleteButton.visibility = View.GONE
            }
            
            // Checkbox click handler
            checkbox.setOnClickListener {
                if (task.completed) {
                    onTaskUncompleted(task)
                } else {
                    // Play completion animation
                    playCompletionAnimation {
                        onTaskCompleted(task)
                    }
                }
            }
            
            // Delete button handler
            deleteButton.setOnClickListener {
                onTaskDeleted(task)
            }
            
            // Tint checkbox
            checkbox.setColorFilter(
                ContextCompat.getColor(itemView.context, R.color.secondaryText)
            )
        }
        
        private fun playCompletionAnimation(onComplete: () -> Unit) {
            // Scale pop animation on checkbox
            val scaleX = ObjectAnimator.ofFloat(checkbox, "scaleX", 1f, 1.3f, 1f)
            val scaleY = ObjectAnimator.ofFloat(checkbox, "scaleY", 1f, 1.3f, 1f)
            
            // Fade and slide out for the whole item
            val fadeOut = ObjectAnimator.ofFloat(container, "alpha", 1f, 0.3f)
            val translateX = ObjectAnimator.ofFloat(container, "translationX", 0f, 50f)
            
            val animatorSet = AnimatorSet()
            animatorSet.playTogether(scaleX, scaleY, fadeOut, translateX)
            animatorSet.duration = 250
            
            animatorSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Reset for recycling
                    container.alpha = 1f
                    container.translationX = 0f
                    onComplete()
                }
            })
            
            animatorSet.start()
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<GatekeeperTask>() {
        override fun areItemsTheSame(oldItem: GatekeeperTask, newItem: GatekeeperTask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GatekeeperTask, newItem: GatekeeperTask): Boolean {
            return oldItem == newItem
        }
    }
}
