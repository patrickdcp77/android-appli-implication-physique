package com.example.android_ble_judo_3capteurs.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Décodage binaire little-endian des trames BLE envoyées par l'ESP32-JudoSensor.
 */
object SensorParser {

    /**
     * Température : uint16_t little-endian (valeur * 10)
     * Ex : 0x6701 (hex) = 367 → 36.7 °C
     */
    fun parseTemp(data: ByteArray): TempData? {
        if (data.size < 2) return null
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val raw = bb.short.toInt() and 0xFFFF
        return TempData(tempC = raw / 10f)
    }

    /**
     * Cardio : int32_t (IR brut) + int16_t (BPM*10) + int16_t (BPMmoy*10) = 8 octets
     */
    fun parseCardio(data: ByteArray): CardioData? {
        if (data.size < 8) return null
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val rawIr = bb.int.toLong() and 0xFFFFFFFFL
        val bpm = bb.short / 10f
        val bpmAvg = bb.short / 10f
        return CardioData(rawIr = rawIr, bpm = bpm, bpmAvg = bpmAvg)
    }

    /**
     * Accélération : int16_t (moy*1000) + int16_t (pic*1000) + uint8_t (impact) + int16_t (delta*1000) = 7 octets
     */
    fun parseAccel(data: ByteArray): AccelData? {
        if (data.size < 7) return null
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val avgG = bb.short / 1000f
        val peakG = bb.short / 1000f
        val impact = bb.get().toInt() != 0
        val deltaPeakG = bb.short / 1000f
        return AccelData(avgG = avgG, peakG = peakG, impact = impact, deltaPeakG = deltaPeakG)
    }
}

