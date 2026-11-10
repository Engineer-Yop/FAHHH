package com.example.sanivend

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Sales Monitoring: this month's orders of the open machine, per category,
 * grouped by week (Week 1 = days 1–7 … Week 5 = 29–31), newest week first.
 *
 * On the 1st of a new month the lists start empty again. Nothing is deleted:
 * every order stays in /devices/{id}/orders, so Sales Performance (the graph)
 * still counts them.
 */
class SalesMonitoringActivity : BaseActivity() {

    private var ordersRef: DatabaseReference? = null
    private var ordersListener: ValueEventListener? = null
    private var traysRef: DatabaseReference? = null
    private var traysListener: ValueEventListener? = null

    private lateinit var monthView: TextView
    private lateinit var adapters: Map<String, SalesTransactionAdapter>
    private lateinit var containers: List<LinearLayout>

    /** Latest data, so a change to either orders or product names redraws the lists. */
    private var lastOrders: DataSnapshot? = null
    private val trayNames = mutableMapOf<Int, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeviceSession.requireDevice(this)) return
        setContentView(R.layout.activity_sales_monitoring)

        monthView = findViewById(R.id.tv_month)
        setupLists()
        setupButtons()
        observeTrayNames()
        observeOrders()
    }

    private fun setupLists() {
        val napkins = SalesTransactionAdapter(emptyList())
        val wipes = SalesTransactionAdapter(emptyList())
        val tissues = SalesTransactionAdapter(emptyList())
        val sanitizers = SalesTransactionAdapter(emptyList())
        adapters = mapOf(
            ProductCatalog.NAPKINS to napkins,
            ProductCatalog.WIPES to wipes,
            ProductCatalog.TISSUES to tissues,
            ProductCatalog.SANITIZERS to sanitizers
        )
        val setup = { id: Int, adapter: SalesTransactionAdapter ->
            val rv = findViewById<RecyclerView>(id)
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = adapter
            rv.isNestedScrollingEnabled = false
        }
        setup(R.id.rvSanitaryNapkins, napkins)
        setup(R.id.rvWetWipes, wipes)
        setup(R.id.rvFacialTissues, tissues)
        setup(R.id.rvHandSanitizers, sanitizers)

        containers = listOf(
            findViewById(R.id.containerNapkins), findViewById(R.id.containerWipes),
            findViewById(R.id.containerTissues), findViewById(R.id.containerSanitizers)
        )
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_sales_napkins).setOnClickListener { toggle(containers[0]) }
        findViewById<Button>(R.id.btn_sales_wipes).setOnClickListener { toggle(containers[1]) }
        findViewById<Button>(R.id.btn_sales_tissues).setOnClickListener { toggle(containers[2]) }
        findViewById<Button>(R.id.btn_sales_sanitizers).setOnClickListener { toggle(containers[3]) }
    }

    private fun toggle(view: View) {
        view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    /** Product names can change on the Products page; older orders don't carry their name. */
    private fun observeTrayNames() {
        val ref = DeviceSession.ref(this, "trays")
        traysRef = ref
        traysListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trayNames.clear()
                for (tray in 1..ProductCatalog.TRAY_COUNT) {
                    trayNames[tray] = ProductCatalog.name(tray, snapshot.child("tray_$tray").child("product").value)
                }
                render()
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(traysListener!!)
    }

    private fun observeOrders() {
        val ref = DeviceSession.ref(this, "orders")      // /devices/{open machine}/orders
        ordersRef = ref
        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                lastOrders = snapshot
                render()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SalesMonitoring", "Database error: ${error.message}")
            }
        }
        ref.addValueEventListener(ordersListener!!)
    }

    private fun render() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        monthView.text = "${MonthWeeks.monthTitle(today)} · grouped by week · starts over each month"

        // category → week → orders
        val byCategory = mutableMapOf<String, TreeMap<Int, MutableList<SalesTransaction>>>()
        val inTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val outTime = SimpleDateFormat("hh:mm a", Locale.US)
        val outDate = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val inDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (child in lastOrders?.children ?: emptyList()) {
            if (child.key == "summary") continue
            val order = try {
                child.getValue(Order::class.java)
            } catch (e: DatabaseException) {
                null
            } ?: continue
            if (order.tray !in 1..ProductCatalog.TRAY_COUNT) continue
            if (!MonthWeeks.inThisMonth(order.date, today)) continue

            val name = if (order.product.isNotBlank()) ProductCatalog.name(order.tray, order.product)
                       else trayNames[order.tray] ?: ProductCatalog.defaultName(order.tray)
            val time = try { inTime.parse(order.datetime)?.let { outTime.format(it) } ?: "" } catch (e: Exception) { "" }
            val date = try { inDate.parse(order.date)?.let { outDate.format(it) } ?: order.date } catch (e: Exception) { order.date }

            val weeks = byCategory.getOrPut(ProductCatalog.category(order.tray)) {
                TreeMap<Int, MutableList<SalesTransaction>>(Collections.reverseOrder())
            }
            weeks.getOrPut(MonthWeeks.week(order.date)) { mutableListOf() }
                .add(SalesTransaction(itemsSold = "$name  ₱${order.price}", date = date, time = time,
                    sortKey = order.datetime.ifBlank { order.date }))
        }

        for ((category, adapter) in adapters) {
            val rows = mutableListOf<SalesTransaction>()
            val weeks = byCategory[category]
            if (weeks != null) {
                for ((week, orders) in weeks) {                      // newest week first
                    val total = orders.size
                    rows.add(SalesTransaction(header = "${MonthWeeks.label(week, today)}  ($total sold)"))
                    rows.addAll(orders.sortedByDescending { it.sortKey })
                }
            }
            adapter.updateData(rows)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ordersListener?.let { ordersRef?.removeEventListener(it) }
        traysListener?.let { traysRef?.removeEventListener(it) }
    }
}
