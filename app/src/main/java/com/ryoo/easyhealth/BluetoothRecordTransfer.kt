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

    private fun requireBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31 &&
            (!has(Manifest.permission.BLUETOOTH_SCAN) ||
             !has(Manifest.permission.BLUETOOTH_CONNECT) ||
             !has(Manifest.permission.BLUETOOTH_ADVERTISE))) {
            throw SecurityException("블루투스 검색·연결·기기 표시 권한이 필요합니다.")
        }
    }

    @Suppress("DEPRECATION")
    fun startDiscovery(listener: Listener) {
        this.listener = listener
        try {
            requireBluetoothPermissions()
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
            requireBluetoothPermissions()
            val a = adapter ?: throw IOException("블루투스를 지원하지 않습니다.")
            if (!a.isEnabled) throw IOException("블루투스를 켜 주세요.")
            listener.onStatus("수신 대기 중입니다. 상대 기기에서 이 기기를 선택해 주세요.")
            executor.execute {
                try {
                    server?.close()
                    val socketServer = a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                    server = socketServer
                    val socket = socketServer.accept()
                    pendingSocket = socket
                    socketServer.close()
                    server = null
                    val input = DataInputStream(socket.inputStream)
                    val header = JSONObject(input.readUTF())
                    if (header.optString("kind") != "offer") throw IOException("전송 요청 형식이 올바르지 않습니다.")
                    val offer = TransferOffer(
                        header.optString("device", "상대 기기"),
                        header.optString("person", "본인"),
                        header.optLong("from"), header.optLong("to"), header.optInt("count")
                    )
                    listener.onIncoming(offer)
                } catch (e: Exception) {
                    listener.onError(e.message ?: "수신 대기 중 오류가 발생했습니다.")
                }
            }
        } catch (e: Exception) {
            listener.onError(e.message ?: "수신 대기를 시작하지 못했습니다.")
        }
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
                requireBluetoothPermissions()
                adapter?.cancelDiscovery()
                socket = peer.device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket.connect()
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
        try { server?.close() } catch (_: Exception) { }
        try { pendingSocket?.close() } catch (_: Exception) { }
        if (receiverRegistered) {
            try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        executor.shutdownNow()
    }
}
