/*
 * =======================================================================================
 *   Cold-Chain ESP32 Firmware — Phase 10 (Silicon ID, Multi-Vector Tamper, Anti-Rollback)
 * =======================================================================================
 */

#include <WiFi.h>
#include <PubSubClient.h>
#include <SPI.h>
#include <SD.h>
#include <Preferences.h>
#include <mbedtls/sha256.h>
#include <ArduinoJson.h>

// —⊀ 1. SHIPMENT BINDING & PHYSICAL IDENTITY ------------------------------------------------
String shipmentId = ""; // Dynamically loaded from NVS or prompted via Serial on boot

// —⊀ 2. NETWORK, GSM & SMS ------------------------------------------------------------------
const char* WIFI_SSID         = "ColdChain-Logistics-WiFi";
const char* WIFI_PASSWORD     = "SuperSecurePassword2026!";
const char* MQTT_BROKER       = "broker.hivemq.com";
const int   MQTT_PORT         = 1883;
const char* MQTT_TOPIC_PREFIX = "coldchain";
const char* ALERT_PHONE_NUMBER = "+919876543210"; // Configurable recipient mobile number for SMS alerts

// —⊀ 3. PINS & PERIPHERALS ------------------------------------------------------------------
#define SD_CS_PIN         5
#define TEMP_SENSOR_PIN   4
#define GAS_SENSOR_PIN    34
#define LED_SYNC_PIN      2
#define GSM_RX_PIN        16  // ESP32 RX2 -> SIM800L TX
#define GSM_TX_PIN        17  // ESP32 TX2 -> SIM800L RX

// Multi-Vector Physical Tamper Sensor Pins
#define PIN_DOOR_SWITCH   12  // Magnetic Reed Switch for Container Lid / Door
#define PIN_SHELL_CUT     13  // Continuous conductive trace for chassis/shell breach
#define PIN_LIGHT_SENSOR  35  // Photodiode/LDR for optical light breach inside container (Input only)
#define PIN_MANUAL_SWITCH 14  // Pushbutton/Microswitch for manual tamper trip

#define BATCH_SIZE             8
#define LOG_INTERVAL_MS        10000
#define SYNC_STATE_FILE        "/sync_state.txt"
#define CHAIN_LOG_FILE         "/chain.log"
#define GENESIS_HASH           "0000000000000000000000000000000000000000000000000000000000000000"

WiFiClient espClient;
PubSubClient mqttClient(espClient);
Preferences prefs;
HardwareSerial gsmSerial(2);

struct GeoLocation {
  float latitude;
  float longitude;
  bool valid;
};

GeoLocation currentGeoLoc = { 12.9716, 77.5946, true }; // Active transit coordinates

unsigned long lastSampleTime   = 0;
uint32_t currentRecordIndex    = 0;             // Recovered from /chain.log on boot
int32_t  lastSyncedIndex       = -1;            // Loaded from /sync_state.txt on boot
uint32_t deviceSequenceCounter = 0;             // Monotonically increasing NVS anti-rollback counter
String   previousHash          = GENESIS_HASH;  // Recovered from /chain.log on boot

// —⊀ 4. TAMPER STATUS & ISR LATCHES ----------------------------------------------------------
struct TamperStatus {
  bool doorOpened;
  bool sdRemoved;
  bool shellCut;
  bool lightBreach;
  bool manualSwitch;
  bool anyTamper;
};

// Volatile hardware latch flags (tripped asynchronously via GPIO interrupts)
volatile bool latchedDoorOpened   = false;
volatile bool latchedShellCut     = false;
volatile bool latchedManualSwitch = false;
volatile bool latchedLightBreach  = false;
volatile bool latchedSdRemoved    = false;

// Interrupt Service Routines (ISRs)
void IRAM_ATTR isrDoorSwitch() {
  if (digitalRead(PIN_DOOR_SWITCH) == HIGH) {
    latchedDoorOpened = true;
  }
}

void IRAM_ATTR isrShellCut() {
  if (digitalRead(PIN_SHELL_CUT) == HIGH) {
    latchedShellCut = true;
  }
}

