// ─────────────────────────────────────────
//  tof.ino — PCA9548A MUX + 8x VL53L0X, one per tray
//  MUX CH0–CH7 = Tray 1–8
//
//  Calibration (two points per tray, saved in flash by the tof_test sketch):
//    EMPTY mm = distance with 0 items,  FULL mm = distance with TRAY_CAPACITY items
//    stock    = round((EMPTY - distance) / ((EMPTY - FULL) / TRAY_CAPACITY)), kept in 0..capacity
//  Works whichever way the sensor faces (distance going up or down as items are added).
//  A tray without a valid calibration is left to ESP32 #1's sale counting.
// ─────────────────────────────────────────
#if USE_TOF
#include <VL53L0X.h>
#include <Preferences.h>

VL53L0X tofSensors[8];
bool    tofOk[8] = {false};
int     tofEmptyMm[8] = {0};
int     tofFullMm[8]  = {0};

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

// PCA9685 answers at 0x70 ("All-Call") after power-up -> same address as the MUX.
// Turn that off (MODE1 bit 0) without touching anything else. Returns 1 if it was on.
int pca_disableAllCall() {
  Wire.beginTransmission(0x40);
  Wire.write(0x00);                                  // MODE1 register
  if (Wire.endTransmission(false) != 0) return -1;   // no PCA9685 on the bus
  if (Wire.requestFrom(0x40, 1) != 1) return -1;
  uint8_t mode1 = Wire.read();
  if (!(mode1 & 0x01)) return 0;
  Wire.beginTransmission(0x40);
  Wire.write(0x00);
  Wire.write(mode1 & ~0x01);
  Wire.endTransmission();
  delay(2);
  return 1;
}

void tof_init() {
  pca_disableAllCall();   // never let the servo board answer at the MUX address
  Wire.beginTransmission(MUX_ADDR);
  if (Wire.endTransmission() != 0) {
    Serial.println("[ToF] MUX not found at 0x" + String(MUX_ADDR, HEX) +
                   " — all trays fall back to counted stock.");
    return;
  }
  for (int i = 0; i < 8; i++) {
    mux_select(i);
    delay(10);
    tofSensors[i].setTimeout(500);
    tofOk[i] = tofSensors[i].init();
    if (tofOk[i]) tofSensors[i].startContinuous();
    Serial.println("[ToF] Sensor " + String(i + 1) + (tofOk[i] ? " OK" : " FAILED") +
                   " on CH" + String(i));
  }
  mux_closeAll();
  tof_loadCalibration();
}

// Flash values (from tof_test) win; the arrays in vending_esp2.ino are the backup
void tof_loadCalibration() {
  Preferences p;
  p.begin("tofcal", true);
  for (int i = 0; i < 8; i++) {
    tofEmptyMm[i] = p.getInt(("e" + String(i)).c_str(), TOF_EMPTY_MM[i]);
    tofFullMm[i]  = p.getInt(("f" + String(i)).c_str(), TOF_FULL_MM[i]);
  }
  p.end();
  for (int i = 0; i < 8; i++) {
    Serial.println("[ToF] Tray " + String(i + 1) + ": " +
                   (tof_isCalibrated(i)
                      ? "empty " + String(tofEmptyMm[i]) + " mm, full " + String(tofFullMm[i]) + " mm -> stock from sensor"
                      : String("not calibrated -> stock counted by ESP32 #1")));
  }
}

bool tof_isCalibrated(int tray) {
  if (tray < 0 || tray > 7 || !tofOk[tray]) return false;
  int e = tofEmptyMm[tray], f = tofFullMm[tray];
  return e > 0 && f > 0 && abs(e - f) >= TOF_MIN_SPAN_MM;
}

// Median of n readings in mm. -1 = sensor error, TOF_OUT_OF_RANGE = sees nothing.
int tof_readMedian(int tray, int n) {
  if (tray < 0 || tray > 7 || !tofOk[tray]) return -1;
  int v[9];
  int k = 0;
  if (n > 9) n = 9;
  mux_select(tray);
  delay(2);
  for (int i = 0; i < n; i++) {
    int d = tofSensors[tray].readRangeContinuousMillimeters();
    if (tofSensors[tray].timeoutOccurred()) continue;
    if (d >= TOF_OUT_OF_RANGE) d = TOF_OUT_OF_RANGE;
    v[k++] = d;
  }
  mux_closeAll();
  if (k < (n + 1) / 2) return -1;      // more than half timed out
  for (int i = 1; i < k; i++) {        // sort, then take the middle value
    int x = v[i], j = i - 1;
    while (j >= 0 && v[j] > x) { v[j + 1] = v[j]; j--; }
    v[j + 1] = x;
  }
  return v[k / 2];
}

// Items in the tray for a distance, or -1 if it can't be worked out
int tof_stockFromDistance(int tray, int d) {
  if (!tof_isCalibrated(tray) || d < 0) return -1;
  int e = tofEmptyMm[tray], f = tofFullMm[tray];
  if (d >= TOF_OUT_OF_RANGE) return (e > f) ? 0 : -1;   // sees nothing: empty only if "empty" is the far end
  float perItem = (float)(e - f) / TRAY_CAPACITY;
  int n = (int)lroundf((e - d) / perItem);
  return constrain(n, 0, TRAY_CAPACITY);
}

#else
// ToF switched off: every tray is counted by ESP32 #1
bool tof_isCalibrated(int tray)           { return false; }
int  tof_readMedian(int tray, int n)       { return -1; }
int  tof_stockFromDistance(int tray, int d) { return -1; }
#endif
