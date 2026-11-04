package com.example.sanivend

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class InventoryWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val trayMapping = mapOf(
        1 to "Sisters Sanitary Napkins",
        2 to "Charmee Sanitary Napkins",
        3 to "Kleenfant Wet Wipes",
        4 to "Giggles Wet Wipes",
        5 to "Femme Facial Tissues",
        6 to "Blest Facial Tissues",
        7 to "Hygienix Hand Sanitizer",
        8 to "Alcosafe Hand Sanitizer"
    )

    override suspend fun doWork(): Result {
        return try {
            val db = FirebaseDatabase.getInstance().reference
            // Fetch inventory once for check
            val snapshot = db.child("inventory").get().await()

            for (child in snapshot.children) {
                val trayKey = child.key ?: continue
                if (trayKey.startsWith("tray_")) {
                    val trayNum = trayKey.replace("tray_", "").toIntOrNull() ?: continue
                    val quantity = (child.value as? Long)?.toInt() ?: 0
                    val productName = trayMapping[trayNum] ?: "Product in Tray $trayNum"

                    if (quantity == 0) {
                        InventoryNotificationUtils.sendNotification(
                            applicationContext,
                            trayNum,
                            "❌ Out of Stock",
                            "$productName is completely empty!"
                        )
                    } else if (quantity < 3) {
                        InventoryNotificationUtils.sendNotification(
                            applicationContext,
                            trayNum,
                            "⚠️ Low Stock Alert",
                            "$productName is running low! Only $quantity left."
                        )
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