void IRAM_ATTR isrManualSwitch() {
  if (digitalRead(PIN_MANUAL_SWITCH) == HIGH) {
    latchedManualSwitch = true;
  }
}

/**
 * Reads the factory hardware Silicon MAC address from eFuse.
 */
String getDeviceSiliconID() {
  uint64_t chipid = ESP.getEfuseMac();
  char buf[32];
  sprintf(buf, "ESP32-%04X%08X", (uint16_t)(chipid >> 32), (uint32_t)chipid);
  return String(buf);
}

/**
 * Sample all 5 physical tamper vectors combining real-time pin levels and latched edge events.
 */
TamperStatus readTamperSensors() {
  TamperStatus status;
  // Edge detection: combine current pin states with interrupt-latched flags
  status.doorOpened   = (digitalRead(PIN_DOOR_SWITCH) == HIGH) || latchedDoorOpened;
  status.shellCut     = (digitalRead(PIN_SHELL_CUT) == HIGH) || latchedShellCut;
  status.lightBreach  = (analogRead(PIN_LIGHT_SENSOR) > 1500) || latchedLightBreach;
  status.manualSwitch = (digitalRead(PIN_MANUAL_SWITCH) == HIGH) || latchedManualSwitch;
  status.sdRemoved    = (!SD.exists("/chain.log")) || latchedSdRemoved;
  status.anyTamper    = status.doorOpened || status.shellCut || status.lightBreach || status.manualSwitch || status.sdRemoved;
  return status;
}

/**
 * Clears volatile latch flags after a sample record has captured the tamper event.
 */
void clearTamperLatches() {
  latchedDoorOpened   = false;
  latchedShellCut     = false;
  latchedLightBreach  = false;
  latchedManualSwitch = false;
  latchedSdRemoved    = false;
}

/**
 * Formats tamper flags as a canonical JSON string matching backend key order:
 * {"doorOpened":...,"sdRemoved":...,"shellCut":...,"lightBreach":...,"manualSwitch":...,"anyTamper":...}
 */
String serializeTamperFlags(const TamperStatus& s) {
  StaticJsonDocument<256> doc;
  doc["doorOpened"]   = s.doorOpened;
  doc["sdRemoved"]    = s.sdRemoved;
  doc["shellCut"]     = s.shellCut;
  doc["lightBreach"]  = s.lightBreach;
  doc["manualSwitch"] = s.manualSwitch;
  doc["anyTamper"]    = s.anyTamper;
  String str;
  serializeJson(doc, str);
  return str;
}

/**
 * Helper to format float values identically to JavaScript Number.toString():
 * Trims trailing zeros and trailing decimal point (e.g. 5.00 -> "5", 5.20 -> "5.2", 5.25 -> "5.25").
 */
String formatNumber(float val) {
  if (val == (long)val) {
    return String((long)val);
  }
  char buf[32];
  snprintf(buf, sizeof(buf), "%.2f", val);
  int len = strlen(buf);
  while (len > 0 && buf[len - 1] == '0') {
    buf[--len] = '\0';
  }
  if (len > 0 && buf[len - 1] == '.') {
    buf[--len] = '\0';
  }
  return String(buf);
}

/**
 * Phase 10 Canonical Cryptographic Record Hash
 * `${index}|${timestamp}|${temp}|${humidity}|${gas}|${tamperFlags}|${deviceSiliconID}|${previousHash}`
 */
String computeRecordHash(uint32_t index, const String& timestamp, float temp, float humidity, float gas, const String& tamperFlags, const String& siliconId, const String& prevHash) {
  String payload = String(index) + "|" + timestamp + "|" + formatNumber(temp) + "|" + formatNumber(humidity) + "|" + formatNumber(gas) + "|" + tamperFlags + "|" + siliconId + "|" + prevHash;
  byte shaResult[32];
  mbedtls_sha256_context ctx;
  mbedtls_sha256_init(&ctx);
  mbedtls_sha256_starts(&ctx, 0);
  mbedtls_sha256_update(&ctx, (const unsigned char*)payload.c_str(), payload.length());
  mbedtls_sha256_finish(&ctx, shaResult);
  mbedtls_sha256_free(&ctx);
  char hexBuf[65];
  for (int i = 0; i < 32; i++) { sprintf(&hexBuf[i * 2], "%02x", shaResult[i]); }
  hexBuf[64] = '\0';
  return String(hexBuf);
}

