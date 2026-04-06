
// S'assurer que les dépendances Arduino sont incluses avant toute fonction utilisant Serial, millis, delay, int16_t, etc.
#include <Arduino.h>


// Prototype pour la fonction utilisée dans la calibration
bool readMPU6050Accel(int16_t &ax, int16_t &ay, int16_t &az);

// Variables pour la moyenne sur 1s
float sum_axg = 0.0f, sum_ayg = 0.0f, sum_azg = 0.0f, sum_accMag = 0.0f;
unsigned long count_accel = 0;
unsigned long lastMeanUpdateMs = 0;
// Offsets d'accélération calibrés au démarrage
float accelOffsetX = 0.0f;
float accelOffsetY = 0.0f;
float accelOffsetZ = 0.0f;

// ...existing code...

// (Déplacer la fonction de calibration ici, après tous les includes Arduino)

// ...existing code...

// (APRÈS tous les includes Arduino, Wire, etc.)

void calibrateMPU6050Offsets(unsigned long calibDurationMs = 2000) {
  Serial.println("Calibration MPU6050 en cours (ne pas bouger le capteur)...");
  unsigned long start = millis();
  unsigned long count = 0;
  double sumX = 0, sumY = 0, sumZ = 0;
  while (millis() - start < calibDurationMs) {
    int16_t ax, ay, az;
    if (readMPU6050Accel(ax, ay, az)) {
      sumX += ax / 2048.0f;
      sumY += ay / 2048.0f;
      sumZ += az / 2048.0f;
      count++;
    }
    delay(2); // ~500Hz max
  }
  if (count > 0) {
    accelOffsetX = sumX / count;
    accelOffsetY = sumY / count;
    accelOffsetZ = sumZ / count;
  }
  Serial.print("Offsets calibrés : X=");
  Serial.print(accelOffsetX, 4);
  Serial.print(" Y=");
  Serial.print(accelOffsetY, 4);
  Serial.print(" Z=");
  Serial.println(accelOffsetZ, 4);
}
/*
================ BLE DATA FORMAT FOR ANDROID =================
Service UUID: 6e400001-b5a3-f393-e0a9-e50e24dcca9e

Characteristic UUIDs and data types:

- Accélération (UUID: 6e400002-b5a3-f393-e0a9-e50e24dcca9e)
  Format: 4 floats (X, Y, Z, magnitude)
  Total: 16 bytes (little endian)
  [float ax][float ay][float az][float magnitude]

- Température (UUID: 6e400003-b5a3-f393-e0a9-e50e24dcca9e)
  Format: 1 float (température en °C)
  Total: 4 bytes (little endian)
  [float tempC]

- Cardio (UUID: 6e400004-b5a3-f393-e0a9-e50e24dcca9e)
  Format: 1 long (IR), 2 floats (BPM, BPMmoy)
  Total: 12 bytes (little endian)
  [long irValue][float bpm][float bpmAvg]

Tous les champs sont envoyés en binaire IEEE754 (float/long, little endian).
Android doit parser les bytes dans cet ordre pour chaque notification reçue.
==============================================================
*/
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// UUIDs générés pour le service et les caractéristiques
#define SERVICE_UUID "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define CHAR_UUID_ACCEL "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
#define CHAR_UUID_TEMP  "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
#define CHAR_UUID_CARDIO "6e400004-b5a3-f393-e0a9-e50e24dcca9e"

BLECharacteristic *pCharAccel = nullptr;
BLECharacteristic *pCharTemp = nullptr;
BLECharacteristic *pCharCardio = nullptr;

