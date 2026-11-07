/*
  SaniVend - MUX diagnostic (ESP32 #2)
  No extra libraries needed (only Wire).

  Wiring expected:
    ESP32 GPIO21 = SDA, GPIO22 = SCL
    PCA9548A / TCA9548A at 0x70 (A0, A1, A2 to GND)
    VL53L0X sensors (0x29) on MUX channels 0..7 = trays 1..8

  Serial Monitor: 115200 baud. Type  r  + Enter to run the test again.
*/
#include <Wire.h>

#define SDA_PIN 21
#define SCL_PIN 22
#define I2C_HZ  100000      // slow and safe for testing

uint8_t muxAddr = 0;
bool onMainBus[128];

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

bool ping(uint8_t addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

const char* nameOf(uint8_t a) {
  if (a == 0x29) return "VL53L0X";
  if (a == 0x40) return "PCA9685 (servo driver)";
  if (a >= 0x70 && a <= 0x77) return "MUX";
  if (a == 0x27 || a == 0x3F) return "LCD backpack";
  if (a >= 0x20 && a <= 0x27) return "PCF8574";
  return "unknown";
}

bool muxSelect(uint8_t ch) {               // ch 0..7, or 8 = all off
  Wire.beginTransmission(muxAddr);
  Wire.write(ch < 8 ? (1 << ch) : 0);
  return Wire.endTransmission() == 0;
}

int muxReadBack() {
  if (Wire.requestFrom((int)muxAddr, 1) != 1) return -1;
  return Wire.read();
}

int vl53ModelId() {                        // VL53L0X register 0xC0 should read 0xEE
  Wire.beginTransmission(0x29);
  Wire.write(0xC0);
  if (Wire.endTransmission(false) != 0) return -1;
  if (Wire.requestFrom(0x29, 1) != 1) return -1;
  return Wire.read();
}

void runTest() {
  Serial.println("\n================ SaniVend MUX test ================");

  // ---- 1. Idle line check (pull-ups) ----
  Wire.end();
  pinMode(SDA_PIN, INPUT);
  pinMode(SCL_PIN, INPUT);
  delay(5);
  int sda = digitalRead(SDA_PIN), scl = digitalRead(SCL_PIN);
  Serial.printf("[1] Idle lines: SDA(21)=%s  SCL(22)=%s\n",
                sda ? "HIGH ok" : "LOW  !!", scl ? "HIGH ok" : "LOW  !!");
  if (!sda || !scl)
    Serial.println("    -> A line is stuck LOW: missing pull-ups to 3.3V, a short to GND,\n"
                   "       or a powered-off board holding the bus. Fix this first.");

  Wire.begin(SDA_PIN, SCL_PIN, I2C_HZ);
  Wire.setTimeOut(50);

  int ac = pca_disableAllCall();
  if (ac == 1)  Serial.println("[0] PCA9685 All-Call (0x70) was ON -> turned OFF so it can't fake the MUX");
  if (ac == 0)  Serial.println("[0] PCA9685 All-Call already off");
  if (ac == -1) Serial.println("[0] No PCA9685 at 0x40 (fine for this test)");

  // ---- 2. Main bus scan (all MUX channels OFF) ----
  memset(onMainBus, 0, sizeof(onMainBus));
  muxAddr = 0;
  for (uint8_t a = 0x70; a <= 0x77; a++) if (ping(a)) { muxAddr = a; break; }
  if (muxAddr) muxSelect(8);               // close every channel before scanning

  Serial.println("[2] Main bus scan (MUX channels closed):");
  int found = 0;
  for (uint8_t a = 1; a < 127; a++) {
    if (ping(a)) {
      onMainBus[a] = true; found++;
      Serial.printf("    0x%02X  %s\n", a, nameOf(a));
    }
  }
  if (!found) Serial.println("    nothing found -> check SDA/SCL wires, GND, and MUX VIN (3.3V)");

  if (!muxAddr) {
    Serial.println("\nRESULT: MUX NOT FOUND (0x70-0x77).");
    Serial.println("  Check: MUX VIN -> 3.3V, GND shared, SDA->21, SCL->22 (not swapped),");
    Serial.println("         RST -> 3.3V (never GND / floating), A0-A2 -> GND for 0x70.");
    return;
  }
  Serial.printf("    MUX found at 0x%02X%s\n", muxAddr,
                muxAddr == 0x70 ? "" : "  (NOT 0x70! tie A0, A1, A2 to GND or change MUX_ADDR in code)");
  if (onMainBus[0x29])
    Serial.println("    !! A VL53L0X answers on the MAIN bus: a sensor is wired to ESP32 SDA/SCL\n"
                   "       instead of to an SDx/SCx channel. It will clash with the others.");

  // ---- 3. MUX register write/read-back ----
  Serial.println("[3] MUX control register test:");
  bool regOk = true;
  for (uint8_t ch = 0; ch < 8; ch++) {
    bool w = muxSelect(ch);
    int r = muxReadBack();
    bool ok = w && r == (1 << ch);
    if (!ok) regOk = false;
    Serial.printf("    CH%d: wrote 0x%02X, read 0x%02X  %s\n", ch, 1 << ch, r & 0xFF, ok ? "ok" : "FAIL");
  }
  muxSelect(8);
  Serial.println(regOk ? "    -> MUX is responding correctly. The MUX itself is OK."
                       : "    -> MUX answers but doesn't switch properly: check RST and power.");
  if (!regOk) {
    Serial.println("\nRESULT: MUX switching unreliable. Fix wiring (A0-A2 to GND, RST to 3.3V,");
    Serial.println("        PCA9685 VCC to 3.3V), then type r + Enter to test again.");
    return;                                // don't scan channels on a bad bus (it can hang)
  }

  // ---- 4. Each channel: what is behind it? ----
  Serial.println("[4] Devices behind each channel (main-bus devices hidden):");
  int sensorsOk = 0;
  for (uint8_t ch = 0; ch < 8; ch++) {
    muxSelect(ch);
    delay(2);
    Serial.printf("    CH%d (tray %d): ", ch, ch + 1);
    int n = 0;
    for (uint8_t a = 1; a < 127; a++) {
      if (onMainBus[a]) continue;
      if (ping(a)) { Serial.printf("0x%02X ", a); n++; }
    }
    if (n == 0) {
      Serial.println("nothing -> sensor not powered, or SDx/SCx wires off/swapped");
    } else if (ping(0x29)) {
      int id = vl53ModelId();
      if (id == 0xEE) { Serial.println(" VL53L0X OK (model ID 0xEE)"); sensorsOk++; }
      else Serial.printf(" answers at 0x29 but model ID = 0x%02X (expected 0xEE) -> bad sensor/wiring\n", id & 0xFF);
    } else {
      Serial.println(" (no device at 0x29)");
    }
  }
  muxSelect(8);

  Serial.printf("\nRESULT: MUX OK at 0x%02X, %d of 8 sensors OK.\n", muxAddr, sensorsOk);
  Serial.println("Type r + Enter to test again.");
}

void setup() {
  Serial.begin(115200);
  delay(1500);
  runTest();
}

void loop() {
  if (Serial.available()) {
    char c = Serial.read();
    if (c == 'r' || c == 'R') runTest();
  }
}
