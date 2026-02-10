package com.example.helmetcompanion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class BluetoothManager(
    private val context: Context,
    private val onMessageReceived: (String) -> Unit
) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var reader: BufferedReader? = null

    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connect(deviceName: String) {
        Thread {
            try {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e("BT", "Permission not granted")
                    return@Thread
                }

                val device = adapter?.bondedDevices
                    ?.firstOrNull { it.name == deviceName }

                if (device == null) {
                    Log.e("BT", "Device not found")
                    return@Thread
                }

                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()

                Log.d("BT", "Socket connected")

                listen()   // ✅ listener now starts safely

            } catch (e: Exception) {
                Log.e("BT", "Connect failed", e)
            }
        }.start()
    }


    private fun listen() {
        Thread {
            try {
                val input = socket?.inputStream ?: return@Thread
                val buffer = StringBuilder()

                while (true) {
                    val byte = input.read()
                    if (byte == -1) break

                    val ch = byte.toChar()

                    if (ch == '\n') {
                        val message = buffer.toString().trim()
                        buffer.setLength(0)

                        Log.d("BT", "Received: [$message]")
                        onMessageReceived(message)
                    } else {
                        buffer.append(ch)
                    }
                }
            } catch (e: Exception) {
                Log.e("BT", "Listen error", e)
            }
        }.start()
    }



    fun send(message: String) {
        try {
            socket?.outputStream?.write("$message\n".toByteArray())
        } catch (e: Exception) {
            Log.e("BT", "Send failed", e)
        }
    }
}


