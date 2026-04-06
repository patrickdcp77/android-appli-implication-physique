package com.example.android_ble_judo_3capteurs.data

/**
 * Données de température décodées depuis la caractéristique BLE.
 * Format BLE : uint16_t little-endian (valeur * 10)
 */
data class TempData(
    val tempC: Float = 0f
)

/**
 * Données cardiaques décodées depuis la caractéristique BLE.
 * Format BLE : int32_t (IR brut) + int16_t (BPM*10) + int16_t (BPMmoy*10) = 8 octets
 */
data class CardioData(
    val rawIr: Long = 0L,
    val bpm: Float = 0f,
    val bpmAvg: Float = 0f
)

/**
 * Données d'accélération décodées depuis la caractéristique BLE.
 * Format BLE : int16_t (moy*1000) + int16_t (pic*1000) + uint8_t (impact) + int16_t (delta*1000) = 7 octets
 */
data class AccelData(
    val avgG: Float = 0f,
    val peakG: Float = 0f,
    val impact: Boolean = false,
    val deltaPeakG: Float = 0f
)

