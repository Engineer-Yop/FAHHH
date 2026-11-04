package com.example.sanivend

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SalesTransactionAdapter(private var transactions: List<SalesTransaction>) :
    RecyclerView.Adapter<SalesTransactionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rowContent: View = view.findViewById(R.id.row_content)
        val etItemsSold: EditText = view.findViewById(R.id.et_items_sold)
        val tvDate: TextView = view.findViewById(R.id.tv_date)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
        val tvNoData: TextView = view.findViewById(R.id.tv_no_data)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sales_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // We always show the row content to maintain the 3x3 grid look
        holder.tvNoData.visibility = View.GONE
        holder.rowContent.visibility = View.VISIBLE

        if (position < transactions.size) {
            val transaction = transactions[position]
            holder.etItemsSold.setText(transaction.itemsSold.toString())
            holder.tvDate.text = transaction.date
            holder.tvTime.text = transaction.time
        } else {
            // Placeholder for empty rows to complete the 3x3 grid
            holder.etItemsSold.setText("")
            holder.tvDate.text = ""
            holder.tvTime.text = ""
        }
    }

    // Always return at least 3 to ensure a 3x3 data grid (plus header makes it 4 rows total)
    override fun getItemCount(): Int = if (transactions.size > 3) transactions.size else 3

    fun updateData(newList: List<SalesTransaction>) {
        this.transactions = newList
        notifyDataSetChanged()
    }
}
