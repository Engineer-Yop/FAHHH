// ─────────────────────────────────────────
//  tof_test.ino — ToF sensor TEST + CALIBRATION for ESP32 #2
//
//  Upload this to ESP32 #2 FIRST (it replaces the vending sketch for a while).
//  Serial Monitor: 115200 baud, line ending "Newline".
//
//  1. Checks the I2C bus (PCA9685 0x40, MUX 0x70) and every sensor (CH0–CH7).
//  2. Shows live distances for all 8 trays.
//  3. Calibrates each tray with two measurements:
//       EMPTY = distance when the tray has 0 items
//       FULL  = distance when the tray holds TRAY_CAPACITY items
//     and saves them to flash. The vending sketch (vending_esp2) reads them
//     from flash automatically, so nothing has to be copied by hand.
//
//  Commands (type, then Enter):
//    h      help
//    l      live readings on / off (every second)
//    r      one reading of all trays
//    w3     watch tray 3 fast for 10 s (for aiming the sensor)
//    e3     record EMPTY for tray 3      ea  = all trays at once
//    f3     record FULL  for tray 3      fa  = all trays at once
//    c      show calibration + check it
//    s      save calibration to flash
//    x      clear calibration in flash
//
//  Libraries: VL53L0X by Pololu, Preferences (built in)
// ─────────────────────────────────────────
#include <Wire.h>
#include <VL53L0X.h>
#include <Preferences.h>

#define SDA_PIN          21
#define SCL_PIN          22
#define MUX_ADDR         0x70   // A0–A2 to GND
#define PCA9685_ADDR     0x40
#define TOF_ADDR         0x29   // every VL53L0X uses this, that's why the MUX is needed
#define TRAY_COUNT       8
#define TRAY_CAPACITY    7      // items in a full tray (same as ESP32 #1)

#define OUT_OF_RANGE     8000   // the library gives ~8190 when nothing is in range
#define MIN_SPAN_MM      20     // FULL and EMPTY must differ by at least this much
#define CAL_SAMPLES      21     // readings per calibration point (median is used)

VL53L0X sensor[TRAY_COUNT];
bool    found[TRAY_COUNT] = {false};
bool    ok[TRAY_COUNT]    = {false};
int     calEmpty[TRAY_COUNT] = {0};
int     calFull[TRAY_COUNT]  = {0};

bool    live = true;
unsigned long lastLive = 0;

// ── MUX ───────────────────────────────────
void muxSelect(int ch) {
  Wire.beginTransmission(MUX_ADDR);
  Wire.write(1 << ch);
  Wire.endTransmission();
}

