package com.example.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun getPendingIntent(alarmId: Int, turnOn: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_TURN_ON", turnOn)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId, // Use specific alarm id as the request code to schedule multiple parallel alarms
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAlarm(alarmId: Int, hour: Int, minute: Int, turnOn: Boolean) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If the time has already passed today, schedule it for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val pendingIntent = getPendingIntent(alarmId, turnOn)

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // setExactAndAllowWhileIdle ensures it runs even during Doze/Standby
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                Log.d("AlarmScheduler", "Exact alarm $alarmId scheduled for ${calendar.time} (Hour: $hour, Min: $minute, TurnOn: $turnOn)")
            } else {
                // Fallback to non-exact background friendly alarm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                Log.d("AlarmScheduler", "Non-exact alarm $alarmId scheduled for ${calendar.time} due to lack of exact alarm permission.")
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Failed to schedule alarm $alarmId (canScheduleExact = $canScheduleExact)", e)
            // Fallback to non-exact standard alarm if permission/security exception occurs
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                Log.d("AlarmScheduler", "Fallback alarm scheduled successfully for $alarmId.")
            } catch (ex: Exception) {
                Log.e("AlarmScheduler", "Fallback scheduling failed for alarm $alarmId", ex)
            }
        }
    }

    fun cancelAlarm(alarmId: Int, turnOn: Boolean) {
        val pendingIntent = getPendingIntent(alarmId, turnOn)
        alarmManager.cancel(pendingIntent)
        Log.d("AlarmScheduler", "Alarm $alarmId cancelled (TurnOn: $turnOn).")
    }
}
