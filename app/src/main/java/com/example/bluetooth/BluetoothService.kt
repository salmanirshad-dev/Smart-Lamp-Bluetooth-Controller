package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

sealed class BluetoothConnectionState {
    object Disconnected : BluetoothConnectionState()
    object Connecting : BluetoothConnectionState()
    object Connected : BluetoothConnectionState()
    data class Error(val message: String) : BluetoothConnectionState()
}

class BluetoothService {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // HC-05 classic SPP UUID

    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): List<Pair<String, String>> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val devices = mutableListOf<Pair<String, String>>()
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                devices.add(device.name to device.address)
            }
        } catch (e: Exception) {
            Log.e("BluetoothService", "Failed to get paired devices", e)
        }
        return devices
    }

    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, address: String, deviceName: String): Boolean = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth not supported")
            return@withContext false
        }
        if (!hasBluetoothPermission(context)) {
            _connectionState.value = BluetoothConnectionState.Error("Bluetooth permission not granted")
            return@withContext false
        }

        _connectionState.value = BluetoothConnectionState.Connecting

        try {
            // Disconnect old socket if any
            disconnect()

            val device = bluetoothAdapter.getRemoteDevice(address)
            val socket = device.createRfcommSocketToServiceRecord(sppUuid)
            bluetoothSocket = socket

            // BluetoothAdapter.cancelDiscovery() to make connection faster
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (e: Exception) {
                Log.w("BluetoothService", "Cancel discovery failed", e)
            }

            socket.connect()
            _connectionState.value = BluetoothConnectionState.Connected
            _connectedDeviceName.value = deviceName
            true
        } catch (e: IOException) {
            Log.e("BluetoothService", "Connection failed", e)
            _connectionState.value = BluetoothConnectionState.Error(e.message ?: "Failed to connect")
            disconnect()
            false
        }
    }

    fun sendData(data: String): Boolean {
        val socket = bluetoothSocket
        if (socket == null || _connectionState.value != BluetoothConnectionState.Connected) {
            Log.w("BluetoothService", "Attempted to send data but socket is not connected")
            return false
        }
        return try {
            socket.outputStream.write(data.toByteArray())
            socket.outputStream.flush()
            Log.d("BluetoothService", "Successfully sent exactly once: $data")
            true
        } catch (e: IOException) {
            Log.e("BluetoothService", "Failed to send data: ${e.message}", e)
            _connectionState.value = BluetoothConnectionState.Error("Transmission lost: ${e.message}")
            disconnect()
            false
        }
    }
      suspend fun sendDataDirectly(context: Context, address: String, data: String): Boolean = withContext(Dispatchers.IO) {
        if (bluetoothAdapter == null || !hasBluetoothPermission(context)) return@withContext false

        // If already connected to this device, reuse the existing connection
        if (bluetoothSocket?.remoteDevice?.address == address && _connectionState.value == BluetoothConnectionState.Connected) {
            val success = sendData(data)
            if (success) {
                Log.d("BluetoothService", "Reused active connection to send alarm/background data.")
                return@withContext true
            }
        }

        var tempSocket: BluetoothSocket? = null
        try {
            val device = bluetoothAdapter.getRemoteDevice(address)
            tempSocket = device.createRfcommSocketToServiceRecord(sppUuid)
            tempSocket.connect()
            tempSocket.outputStream.write(data.toByteArray())
            tempSocket.outputStream.flush()
            Log.d("BluetoothService", "Directly sent $data to $address successfully (created temporary socket)!")
            true
        } catch (e: Exception) {
            Log.e("BluetoothService", "Failed to direct send $data to $address", e)
            false
        } finally {
            try {
                tempSocket?.close()
            } catch (ignored: Exception) {}
        }
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
        } catch (e: Exception) {
            Log.e("BluetoothService", "Error closing socket", e)
        } finally {
            bluetoothSocket = null
            _connectionState.value = BluetoothConnectionState.Disconnected
            _connectedDeviceName.value = null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: BluetoothService? = null

        fun getInstance(): BluetoothService {
            return INSTANCE ?: synchronized(this) {
                val instance = BluetoothService()
                INSTANCE = instance
                instance
            }
        }
    }
}
