package com.example.sanivend

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class SalesMonitoringActivity : BaseActivity() {

    private var ordersRef: DatabaseReference? = null
    private var ordersListener: ValueEventListener? = null

    private lateinit var rvNapkins: RecyclerView
    private lateinit var rvWipes: RecyclerView
    private lateinit var rvTissues: RecyclerView
    private lateinit var rvSanitizers: RecyclerView

    private lateinit var containerNapkins: LinearLayout
    private lateinit var containerWipes: LinearLayout
    private lateinit var containerTissues: LinearLayout
    private lateinit var containerSanitizers: LinearLayout

    private lateinit var adapterNapkins: SalesTransactionAdapter
    private lateinit var adapterWipes: SalesTransactionAdapter
    private lateinit var adapterTissues: SalesTransactionAdapter
    private lateinit var adapterSanitizers: SalesTransactionAdapter

    // Mapping of tray numbers to product names/categories
    private val trayMapping = mapOf(
        1 to "Sisters",
        2 to "Charmee",
        3 to "Kleenfant",
        4 to "Giggles",
        5 to "Femme",
        6 to "Blest",
        7 to "Hygienix",
        8 to "Alcosafe"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!DeviceSession.requireDevice(this)) return
        setContentView(R.layout.activity_sales_monitoring)

        setupRecyclerViews()
        setupButtons()
        
        observeOrders()
    }

    private fun setupRecyclerViews() {
        rvNapkins = findViewById(R.id.rvSanitaryNapkins)
        rvWipes = findViewById(R.id.rvWetWipes)
        rvTissues = findViewById(R.id.rvFacialTissues)
        rvSanitizers = findViewById(R.id.rvHandSanitizers)

        containerNapkins = findViewById(R.id.containerNapkins)
        containerWipes = findViewById(R.id.containerWipes)
        containerTissues = findViewById(R.id.containerTissues)
        containerSanitizers = findViewById(R.id.containerSanitizers)

        adapterNapkins = SalesTransactionAdapter(emptyList())
        adapterWipes = SalesTransactionAdapter(emptyList())
        adapterTissues = SalesTransactionAdapter(emptyList())
        adapterSanitizers = SalesTransactionAdapter(emptyList())

        val setup = { rv: RecyclerView, adapter: SalesTransactionAdapter ->
            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = adapter
        }

        setup(rvNapkins, adapterNapkins)
        setup(rvWipes, adapterWipes)
        setup(rvTissues, adapterTissues)
        setup(rvSanitizers, adapterSanitizers)
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_sales_napkins).setOnClickListener { toggleVisibility(containerNapkins) }
        findViewById<Button>(R.id.btn_sales_wipes).setOnClickListener { toggleVisibility(containerWipes) }
        findViewById<Button>(R.id.btn_sales_tissues).setOnClickListener { toggleVisibility(containerTissues) }
        findViewById<Button>(R.id.btn_sales_sanitizers).setOnClickListener { toggleVisibility(containerSanitizers) }
    }

    private fun toggleVisibility(view: View) {
        view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun observeOrders() {
        val ref = DeviceSession.ref(this, "orders")      // /devices/{open machine}/orders
        ordersRef = ref
        ordersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val napkinsList = mutableListOf<SalesTransaction>()
                val wipesList = mutableListOf<SalesTransaction>()
                val tissuesList = mutableListOf<SalesTransaction>()
                val sanitizersList = mutableListOf<SalesTransaction>()

                for (child in snapshot.children) {
                    if (child.key == "summary") continue
                    val order = try {
                        child.getValue(Order::class.java)
                    } catch (e: DatabaseException) {
                        null
                    } ?: continue
                    
                    // Map tray to product name
                    val productName = trayMapping[order.tray] ?: "Unknown Tray ${order.tray}"
                    
                    // Extract time from datetime "yyyy-MM-dd HH:mm:ss" to "hh:mm a"
                    val timeFormatted = try {
                        val inputSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        val outputTimeSdf = SimpleDateFormat("hh:mm a", Locale.US)
                        val date = inputSdf.parse(order.datetime)
                        if (date != null) outputTimeSdf.format(date) else ""
                    } catch (e: Exception) {
                        ""
                    }

                    // Convert Order to SalesTransaction for the adapter
                    // The date in JSON is "yyyy-MM-dd", convert to "MM/dd/yyyy" if needed for consistency
                    val dateFormatted = try {
                        val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val outputDateSdf = SimpleDateFormat("MM/dd/yyyy", Locale.US)
                        val dateObj = inputSdf.parse(order.date)
                        if (dateObj != null) outputDateSdf.format(dateObj) else order.date
                    } catch (e: Exception) {
                        order.date
                    }

                    val transaction = SalesTransaction(
                        itemsSold = productName,
                        date = dateFormatted,
                        time = timeFormatted
                    )

                    // Categorize based on tray
                    when (order.tray) {
                        1, 2 -> napkinsList.add(transaction)
                        3, 4 -> wipesList.add(transaction)
                        5, 6 -> tissuesList.add(transaction)
                        7, 8 -> sanitizersList.add(transaction)
                    }
                }

                // Sort lists by newest
                val sortSdf = SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US)
                val comparator = Comparator<SalesTransaction> { t1, t2 ->
                    try {
                        val d1 = sortSdf.parse("${t1.date} ${t1.time}")
                        val d2 = sortSdf.parse("${t2.date} ${t2.time}")
                        d2?.compareTo(d1) ?: 0
                    } catch (e: Exception) { 0 }
                }

                napkinsList.sortWith(comparator)
                wipesList.sortWith(comparator)
                tissuesList.sortWith(comparator)
                sanitizersList.sortWith(comparator)

                adapterNapkins.updateData(napkinsList)
                adapterWipes.updateData(wipesList)
                adapterTissues.updateData(tissuesList)
                adapterSanitizers.updateData(sanitizersList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SalesMonitoring", "Database error: ${error.message}")
            }
        }
        ref.addValueEventListener(ordersListener!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        ordersListener?.let { ordersRef?.removeEventListener(it) }
    }
}
