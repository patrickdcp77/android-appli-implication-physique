package com.example.android_ble_judo_3capteurs.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.example.android_ble_judo_3capteurs.data.AccelData
import com.example.android_ble_judo_3capteurs.data.CardioData
import com.example.android_ble_judo_3capteurs.data.SensorParser
import com.example.android_ble_judo_3capteurs.data.TempData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// ─── UUIDs BLE ────────────────────────────────────────────────────────────────
val SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
val CHAR_ACCEL_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
val CHAR_TEMP_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
val CHAR_CARDIO_UUID: UUID = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e")
val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

const val DEVICE_NAME = "ESP32-JudoSensor"
private const val MAX_CONNECTIONS = 2
private const val SCAN_TIMEOUT_MS = 15_000L
private const val RECONNECT_DELAY_MS = 2_000L

// ─── État de connexion BLE ────────────────────────────────────────────────────
enum class DeviceConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class ScannedBleDevice(
    val address: String,
    val name: String,
    val rssi: Int
)

data class DeviceDataState(
    val address: String,
    val name: String,
    val status: DeviceConnectionStatus = DeviceConnectionStatus.DISCONNECTED,
    val tempData: TempData = TempData(),
    val cardioData: CardioData = CardioData(),
    val accelData: AccelData = AccelData()
)

private data class DeviceSession(
    val device: BluetoothDevice,
    var gatt: BluetoothGatt? = null,
    val notifyQueue: ArrayDeque<BluetoothGattCharacteristic> = ArrayDeque()
)

/**
 * Gère le cycle complet BLE :
 *  - scan → filtre par nom DEVICE_NAME
 *  - connexion GATT
 *  - découverte de services
 *  - abonnement CCCD séquentiel sur 3 caractéristiques
 *  - décodage binaire little-endian
 *  - exposition via StateFlow
 */
