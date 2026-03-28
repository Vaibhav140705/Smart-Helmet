package com.example.helmetcompanion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val connectionLiveData = MutableLiveData(BluetoothConnectionState())
    private val helmetEventLiveData = MutableLiveData<HelmetEvent?>()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var reader: BufferedReader? = null
    private var shouldReconnect = false

    fun observeConnectionState(): LiveData<BluetoothConnectionState> = connectionLiveData

    fun observeHelmetEvents(): LiveData<HelmetEvent?> = helmetEventLiveData

    fun connect() {
        val targetDevice = settingsRepository.currentSettings().pairedDeviceName
        if (!hasBluetoothPermission()) {
            connectionLiveData.postValue(
                BluetoothConnectionState(
                    status = BluetoothConnectionStatus.PERMISSION_REQUIRED,
                    deviceName = targetDevice,
                    details = "Bluetooth permission is required to pair with the helmet."
                )
            )
            return
        }

        shouldReconnect = true
        connectionLiveData.postValue(
            BluetoothConnectionState(
                status = BluetoothConnectionStatus.CONNECTING,
                deviceName = targetDevice,
                details = "Searching for $targetDevice..."
            )
        )

        thread {
            try {
                val device = adapter?.bondedDevices?.firstOrNull { it.name == targetDevice }
                if (device == null) {
                    connectionLiveData.postValue(
                        BluetoothConnectionState(
                            status = BluetoothConnectionStatus.DEVICE_NOT_FOUND,
                            deviceName = targetDevice,
                            details = "Pair the phone with $targetDevice in Android Bluetooth settings first."
                        )
                    )
                    scheduleReconnect()
                    return@thread
                }

                socket?.close()
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()
                reader = BufferedReader(InputStreamReader(socket?.inputStream))

                connectionLiveData.postValue(
                    BluetoothConnectionState(
                        status = BluetoothConnectionStatus.CONNECTED,
                        deviceName = targetDevice,
                        details = "Helmet connected and listening for ride events."
                    )
                )
                listenForMessages()
            } catch (error: Exception) {
                connectionLiveData.postValue(
                    BluetoothConnectionState(
                        status = BluetoothConnectionStatus.ERROR,
                        deviceName = targetDevice,
                        details = error.message ?: "Helmet connection failed."
                    )
                )
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectHandler.removeCallbacksAndMessages(null)
        closeSocket()
        connectionLiveData.postValue(
            BluetoothConnectionState(
                status = BluetoothConnectionStatus.DISCONNECTED,
                deviceName = settingsRepository.currentSettings().pairedDeviceName,
                details = "Helmet link disconnected."
            )
        )
    }

    fun sendCommand(command: String) {
        try {
            socket?.outputStream?.write("$command\n".toByteArray())
            val current = connectionLiveData.value ?: BluetoothConnectionState()
            connectionLiveData.postValue(current.copy(lastMessage = command))
        } catch (error: Exception) {
            connectionLiveData.postValue(
                BluetoothConnectionState(
                    status = BluetoothConnectionStatus.ERROR,
                    deviceName = settingsRepository.currentSettings().pairedDeviceName,
                    details = "Unable to send $command to helmet: ${error.message.orEmpty()}",
                    lastMessage = command
                )
            )
            scheduleReconnect()
        }
    }

    fun consumeEvent() {
        helmetEventLiveData.postValue(null)
    }

    private fun listenForMessages() {
        thread {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    val trimmed = line.trim()
                    val event = when (trimmed) {
                        "CRASH" -> HelmetEvent(HelmetEventType.CRASH, trimmed)
                        "SOS" -> HelmetEvent(HelmetEventType.SOS, trimmed)
                        "CANCELLED" -> HelmetEvent(HelmetEventType.CANCELLED, trimmed)
                        "OK" -> HelmetEvent(HelmetEventType.HEARTBEAT, trimmed)
                        else -> HelmetEvent(HelmetEventType.MESSAGE, trimmed)
                    }
                    val current = connectionLiveData.value ?: BluetoothConnectionState()
                    connectionLiveData.postValue(current.copy(lastMessage = trimmed, details = "Last helmet event: $trimmed"))
                    helmetEventLiveData.postValue(event)
                }
            } catch (_: Exception) {
                // handled below as disconnect
            } finally {
                closeSocket()
                if (shouldReconnect) {
                    connectionLiveData.postValue(
                        BluetoothConnectionState(
                            status = BluetoothConnectionStatus.DISCONNECTED,
                            deviceName = settingsRepository.currentSettings().pairedDeviceName,
                            details = "Helmet link dropped. Retrying..."
                        )
                    )
                    scheduleReconnect()
                }
            }
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) {
            return
        }
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectHandler.postDelayed({ connect() }, 4_000L)
    }

    private fun closeSocket() {
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        reader = null
        socket = null
    }
}
