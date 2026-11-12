// ─────────────────────────────────────────
//  Vending Machine — ESP32 #2 (Mechanism)
//  Vend servos   CH0–CH7   MG996R 360° (6V)
//  No change dispenser (removed): the machine takes the price and gives no change
//
//  Tabs in this sketch folder:
//    vending_esp2.ino  (this file — settings, command loop)
//    firebase.ino      WiFi + vend log
//    servo.ino         PCA9685 servo control
//    stock.ino         tray stock from the ToF sensors -> Firebase
//    tof.ino           PCA9548A MUX + 8x VL53L0X (calibrated with the tof_test sketch)
//    uart.ino          UART2 link to ESP32 #1
//
//  Libraries: Adafruit PWM Servo Driver (+ Adafruit BusIO)
//             VL53L0X by Pololu (only when USE_TOF is 1), Preferences (built in)
// ─────────────────────────────────────────
#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

// ── Feature switches ─────────────────────
#define USE_TOF        0     // 0 = ToF off: stock counted per sale by ESP32 #1 (1 = measured by sensors)
#define USE_FIREBASE   1     // 1 = log vends to Firebase (set WiFi in firebase.ino)

// ── I2C addresses ────────────────────────
#define PCA9685_ADDR   0x40
#define MUX_ADDR       0x70  // A0–A2 to GND (new PCB). Use 0x73 if they are still floating.

Adafruit_PWMServoDriver pca9685 = Adafruit_PWMServoDriver(PCA9685_ADDR);

// ── UART to ESP32 #1 ─────────────────────
#define UART_RX        16
#define UART_TX        17
#define UART_BAUD      9600

// ── Vend servos (MG996R 360°) ────────────
#define SERVO_STOP     307   // ~1500 µs
#define SERVO_FORWARD  370   // ~1800 µs
#define SERVO_VEND_MS  1300  // spin time per vend (1.3 s)

// ── Trays + ToF stock ─────────────────────
#define TRAY_CAPACITY      7      // items in a full tray (same as ESP32 #1)
#define TOF_SAMPLES        5      // readings per background measurement (median used)
#define TOF_STABLE_COUNT   3      // same count this many times in a row before it is accepted
#define TOF_SCAN_MS        300    // one tray measured every 300 ms -> all 8 about every 2.4 s
#define TOF_AFTER_VEND_MS  600    // wait for the item to fall before measuring again
#define TOF_OUT_OF_RANGE   8000   // library gives ~8190 when nothing is in range
#define TOF_MIN_SPAN_MM    20     // EMPTY and FULL must differ by at least this much
#define STOCK_REPUBLISH_MS 300000 // re-send sensor stock every 5 min (overrides manual edits)

// Calibration backup (mm). The tof_test sketch saves the real values in flash and
// those are used first; paste its "c" output here only if the flash gets erased.
// 0 = not calibrated -> that tray is counted by ESP32 #1 instead.
const int TOF_EMPTY_MM[8] = {0, 0, 0, 0, 0, 0, 0, 0};
const int TOF_FULL_MM[8]  = {0, 0, 0, 0, 0, 0, 0, 0};

// ── Machine ID (sent by ESP32 #1 as "ID:SV-XXXXXX") ──
String deviceId = "";            // Firebase logs go under /devices/<deviceId>/vend_logs
bool   deviceIdChanged = false;

// ── UART command codes ───────────────────
#define CMD_NONE       0
#define CMD_VEND       1

void setup() {
  Serial.begin(115200);
  Serial.println("ESP32 #2 booting...");

  Wire.begin(21, 22);
  Wire.setClock(100000);

  // I2C general-call software reset (PCA9685 etc.)
  Wire.beginTransmission(0x00);
  Wire.write(0x06);
  Wire.endTransmission();
  delay(100);

  uart_init();
  servo_init();

#if USE_TOF
  tof_init();
#else
  Serial.println("[ToF] Disabled (USE_TOF 0) — all trays counted by ESP32 #1");
#endif
  stock_init();

#if USE_FIREBASE
  firebase_init();
#endif

  Serial.println("ESP32 #2 ready. Waiting for commands...");
}

void loop() {
  int value = 0;
  int cmd = uart_readCommand(&value);

  if (cmd == CMD_VEND) {
    Serial.println("Vend command for tray " + String(value + 1));
    bool ok = vendTray(value);
    uart_sendResult(value, ok);          // reply first, measure + log after
    stock_afterVend(value, ok);
#if USE_FIREBASE
    firebase_logVend(value, ok);
#endif

  }

  stock_tick();                          // background tray measuring + Firebase update

#if USE_FIREBASE
  firebase_tick();
#endif
}

// ── Vend one tray ─────────────────────────
bool vendTray(int tray) {
  servo_vend(tray);
  return true;   // a finished spin counts as success; the sensor then updates the stock
}
