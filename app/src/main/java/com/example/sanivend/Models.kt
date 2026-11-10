package com.example.sanivend

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Order(
    val amount_paid: Int = 0,
    val product: String = "",        // product name at the time of the sale (firmware 2.2+)
    val change: Int = 0,
    val date: String = "",
    val datetime: String = "",
    val price: Int = 0,
    val status: String = "",
    val tray: Int = 0
)

@IgnoreExtraProperties
data class SalesTransaction(
    val itemsSold: String = "",
    val date: String = "",
    val time: String = "",
    val amount: Double = 0.0, // Added to support older structure if needed
    val header: String? = null, // set for a "Week 1 · Nov 1–7" heading row instead of an order
    val sortKey: String = ""    // "yyyy-MM-dd HH:mm:ss", newest first within a week
)

@IgnoreExtraProperties
data class InventoryItem(
    val brand: String = "",
    val quantity: Int = 0
)

@IgnoreExtraProperties
data class Product(
    val brand: String = "",
    val category: String = "",
    val imageUrl: String = "",
    val expirationDate: String = "",
    val price: Double = 0.0
)
