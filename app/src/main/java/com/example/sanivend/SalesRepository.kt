package com.example.sanivend

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.*

class SalesRepository {
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference

    fun recordSale(category: String, amount: Double) {
        val now = Date()
        val year = SimpleDateFormat("yyyy", Locale.US).format(now)
        val month = SimpleDateFormat("MM", Locale.US).format(now)
        val day = SimpleDateFormat("dd", Locale.US).format(now)
        val hour = SimpleDateFormat("HH", Locale.US).format(now) // 24-hour format for DB keys

        val transactionId = db.child("sales").child(category).push().key ?: return
        
        val transaction = mapOf(
            "amount" to amount,
            "date" to SimpleDateFormat("MM/dd/yyyy", Locale.US).format(now),
            "time" to SimpleDateFormat("hh:mm a", Locale.US).format(now),
            "itemsSold" to category
        )

        val categoryKey = category.replace(" ", "_").lowercase()

        val updates = hashMapOf<String, Any>(
            "/sales/$category/$transactionId" to transaction,
            
            // Global aggregates
            "/sales_summaries/hourly/$year/$month/$day/$hour" to ServerValue.increment(amount),
            "/sales_summaries/daily/$year/$month/$day" to ServerValue.increment(amount),
            "/sales_summaries/monthly/$year/$month" to ServerValue.increment(amount),
            "/sales_summaries/annually/$year" to ServerValue.increment(amount),

            // Category-specific aggregates
            "/category_sales_summaries/$categoryKey/hourly/$year/$month/$day/$hour" to ServerValue.increment(amount),
            "/category_sales_summaries/$categoryKey/daily/$year/$month/$day" to ServerValue.increment(amount),
            "/category_sales_summaries/$categoryKey/monthly/$year/$month" to ServerValue.increment(amount),
            "/category_sales_summaries/$categoryKey/annually/$year" to ServerValue.increment(amount)
        )

        db.updateChildren(updates)
    }
}