void savePersistentSyncState(int32_t syncedIndex) {
  File f = SD.open(SYNC_STATE_FILE, FILE_WRITE);
  if (f) {
    f.println(syncedIndex);
    f.flush();
    f.close();
    lastSyncedIndex = syncedIndex;
    Serial.printf("[SD] Persistent sync state saved: lastSyncedIndex = %d\n", lastSyncedIndex);
  }
}

void loadPersistentSyncState() {
  if (!SD.exists(SYNC_STATE_FILE)) {
    savePersistentSyncState(-1);
    lastSyncedIndex = -1;
    return;
  }
  File f = SD.open(SYNC_STATE_FILE, FILE_READ);
  if (f) {
    String content = f.readStringUntil('\n');
    content.trim();
    lastSyncedIndex = content.toInt();
    f.close();
    Serial.printf("[SD] Persistent sync state loaded: lastSyncedIndex = %d\n", lastSyncedIndex);
  }
}

void recoverChainStateFromLog() {
  if (!SD.exists(CHAIN_LOG_FILE)) {
    Serial.println(F("[SD] /chain.log does not exist. Initializing fresh genesis state."));
    currentRecordIndex = 0;
    previousHash = GENESIS_HASH;
    return;
  }

  File logFile = SD.open(CHAIN_LOG_FILE, FILE_READ);
  if (!logFile) {
    Serial.println(F("[SD] ❗ Failed to open /chain.log for state recovery."));
    return;
  }

  String lastLine = "";
  while (logFile.available()) {
    String line = logFile.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) lastLine = line;
  }
  logFile.close();

  if (lastLine.length() == 0) {
    currentRecordIndex = 0;
    previousHash = GENESIS_HASH;
    return;
  }

  int p0 = lastLine.indexOf(',');
  int p1 = lastLine.indexOf(',', p0 + 1);
  int p2 = lastLine.indexOf(',', p1 + 1);
  int p3 = lastLine.indexOf(',', p2 + 1);
  int p4 = lastLine.indexOf(',', p3 + 1);
  int p5 = lastLine.indexOf(',', p4 + 1);
  int p6 = lastLine.indexOf(',', p5 + 1);
  int p7 = lastLine.indexOf(',', p6 + 1);
  int p8 = lastLine.indexOf(',', p7 + 1);

  if (p0 > 0 && p7 > 0 && p8 > 0) {
    uint32_t lastIndex = lastLine.substring(0, p0).toInt();
    String lastHash    = lastLine.substring(p7 + 1, p8);
    currentRecordIndex = lastIndex + 1;
    previousHash       = lastHash;
    Serial.printf("[SD] ✓ Recovered chain state: NextIndex=%u, PrevHash=%s\n", currentRecordIndex, previousHash.c_str());
  } else {
    currentRecordIndex = 0;
    previousHash = GENESIS_HASH;
  }
}

void appendRecordToLog(uint32_t index, uint32_t seq, const String& siliconId, const String& timestamp, float temp, float humidity, float gas, const String& tamperFlags, const String& prevHash, const String& hash) {
  File logFile = SD.open(CHAIN_LOG_FILE, FILE_APPEND);
  if (logFile) {
    logFile.printf("%u,%u,%s,%s,%.2f,%.2f,%.2f,%s,%s,%s,0\n",
                   index, seq, siliconId.c_str(), timestamp.c_str(), temp, humidity, gas, tamperFlags.c_str(), prevHash.c_str(), hash.c_str());
    logFile.flush();
    logFile.close();
  }
}