void muxOff() {
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

bool i2cPresent(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

// ── Readings ──────────────────────────────
void sortInts(int *v, int n) {
  for (int i = 1; i < n; i++) {
    int x = v[i], j = i - 1;
    while (j >= 0 && v[j] > x) { v[j + 1] = v[j]; j--; }
    v[j + 1] = x;
  }
}

// Median of n readings in mm. -1 = sensor error. OUT_OF_RANGE = sees nothing.
int readMedian(int t, int n) {
  if (!ok[t]) return -1;
  int v[CAL_SAMPLES];
  int k = 0;
  if (n > CAL_SAMPLES) n = CAL_SAMPLES;
  muxSelect(t);
  delay(2);
  for (int i = 0; i < n; i++) {
    int d = sensor[t].readRangeContinuousMillimeters();
    if (sensor[t].timeoutOccurred()) continue;
    if (d >= OUT_OF_RANGE) d = OUT_OF_RANGE;
    v[k++] = d;
  }
  muxOff();
  if (k < (n + 1) / 2) return -1;      // more than half timed out
  sortInts(v, k);
  return v[k / 2];
}

bool calibrated(int t) {
  return ok[t] && calEmpty[t] > 0 && calFull[t] > 0 && abs(calEmpty[t] - calFull[t]) >= MIN_SPAN_MM;
}

// Same formula as the vending sketch
int stockFromDistance(int t, int d) {
  if (!calibrated(t) || d < 0) return -1;
  int e = calEmpty[t], f = calFull[t];
  if (d >= OUT_OF_RANGE) return (e > f) ? 0 : -1;
  float perItem = (float)(e - f) / TRAY_CAPACITY;
  int n = (int)lroundf((e - d) / perItem);
  return constrain(n, 0, TRAY_CAPACITY);
}

String mmText(int d) {
  if (d < 0) return "ERR";
  if (d >= OUT_OF_RANGE) return "far";
  return String(d);
}

// ── Setup checks ──────────────────────────
void scanBus() {
  muxOff();
  Serial.println("\n[1] I2C bus scan (SDA 21, SCL 22):");
  int n = 0;
  for (uint8_t a = 1; a < 127; a++) {
    if (!i2cPresent(a)) continue;
    n++;
    String what = (a == MUX_ADDR) ? "  <- PCA9548A MUX (good)"
                : (a == PCA9685_ADDR) ? "  <- PCA9685 servo board (good)"
                : (a > 0x70 && a <= 0x77) ? "  <- a MUX on the wrong address: tie A0, A1, A2 to GND"
                : (a == TOF_ADDR) ? "  <- a VL53L0X wired straight to the bus: it must go through the MUX"
                : "";
    Serial.println("    0x" + String(a, HEX) + what);
  }
  if (n == 0) Serial.println("    nothing found: check SDA/SCL, 3.3V and GND");
  if (!i2cPresent(MUX_ADDR)) Serial.println("    !! MUX not found at 0x70 — sensors can't be tested.");
}

void initSensors() {
  Serial.println("\n[2] Sensors behind the MUX:");
  for (int t = 0; t < TRAY_COUNT; t++) {
    muxSelect(t);
    delay(5);
    found[t] = i2cPresent(TOF_ADDR);
    ok[t] = false;
    if (found[t]) {
      sensor[t].setTimeout(500);
      ok[t] = sensor[t].init();
      if (ok[t]) sensor[t].startContinuous();
    }
    Serial.print("    CH" + String(t) + "  Tray " + String(t + 1) + ": ");
    if (!found[t])      Serial.println("NOT FOUND  (check SD" + String(t) + "/SC" + String(t) + ", 3.3V, GND)");
    else if (!ok[t])    Serial.println("found but init FAILED (power or a loose wire)");
    else                Serial.println("OK");
  }
  muxOff();
}

// ── Calibration storage ───────────────────
void loadCal() {
  Preferences p;
  p.begin("tofcal", true);
  for (int t = 0; t < TRAY_COUNT; t++) {
    calEmpty[t] = p.getInt(("e" + String(t)).c_str(), 0);
    calFull[t]  = p.getInt(("f" + String(t)).c_str(), 0);
  }
  p.end();
}

void saveCal() {
  Preferences p;
  p.begin("tofcal", false);
  for (int t = 0; t < TRAY_COUNT; t++) {
    p.putInt(("e" + String(t)).c_str(), calEmpty[t]);
    p.putInt(("f" + String(t)).c_str(), calFull[t]);
  }
  p.end();
  Serial.println("Saved to flash. The vending sketch will use it after you upload it.");
}

void clearCal() {
  Preferences p;
  p.begin("tofcal", false);
  p.clear();
  p.end();
  for (int t = 0; t < TRAY_COUNT; t++) { calEmpty[t] = 0; calFull[t] = 0; }
  Serial.println("Calibration cleared (flash and memory).");
}

void showCal() {
  Serial.println("\nTray  EMPTY mm  FULL mm  mm/item  status");
  for (int t = 0; t < TRAY_COUNT; t++) {
    int e = calEmpty[t], f = calFull[t];
    String status;
    if (!ok[t])                     status = "sensor not working";
    else if (e == 0 && f == 0)      status = "not calibrated (counted by ESP32 #1)";
    else if (e == 0)                status = "needs EMPTY (e" + String(t + 1) + ")";
    else if (f == 0)                status = "needs FULL (f" + String(t + 1) + ")";
    else if (abs(e - f) < MIN_SPAN_MM) status = "TOO SMALL a difference: re-aim the sensor";
    else                            status = "OK";
    float per = (e > 0 && f > 0) ? (float)(e - f) / TRAY_CAPACITY : 0;
    Serial.println("  " + String(t + 1) + "    " + String(e) + "       " + String(f) +
                   "      " + String(per, 1) + "    " + status);
  }
  Serial.println("\nBackup copy for vending_esp2.ino (only needed if flash is erased):");
  String eLine = "const int TOF_EMPTY_MM[8] = {";
  String fLine = "const int TOF_FULL_MM[8]  = {";
  for (int t = 0; t < TRAY_COUNT; t++) {
    eLine += String(calEmpty[t]) + (t < 7 ? ", " : "};");
    fLine += String(calFull[t])  + (t < 7 ? ", " : "};");
  }
  Serial.println(eLine);
  Serial.println(fLine);
}

void record(bool empty, int t) {
  if (!ok[t]) { Serial.println("Tray " + String(t + 1) + ": sensor not working, skipped."); return; }
  int d = readMedian(t, CAL_SAMPLES);
  if (d < 0 || d >= OUT_OF_RANGE) {
    Serial.println("Tray " + String(t + 1) + ": no valid reading (" + mmText(d) + "), not recorded.");
    return;
  }
  if (empty) calEmpty[t] = d; else calFull[t] = d;
  Serial.println("Tray " + String(t + 1) + (empty ? " EMPTY = " : " FULL = ") + String(d) + " mm");
}

// ── Output ────────────────────────────────
void printRow() {
  String line = "";
  for (int t = 0; t < TRAY_COUNT; t++) {
    int d = readMedian(t, 5);
    line += "T" + String(t + 1) + ":" + mmText(d);
    int s = stockFromDistance(t, d);
    if (s >= 0) line += "(" + String(s) + ")";
    line += "  ";
  }
  Serial.println(line);
}

void watch(int t) {
  if (!ok[t]) { Serial.println("Tray " + String(t + 1) + ": sensor not working."); return; }
  Serial.println("Watching tray " + String(t + 1) + " for 10 s (min / max shows the noise):");
  int mn = 99999, mx = -1;
  unsigned long start = millis();
  while (millis() - start < 10000) {
    int d = readMedian(t, 1);
    if (d >= 0 && d < OUT_OF_RANGE) { mn = min(mn, d); mx = max(mx, d); }
    Serial.println("  " + mmText(d) + " mm");
    delay(100);
  }
  if (mx >= 0) Serial.println("Range: " + String(mn) + "–" + String(mx) + " mm (noise " + String(mx - mn) + " mm)");
}

void help() {
  Serial.println("\nCommands: h help | l live on/off | r read once | w# watch tray # |");
  Serial.println("          e# / ea record EMPTY | f# / fa record FULL | c show | s save | x clear");
  Serial.println("Calibrate: empty the trays -> ea   fill every tray with " + String(TRAY_CAPACITY) +
                 " items -> fa   then c, then s");
  Serial.println("Live row: T#:distance(stock). 'far' = nothing seen, ERR = no reading.\n");
}

// ── Main ──────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=== SaniVend ToF test + calibration (ESP32 #2) ===");
  Wire.begin(SDA_PIN, SCL_PIN);
  Wire.setClock(100000);
  if (pca_disableAllCall() == 1)
    Serial.println("[0] PCA9685 All-Call (0x70) turned OFF (it clashes with the MUX)");
  scanBus();
  initSensors();
  loadCal();
  showCal();
  help();
}

void handleCommand(String cmd) {
  cmd.trim();
  cmd.toLowerCase();
  if (cmd.length() == 0) return;
  char c = cmd[0];
  String arg = cmd.substring(1);
  int t = arg.toInt() - 1;                      // "e3" -> tray index 2
  bool all = (arg == "a");
  bool trayOk = (t >= 0 && t < TRAY_COUNT);

  if (c == 'h') help();
  else if (c == 'l') { live = !live; Serial.println(live ? "Live ON" : "Live OFF"); }
  else if (c == 'r') printRow();
  else if (c == 'w' && trayOk) watch(t);
  else if ((c == 'e' || c == 'f') && (all || trayOk)) {
    bool wasLive = live; live = false;
    if (all) for (int i = 0; i < TRAY_COUNT; i++) record(c == 'e', i);
    else record(c == 'e', t);
    Serial.println("(not saved yet — type s to save)");
    live = wasLive;
  }
  else if (c == 'c') showCal();
  else if (c == 's') saveCal();
  else if (c == 'x') clearCal();
  else Serial.println("Unknown command '" + cmd + "'. Type h for help.");
}

void loop() {
  if (Serial.available()) handleCommand(Serial.readStringUntil('\n'));
  if (live && millis() - lastLive >= 1000) {
    lastLive = millis();
    printRow();
  }
}
