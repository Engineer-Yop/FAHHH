// ─────────────────────────────────────────
//  Vending Machine — ESP32 #2 (Mechanism)
//  Vend servos   CH0–CH7   MG996R 360° (6V)
//  No change dispenser (removed): the machine takes the price and gives no change
//
//  Tabs in this sketch folder:
//    vending_esp2.ino  (this file — settings, command loop)
//    firebase.ino      WiFi + vend log
//    servo.ino         PCA9685 servo control
//    stock.ino         real stock: vend count checked by the ToF sensors -> Firebase
//    tof.ino           TCA9548A MUX + 8x VL53L0X, distance -> possible stock, bus self-repair
//    uart.ino          UART2 link to ESP32 #1
//
//  Libraries: Adafruit PWM Servo Driver (+ Adafruit BusIO)
//             VL53L0X by Pololu (only when USE_TOF is 1)
// ─────────────────────────────────────────
#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

// ── Feature switches ─────────────────────
#define USE_TOF        1     // 1 = read the ToF sensors (0 = off: everything counted by ESP32 #1)
#define USE_FIREBASE   1     // 1 = log vends to Firebase (set WiFi in firebase.ino)

// ── I2C addresses ────────────────────────
#define PCA9685_ADDR   0x40
#define MUX_ADDR       0x70  // TCA9548A, A0–A2 to GND
#define I2C_CLOCK_HZ   50000 // 50 kHz: steadier on long sensor wires than 100 kHz

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
#define TOF_STABLE_COUNT   3      // same sensor result this many times in a row before it is used
#define TOF_FAIL_READS     5      // bad readings in a row -> tray handed back to ESP32 #1's count
#define TOF_SCAN_MS        300    // one tray measured every 300 ms -> all 8 about every 2.4 s
#define TOF_AFTER_VEND_MS  600    // wait for the item to fall before measuring again
#define TOF_OUT_OF_RANGE   8000   // library gives ~8190 when nothing is in range
#define TOF_REPAIR_EVERY_MS 10000 // at most one I2C bus repair every 10 s
#define STOCK_SYNC_MS      30000  // read stock from Firebase every 30 s (picks up app refills)
#define STOCK_REPUBLISH_MS 300000 // re-send every tray's stock every 5 min

// Tray geometry, measured on the machine (2026-11-25), same for all 8 trays:
#define TOF_FULL_MIN_MM    100    // 7 items: first item 100–140 mm from the sensor
#define TOF_FULL_MAX_MM    140
#define TOF_STEP_MIN_MM    90     // each item taken moves it 90–120 mm further
#define TOF_STEP_MAX_MM    120
#define TOF_WALL_MM        575    // empty tray: back wall at ~580–600 mm
#define TOF_TOLERANCE_MM   15     // sensor noise allowed on every limit
#define TOF_MAX_VALID_MM   1000   // anything further (8000, 8190) = sensor/bus error
#define TOF_RAW_PUBLISH_MS 5000   // send all 8 raw distances to Firebase every 5 s
                                  //   -> /devices/<ID>/tof_readings {tray_1..tray_8 (mm), updated}

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
  Wire.setClock(I2C_CLOCK_HZ);

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
  return true;   // a finished spin counts as success; the sensor confirms or corrects the stock
}