void sendSmsAlert(const String& message) {
  if (strlen(ALERT_PHONE_NUMBER) == 0 || String(ALERT_PHONE_NUMBER).startsWith("+000")) {
    Serial.println(F("[SMS] Alert phone number not configured. Skipping SMS."));
    return;
  }
  Serial.printf("[SMS] 🚨 Sending SMS alert to %s: %s\n", ALERT_PHONE_NUMBER, message.c_str());
  
  gsmSerial.println("AT+CMGF=1"); // Set SMS text mode
  delay(150);
  gsmSerial.printf("AT+CMGS=\"%s\"\r\n", ALERT_PHONE_NUMBER);
  delay(150);
  gsmSerial.print(message);
  gsmSerial.write(26); // ASCII 26 (Ctrl+Z) terminates SMS body and dispatches
  delay(500);
  Serial.println(F("[SMS] ✓ AT+CMGS dispatch sequence initiated."));
}

/**
 * Queries GSM base-station cell tower coordinates using AT+CIPGSMLOC=1,1
 * Expected reply on success: +CIPGSMLOC: 0,<longitude>,<latitude>,<date>,<time>
 * On success (code == 0): updates currentGeoLoc and sets valid = true.
 * On failure/timeout/error: returns last-known currentGeoLoc (does not fabricate coordinates).
 */
GeoLocation getCellTowerLocation() {
  Serial.println(F("[GSM] Executing AT+CIPGSMLOC=1,1 for cell tower coordinates..."));
  
  gsmSerial.println("AT+CIPGSMLOC=1,1");
  unsigned long start = millis();
  String response = "";
  while (millis() - start < 3000) {
    if (gsmSerial.available()) {
      char c = gsmSerial.read();
      response += c;
    }
  }

  int idx = response.indexOf("+CIPGSMLOC:");
  if (idx >= 0) {
    String payload = response.substring(idx + 11);
    payload.trim();
    int c1 = payload.indexOf(',');
    if (c1 > 0) {
      int code = payload.substring(0, c1).toInt();
      if (code == 0) { // Success code 0
        int c2 = payload.indexOf(',', c1 + 1);
        int c3 = payload.indexOf(',', c2 + 1);
        if (c2 > 0 && c3 > 0) {
          GeoLocation loc;
          loc.longitude = payload.substring(c1 + 1, c2).toFloat();
          loc.latitude  = payload.substring(c2 + 1, c3).toFloat();
          loc.valid     = true;
          Serial.printf("[GSM] ✓ AT+CIPGSMLOC Success: Lat=%.6f, Lon=%.6f\n", loc.latitude, loc.longitude);
          currentGeoLoc = loc;
          return currentGeoLoc;
        }
      } else {
        Serial.printf("[GSM] ⚠ AT+CIPGSMLOC error code %d. Retaining last-known coordinates.\n", code);
      }
    }
  } else {
    Serial.println(F("[GSM] ⚠ No +CIPGSMLOC response. Retaining last-known coordinates."));
  }

  return currentGeoLoc;
}

