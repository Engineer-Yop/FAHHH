// ─────────────────────────────────────────
//  Vending Machine — ESP32 #2 (Mechanism)
//  Vend servos   CH0–CH7   MG996R 360° (6V)
//  Change servos CH12–CH15 SG90/MG90S positional (5V)
//
//  Tabs in this sketch folder:
//    vending_esp2.ino  (this file — settings, command loop)
//    change.ino        greedy change: P10 → P5 (A/B) → P1
//    firebase.ino      WiFi + vend/change log
//    servo.ino         PCA9685 servo control
//    tof.ino           PCA9548A MUX + 8x VL53L0X (off by default)
//    uart.ino          UART2 link to ESP32 #1
//
//  Libraries: Adafruit PWM Servo Driver (+ Adafruit BusIO)
//             VL53L0X by Pololu (only when USE_TOF is 1)
// ─────────────────────────────────────────
#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

// ── Feature switches ─────────────────────
#define USE_TOF        0     // 1 = start the 8 ToF sensors (needs MUX + VL53L0X library)
#define USE_FIREBASE   1     // 1 = log vends/change to Firebase (set WiFi in firebase.ino)

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
#define SERVO_VEND_MS  1500  // spin time per vend

// ── Change servos (positional, 0° ↔ 90°) ─
#define CHANGE_REST    150   // 0°
#define CHANGE_PUSH    375   // 90°
#define CHANGE_HOLD_MS 400
#define CHANGE_WAIT_MS 300

#define CH_1PESO       12    // SG90
#define CH_5PESO_A     13    // SG90
#define CH_10PESO      14    // MG90S
#define CH_5PESO_B     15    // MG90S

// ── Machine ID (sent by ESP32 #1 as "ID:SV-XXXXXX") ──
String deviceId = "";            // Firebase logs go under /devices/<deviceId>/vend_logs
bool   deviceIdChanged = false;

// ── UART command codes ───────────────────
#define CMD_NONE       0
#define CMD_VEND       1
#define CMD_CHANGE     2

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
  Serial.println("[ToF] Disabled (USE_TOF 0) — servo spin = success");
#endif

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
    uart_sendResult(value, ok);          // reply first, log after
#if USE_FIREBASE
    firebase_logVend(value, ok);
#endif

  } else if (cmd == CMD_CHANGE) {
    Serial.println("Change command: P" + String(value));
    change_dispense(value);
    uart_sendChangeDone();
#if USE_FIREBASE
    firebase_logChange(value);
#endif
  }

#if USE_FIREBASE
  firebase_tick();
#endif
}

// ── Vend one tray ─────────────────────────
bool vendTray(int tray) {
#if USE_TOF
  int before = tof_read(tray);
  servo_vend(tray);
  delay(300);
  int after = tof_read(tray);
  Serial.println("[ToF] Tray " + String(tray + 1) + " before " + String(before) +
                 " mm, after " + String(after) + " mm");
#else
  servo_vend(tray);
#endif
  return true;   // no item-drop check yet: a finished spin counts as success
}
