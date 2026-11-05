// ─────────────────────────────────────────
//  tof.ino — PCA9548A MUX + 8x VL53L0X
//  MUX CH0–CH7 = Tray 1–8
//  Only compiled when USE_TOF is 1 (vending_esp2.ino)
// ─────────────────────────────────────────
#if USE_TOF
#include <VL53L0X.h>

VL53L0X tofSensors[8];
bool    tofOk[8] = {false};

void mux_select(uint8_t channel) {
  if (channel > 7) return;
  Wire.beginTransmission(MUX_ADDR);
  Wire.write(1 << channel);
  Wire.endTransmission();
}

void mux_closeAll() {
  Wire.beginTransmission(MUX_ADDR);
  Wire.write(0x00);
  Wire.endTransmission();
}

void tof_init() {
  Wire.beginTransmission(MUX_ADDR);
  if (Wire.endTransmission() != 0) {
    Serial.println("[ToF] MUX not found at 0x" + String(MUX_ADDR, HEX) + " — check MUX_ADDR");
    return;
  }
  for (int i = 0; i < 8; i++) {
    mux_select(i);
    delay(10);
    tofSensors[i].setTimeout(500);
    tofOk[i] = tofSensors[i].init();
    if (tofOk[i]) {
      tofSensors[i].startContinuous();
      Serial.println("[ToF] Sensor " + String(i + 1) + " OK on CH" + String(i));
    } else {
      Serial.println("[ToF] Sensor " + String(i + 1) + " FAILED on CH" + String(i));
    }
  }
  mux_closeAll();
}

// Distance in mm, or -1 if the sensor is missing / timed out
int tof_read(int tray) {
  if (tray < 0 || tray > 7 || !tofOk[tray]) return -1;
  mux_select(tray);
  delay(10);
  int distance = tofSensors[tray].readRangeContinuousMillimeters();
  bool timeout = tofSensors[tray].timeoutOccurred();
  mux_closeAll();
  return timeout ? -1 : distance;
}

void tof_readAll() {
  for (int i = 0; i < 8; i++) {
    Serial.println("[ToF] Tray " + String(i + 1) + ": " + String(tof_read(i)) + " mm");
  }
}
#endif
