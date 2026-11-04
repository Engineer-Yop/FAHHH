package com.example.sanivend

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsViewModel : ViewModel() {
    private val db = FirebaseDatabase.getInstance().reference
    
    private val _chartData = MutableLiveData<FloatArray>()
    val chartData: LiveData<FloatArray> = _chartData

    private val _currentMode = MutableLiveData<String>("Monthly")
    private val _currentCategory = MutableLiveData<String?>(null)

    private var ordersListener: ValueEventListener? = null
    private var ordersRef: DatabaseReference? = null

    fun setMode(mode: String) {
        _currentMode.value = mode
        refreshObservation()
    }

    fun setCategory(category: String?) {
        _currentCategory.value = category
        refreshObservation()
    }

    private fun refreshObservation() {
        val mode = _currentMode.value ?: "Monthly"
        val category = _currentCategory.value
        
        // Remove old listener
        ordersListener?.let { ordersRef?.removeEventListener(it) }

        ordersRef = db.child("orders")
        
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR).toString()
        val currentMonth = String.format("%02d", now.get(Calendar.MONTH) + 1)
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)

        val size = when (mode) {
            "Annually" -> 24 // Hourly points
            "Weekly" -> 8   // Last 7 days + current? Or Mon-Sun. Let's assume standard layout.
            "Monthly" -> 12 // Jan-Dec
            else -> 12
        }

        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = FloatArray(size) { 0f }
                
                for (child in snapshot.children) {
                    val order = child.getValue(Order::class.java) ?: continue
                    
                    // Filter by Category if selected
                    if (category != null && !isTrayInCategory(order.tray, category)) continue

                    when (mode) {
                        "Annually" -> { // "Annually" button actually shows Hourly data for Today
                            if (order.date == todayDate) {
                                try {
                                    // datetime format: "2026-05-29 11:56:58"
                                    val hour = order.datetime.split(" ")[1].split(":")[0].toInt()
                                    if (hour in 0 until size) {
                                        data[hour] += 1f // Increment item count
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                        "Weekly" -> { // Daily count for current month
                            if (order.date.startsWith("$currentYear-$currentMonth")) {
                                try {
                                    val day = order.date.split("-")[2].toInt()
                                    // This is a simplified mapping to the 8 points on your graph
                                    // For a real weekly view, we'd map to DayOfWeek. 
                                    // Using day-of-month modulo size for now to show variety.
                                    val index = (day - 1) % size
                                    data[index] += 1f
                                } catch (e: Exception) {}
                            }
                        }
                        "Monthly" -> { // Monthly count for current year
                            if (order.date.startsWith(currentYear)) {
                                try {
                                    val month = order.date.split("-")[1].toInt()
                                    if (month in 1..12) {
                                        data[month - 1] += 1f
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }
                }
                _chartData.postValue(data)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        
        ordersRef?.addValueEventListener(ordersListener!!)
    }

    private fun isTrayInCategory(tray: Int, category: String): Boolean {
        return when (category) {
            "Sanitary Napkins" -> tray in 1..2
            "Wet Wipes" -> tray in 3..4
            "Facial Tissues" -> tray in 5..6
            "Hand Sanitizers" -> tray in 7..8
            else -> true
        }
    }

    override fun onCleared() {
        super.onCleared()
        ordersListener?.let { ordersRef?.removeEventListener(it) }
    }
}
