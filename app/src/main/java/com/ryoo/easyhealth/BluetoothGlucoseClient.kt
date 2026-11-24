package com.ryoo.easyhealth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID

data class MeterCandidate(val name: String, val address: String, val device: BluetoothDevice)

data class MeterReading(val type: String, val value: String, val timestamp: Long, val sequence: Int)

class BluetoothGlucoseClient(private val context: Context) {
    interface Listener {
        fun onStatus(message: String)
        fun onDevices(devices: List<MeterCandidate>)
        fun onConnected(device: MeterCandidate)
        fun onReadings(readings: List<MeterReading>)
        fun onError(message: String)
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())
    private val found = linkedMapOf<String, MeterCandidate>()
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var listener: Listener? = null
    private var activeDevice: MeterCandidate? = null
    private val pending = mutableListOf<MeterReading>()
    private val ketoneSequences = mutableSetOf<Int>()
    private val descriptorQueue = java.util.ArrayDeque<BluetoothGattDescriptor>()
    private var descriptorWriting = false
    private var measurementReady = false
    private var contextReady = false
    private var racpReady = false
    private var requestedRecords = false

    companion object {
        private val GLUCOSE_SERVICE = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
        private val MEASUREMENT = UUID.fromString("00002a18-0000-1000-8000-00805f9b34fb")
        private val CONTEXT = UUID.fromString("00002a34-0000-1000-8000-00805f9b34fb")
        private val RACP = UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SCAN_MS = 15000L
    }

