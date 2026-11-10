package com.example.sanivend

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sales Performance data for the open machine, read from /devices/{id}/orders:
 * items sold per day (Weekly: Sun–Sat, Monthly: day 1–31) or per month (Annually: Jan–Dec),
 * plus the period's totals ("This week: 12 items · ₱180").
 * Uses every order ever saved, so it is not affected by Sales Monitoring's monthly reset.
 */
class AnalyticsViewModel : ViewModel() {

    private val _chartData = MutableLiveData<FloatArray>()
    val chartData: LiveData<FloatArray> = _chartData

    private val _totals = MutableLiveData<String>()
    val totals: LiveData<String> = _totals

    private var deviceId: String? = null
    private var mode: String = SalesBuckets.WEEK
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
        val size = SalesBuckets.size(mode, today)

        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = FloatArray(size) { 0f }
                var items = 0
                var revenue = 0
                for (child in snapshot.children) {
                    if (child.key == "summary") continue
                    val order = try {
                        child.getValue(Order::class.java)
                    } catch (e: DatabaseException) {
                        null
                    } ?: continue
                    if (!ProductCatalog.inCategory(order.tray, category)) continue
                    val i = SalesBuckets.index(mode, order.date, today)
                    if (i in 0 until size) {
                        data[i] += 1f       // one item per order
                        items++
                        revenue += order.price
                    }
                }
                _chartData.postValue(data)
                val what = category ?: "All products"
                _totals.postValue("${SalesBuckets.periodName(mode, today)} · $what: " +
                        "$items item${if (items == 1) "" else "s"} · ₱$revenue")
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ordersRef?.addValueEventListener(ordersListener!!)
    }

    override fun onCleared() {
        super.onCleared()
        ordersListener?.let { ordersRef?.removeEventListener(it) }
    }
}
