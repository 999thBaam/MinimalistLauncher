package com.minimalist.launcher

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.minimalist.launcher.service.VipDetector

class IntentFirewallSettingsActivity : AppCompatActivity() {

    private lateinit var masterSwitch: SwitchMaterial
    private lateinit var vipRecycler: RecyclerView
    private lateinit var addVipButton: TextView
    private lateinit var adapter: VipAdapter
    
    private val prefs by lazy { 
        getSharedPreferences("minimalist_prefs", Context.MODE_PRIVATE) 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intent_firewall_settings)

        findViewById<View>(R.id.settings_back).setOnClickListener { finish() }

        // Master Switch
        masterSwitch = findViewById(R.id.settings_master_switch)
        masterSwitch.isChecked = prefs.getBoolean("smart_notifications_enabled", true)
        masterSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("smart_notifications_enabled", isChecked).apply()
            val msg = if (isChecked) "Smart Focus Enabled" else "Smart Focus Disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // VIP List
        vipRecycler = findViewById(R.id.settings_vip_recycler)
        vipRecycler.layoutManager = LinearLayoutManager(this)
        
        adapter = VipAdapter(
            onDeleteClick = { entry ->
                VipDetector.removeVip(this, entry.senderId)
                loadVips()
            }
        )
        vipRecycler.adapter = adapter
        
        addVipButton = findViewById(R.id.settings_add_vip)
        addVipButton.setOnClickListener { showAddVipDialog() }

        loadVips()
    }

    private fun loadVips() {
        val vips = VipDetector.getVipList(this)
            .sortedBy { it.source != VipDetector.VipSource.Source.MANUAL } // Manual first
        adapter.updateList(vips)
    }

    private fun showAddVipDialog() {
        val input = EditText(this)
        input.hint = "Phone number"
        
        AlertDialog.Builder(this)
            .setTitle("Add VIP")
            .setMessage("Calls & messages from this number will always be Important.")
            .setView(input) // Standard padding would be nice but keeping it minimal
            .setPositiveButton("Add") { _, _ ->
                val number = input.text.toString()
                if (number.isNotBlank()) {
                    VipDetector.addManualVip(this, number)
                    loadVips()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Inner Adapter Class
    class VipAdapter(
        private val onDeleteClick: (VipDetector.VipEntry) -> Unit
    ) : RecyclerView.Adapter<VipAdapter.ViewHolder>() {

        private var items: List<VipDetector.VipEntry> = emptyList()

        fun updateList(newItems: List<VipDetector.VipEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.vip_name)
            val source: TextView = view.findViewById(R.id.vip_source)
            val delete: ImageView = view.findViewById(R.id.vip_delete)
            val icon: ImageView = view.findViewById(R.id.vip_icon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_vip_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.senderId
            
            when (item.source) {
                VipDetector.VipSource.Source.MANUAL -> {
                    holder.source.text = "Manual"
                    holder.delete.visibility = View.VISIBLE
                    holder.icon.setImageResource(android.R.drawable.ic_menu_add) // Generic icon
                }
                VipDetector.VipSource.Source.STARRED -> {
                    holder.source.text = "Starred Contact"
                    holder.delete.visibility = View.GONE // Can't delete here
                    holder.icon.setImageResource(android.R.drawable.star_on)
                }
                VipDetector.VipSource.Source.CALL_DURATION -> {
                    holder.source.text = "Frequent Caller"
                    holder.delete.visibility = View.GONE
                    holder.icon.setImageResource(android.R.drawable.sym_action_call)
                }
                VipDetector.VipSource.Source.REPLY_RATIO -> {
                    holder.source.text = "High Reply Rate"
                    holder.delete.visibility = View.GONE
                    holder.icon.setImageResource(android.R.drawable.sym_action_chat)
                }
            }

            holder.delete.setOnClickListener { onDeleteClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