    fun startScan(listener: Listener) {
        this.listener = listener
        val a = adapter
        if (a == null || !a.isEnabled) {
            listener.onError("휴대폰 블루투스를 켜 주세요.")
            return
        }
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            listener.onError("주변 기기 검색 권한이 필요합니다.")
            return
        }
        stopScan()
        found.clear()
        listener.onDevices(emptyList())
        listener.onStatus("주변 측정기기를 찾고 있습니다. 측정기를 페어링 모드로 바꿔 주세요.")
        val scanner = a.bluetoothLeScanner
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = result.scanRecord?.deviceName ?: safeName(device)
                if (name.isNullOrBlank()) return
                val normalized = name.lowercase()
                if (!normalized.contains("contour") && !normalized.contains("caresens") &&
                    !normalized.contains("i-sens") && !normalized.contains("glucose")) return
                val candidate = MeterCandidate(name, device.address, device)
                found[device.address] = candidate
                handler.post { this@BluetoothGlucoseClient.listener?.onDevices(found.values.toList()) }
            }
            override fun onScanFailed(errorCode: Int) {
                handler.post { this@BluetoothGlucoseClient.listener?.onError("블루투스 검색에 실패했습니다. ($errorCode)") }
            }
        }
        scanCallback = callback
        try {
            scanner.startScan(callback)
            handler.postDelayed({
                stopScan()
                this.listener?.onStatus(if (found.isEmpty()) "측정기기를 찾지 못했습니다. 측정기 페어링 모드와 휴대폰 권한을 확인해 주세요." else "검색이 끝났습니다. 연결할 측정기를 선택해 주세요.")
                this.listener?.onDevices(found.values.toList())
            }, SCAN_MS)
        } catch (_: SecurityException) {
            listener.onError("블루투스 검색 권한을 허용해 주세요.")
        } catch (e: Exception) {
            listener.onError("검색을 시작하지 못했습니다: ${e.localizedMessage ?: "오류"}")
        }
    }

    fun stopScan() {
        val callback = scanCallback ?: return
        try {
            if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: Exception) { }
        scanCallback = null
    }

    fun connect(candidate: MeterCandidate, listener: Listener) {
        this.listener = listener
        stopScan()
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            listener.onError("블루투스 연결 권한이 필요합니다.")
            return
        }
        closeGatt()
        activeDevice = candidate
        pending.clear()
        ketoneSequences.clear()
        descriptorQueue.clear()
        descriptorWriting = false
        measurementReady = false
        contextReady = false
        racpReady = false
        requestedRecords = false
        try {
            listener.onStatus("${candidate.name} 연결 중입니다.")
            if (candidate.device.bondState != BluetoothDevice.BOND_BONDED) {
                candidate.device.createBond()
            }
            gatt = if (Build.VERSION.SDK_INT >= 23) {
                candidate.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                candidate.device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            listener.onError("측정기 연결에 실패했습니다: ${e.localizedMessage ?: "오류"}")
        }
    }


    fun connectAddress(name: String, address: String, listener: Listener) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            listener.onError("블루투스 연결 권한이 필요합니다.")
            return
        }
        try {
            val device = adapter?.getRemoteDevice(address)
            if (device == null) {
                listener.onError("등록된 측정기를 찾을 수 없습니다.")
                return
            }
            connect(MeterCandidate(name, address, device), listener)
        } catch (e: Exception) {
            listener.onError("등록된 측정기에 연결하지 못했습니다: ${e.localizedMessage ?: "오류"}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { listener?.onError("측정기 연결이 끊겼습니다. ($status)") }
                closeGatt()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                handler.post { listener?.onStatus("연결됨. 측정 기록 서비스를 확인하고 있습니다.") }
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post { listener?.onStatus("측정기 연결이 종료되었습니다.") }
                closeGatt()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handler.post { listener?.onError("측정기 데이터 서비스를 찾지 못했습니다.") }
                return
            }
            val service = g.getService(GLUCOSE_SERVICE)
            val measurement = service?.getCharacteristic(MEASUREMENT)
            val racp = service?.getCharacteristic(RACP)
            if (service == null || measurement == null || racp == null) {
                handler.post { listener?.onError("이 기기는 표준 혈당 기록 서비스(1808)를 제공하지 않습니다. 기기별 연결 방식을 추가로 확인해야 합니다.") }
                return
            }
            handler.post { activeDevice?.let { listener?.onConnected(it) } }
            enableNotifications(g, measurement)
            service.getCharacteristic(CONTEXT)?.let { enableNotifications(g, it) }
            enableNotifications(g, racp)
            handler.post { listener?.onStatus("측정 기록 수신을 준비하고 있습니다.") }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val c = descriptor.characteristic
            if (c.uuid == MEASUREMENT) measurementReady = true
            if (c.uuid == CONTEXT) contextReady = true
            if (c.uuid == RACP) racpReady = true
            descriptorWriting = false
            writeNextDescriptor(g)
            if (measurementReady && racpReady && (serviceHasContext(g).not() || contextReady) && !requestedRecords) {
                requestedRecords = true
                handler.post { listener?.onStatus("저장된 측정 기록을 요청했습니다.") }
                writeRacp(g, byteArrayOf(0x01, 0x01))
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value ?: return
            handleCharacteristicChanged(characteristic, bytes)
        }

        // Android 13+ supplies the notification payload directly in this callback.
        // Handle both callback forms so the newest measurement is not lost on newer Android versions.
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChanged(characteristic, value)
        }
    }

    private fun handleCharacteristicChanged(characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (characteristic.uuid == MEASUREMENT) {
            parseMeasurement(bytes)?.let { reading ->
                synchronized(pending) {
                    pending.removeAll { it.sequence == reading.sequence }
                    pending.add(if (ketoneSequences.contains(reading.sequence)) reading.copy(type = "케톤", value = (reading.value.toDoubleOrNull()?.div(10.0)?.toString() ?: reading.value)) else reading)
                }
            }
        } else if (characteristic.uuid == CONTEXT) {
            parseContext(bytes)?.let { contextInfo ->
                synchronized(pending) {
                    if (contextInfo.second) {
                        ketoneSequences.add(contextInfo.first)
                        val index = pending.indexOfFirst { it.sequence == contextInfo.first }
                        if (index >= 0) pending[index] = pending[index].copy(type = "케톤", value = (pending[index].value.toDoubleOrNull()?.div(10.0)?.toString() ?: pending[index].value))
                    }
                }
            }
        } else if (characteristic.uuid == RACP && bytes.size >= 2 && (bytes[0].toInt() and 0xff) == 6) {
            // RACP response code: wait briefly after completion so a final measurement/context
            // notification queued by the meter is processed before taking the import snapshot.
            if (bytes.size >= 4 && (bytes[3].toInt() and 0xff) != 1) {
                handler.post { listener?.onError("측정기에서 저장 기록 전송을 완료하지 못했습니다. RACP 응답을 확인해 주세요.") }
                return
            }
            handler.postDelayed({
                val result = synchronized(pending) { pending.toList().sortedBy { it.timestamp } }
                if (result.isEmpty()) listener?.onStatus("가져올 새 측정 기록이 없습니다.")
                else listener?.onReadings(result)
            }, 300L)
        }
    }

    private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            g.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD) ?: return
            descriptor.value = if (characteristic.uuid == RACP) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            synchronized(descriptorQueue) { descriptorQueue.add(descriptor) }
            writeNextDescriptor(g)
        } catch (_: Exception) { }
    }

    private fun writeNextDescriptor(g: BluetoothGatt) {
        if (descriptorWriting) return
        val next = synchronized(descriptorQueue) { if (descriptorQueue.isEmpty()) null else descriptorQueue.removeFirst() } ?: return
        descriptorWriting = true
        try {
            if (!g.writeDescriptor(next)) { descriptorWriting = false; writeNextDescriptor(g) }
        } catch (_: Exception) {
            descriptorWriting = false
        }
    }

    private fun writeRacp(g: BluetoothGatt, value: ByteArray) {
        try {
            val c = g.getService(GLUCOSE_SERVICE)?.getCharacteristic(RACP) ?: return
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = value
            g.writeCharacteristic(c)
        } catch (_: Exception) { }
    }

    private fun serviceHasContext(g: BluetoothGatt): Boolean =
        g.getService(GLUCOSE_SERVICE)?.getCharacteristic(CONTEXT) != null

    private fun parseMeasurement(b: ByteArray): MeterReading? {
        if (b.size < 12) return null
        return try {
            var p = 0
            val flags = b[p++].toInt() and 0xff
            val seq = u16(b, p); p += 2
            val year = u16(b, p); p += 2
            val month = b[p++].toInt() and 0xff
            val day = b[p++].toInt() and 0xff
            val hour = b[p++].toInt() and 0xff
            val minute = b[p++].toInt() and 0xff
            val second = b[p++].toInt() and 0xff
            var offset = 0
            if ((flags and 0x01) != 0) { offset = s16(b, p); p += 2 }
            val raw = sfloat(b[p], b[p + 1]); p += 2
            val mgdl = if ((flags and 0x04) == 0) raw * 100000.0 else raw * 18.0182
            if ((flags and 0x02) != 0) p++
            if ((flags and 0x08) != 0) p++
            val timestamp = if ((flags and 0x01) != 0) {
                java.time.LocalDateTime.of(year, month, day, hour, minute, second)
                    .toInstant(java.time.ZoneOffset.ofTotalSeconds(offset * 60)).toEpochMilli()
            } else {
                java.util.Calendar.getInstance().apply {
                    set(year, month - 1, day, hour, minute, second)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            MeterReading("혈당", String.format(java.util.Locale.US, "%.0f", mgdl), timestamp, seq)
        } catch (_: Exception) { null }
    }

    private fun parseContext(b: ByteArray): Pair<Int, Boolean>? {
        if (b.size < 3) return null
        return try {
            var p = 0
            val flags = b[p++].toInt() and 0xff
            val seq = u16(b, p); p += 2
            if ((flags and 0x80) != 0) p++
            if ((flags and 0x01) != 0) p += 3
            var mealType = -1
            if ((flags and 0x02) != 0 && p < b.size) mealType = b[p].toInt() and 0xff
            Pair(seq, mealType == 6)
        } catch (_: Exception) { null }
    }

    private fun sfloat(a: Byte, b: Byte): Double {
        val raw = ((b.toInt() and 0xff) shl 8) or (a.toInt() and 0xff)
        var mantissa = raw and 0x0fff
        var exponent = (raw shr 12) and 0x0f
        if (mantissa >= 0x0800) mantissa -= 0x1000
        if (exponent >= 0x08) exponent -= 0x10
        return mantissa * Math.pow(10.0, exponent.toDouble())
    }

    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 0xff) or ((b[p + 1].toInt() and 0xff) shl 8)
    private fun s16(b: ByteArray, p: Int): Int = u16(b, p).let { if (it >= 0x8000) it - 0x10000 else it }

    private fun safeName(device: BluetoothDevice): String? =
        try { if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) device.name else null } catch (_: Exception) { null }

    private fun hasPermission(permission: String): Boolean =
        Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun closeGatt() {
        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) { }
        gatt = null
    }

    fun close() {
        stopScan()
        closeGatt()
    }
}
