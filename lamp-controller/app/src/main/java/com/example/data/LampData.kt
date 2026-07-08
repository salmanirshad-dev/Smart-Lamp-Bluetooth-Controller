package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "lamp_config")
data class LampConfig(
    @PrimaryKey val id: Int = 1,
    val themeName: String = "Neon Sunset",
    val alarmEnabled: Boolean = false,
    val alarmHour: Int = 8,
    val alarmMinute: Int = 0,
    val dipperEnabled: Boolean = false,
    val dipperDurationMinutes: Int = 5,
    val lastDeviceAddress: String = "",
    val lastDeviceName: String = "",
    val lampState: Boolean = false,
    val autoConnectEnabled: Boolean = true
)

@Entity(tableName = "alarms")
data class LampAlarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val turnOn: Boolean = true // true: turn lamp ON, false: turn lamp OFF
)

@Dao
interface LampConfigDao {
    @Query("SELECT * FROM lamp_config WHERE id = 1")
    fun getConfigFlow(): Flow<LampConfig?>

    @Query("SELECT * FROM lamp_config WHERE id = 1")
    suspend fun getConfig(): LampConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: LampConfig)
}

@Dao
interface LampAlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun getAllAlarmsFlow(): Flow<List<LampAlarm>>

    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    suspend fun getAllAlarms(): List<LampAlarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Int): LampAlarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlarm(alarm: LampAlarm): Long

    @Delete
    suspend fun deleteAlarm(alarm: LampAlarm)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Int)
}

@Database(entities = [LampConfig::class, LampAlarm::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lampConfigDao(): LampConfigDao
    abstract fun lampAlarmDao(): LampAlarmDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lamp_controller_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LampRepository(
    private val configDao: LampConfigDao,
    private val alarmDao: LampAlarmDao
) {
    val config: Flow<LampConfig> = configDao.getConfigFlow().map { it ?: LampConfig() }
    val alarms: Flow<List<LampAlarm>> = alarmDao.getAllAlarmsFlow()

    suspend fun getConfig(): LampConfig {
        return configDao.getConfig() ?: LampConfig()
    }

    suspend fun updateConfig(config: LampConfig) {
        configDao.saveConfig(config)
    }

    suspend fun updateTheme(themeName: String) {
        val current = getConfig()
        configDao.saveConfig(current.copy(themeName = themeName))
    }

    suspend fun updateAlarm(enabled: Boolean, hour: Int, minute: Int) {
        val current = getConfig()
        configDao.saveConfig(current.copy(alarmEnabled = enabled, alarmHour = hour, alarmMinute = minute))
    }

    suspend fun updateDipper(enabled: Boolean, durationMinutes: Int) {
        val current = getConfig()
        configDao.saveConfig(current.copy(dipperEnabled = enabled, dipperDurationMinutes = durationMinutes))
    }

    suspend fun updateLastDevice(address: String, name: String) {
        val current = getConfig()
        configDao.saveConfig(current.copy(lastDeviceAddress = address, lastDeviceName = name))
    }

    suspend fun updateLampState(enabled: Boolean) {
        val current = getConfig()
        configDao.saveConfig(current.copy(lampState = enabled))
    }

    suspend fun updateAutoConnect(enabled: Boolean) {
        val current = getConfig()
        configDao.saveConfig(current.copy(autoConnectEnabled = enabled))
    }

    // Multiple Alarms CRUD Support
    suspend fun getAllAlarms(): List<LampAlarm> {
        return alarmDao.getAllAlarms()
    }

    suspend fun getAlarmById(id: Int): LampAlarm? {
        return alarmDao.getAlarmById(id)
    }

    suspend fun saveAlarm(alarm: LampAlarm): Long {
        return alarmDao.saveAlarm(alarm)
    }

    suspend fun deleteAlarm(alarm: LampAlarm) {
        alarmDao.deleteAlarm(alarm)
    }

    suspend fun deleteAlarmById(id: Int) {
        alarmDao.deleteAlarmById(id)
    }
}