bool publishUnsyncedBatch() {
  if (!mqttClient.connected()) return false;
  File logFile = SD.open(CHAIN_LOG_FILE, FILE_READ);
  if (!logFile) return false;

  StaticJsonDocument<8192> doc;
  doc["shipmentId"]      = shipmentId;
  doc["deviceSiliconID"] = getDeviceSiliconID();
  JsonArray records = doc.createNestedArray("records");

  int32_t highestIndexInBatch = lastSyncedIndex;
  uint32_t countInBatch = 0;

  while (logFile.available() && countInBatch < BATCH_SIZE) {
    String line = logFile.readStringUntil('\n');
    line.trim();
    if (line.length() == 0) continue;

    int p0 = line.indexOf(',');
    int p1 = line.indexOf(',', p0 + 1);
    int p2 = line.indexOf(',', p1 + 1);
    int p3 = line.indexOf(',', p2 + 1);
    int p4 = line.indexOf(',', p3 + 1);
    int p5 = line.indexOf(',', p4 + 1);
    int p6 = line.indexOf(',', p5 + 1);
    int p7 = line.indexOf(',', p6 + 1);
    int p8 = line.indexOf(',', p7 + 1);
    int p9 = line.indexOf(',', p8 + 1);

    int32_t recIdx = line.substring(0, p0).toInt();
    if (recIdx <= lastSyncedIndex) continue;

    JsonObject r = records.createNestedObject();
    r["shipmentId"]          = shipmentId;
    r["deviceSiliconID"]     = line.substring(p1 + 1, p2);
    r["index"]               = recIdx;
    r["deviceSequenceIndex"] = line.substring(p0 + 1, p1).toInt();
    r["timestamp"]           = line.substring(p2 + 1, p3);
    r["temp"]                = line.substring(p3 + 1, p4).toFloat();
    r["humidity"]            = line.substring(p4 + 1, p5).toFloat();
    r["gas"]                 = line.substring(p5 + 1, p6).toFloat();
    r["tamperFlags"]         = line.substring(p6 + 1, p7);
    r["latitude"]            = currentGeoLoc.latitude;
    r["longitude"]           = currentGeoLoc.longitude;
    r["previousHash"]        = line.substring(p7 + 1, p8);
    r["hash"]                = line.substring(p8 + 1, p9);

    highestIndexInBatch = recIdx;
    countInBatch++;
  }
  logFile.close();

  if (countInBatch == 0) return true;

  String payload;
  serializeJson(doc, payload);

  String topic = String(MQTT_TOPIC_PREFIX) + "/" + shipmentId + "/batch";
  if (mqttClient.publish(topic.c_str(), payload.c_str(), true)) {
    savePersistentSyncState(highestIndexInBatch);
    digitalWrite(LED_SYNC_PIN, HIGH); delay(100); digitalWrite(LED_SYNC_PIN, LOW);
    return true;
  } else {
    return false;
  }
}

/**
 * Loads the active Shipment ID from NVS, or prompts via Serial Monitor if none is configured.
 * Allows typing 'RESET' within a 3-second startup window to reconfigure.
 */
