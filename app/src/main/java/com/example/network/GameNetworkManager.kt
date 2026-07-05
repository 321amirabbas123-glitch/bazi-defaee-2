package com.example.network

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.*

class GameNetworkManager(
    private val onMessageReceived: (String) -> Unit,
    private val onConnectionStateChanged: (ConnectionStatus) -> Unit
) {
    private val TAG = "GameNetworkManager"
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    enum class ConnectionStatus {
        DISCONNECTED,
        HOST_LISTENING,
        CONNECTED
    }

    // Starts Host TCP Server
    fun startServer(port: Int = 8888) {
        scope.launch {
            try {
                Log.d(TAG, "Starting server on port $port...")
                serverSocket = ServerSocket(port)
                onConnectionStateChanged(ConnectionStatus.HOST_LISTENING)
                isRunning = true
                
                val socket = serverSocket?.accept() ?: return@launch
                Log.d(TAG, "Client connected: ${socket.inetAddress.hostAddress}")
                clientSocket = socket
                setupStreams(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                disconnect()
            }
        }
    }

    // Connects Client to Host
    fun connectToHost(ip: String, port: Int = 8888) {
        scope.launch {
            try {
                Log.d(TAG, "Connecting to host $ip:$port...")
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 6000)
                Log.d(TAG, "Connected to host!")
                clientSocket = socket
                setupStreams(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                disconnect()
            }
        }
    }

    private fun setupStreams(socket: Socket) {
        try {
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8")), true)
            isRunning = true
            onConnectionStateChanged(ConnectionStatus.CONNECTED)
            
            // Listening loop
            scope.launch {
                try {
                    while (isRunning) {
                        val line = reader?.readLine()
                        if (line == null) {
                            Log.d(TAG, "Stream reached EOF, closing.")
                            break
                        }
                        onMessageReceived(line)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Reader loop exception", e)
                } finally {
                    disconnect()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting up streams", e)
            disconnect()
        }
    }

    fun sendMessage(msg: String) {
        scope.launch {
            try {
                writer?.println(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
            }
        }
    }

    fun disconnect() {
        if (!isRunning && clientSocket == null && serverSocket == null) return
        isRunning = false
        Log.d(TAG, "Disconnecting sockets...")
        scope.launch {
            try { reader?.close() } catch (e: Exception) {}
            try { writer?.close() } catch (e: Exception) {}
            try { clientSocket?.close() } catch (e: Exception) {}
            try { serverSocket?.close() } catch (e: Exception) {}
            reader = null
            writer = null
            clientSocket = null
            serverSocket = null
            onConnectionStateChanged(ConnectionStatus.DISCONNECTED)
        }
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    // Skip virtual or loopback interfaces
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val ip = address.hostAddress ?: ""
                            if (ip.isNotEmpty() && ip != "127.0.0.1") {
                                return ip
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GameNetworkManager", "Error getting IP", e)
            }
            return "127.0.0.1"
        }
    }
}
