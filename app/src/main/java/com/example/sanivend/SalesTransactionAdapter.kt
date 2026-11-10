package com.example.sanivend

import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * Rows of one category's Sales Monitoring table: "Week 1 · Nov 1–7" headers,
 * each followed by that week's orders (item, date, time). Shows one
 * "No orders yet this month" row when the category has none.
 */
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
        // The items cell only displays the product; it isn't for typing
        view.findViewById<EditText>(R.id.et_items_sold).apply {
            isFocusable = false
            isCursorVisible = false
            keyListener = null
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (transactions.isEmpty()) {
            showMessage(holder, "No orders yet this month", header = false)
            return
        }
        val t = transactions[position]
        if (t.header != null) {
            showMessage(holder, t.header, header = true)
            return
        }
        holder.tvNoData.visibility = View.GONE
        holder.rowContent.visibility = View.VISIBLE
        holder.etItemsSold.setText(t.itemsSold)
        holder.tvDate.text = t.date
        holder.tvTime.text = t.time
    }

    private fun showMessage(holder: ViewHolder, text: String, header: Boolean) {
        holder.rowContent.visibility = View.GONE
        holder.tvNoData.visibility = View.VISIBLE
        holder.tvNoData.text = text
        holder.tvNoData.gravity = if (header) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        holder.tvNoData.setTypeface(null, if (header) Typeface.BOLD else Typeface.NORMAL)
        holder.tvNoData.setTextColor(
            ContextCompat.getColor(holder.itemView.context, if (header) R.color.text_primary else R.color.black)
        )
    }

    override fun getItemCount(): Int = if (transactions.isEmpty()) 1 else transactions.size

    fun updateData(newList: List<SalesTransaction>) {
        this.transactions = newList
        notifyDataSetChanged()
    }
}