void setupBLE() {
  BLEDevice::init("ESP32-JudoSensor");
  BLEServer *pServer = BLEDevice::createServer();
  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharAccel = pService->createCharacteristic(
    CHAR_UUID_ACCEL,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pCharAccel->addDescriptor(new BLE2902());

  pCharTemp = pService->createCharacteristic(
    CHAR_UUID_TEMP,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pCharTemp->addDescriptor(new BLE2902());

  pCharCardio = pService->createCharacteristic(
    CHAR_UUID_CARDIO,
    BLECharacteristic::PROPERTY_NOTIFY
  );
  pCharCardio->addDescriptor(new BLE2902());

  pService->start();
  BLEDevice::startAdvertising();
}

void sendAccelBLE(float accMagnitudeG) {
  if (!pCharAccel) return;
  // Format: 1 int32_t (magnitude*1000)
  int32_t data = (int32_t)(accMagnitudeG * 1000.0f);
  pCharAccel->setValue((uint8_t*)&data, sizeof(data));
  pCharAccel->notify();
}

void sendTempBLE(float tempC) {
  if (!pCharTemp) return;
  // Format: 1 int16_t (température*10)
  int16_t tempInt = (int16_t)(tempC * 10.0f);
  pCharTemp->setValue((uint8_t*)&tempInt, sizeof(tempInt));
  pCharTemp->notify();
}

void sendCardioBLE(long irValue, float bpm, float bpmAvg) {
  if (!pCharCardio) return;
  // Format: 1 int32_t (IR), 2 int16_t (BPM*10, BPMmoy*10)
  int32_t irInt = (int32_t)irValue;
  int16_t bpmInt = (int16_t)(bpm * 10.0f);
  int16_t bpmAvgInt = (int16_t)(bpmAvg * 10.0f);
  uint8_t data[sizeof(int32_t) + 2 * sizeof(int16_t)];
  memcpy(data, &irInt, sizeof(int32_t));
  memcpy(data + sizeof(int32_t), &bpmInt, sizeof(int16_t));
  memcpy(data + sizeof(int32_t) + sizeof(int16_t), &bpmAvgInt, sizeof(int16_t));
  pCharCardio->setValue(data, sizeof(data));
  pCharCardio->notify();
}
/*
  Projet : ESP32-C3 + DS18B20 + MAX30102 + MPU6050
  ------------------------------------------------
  Concept :
    Ce programme permet de lire simultanément la température (DS18B20), le rythme cardiaque/oxymétrie (MAX30102) et les accélérations (MPU6050) sur un ESP32-C3.
    L'affichage série donne :
      - La température en °C (DS18B20)
      - L'IR, le BPM et la moyenne BPM (MAX30102)
      - Les accélérations (valeurs absolues, 0G au repos) sur X, Y, Z et la somme dynamique (MPU6050)
    Le code est robuste : détection d'absence de doigt, d'absence de battement, gestion d'erreur capteur.

  Brochage :
    - DS18B20 :
        DATA  -> GPIO4 (avec résistance de 4.7kΩ entre DATA et 3V3)
        VCC   -> 3V3
        GND   -> GND
    - MAX30102 (I2C) :
        SDA   -> GPIO8
        SCL   -> GPIO9
        VCC   -> 3V3
        GND   -> GND
        
        SDA   -> GPIO8 (partagé avec MAX30102)
        SCL   -> GPIO9 (partagé avec MAX30102)
        VCC   -> 3V3
        GND   -> GND

  Fonctionnement :
    - Le bus I2C (Wire) est initialisé sur GPIO8/9 pour MAX30102 et MPU6050.
    - Le DS18B20 utilise OneWire sur GPIO4.
    - Le MAX30102 mesure l'IR et calcule le BPM via l'algorithme SparkFun.
    - Le MPU6050 fournit les accélérations, corrigées pour afficher 0G au repos (gravité retirée sur Z).
    - Les valeurs d'accélération sont affichées en valeur absolue (pas de sens, uniquement l'intensité).
    - L'affichage série est limité à 1Hz pour chaque capteur.
    - Gestion d'erreur : messages si capteur absent, doigt non détecté, ou pas de battement depuis 5s.

  Auteur : [À compléter]
  Date   : [À compléter]
*/

#define MPU6050_ADDR 0x68
#define MPU6050_PWR_MGMT_1 0x6B
#define MPU6050_ACCEL_XOUT_H 0x3B
#define MPU6050_ACCEL_CONFIG 0x1C
#define MPU6050_CONFIG 0x1A

// Debug: 0 = aucun, 1 = debug MPU, 2 = tout
int debug = 1;



bool mpuAvailable = false;


#include <Arduino.h>
#include <stdint.h>
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <MAX30105.h>
#include "heartRate.h"

#define MPU6050_ADDR 0x68
#define MPU6050_PWR_MGMT_1 0x6B
#define MPU6050_ACCEL_XOUT_H 0x3B

void printAddress(const DeviceAddress address) {
  for (uint8_t i = 0; i < 8; i++) {
    if (address[i] < 16) {
      Serial.print("0");
    }
    Serial.print(address[i], HEX);
  }
}
MAX30105 particleSensor;
long irValue = 0;
float beatsPerMinute = 0;
float beatAvg = 0;
bool sensorFound = false;
unsigned long lastDisplayMs = 0;
unsigned long lastBeat = 0;
float rates[4];
byte rateSpot = 0;
static const uint8_t DS18B20_PIN = 4;
static const unsigned long TEMP_REPORT_INTERVAL_MS = 1000;
static const unsigned long CARDIO_REPORT_INTERVAL_MS = 1000;
static const unsigned long ACCEL_REPORT_INTERVAL_MS = 1000;
static const unsigned long NO_BEAT_TIMEOUT_MS = 5000;
static const float IMPACT_DELTA_THRESHOLD_G = 1.20f;
OneWire oneWire(DS18B20_PIN);
DallasTemperature sensors(&oneWire);
DeviceAddress sensorAddress;
unsigned long lastTempRequestMs = 0;
unsigned long lastTempReportMs = 0;
unsigned long lastCardioReportMs = 0;
unsigned long lastAccelReportMs = 0;
bool tempConversionPending = false;


void setupMPU6050() {
  // Wake up MPU6050
  Wire.beginTransmission(MPU6050_ADDR);
  Wire.write(MPU6050_PWR_MGMT_1);
  Wire.write(0);
  Wire.endTransmission();
  delay(10);

  // Set full scale to ±16g (ACCEL_CONFIG register, bits 3:4 = 0b11)
  Wire.beginTransmission(MPU6050_ADDR);
  Wire.write(MPU6050_ACCEL_CONFIG);
  Wire.write(0x18); // 0b00011000
  Wire.endTransmission();
  delay(10);

  // Set DLPF to 0 (max bandwidth, min delay)
  Wire.beginTransmission(MPU6050_ADDR);
  Wire.write(MPU6050_CONFIG);
  Wire.write(0x00);
  Wire.endTransmission();
  delay(10);

  mpuAvailable = true;
}

bool readMPU6050Accel(int16_t &ax, int16_t &ay, int16_t &az) {
  Wire.beginTransmission(MPU6050_ADDR);
  Wire.write(MPU6050_ACCEL_XOUT_H);
  if (Wire.endTransmission(false) != 0) return false;
  if (Wire.requestFrom(MPU6050_ADDR, 6) != 6) return false;
  ax = (Wire.read() << 8) | Wire.read();
  ay = (Wire.read() << 8) | Wire.read();
  az = (Wire.read() << 8) | Wire.read();
  return true;
}

void setup() {
  setupBLE();
  Serial.begin(115200);
  if (debug >= 1) {
    Serial.println("Fichier flashé : " __FILE__);
  }

  // Initialisation I2C pour MAX30102 sur GPIO8 (SDA), GPIO9 (SCL)
  Wire.begin(8, 9);

  // MPU6050
  Serial.println("--- Initialisation MPU6050 ---");
  setupMPU6050();
  if (mpuAvailable) {
    Serial.println("MPU6050 prêt.");
    calibrateMPU6050Offsets(2000); // 2s de calibration
  } else Serial.println("MPU6050 non détecté !");
  const unsigned long serialWaitStart = millis();
  while (!Serial && (millis() - serialWaitStart < 4000)) {
    delay(10);
  }
  delay(500);



  // Initialisation I2C pour MAX30102 sur GPIO8 (SDA), GPIO9 (SCL)
  Wire.begin(8, 9);

  Serial.println("--- Initialisation MAX30102 ---");
  if (particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    sensorFound = true;
    Serial.println("MAX30102 détecté et initialisé.");
    particleSensor.setup();
    particleSensor.setPulseAmplitudeRed(0x0A); // LED faible pour test
    particleSensor.setPulseAmplitudeGreen(0);  // LED verte off
  } else {
    Serial.println("MAX30102 non détecté !");
    sensorFound = false;
  }

  Serial.println("=== Test DS18B20 ESP32-C3 ===");
  Serial.println("Alimentation capteur recommandee: 3V3");
  Serial.println("Lecture temperature toutes les 1 seconde");

  sensors.begin();
  const uint8_t deviceCount = sensors.getDeviceCount();

  Serial.print("Nombre de capteurs detectes: ");
  Serial.println(deviceCount);

  if (deviceCount == 0) {
    Serial.println("Aucun DS18B20 detecte. Verifie GPIO, GND, 3V3 et resistance 4.7k vers 3V3.");
    return;
  }

  if (sensors.getAddress(sensorAddress, 0)) {
    Serial.print("Adresse capteur[0]: ");
    printAddress(sensorAddress);
    Serial.println();
  }

  sensors.setResolution(12);
  sensors.setWaitForConversion(false);
  sensors.requestTemperatures();
  lastTempRequestMs = millis();
  lastTempReportMs = millis();
  lastCardioReportMs = millis();
  lastAccelReportMs = millis();
  lastMeanUpdateMs = millis();
  tempConversionPending = true;
}

void loop() {
  static float last_tempC = DEVICE_DISCONNECTED_C;
  static float last_accMagnitudeG = 0.0f;
  static float peak_accMagnitudeG = 0.0f;
  static float peak_deltaMagnitudeG = 0.0f;
  static bool impactDetected = false;

  unsigned long now = millis();

  if (mpuAvailable) {
    int16_t ax, ay, az;
    if (readMPU6050Accel(ax, ay, az)) {
      float axg = (ax / 2048.0f) - accelOffsetX;
      float ayg = (ay / 2048.0f) - accelOffsetY;
      float azg = (az / 2048.0f) - accelOffsetZ;
      float accMagnitudeG = sqrtf((axg * axg) + (ayg * ayg) + (azg * azg));
      float deltaMagnitudeG = fabsf(accMagnitudeG - last_accMagnitudeG);

      sum_accMag += accMagnitudeG;
      count_accel++;
      if (accMagnitudeG > peak_accMagnitudeG) {
        peak_accMagnitudeG = accMagnitudeG;
      }
      if (deltaMagnitudeG > peak_deltaMagnitudeG) {
        peak_deltaMagnitudeG = deltaMagnitudeG;
      }
      if (deltaMagnitudeG >= IMPACT_DELTA_THRESHOLD_G) {
        impactDetected = true;
      }
      last_accMagnitudeG = accMagnitudeG;
    } else if (debug >= 1) {
      Serial.println("Erreur lecture MPU6050");
    }
  }

  if (sensorFound) {
    irValue = particleSensor.getIR();
    if (irValue < 500) {
      beatsPerMinute = 0;
      beatAvg = 0;
    } else if (checkForBeat(irValue)) {
      if (lastBeat > 0) {
        float delta = (now - lastBeat);
        if (delta > 0) {
          beatsPerMinute = 60000.0f / delta;
          rates[rateSpot++] = beatsPerMinute;
          rateSpot %= 4;
          float sum = 0;
          for (byte i = 0; i < 4; i++) {
            sum += rates[i];
          }
          beatAvg = sum / 4.0f;
        }
      }
      lastBeat = now;
    }

    if (lastBeat > 0 && (now - lastBeat) > NO_BEAT_TIMEOUT_MS) {
      beatsPerMinute = 0;
      beatAvg = 0;
    }
  }

  if (tempConversionPending && (now - lastTempRequestMs >= 750)) {
    float tempC = sensors.getTempCByIndex(0);
    if (tempC != DEVICE_DISCONNECTED_C) {
      last_tempC = tempC;
    } else if (debug >= 1) {
      Serial.println("DS18B20 non disponible");
    }
    tempConversionPending = false;
  }

  if (!tempConversionPending && (now - lastTempReportMs >= TEMP_REPORT_INTERVAL_MS)) {
    lastTempReportMs = now;
    sensors.requestTemperatures();
    lastTempRequestMs = now;
    tempConversionPending = true;

    if (last_tempC != DEVICE_DISCONNECTED_C) {
      sendTempBLE(last_tempC);
    }

    Serial.print("Temp: ");
    if (last_tempC != DEVICE_DISCONNECTED_C) {
      Serial.print(last_tempC, 2);
      Serial.println(" C");
    } else {
      Serial.println("n/a");
    }
  }

  if (now - lastCardioReportMs >= CARDIO_REPORT_INTERVAL_MS) {
    lastCardioReportMs = now;
    sendCardioBLE(irValue, beatsPerMinute, beatAvg);

    Serial.print("Cardio: ");
    Serial.print(beatsPerMinute, 1);
    Serial.println(" bpm");
  }

  if (now - lastAccelReportMs >= ACCEL_REPORT_INTERVAL_MS) {
    float mean_accMag = count_accel > 0 ? (sum_accMag / count_accel) : last_accMagnitudeG;

    sum_axg = 0.0f;
    sum_ayg = 0.0f;
    sum_azg = 0.0f;
    sum_accMag = 0.0f;
    count_accel = 0;
    lastAccelReportMs = now;
    lastMeanUpdateMs = now;

    sendAccelBLE(mean_accMag);

    Serial.print("Accel: moyenne=");
    Serial.print(mean_accMag, 3);
    Serial.print(" g | pic=");
    Serial.print(peak_accMagnitudeG, 3);
    Serial.print(" g | impact=");
    Serial.print(impactDetected ? "OUI" : "non");
    Serial.print(" | delta pic=");
    Serial.print(peak_deltaMagnitudeG, 3);
    Serial.println(" g");

    peak_accMagnitudeG = 0.0f;
    peak_deltaMagnitudeG = 0.0f;
    impactDetected = false;
  }
}
