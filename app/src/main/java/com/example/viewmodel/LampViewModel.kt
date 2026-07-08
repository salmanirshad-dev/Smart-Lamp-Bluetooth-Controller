package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmScheduler
import com.example.bluetooth.BluetoothConnectionState
import com.example.bluetooth.BluetoothService
import com.example.data.AppDatabase
import com.example.data.LampAlarm
import com.example.data.LampConfig
import com.example.data.LampRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LampViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LampRepository
    private val bluetoothService = BluetoothService.getInstance()
    private val alarmScheduler = AlarmScheduler(application)

    val configState: StateFlow<LampConfig>
    val connectionState: StateFlow<BluetoothConnectionState> = bluetoothService.connectionState
    val connectedDeviceName: StateFlow<String?> = bluetoothService.connectedDeviceName

    private val _pairedDevices = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val pairedDevices: StateFlow<List<Pair<String, String>>> = _pairedDevices.asStateFlow()

    private val _dipperTimeRemaining = MutableStateFlow<Int?>(null) // in seconds
    val dipperTimeRemaining: StateFlow<Int?> = _dipperTimeRemaining.asStateFlow()

    private val _alarmTimeRemaining = MutableStateFlow<Long?>(null) // in seconds
    val alarmTimeRemaining: StateFlow<Long?> = _alarmTimeRemaining.asStateFlow()

    private val _alarms = MutableStateFlow<List<LampAlarm>>(emptyList())
    val alarms: StateFlow<List<LampAlarm>> = _alarms.asStateFlow()

    private val _nextAlarm = MutableStateFlow<LampAlarm?>(null)
    val nextAlarm: StateFlow<LampAlarm?> = _nextAlarm.asStateFlow()

    // Auto-reconnect handling
    private val _isAutoReconnecting = MutableStateFlow(false)
    val isAutoReconnecting: StateFlow<Boolean> = _isAutoReconnecting.asStateFlow()
    private var isManuallyDisconnecting = false
    private var autoReconnectJob: Job? = null

    private var dipperJob: Job? = null
    private var alarmTrackerJob: Job? = null
    private var lastTriggeredAlarmTime: Long = 0

    init {
        val database = AppDatabase.getDatabase(application)
        repository = LampRepository(database.lampConfigDao(), database.lampAlarmDao())

        configState = repository.config.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LampConfig()
        )

        refreshDevices()

        // Auto-connect to the saved HC-05 device on startup if enabled
        viewModelScope.launch {
            delay(1500) // brief delay to ensure the database layer is stable
            val currentConfig = repository.getConfig()
            if (currentConfig.autoConnectEnabled && 
                currentConfig.lastDeviceAddress.isNotEmpty() &&
                connectionState.value == BluetoothConnectionState.Disconnected
            ) {
                connectDevice(currentConfig.lastDeviceAddress, currentConfig.lastDeviceName)
            }
        }

        // Track and count down the multiple alarms dynamically
        viewModelScope.launch {
            repository.alarms.collect { list ->
                if (list.isEmpty()) {
                    // Create an initial default alarm for 8:00 AM ON to help begin using it
                    addAlarm(8, 0, turnOn = true)
                } else {
                    _alarms.value = list
                    updateAlarmTracker(list)
                }
            }
        }

        // Monitor connection state to auto-reconnect on accidental drop
        viewModelScope.launch {
            var previousState: BluetoothConnectionState = BluetoothConnectionState.Disconnected
            connectionState.collect { currentState ->
                if (previousState == BluetoothConnectionState.Connected &&
                    (currentState == BluetoothConnectionState.Disconnected || currentState is BluetoothConnectionState.Error)
                ) {
                    if (!isManuallyDisconnecting) {
                        val currentConfig = repository.getConfig()
                        val address = currentConfig.lastDeviceAddress
                        val name = currentConfig.lastDeviceName
                        if (address.isNotEmpty()) {
                            triggerAutoReconnect(address, name)
                        }
                    }
                }
                previousState = currentState
            }
        }
    }

    fun hasBluetoothPermission(): Boolean {
        return bluetoothService.hasBluetoothPermission(getApplication())
    }

    fun isBluetoothSupported(): Boolean {
        return bluetoothService.isBluetoothSupported()
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothService.isBluetoothEnabled()
    }

    fun refreshDevices() {
        if (hasBluetoothPermission()) {
            _pairedDevices.value = bluetoothService.getPairedDevices(getApplication())
        }
    }

    fun connectDevice(address: String, name: String) {
        isManuallyDisconnecting = false
        autoReconnectJob?.cancel()
        _isAutoReconnecting.value = false
        viewModelScope.launch {
            val success = bluetoothService.connect(getApplication(), address, name)
            if (success) {
                repository.updateLastDevice(address, name)
            }
        }
    }

    fun disconnectDevice() {
        isManuallyDisconnecting = true
        autoReconnectJob?.cancel()
        _isAutoReconnecting.value = false
        bluetoothService.disconnect()
    }

    private fun triggerAutoReconnect(address: String, name: String) {
        autoReconnectJob?.cancel()
        autoReconnectJob = viewModelScope.launch {
            _isAutoReconnecting.value = true
            var attempts = 0
            val maxAttempts = 5
            while (attempts < maxAttempts && connectionState.value != BluetoothConnectionState.Connected) {
                if (isManuallyDisconnecting) break
                attempts++
                delay(3000) // Delay 3 seconds for HC-05 boot-up/reboot
                if (isManuallyDisconnecting || connectionState.value == BluetoothConnectionState.Connected) break
                bluetoothService.connect(getApplication(), address, name)
            }
            _isAutoReconnecting.value = false
        }
    }

    fun toggleLamp() {
        viewModelScope.launch {
            val currentConfig = configState.value
            val nextState = !currentConfig.lampState
            
            // Send exact state char ONCE
            val codeToSend = if (nextState) "1" else "0"
            val sent = if (connectionState.value == BluetoothConnectionState.Connected) {
                bluetoothService.sendData(codeToSend)
            } else {
                false // Mock or local fallback if not connected so user can test the UI!
            }

            // Update database state
            repository.updateLampState(nextState)

            if (nextState) {
                // Turning ON
                if (currentConfig.dipperEnabled) {
                    startDipperTimer(currentConfig.dipperDurationMinutes)
                }
            } else {
                // Turning OFF
                cancelDipperTimer()
            }
        }
    }

    private fun startDipperTimer(minutes: Int) {
        cancelDipperTimer()
        dipperJob = viewModelScope.launch {
            var seconds = minutes * 60
            while (seconds > 0) {
                _dipperTimeRemaining.value = seconds
                delay(1000)
                seconds--
            }
            _dipperTimeRemaining.value = null
            
            // Time is up! Turn Off the lamp
            val codeToSend = "0"
            if (connectionState.value == BluetoothConnectionState.Connected) {
                bluetoothService.sendData(codeToSend)
            }
            repository.updateLampState(false)
        }
    }

    private fun cancelDipperTimer() {
        dipperJob?.cancel()
        dipperJob = null
        _dipperTimeRemaining.value = null
    }

    fun updateTheme(themeName: String) {
        viewModelScope.launch {
            repository.updateTheme(themeName)
        }
    }

    fun updateDipper(enabled: Boolean, durationMinutes: Int) {
        viewModelScope.launch {
            repository.updateDipper(enabled, durationMinutes)
            // If we disable dipper mode while the lamp is currently on and dipper timer is running:
            if (!enabled) {
                cancelDipperTimer()
            } else if (configState.value.lampState) {
                // If we enable it and lamp is already on, trigger the timer
                startDipperTimer(durationMinutes)
            }
        }
    }

    // MULTIPLE ALARMS CRUD SUPPORT
    fun addAlarm(hour: Int, minute: Int, turnOn: Boolean) {
        viewModelScope.launch {
            val alarm = LampAlarm(hour = hour, minute = minute, enabled = true, turnOn = turnOn)
            val generatedId = repository.saveAlarm(alarm).toInt()
            alarmScheduler.scheduleAlarm(generatedId, hour, minute, turnOn)
        }
    }

    fun toggleAlarm(alarm: LampAlarm, enabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(enabled = enabled)
            repository.saveAlarm(updated)
            if (enabled) {
                alarmScheduler.scheduleAlarm(updated.id, updated.hour, updated.minute, updated.turnOn)
            } else {
                alarmScheduler.cancelAlarm(updated.id, updated.turnOn)
            }
        }
    }

    fun updateAlarmTime(alarm: LampAlarm, hour: Int, minute: Int, turnOn: Boolean) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id, alarm.turnOn)
            val updated = alarm.copy(hour = hour, minute = minute, turnOn = turnOn)
            repository.saveAlarm(updated)
            if (updated.enabled) {
                alarmScheduler.scheduleAlarm(updated.id, hour, minute, turnOn)
            }
        }
    }

    fun deleteAlarm(alarm: LampAlarm) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm.id, alarm.turnOn)
            repository.deleteAlarm(alarm)
        }
    }

    fun updateAutoConnect(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateAutoConnect(enabled)
        }
    }

    private var lastTriggeredMinute: Long = 0

    private fun updateAlarmTracker(alarmList: List<LampAlarm>) {
        alarmTrackerJob?.cancel()
        
        val activeAlarms = alarmList.filter { it.enabled }
        if (activeAlarms.isEmpty()) {
            _nextAlarm.value = null
            _alarmTimeRemaining.value = null
            return
        }

        alarmTrackerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                
                // 1. Calculate remaining countdown for UI display
                var targetAlarmForDisplay: LampAlarm? = null
                var minTargetTime = Long.MAX_VALUE
                
                for (alarm in activeAlarms) {
                    val targetTime = getNextAlarmTimeMillis(alarm.hour, alarm.minute)
                    if (targetTime < minTargetTime) {
                        minTargetTime = targetTime
                        targetAlarmForDisplay = alarm
                    }
                }

                if (targetAlarmForDisplay != null) {
                    _nextAlarm.value = targetAlarmForDisplay
                    val diffMs = minTargetTime - now
                    if (diffMs > 0) {
                        _alarmTimeRemaining.value = diffMs / 1000
                    } else {
                        _alarmTimeRemaining.value = null
                    }
                } else {
                    _nextAlarm.value = null
                    _alarmTimeRemaining.value = null
                }

                // 2. Exact Clock-Time Matching trigger (robust, behaves just like a live timer countdown)
                val currentCal = java.util.Calendar.getInstance()
                val currentHour = currentCal.get(java.util.Calendar.HOUR_OF_DAY)
                val currentMinute = currentCal.get(java.util.Calendar.MINUTE)
                val currentEpochMinute = now / 60_000

                var alarmToTrigger: LampAlarm? = null
                for (alarm in activeAlarms) {
                    if (alarm.hour == currentHour && alarm.minute == currentMinute) {
                        alarmToTrigger = alarm
                        break
                    }
                }

                if (alarmToTrigger != null) {
                    if (currentEpochMinute != lastTriggeredMinute) {
                        lastTriggeredMinute = currentEpochMinute
                        triggerAlarmLocally(alarmToTrigger)
                    }
                }

                delay(1000)
            }
        }
    }

    private fun getNextAlarmTimeMillis(hour: Int, minute: Int): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun triggerAlarmLocally(alarm: LampAlarm) {
        viewModelScope.launch {
            repository.updateLampState(alarm.turnOn)
            val codeToSend = if (alarm.turnOn) "1" else "0"
            if (connectionState.value == BluetoothConnectionState.Connected) {
                bluetoothService.sendData(codeToSend)
            }
            if (alarm.turnOn && configState.value.dipperEnabled) {
                startDipperTimer(configState.value.dipperDurationMinutes)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothService.disconnect()
        cancelDipperTimer()
        alarmTrackerJob?.cancel()
    }
}
