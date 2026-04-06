package com.example.android_ble_judo_3capteurs.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.android_ble_judo_3capteurs.ble.BleRepository
import com.example.android_ble_judo_3capteurs.ble.DeviceConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val HISTORY_CAP = 120

data class DeviceGraphHistory(
    val temp: List<Float> = emptyList(),
    val cardio: List<Float> = emptyList(),
    val cardioAvg: List<Float> = emptyList(),
    val accel: List<Float> = emptyList(),
    val impact: List<Float> = emptyList()
)

class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BleRepository(application)

    val isScanning = repository.isScanning
    val scanDevices = repository.scanDevices
    val deviceStates = repository.deviceStates
    val errorMessage = repository.errorMessage

    private val _graphSelection = MutableStateFlow<Set<String>>(emptySet())
    val graphSelection: StateFlow<Set<String>> = _graphSelection

    private val _graphHistory = MutableStateFlow<Map<String, DeviceGraphHistory>>(emptyMap())
    val graphHistory: StateFlow<Map<String, DeviceGraphHistory>> = _graphHistory

    private var lastSnapshot = emptyMap<String, List<Float>>()

    init {
        viewModelScope.launch {
            deviceStates.collect { states ->
                val connectedAddresses = states.values
                    .filter { it.status == DeviceConnectionStatus.CONNECTED }
                    .map { it.address }
                    .toSet()

                _graphSelection.value = _graphSelection.value
                    .filter { connectedAddresses.contains(it) }
                    .toSet()
                    .ifEmpty { connectedAddresses.take(2).toSet() }

                val historyMap = _graphHistory.value.toMutableMap()
                val snapshotMap = lastSnapshot.toMutableMap()

                states.values
                    .filter { it.status == DeviceConnectionStatus.CONNECTED }
                    .forEach { state ->
                        val current = Triple(
                            state.tempData.tempC,
                            state.accelData.avgG,
                            if (state.accelData.impact) 1f else 0f
                        )
                        val currentList = listOf(
                            current.first,
                            state.cardioData.bpm,
                            state.cardioData.bpmAvg,
                            current.second,
                            current.third
                        )

                        if (snapshotMap[state.address] == currentList) return@forEach
                        snapshotMap[state.address] = currentList

                        val old = historyMap[state.address] ?: DeviceGraphHistory()
                        historyMap[state.address] = DeviceGraphHistory(
                            temp = appendAndTrim(old.temp, current.first),
                            cardio = appendAndTrim(old.cardio, state.cardioData.bpm),
                            cardioAvg = appendAndTrim(old.cardioAvg, state.cardioData.bpmAvg),
                            accel = appendAndTrim(old.accel, current.second),
                            impact = appendAndTrim(old.impact, current.third)
                        )
                    }

                lastSnapshot = snapshotMap
                _graphHistory.value = historyMap
            }
        }
    }

    fun startScan() = repository.startScan()

    fun stopScan() = repository.stopScan()

    fun connect(address: String) = repository.connect(address)

    fun disconnect(address: String) = repository.disconnect(address)

    fun disconnectAll() = repository.disconnectAll()

    fun toggleGraphSelection(address: String) {
        val current = _graphSelection.value
        _graphSelection.value = if (current.contains(address)) {
            current - address
        } else {
            current + address
        }
    }

    private fun appendAndTrim(values: List<Float>, next: Float): List<Float> {
        return (values + next).let { if (it.size > HISTORY_CAP) it.takeLast(HISTORY_CAP) else it }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnectAll()
    }
}
