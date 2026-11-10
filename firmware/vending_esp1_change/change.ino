// ─────────────────────────────────────────
//  change.ino — coin change dispenser, driven DIRECTLY by ESP32 #1
//  ONE servo, P5 coins only, on GPIO 25
//
//  Change is given in P5 coins:  P20 paid for a P5 item → P15 change → 3 × P5
//  Any part of the change below P5 can't be given (keep prices in steps of P5).
//
//  Wiring (SG90 / MG90S):
//    Orange / yellow (signal) → GPIO 25
//    Red   (+)                → 5V buck converter output (NOT the ESP32 5V/VIN pin)
//    Brown (−)                → GND — must be shared with the ESP32 GND
//
//  Library: ESP32Servo (Kevin Harrington / John K. Bennett)
// ─────────────────────────────────────────
#include <ESP32Servo.h>

#define PIN_5PESO      25    // the only change servo
#define COIN_VALUE     5     // each push gives one P5 coin

// 0° = rest, 90° = push one coin out. Adjust here if a coin doesn't drop.
#define CHANGE_REST_US 732   // 0°
#define CHANGE_PUSH_US 1831  // 90°
#define CHANGE_HOLD_MS 400   // how long the servo stays pushed
#define CHANGE_WAIT_MS 300   // pause after returning, lets the next coin settle

Servo servo5;

void change_init() {
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  servo5.setPeriodHertz(50);
  servo5.attach(PIN_5PESO, 500, 2500);
  servo5.writeMicroseconds(CHANGE_REST_US);
  delay(300);
  Serial.println("[Change] P5 servo on GPIO25 at rest.");
}

// Push one coin: servo to 90°, then back to 0°
void change_push_once() {
  Serial.println("[Change] P5 pushing...");
  servo5.writeMicroseconds(CHANGE_PUSH_US);
  delay(CHANGE_HOLD_MS);
  servo5.writeMicroseconds(CHANGE_REST_US);
  delay(CHANGE_WAIT_MS);
}

void change_dispense(int amount) {
  int coins = amount / COIN_VALUE;
  int left  = amount % COIN_VALUE;
  Serial.println("[Change] Dispensing P" + String(amount) + " = " + String(coins) + " x P5");

  for (int n = 0; n < coins; n++) { change_push_once(); delay(100); }

  if (left > 0)
    Serial.println("[Change] P" + String(left) + " can't be given (P5 coins only). Use prices in steps of P5.");
  Serial.println("[Change] Done.");
}
