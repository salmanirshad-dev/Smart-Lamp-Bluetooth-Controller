package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.LampRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device reboot detected! Restoring active lamp alarms...")
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getDatabase(context)
                    val repository = LampRepository(database.lampConfigDao(), database.lampAlarmDao())
                    
                    val alarms = repository.getAllAlarms()
                    val scheduler = AlarmScheduler(context)
                    var count = 0
                    
                    for (alarm in alarms) {
                        if (alarm.enabled) {
                            scheduler.scheduleAlarm(alarm.id, alarm.hour, alarm.minute, alarm.turnOn)
                            Log.d("BootReceiver", "Successfully restored alarm ${alarm.id} at ${alarm.hour}:${alarm.minute} (TurnOn: ${alarm.turnOn})")
                            count++
                        }
                    }
                    Log.d("BootReceiver", "Re-scheduled $count active alarms on system boot completed.")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to restore alarms upon boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
