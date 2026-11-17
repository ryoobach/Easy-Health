package com.ryoo.easyhealth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

data class TransferPeer(val name: String, val address: String, val device: BluetoothDevice)
data class TransferOffer(val peerName: String, val person: String, val from: Long, val to: Long, val count: Int)

class BluetoothRecordTransfer(private val context: Context) {
    interface Listener {
        fun onStatus(message: String)
        fun onPeers(peers: List<TransferPeer>)
        fun onIncoming(offer: TransferOffer)
        fun onProgress(done: Int, total: Int)
        fun onReceived(json: String, offer: TransferOffer)
        fun onError(message: String)
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("7b7f2c10-6f4b-4b2c-9f2a-6f7e8d9c1011")
        private const val SERVICE_NAME = "EasyHealthTransfer"
        private const val MAX_BYTES = 20 * 1024 * 1024
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var listener: Listener? = null
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var pendingSocket: BluetoothSocket? = null
    private val peers = linkedMapOf<String, TransferPeer>()
    private var receiverRegistered = false
    @Volatile private var serverRunning = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device ?: return
                    val name = try { device.name ?: "이름 없는 기기" } catch (_: SecurityException) { "이름 없는 기기" }
                    synchronized(peers) {
                        peers[device.address] = TransferPeer(name, device.address, device)
                        listener?.onPeers(peers.values.toList())
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> listener?.onStatus("주변 기기 검색이 끝났습니다.")
            }
        }
    }

    private fun has(permission: String): Boolean =
        Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun requireBluetoothPermissions(
        needsScan: Boolean = false,
        needsConnect: Boolean = true,
        needsAdvertise: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= 31 &&
            ((needsScan && !has(Manifest.permission.BLUETOOTH_SCAN)) ||
             (needsConnect && !has(Manifest.permission.BLUETOOTH_CONNECT)) ||
             (needsAdvertise && !has(Manifest.permission.BLUETOOTH_ADVERTISE)))) {
            throw SecurityException("블루투스 작업에 필요한 권한이 허용되지 않았습니다.")
        }
    }

    @Suppress("DEPRECATION")
    fun startDiscovery(listener: Listener) {
        this.listener = listener
        try {
            requireBluetoothPermissions(needsScan = true, needsConnect = true)
            val a = adapter ?: throw IOException("이 기기는 블루투스를 지원하지 않습니다.")
            if (!a.isEnabled) throw IOException("휴대폰 블루투스를 켜 주세요.")
            if (!receiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                }
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                else context.registerReceiver(discoveryReceiver, filter)
                receiverRegistered = true
            }
            synchronized(peers) { peers.clear() }
            listener.onPeers(emptyList())
            if (a.isDiscovering) a.cancelDiscovery()
            listener.onStatus("주변 기기를 검색하고 있습니다. 두 기기 모두 블루투스를 켜 주세요.")
            if (!a.startDiscovery()) throw IOException("검색을 시작하지 못했습니다. 기기 검색 권한을 확인해 주세요.")
        } catch (e: Exception) {
            listener.onError(e.message ?: "블루투스 검색에 실패했습니다.")
        }
    }

    fun startServer(listener: Listener) {
        this.listener = listener
        try {
            requireBluetoothPermissions(needsConnect = true, needsAdvertise = true)
            val a = adapter ?: throw IOException("블루투스를 지원하지 않습니다.")
            if (!a.isEnabled) throw IOException("블루투스가 꺼져 있습니다. 블루투스를 켠 뒤 다시 시도해 주세요.")
            try { server?.close() } catch (_: Exception) { }
            try { pendingSocket?.close() } catch (_: Exception) { }
            pendingSocket = null
            serverRunning = true
            Log.i("EasyHealthTransfer", "startServer requested; adapter enabled=${a.isEnabled}, uuid=$SERVICE_UUID")
            listener.onStatus("수신 서버를 시작하고 있습니다. 서버 소켓 생성 중...")
            executor.execute {
                var socketServer: BluetoothServerSocket? = null
                var socket: BluetoothSocket? = null
                try {
                    Log.i("EasyHealthTransfer", "Creating RFCOMM server socket; service=$SERVICE_NAME uuid=$SERVICE_UUID")
                    socketServer = a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                    server = socketServer
                    Log.i("EasyHealthTransfer", "RFCOMM server socket created successfully")
                    if (!serverRunning) {
                        try { socketServer.close() } catch (_: Exception) { }
                        if (server === socketServer) server = null
                        return@execute
                    }
                    listener.onStatus("수신 대기 중입니다. 보내는 기기에서 이 기기를 검색해 주세요.")
                    Log.i("EasyHealthTransfer", "RFCOMM server is listening; waiting for accept()")
                    socket = socketServer.accept()
                    Log.i("EasyHealthTransfer", "RFCOMM client accepted")
                    pendingSocket = socket
                    listener.onStatus("보내는 기기와 연결되었습니다. 데이터 수신을 준비합니다.")
                    try { socketServer.close() } catch (_: Exception) { }
                    if (server === socketServer) server = null

                    val input = DataInputStream(socket.inputStream)
                    val output = DataOutputStream(socket.outputStream)
                    val header = JSONObject(input.readUTF())
                    if (header.optString("kind") != "offer") throw IOException("전송 요청 형식이 올바르지 않습니다.")
                    val offer = TransferOffer(
                        header.optString("device", "상대 기기"),
                        header.optString("person", "본인"),
                        header.optLong("from"), header.optLong("to"), header.optInt("count")
                    )

                    // 연결된 상대 기기의 전송 요청을 자동 승인하고 곧바로 수신합니다.
                    output.writeByte(1)
                    output.flush()
                    listener.onStatus(offer.peerName + " 기기와 연결되었습니다. 데이터를 수신하고 있습니다.")
                    val size = input.readInt()
                    if (size !in 1..MAX_BYTES) throw IOException("전송 데이터 크기가 올바르지 않습니다.")
                    val bytes = ByteArray(size)
                    var offset = 0
                    while (offset < size) {
                        val n = input.read(bytes, offset, minOf(8192, size - offset))
                        if (n < 0) throw IOException("전송 도중 연결이 끊겼습니다.")
                        offset += n
                        listener.onProgress(offset, size)
                    }
                    val json = String(bytes, Charsets.UTF_8)
                    JSONArray(json)
                    listener.onStatus("데이터 수신이 완료되었습니다. 저장을 마무리합니다.")
                    listener.onReceived(json, offer)
                    try { socket.close() } catch (_: Exception) { }
                    if (pendingSocket === socket) pendingSocket = null
                    serverRunning = false
                    listener.onStatus("데이터 수신이 완료되었습니다.")
                } catch (e: Exception) {
                    Log.e("EasyHealthTransfer", "RFCOMM server/receive failure", e)
                    val detail = e.localizedMessage ?: e.javaClass.simpleName
                    if (serverRunning) listener.onError("수신 서버 실행 실패: $detail")
                    else if (socket != null) listener.onError("데이터 수신 실패: $detail")
                    serverRunning = false
                    try { socketServer?.close() } catch (_: Exception) { }
                    try { socket?.close() } catch (_: Exception) { }
                    if (server === socketServer) server = null
                    if (pendingSocket === socket) pendingSocket = null
                }
            }
        } catch (e: Exception) {
            serverRunning = false
            Log.e("EasyHealthTransfer", "Unable to start RFCOMM server", e)
            listener.onError("수신 대기를 시작하지 못했습니다: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    fun cancelDiscovery() {
        try { adapter?.cancelDiscovery() } catch (_: Exception) { }
        listener?.onStatus("기기 검색을 취소했습니다.")
    }

    fun stopServer() {
        serverRunning = false
        try { server?.close() } catch (_: Exception) { }
        server = null
        try { pendingSocket?.close() } catch (_: Exception) { }
        pendingSocket = null
        listener?.onStatus("수신 대기를 취소했습니다.")
    }

    fun acceptIncoming(offer: TransferOffer) {
        val socket = pendingSocket ?: run { listener?.onError("전송 연결이 이미 종료되었습니다."); return }
        executor.execute {
            try {
                val input = DataInputStream(socket.inputStream)
                val output = DataOutputStream(socket.outputStream)
                output.writeByte(1)
                output.flush()
                val size = input.readInt()
                if (size !in 1..MAX_BYTES) throw IOException("전송 파일 크기가 올바르지 않습니다.")
                val bytes = ByteArray(size)
                var offset = 0
                while (offset < size) {
                    val n = input.read(bytes, offset, minOf(8192, size - offset))
                    if (n < 0) throw IOException("전송 도중 연결이 끊겼습니다.")
                    offset += n
                    listener?.onProgress(offset, size)
                }
                val json = String(bytes, Charsets.UTF_8)
                JSONArray(json)
                listener?.onReceived(json, offer)
                socket.close()
                pendingSocket = null
            } catch (e: Exception) {
                try { socket.close() } catch (_: Exception) { }
                pendingSocket = null
                listener?.onError(e.message ?: "데이터를 받지 못했습니다.")
            }
        }
    }

    fun rejectIncoming() {
        val socket = pendingSocket ?: return
        executor.execute {
            try { DataOutputStream(socket.outputStream).writeByte(0); socket.close() } catch (_: Exception) { }
            pendingSocket = null
            listener?.onStatus("상대 기기의 전송 요청을 거절했습니다.")
        }
    }

    fun send(peer: TransferPeer, person: String, from: Long, to: Long, json: String, count: Int) {
        listener?.onStatus(peer.name + "에 연결하고 있습니다.")
        executor.execute {
            var socket: BluetoothSocket? = null
            try {
                requireBluetoothPermissions(needsScan = true, needsConnect = true)
                adapter?.cancelDiscovery()
                listener?.onStatus(peer.name + " 기기의 페어링 및 연결을 진행하고 있습니다. Android 페어링 요청이 나타나면 허용해 주세요.")
                socket = peer.device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
                listener?.onStatus(peer.name + " 기기와 블루투스 연결이 완료되었습니다. 데이터를 전송합니다.")
                val output = DataOutputStream(socket.outputStream)
                val input = DataInputStream(socket.inputStream)
                output.writeUTF(JSONObject().put("kind", "offer").put("device", adapter?.name ?: "Easy Health")
                    .put("person", person).put("from", from).put("to", to).put("count", count).toString())
                output.flush()
                if (input.readByte().toInt() != 1) throw IOException("받는 기기에서 전송을 승인하지 않았습니다.")
                val bytes = json.toByteArray(Charsets.UTF_8)
                if (bytes.size !in 1..MAX_BYTES) throw IOException("전송 데이터가 너무 큽니다.")
                output.writeInt(bytes.size)
                var offset = 0
                while (offset < bytes.size) {
                    val n = minOf(8192, bytes.size - offset)
                    output.write(bytes, offset, n)
                    output.flush()
                    offset += n
                    listener?.onProgress(offset, bytes.size)
                }
                listener?.onStatus("전송이 완료되었습니다. (" + count + "건)")
            } catch (e: Exception) {
                listener?.onError(e.message ?: "전송에 실패했습니다.")
            } finally {
                try { socket?.close() } catch (_: Exception) { }
            }
        }
    }

    fun close() {
        try { adapter?.cancelDiscovery() } catch (_: Exception) { }
        serverRunning = false
        try { server?.close() } catch (_: Exception) { }
        try { pendingSocket?.close() } catch (_: Exception) { }
        if (receiverRegistered) {
            try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        executor.shutdownNow()
    }
}
