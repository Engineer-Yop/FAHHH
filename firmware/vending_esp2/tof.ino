// ─────────────────────────────────────────
//  tof.ino — TCA9548A MUX + 8x VL53L0X, one per tray (MUX CH0–CH7 = Tray 1–8)
//
//  Reads the distance (mm) from the sensor to the first item in each tray,
//  and turns it into "how many items can be there" (tof_window). The stock itself
//  is decided in stock.ino together with the vend count.
//
//  Measured on the machine (2026-11-25):
//    full (7 items)  -> 100–140 mm
//    each item taken -> distance grows by 90–120 mm
//    empty           -> 580–600 mm (the back of the tray)
//  So the sensor can tell 7, 6, 5, 4 apart, but 3, 2, 1 and 0 all read
//  near the back wall: there the vend count decides.
//
//  Self-repair: if the MUX stops answering (I2C bus stuck, power dip), the bus is
//  reset and the sensors are started again without restarting the ESP32.
// ─────────────────────────────────────────
#if USE_TOF
#include <VL53L0X.h>

VL53L0X tofSensors[8];
bool    tofOk[8] = {false};
bool    tofMuxOk = false;
int     tofBusErrors = 0;            // MUX writes that failed since the last repair
unsigned long tofLastRepair = 0;
int     tofRepairs = 0;

bool mux_select(uint8_t channel) {
  if (channel > 7) return false;
  Wire.beginTransmission(MUX_ADDR);
  Wire.write(1 << channel);
  bool ok = (Wire.endTransmission() == 0);
  if (!ok) tofBusErrors++;
  return ok;
}

void mux_closeAll() {
  Wire.beginTransmission(MUX_ADDR);
  Wire.write(0x00);
  Wire.endTransmission();
}

// PCA9685 answers at 0x70 ("All-Call") after power-up -> same address as the MUX.
// Turn that off (MODE1 bit 0) without touching anything else.
// Returns 1 if it was on, 0 if already off, -1 if the PCA9685 didn't answer.
int pca_disableAllCall() {
  Wire.beginTransmission(PCA9685_ADDR);
  Wire.write(0x00);                                  // MODE1 register
  if (Wire.endTransmission(false) != 0) return -1;
  if (Wire.requestFrom(PCA9685_ADDR, 1) != 1) return -1;
  uint8_t mode1 = Wire.read();
  if (!(mode1 & 0x01)) return 0;
  Wire.beginTransmission(PCA9685_ADDR);
  Wire.write(0x00);
  Wire.write(mode1 & ~0x01);
  Wire.endTransmission();
  delay(2);
  return 1;
}

// Start (or restart) every sensor behind the MUX
void tof_startSensors() {
  Wire.beginTransmission(MUX_ADDR);
  tofMuxOk = (Wire.endTransmission() == 0);
  if (!tofMuxOk) {
    for (int i = 0; i < 8; i++) tofOk[i] = false;
    Serial.println("[ToF] MUX not found at 0x" + String(MUX_ADDR, HEX) +
                   " — trays fall back to counted stock.");
    return;
  }
  for (int i = 0; i < 8; i++) {
    tofOk[i] = false;
    if (!mux_select(i)) continue;
    delay(10);
    tofSensors[i].setTimeout(500);
    tofOk[i] = tofSensors[i].init();
    if (tofOk[i]) tofSensors[i].startContinuous();
    Serial.println("[ToF] Sensor " + String(i + 1) + (tofOk[i] ? " OK" : " FAILED") +
                   " on CH" + String(i));
  }
  mux_closeAll();
  tofBusErrors = 0;
}

void tof_init() {
  pca_disableAllCall();   // never let the servo board answer at the MUX address
  tof_startSensors();
}

// Free a stuck I2C bus: a chip holding SDA low is released by up to 9 clock pulses
void i2c_busClear() {
  Wire.end();
  pinMode(21, INPUT_PULLUP);
  pinMode(22, OUTPUT_OPEN_DRAIN);
  for (int i = 0; i < 9 && digitalRead(21) == LOW; i++) {
    digitalWrite(22, LOW);  delayMicroseconds(10);
    digitalWrite(22, HIGH); delayMicroseconds(10);
  }
  pinMode(21, OUTPUT_OPEN_DRAIN);            // STOP condition: SDA low -> high while SCL high
  digitalWrite(21, LOW);  delayMicroseconds(10);
  digitalWrite(22, HIGH); delayMicroseconds(10);
  digitalWrite(21, HIGH); delayMicroseconds(10);
  Wire.begin(21, 22);
  Wire.setClock(I2C_CLOCK_HZ);
}

