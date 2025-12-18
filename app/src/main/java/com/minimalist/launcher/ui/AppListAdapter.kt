package com.minimalist.launcher.ui

import android.view.LayoutInflater
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
    private val onAppClick: (AppItem) -> Unit
) : ListAdapter<AppItem, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false) as TextView
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app)
    }

    inner class AppViewHolder(
        private val textView: TextView
    ) : RecyclerView.ViewHolder(textView) {

        fun bind(app: AppItem) {
            textView.text = app.label
            textView.setOnClickListener { onAppClick(app) }
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
