package com.cmota.playground.storage.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cmota.playground.storage.R
import kotlinx.android.synthetic.main.item_filter.view.*

class FilterAdapter(private val filters: List<Int>,
                    private val clickAction: (Int) -> Unit): RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {

    var selected = filters[0]
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return FilterViewHolder(inflater.inflate(R.layout.item_filter, parent, false))
    }

    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        val filter = filters[position]
        holder.filter.text = holder.filter.context.getString(filter)
        holder.filter.setOnClickListener {
            selected = filter
            clickAction(filter)

            notifyDataSetChanged()
        }

        holder.filter.isSelected = filter == selected
    }

    override fun getItemCount() = filters.size

    inner class FilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val filter: TextView = itemView.tv_filter
    }
}