void loadOrPromptShipmentId() {
  shipmentId = prefs.getString("shipment_id", "");
  shipmentId.trim();

  if (shipmentId.length() > 0) {
    Serial.printf("[NVS] Loaded active Shipment ID: %s\n", shipmentId.c_str());
    Serial.println(F("[SETUP] Type 'RESET' within 3 seconds to clear and change Shipment ID..."));
    
    unsigned long start = millis();
    while (millis() - start < 3000) {
      if (Serial.available()) {
        String input = Serial.readStringUntil('\n');
        input.trim();
        if (input.equalsIgnoreCase("RESET")) {
          Serial.println(F("[NVS] RESET command received. Clearing stored Shipment ID."));
          prefs.remove("shipment_id");
          shipmentId = "";
          break;
        }
      }
      delay(10);
    }
  }

  while (shipmentId.length() == 0) {
    Serial.println(F("\n======================================================="));
    Serial.println(F("  [CONFIG REQUIRED] No Shipment ID configured in NVS."));
    Serial.println(F("  Please enter Shipment ID (e.g. SHIP-MTOIMJJ0-91EA60):"));
    Serial.println(F("======================================================="));

    while (!Serial.available()) {
      delay(100);
    }

    String input = Serial.readStringUntil('\n');
    input.trim();
    if (input.length() > 0) {
      shipmentId = input;
      prefs.putString("shipment_id", shipmentId);
      Serial.printf("[NVS] ✓ Saved new Shipment ID: %s\n\n", shipmentId.c_str());
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  pinMode(LED_SYNC_PIN, OUTPUT);
  pinMode(PIN_DOOR_SWITCH, INPUT_PULLUP);
  pinMode(PIN_SHELL_CUT, INPUT_PULLUP);
  pinMode(PIN_MANUAL_SWITCH, INPUT_PULLDOWN);

  // Initialize GSM UART for SIM800L SMS & Cell Tower Location
  gsmSerial.begin(9600, SERIAL_8N1, GSM_RX_PIN, GSM_TX_PIN);

  // Attach hardware interrupts for zero-latency tamper edge detection
  attachInterrupt(digitalPinToInterrupt(PIN_DOOR_SWITCH), isrDoorSwitch, CHANGE);
  attachInterrupt(digitalPinToInterrupt(PIN_SHELL_CUT), isrShellCut, CHANGE);
  attachInterrupt(digitalPinToInterrupt(PIN_MANUAL_SWITCH), isrManualSwitch, RISING);

  // Initialize NVS namespace
  prefs.begin("coldchain", false);
  deviceSequenceCounter = prefs.getUInt("seq_counter", 0);
  Serial.printf("[NVS] Anti-Rollback Sequence Counter loaded: #%u\n", deviceSequenceCounter);

  // Load or prompt for Shipment ID
  loadOrPromptShipmentId();

  if (!SD.begin(SD_CS_PIN)) {
    Serial.println(F("[SD] SD card initialization failed."));
    while (true) { delay(1000); }
  }

  loadPersistentSyncState();
  recoverChainStateFromLog();

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
}

void loop() {
  // Check for asynchronous RESET command via Serial during runtime
  if (Serial.available()) {
    String cmd = Serial.readStringUntil('\n');
    cmd.trim();
    if (cmd.equalsIgnoreCase("RESET")) {
      Serial.println(F("[NVS] Runtime RESET requested. Clearing Shipment ID and restarting..."));
      prefs.remove("shipment_id");
      ESP.restart();
    }
  }

  // Continuous background checks for non-interrupt tamper sources
  if (analogRead(PIN_LIGHT_SENSOR) > 1500) {
    latchedLightBreach = true;
  }
  if (!SD.exists("/chain.log")) {
    latchedSdRemoved = true;
  }

  if (WiFi.status() == WL_CONNECTED && !mqttClient.connected()) {
    String clientId = "ESP32-ColdChain-" + shipmentId;
    mqttClient.connect(clientId.c_str());
  }
  mqttClient.loop();

  unsigned long now = millis();
  if (now - lastSampleTime >= LOG_INTERVAL_MS) {
    lastSampleTime = now;
    uint32_t indexToLog = currentRecordIndex++;

    // Increment and persist NVS anti-rollback monotonic counter
    deviceSequenceCounter++;
    prefs.putUInt("seq_counter", deviceSequenceCounter);

    float temp = 4.5 + (random(0, 200) / 100.0);
    float hum  = 65.0 + (random(0, 150) / 10.0);
    float gas  = 320.0 + random(0, 50);
    String timestamp = "2026-09-07T12:00:00Z";
    String siliconId = getDeviceSiliconID();

    // Read combined real-time and interrupt-latched tamper sensors
    TamperStatus tamper = readTamperSensors();
    String tamperFlagsStr = serializeTamperFlags(tamper);

    // Clear latches after capturing into current record
    clearTamperLatches();

    // Cold-chain excursions (<2.0C or >8.0C) or physical tamper trigger SMS + Geo-Location
    bool tempExcursion = (temp < 2.0f || temp > 8.0f);
    if (tamper.anyTamper || tempExcursion) {
      getCellTowerLocation();
      String alertMsg = String("[FrostLedger Alert] Shipment: ") + shipmentId + "\n";
      if (tamper.anyTamper) {
        alertMsg += "BREACH: Physical tamper vector tripped!\n";
      }
      if (tempExcursion) {
        alertMsg += "TEMP VIOLATION: " + formatNumber(temp) + "C (Safe: 2-8C)\n";
      }
      alertMsg += "Loc: " + String(currentGeoLoc.latitude, 4) + "," + String(currentGeoLoc.longitude, 4);
      sendSmsAlert(alertMsg);
    }

    // Compute cryptographic record hash (lat/lon excluded to preserve hash chain parity)
    String hash = computeRecordHash(indexToLog, timestamp, temp, hum, gas, tamperFlagsStr, siliconId, previousHash);
    appendRecordToLog(indexToLog, deviceSequenceCounter, siliconId, timestamp, temp, hum, gas, tamperFlagsStr, previousHash, hash);
    previousHash = hash;

    if (currentRecordIndex - (uint32_t)(lastSyncedIndex < 0 ? 0 : lastSyncedIndex) >= BATCH_SIZE || tamper.anyTamper) {
      publishUnsyncedBatch();
    }
  }
}