@SuppressLint("MissingPermission")
class BleRepository(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanDevices = MutableStateFlow<List<ScannedBleDevice>>(emptyList())
    val scanDevices: StateFlow<List<ScannedBleDevice>> = _scanDevices

    private val _deviceStates = MutableStateFlow<Map<String, DeviceDataState>>(emptyMap())
    val deviceStates: StateFlow<Map<String, DeviceDataState>> = _deviceStates

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var scanner: BluetoothLeScanner? = null
    private val discoveredDevices = linkedMapOf<String, BluetoothDevice>()
    private val scanRssi = mutableMapOf<String, Int>()
    private val scanNames = mutableMapOf<String, String>()
    private val sessions = mutableMapOf<String, DeviceSession>()
    private val desiredConnections = mutableSetOf<String>()
    private val reconnectJobs = mutableMapOf<String, Job>()
    private var scanTimeoutJob: Job? = null

    // ─── Scan ─────────────────────────────────────────────────────────────────

    fun startScan() {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (!adapter.isEnabled) {
            _errorMessage.value = "Bluetooth desactive"
            return
        }

        scanner = adapter.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _isScanning.value = true
        _errorMessage.value = null
        scanner?.startScan(null, settings, scanCallback)

        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_isScanning.value) {
                stopScan()
                _errorMessage.value = "Scan termine (timeout ${SCAN_TIMEOUT_MS / 1000}s)"
            }
        }
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        scanner = null
        _isScanning.value = false
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
    }

    fun connect(address: String) {
        desiredConnections.add(address)
        reconnectJobs.remove(address)?.cancel()

        val alreadyConnected = sessions[address] != null
        val canOpenNew = sessions.size < MAX_CONNECTIONS
        if (!alreadyConnected && !canOpenNew) {
            _errorMessage.value = "Maximum 2 connexions BLE"
            return
        }

        val device = discoveredDevices[address] ?: run {
            _errorMessage.value = "Peripherique introuvable"
            return
        }

        updateDeviceState(address, device.name ?: DEVICE_NAME) { current ->
            current.copy(status = DeviceConnectionStatus.CONNECTING)
        }

        val session = sessions.getOrPut(address) { DeviceSession(device) }
        session.gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect(address: String) {
        desiredConnections.remove(address)
        reconnectJobs.remove(address)?.cancel()
        sessions[address]?.gatt?.disconnect()
    }

    fun disconnectAll() {
        stopScan()
        desiredConnections.clear()
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        sessions.values.forEach { it.gatt?.disconnect() }
    }

    // ─── Callbacks BLE ──────────────────────────────────────────────────────

    private fun matchesTargetDevice(result: ScanResult): Boolean {
        val deviceName = result.device.name.orEmpty()
        val advertisedName = result.scanRecord?.deviceName.orEmpty()

        val nameMatches = deviceName.equals(DEVICE_NAME, ignoreCase = true) ||
            advertisedName.equals(DEVICE_NAME, ignoreCase = true) ||
            deviceName.contains("JudoSensor", ignoreCase = true) ||
            advertisedName.contains("JudoSensor", ignoreCase = true)

        val hasTargetService = result.scanRecord?.serviceUuids
            ?.contains(ParcelUuid(SERVICE_UUID)) == true

        return hasTargetService || nameMatches
    }

    private fun displayNameFor(result: ScanResult): String {
        val fromRecord = result.scanRecord?.deviceName
        val fromDevice = result.device.name
        return when {
            !fromRecord.isNullOrBlank() -> fromRecord
            !fromDevice.isNullOrBlank() -> fromDevice
            else -> "BLE-${result.device.address.takeLast(5)}"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!matchesTargetDevice(result)) return

            val displayName = displayNameFor(result)
            discoveredDevices[device.address] = device
            scanRssi[device.address] = result.rssi
            scanNames[device.address] = displayName
            _scanDevices.value = discoveredDevices.values.map {
                ScannedBleDevice(
                    address = it.address,
                    name = scanNames[it.address] ?: DEVICE_NAME,
                    rssi = scanRssi[it.address] ?: 0
                )
            }

            updateDeviceState(device.address, scanNames[device.address] ?: DEVICE_NAME) { it }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            scanTimeoutJob?.cancel()
            scanTimeoutJob = null
            _errorMessage.value = "Scan BLE en erreur ($errorCode)"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                reconnectJobs.remove(address)?.cancel()
                sessions[address]?.gatt = gatt
                updateDeviceState(address, gatt.device.name ?: DEVICE_NAME) {
                    it.copy(status = DeviceConnectionStatus.CONNECTING)
                }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close()
                sessions.remove(address)
                updateDeviceState(address, gatt.device.name ?: DEVICE_NAME) {
                    it.copy(status = DeviceConnectionStatus.DISCONNECTED)
                }

                if (desiredConnections.contains(address)) {
                    scheduleReconnect(address, gatt.device)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val address = gatt.device.address
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateDeviceState(address, gatt.device.name ?: DEVICE_NAME) {
                    it.copy(status = DeviceConnectionStatus.ERROR)
                }
                return
            }

            val service = gatt.getService(SERVICE_UUID) ?: run {
                updateDeviceState(address, gatt.device.name ?: DEVICE_NAME) {
                    it.copy(status = DeviceConnectionStatus.ERROR)
                }
                return
            }

            val session = sessions[address] ?: return
            session.notifyQueue.clear()
            listOf(CHAR_ACCEL_UUID, CHAR_TEMP_UUID, CHAR_CARDIO_UUID).forEach { uuid ->
                service.getCharacteristic(uuid)?.let { session.notifyQueue.add(it) }
            }
            subscribeNext(gatt)
        }

        /** Appelé quand le CCCD a été écrit → on passe à la caractéristique suivante */
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            subscribeNext(gatt)
        }

        /** API >= 33 : reçoit directement la valeur en paramètre */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            dispatchData(gatt.device.address, gatt.device.name ?: DEVICE_NAME, characteristic.uuid, value)
        }

        /** API < 33 : lit la valeur depuis characteristic.value */
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                dispatchData(
                    gatt.device.address,
                    gatt.device.name ?: DEVICE_NAME,
                    characteristic.uuid,
                    characteristic.value
                )
            }
        }
    }

    // ─── Abonnement CCCD séquentiel ───────────────────────────────────────────

    private fun subscribeNext(gatt: BluetoothGatt) {
        val address = gatt.device.address
        val session = sessions[address] ?: return
        val char = session.notifyQueue.removeFirstOrNull()

        if (char == null) {
            updateDeviceState(address, gatt.device.name ?: DEVICE_NAME) {
                it.copy(status = DeviceConnectionStatus.CONNECTED)
            }
            return
        }

        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(CCCD_UUID) ?: run {
            subscribeNext(gatt)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    // ─── Décodage des trames binaires ─────────────────────────────────────────

    private fun dispatchData(address: String, name: String, uuid: UUID, data: ByteArray) {
        when (uuid) {
            CHAR_TEMP_UUID -> SensorParser.parseTemp(data)?.let { parsed ->
                updateDeviceState(address, name) { it.copy(tempData = parsed) }
            }

            CHAR_CARDIO_UUID -> SensorParser.parseCardio(data)?.let { parsed ->
                updateDeviceState(address, name) { it.copy(cardioData = parsed) }
            }

            CHAR_ACCEL_UUID -> SensorParser.parseAccel(data)?.let { parsed ->
                updateDeviceState(address, name) { it.copy(accelData = parsed) }
            }
        }
    }

    private fun scheduleReconnect(address: String, device: BluetoothDevice) {
        reconnectJobs.remove(address)?.cancel()
        reconnectJobs[address] = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!desiredConnections.contains(address)) return@launch
            if (sessions.size >= MAX_CONNECTIONS && sessions[address] == null) return@launch

            updateDeviceState(address, device.name ?: DEVICE_NAME) {
                it.copy(status = DeviceConnectionStatus.CONNECTING)
            }

            val session = sessions.getOrPut(address) { DeviceSession(device) }
            session.gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private fun updateDeviceState(
        address: String,
        name: String,
        transform: (DeviceDataState) -> DeviceDataState
    ) {
        val current = _deviceStates.value[address] ?: DeviceDataState(address = address, name = name)
        _deviceStates.value = _deviceStates.value.toMutableMap().apply {
            this[address] = transform(current)
        }
    }
}
