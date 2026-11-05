package com.example.sanivend

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sales Graph data for the open machine: items sold per hour (Today),
 * per day (This Week, Sun–Sat) or per month (This Year), read from /devices/{id}/orders.
 */
class AnalyticsViewModel : ViewModel() {

    private val _chartData = MutableLiveData<FloatArray>()
    val chartData: LiveData<FloatArray> = _chartData

    private var deviceId: String? = null
    private var mode: String = SalesBuckets.YEAR
    private var category: String? = null

    private var ordersListener: ValueEventListener? = null
    private var ordersRef: DatabaseReference? = null

    fun setDevice(id: String) {
        deviceId = id
        refreshObservation()
    }

    fun setMode(newMode: String) {
        mode = newMode
        refreshObservation()
    }

    fun setCategory(newCategory: String?) {
        category = newCategory
        refreshObservation()
    }

    private fun refreshObservation() {
        val id = deviceId ?: return
        val mode = this.mode
        val category = this.category

        ordersListener?.let { ordersRef?.removeEventListener(it) }
        ordersRef = FirebaseDatabase.getInstance().reference.child("devices").child(id).child("orders")

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val size = SalesBuckets.size(mode)

        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = FloatArray(size) { 0f }
                for (child in snapshot.children) {
                    if (child.key == "summary") continue
                    val order = try {
                        child.getValue(Order::class.java)
                    } catch (e: DatabaseException) {
                        null
                    } ?: continue
                    if (category != null && !isTrayInCategory(order.tray, category)) continue
                    val i = SalesBuckets.index(mode, order.date, order.datetime, today)
                    if (i in 0 until size) data[i] += 1f       // one item per order
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