// Called from stock.ino: repair the bus when the MUX stops answering
void tof_checkBus() {
  bool broken = (!tofMuxOk || tofBusErrors >= 3);
  if (!broken || millis() - tofLastRepair < TOF_REPAIR_EVERY_MS) return;
  tofLastRepair = millis();
  tofRepairs++;
  Serial.println("[ToF] MUX/bus not answering -> resetting I2C bus (repair #" + String(tofRepairs) + ")");
  i2c_busClear();
  // If the servo board lost power it wakes up asleep with All-Call on: set it up again
  Wire.beginTransmission(PCA9685_ADDR);
  Wire.write(0x00);
  if (Wire.endTransmission(false) == 0 && Wire.requestFrom(PCA9685_ADDR, 1) == 1 && (Wire.read() & 0x10)) {
    Serial.println("[ToF] Servo board had reset -> setting it up again");
    servo_init();
  }
  pca_disableAllCall();
  tof_startSensors();
}

bool tof_sensorOk(int tray) {
  return tray >= 0 && tray < 8 && tofMuxOk && tofOk[tray];
}

// Median of n readings in mm. -1 = no usable reading (sensor or bus error).
int tof_readMedian(int tray, int n) {
  if (!tof_sensorOk(tray)) return -1;
  int v[9];
  int k = 0;
  if (n > 9) n = 9;
  if (!mux_select(tray)) return -1;
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

#else
bool tof_sensorOk(int tray)          { return false; }
int  tof_readMedian(int tray, int n) { return -1; }
void tof_checkBus()                  {}
#endif

// ── Distance -> possible stock (plain math) ──
// A reading is usable only if it lands inside the tray (the empty tray reads ~590 mm,
// so 8000 "nothing in range" or -1 means the sensor or the bus failed).
bool tof_valid(int d) {
  return d > 0 && d <= TOF_MAX_VALID_MM;
}

// Range of item counts that fit a distance d: *nLo..*nHi.
// n items put the first one (cap - n) positions back: between
//   FULL_MIN + (cap-n)*STEP_MIN  and  FULL_MAX + (cap-n)*STEP_MAX  mm,
// but never further than the back wall, where every low count reads the same.
void tof_window(int d, int cap, int* nLo, int* nHi) {
  *nLo = -1; *nHi = -1;
  int bestN = 0; long bestGap = 1000000;
  for (int n = 0; n <= cap; n++) {
    long lo = TOF_FULL_MIN_MM + (long)(cap - n) * TOF_STEP_MIN_MM;
    long hi = TOF_FULL_MAX_MM + (long)(cap - n) * TOF_STEP_MAX_MM;
    if (lo > TOF_WALL_MM) lo = TOF_WALL_MM;
    bool reachesWall = hi >= TOF_WALL_MM;
    bool fits = d >= lo - TOF_TOLERANCE_MM && (reachesWall || d <= hi + TOF_TOLERANCE_MM);
    if (fits) {
      if (*nLo < 0) *nLo = n;
      *nHi = n;
    }
    long gap = d < lo ? lo - d : (reachesWall || d <= hi) ? 0 : d - hi;
    if (gap < bestGap) { bestGap = gap; bestN = n; }
  }
  if (*nLo < 0) { *nLo = bestN; *nHi = bestN; }   // between two windows: nearest one
}

// The stock to show: the vend count if the sensor agrees with it, otherwise the
// nearest count the sensor allows. Full-tray reading = refilled = capacity.
int tof_fuse(int d, int count, int cap) {
  if (d <= TOF_FULL_MAX_MM + TOF_TOLERANCE_MM) return cap;
  int lo, hi;
  tof_window(d, cap, &lo, &hi);
  if (count < 0) return (lo == hi) ? lo : -1;      // count unknown and sensor not sure
  return constrain(count, lo, hi);
}
