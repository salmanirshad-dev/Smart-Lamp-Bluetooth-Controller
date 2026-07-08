package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.LampRepository
import com.example.bluetooth.BluetoothService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        val turnOn = intent.getBooleanExtra("ALARM_TURN_ON", true)
        Log.d("AlarmReceiver", "Alarm intent triggered! AlarmId: $alarmId, TurnOn: $turnOn")
        
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val repository = LampRepository(database.lampConfigDao(), database.lampAlarmDao())
                val config = repository.getConfig()

                var shouldProcess = true
                var alarmHour = 8
                var alarmMinute = 0
                
                if (alarmId != -1) {
                    val dbAlarm = repository.getAlarmById(alarmId)
                    if (dbAlarm == null || !dbAlarm.enabled) {
                        shouldProcess = false
                        Log.d("AlarmReceiver", "Alarm $alarmId is deleted or disabled in database, skipping execution.")
                    } else {
                        alarmHour = dbAlarm.hour
                        alarmMinute = dbAlarm.minute
                    }
                } else {
                    // Fallback to master config if not specified
                    shouldProcess = config.alarmEnabled
                    alarmHour = config.alarmHour
                    alarmMinute = config.alarmMinute
                }

                if (shouldProcess) {
                    // Update local database lamp state to reflect active glow
                    repository.updateLampState(turnOn)
                    Log.d("AlarmReceiver", "Alarm triggered: updated local lamp state database to: $turnOn")

                    val address = config.lastDeviceAddress
                    if (address.isNotEmpty()) {
                        val signalToSend = if (turnOn) "1" else "0"
                        Log.d("AlarmReceiver", "Attempting to send signal ('$signalToSend') to HC-05: $address")
                        val success = BluetoothService.getInstance().sendDataDirectly(context, address, signalToSend)
                        if (success) {
                            Log.d("AlarmReceiver", "Lamp turned ${if (turnOn) "on" else "off"} successfully via Alarm background trigger.")
                        } else {
                            Log.e("AlarmReceiver", "Failed to connect to HC-05 in background.")
                        }
                    } else {
                        Log.w("AlarmReceiver", "No HC-05 device MAC saved. Cannot control lamp state.")
                    }

                    // Reschedule for the next day to keep alarm alive continuously
                    val scheduler = AlarmScheduler(context)
                    scheduler.scheduleAlarm(alarmId, alarmHour, alarmMinute, turnOn)
                    Log.d("AlarmReceiver", "Rescheduled alarm $alarmId for tomorrow...")
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error processing alarm receiver", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
