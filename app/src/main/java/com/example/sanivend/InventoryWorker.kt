package com.example.sanivend

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Background check (every 15 minutes, even when the app is closed):
 * reads /trays once and lets TrayAlerts send or clear the stock and expiry notifications.
 */
class InventoryWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val snapshot = FirebaseDatabase.getInstance().reference.child("trays").get().await()
            TrayAlerts.check(applicationContext, snapshot)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
