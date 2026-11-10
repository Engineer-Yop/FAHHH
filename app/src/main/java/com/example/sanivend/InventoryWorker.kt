package com.example.sanivend

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Background check (every 15 minutes, even when the app is closed):
 * for every machine paired to the signed-in account, reads its trays once and
 * lets TrayAlerts send or clear the stock, refill and expiry notifications,
 * reads its change tubes (/coins) for CoinAlerts and its new orders for OrderAlerts.
 */
class InventoryWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()  // signed out
        return try {
            val db = FirebaseDatabase.getInstance().reference
            val mine = db.child("users").child(uid).child("devices").get().await()
            for (machine in mine.children) {
                val id = machine.key ?: continue
                val name = TrayAlerts.machineName(id, machine.child("name").value)
                val trays = db.child("devices").child(id).child("trays").get().await()
                TrayAlerts.check(applicationContext, id, name, trays)
                val coins = db.child("devices").child(id).child("coins").get().await()
                CoinAlerts.check(applicationContext, id, name, coins)
                val orders = db.child("devices").child(id).child("orders").get().await()
                OrderAlerts.checkAll(applicationContext, id, name, orders)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
