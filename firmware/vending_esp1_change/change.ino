// ─────────────────────────────────────────
//  change.ino — coin change dispenser, driven DIRECTLY by ESP32 #1
//  3 servos: one each for P1, P5 and P10 (no PCA9685 needed)
//
//  Largest coins first:  P18 = 10 + 5 + 1 + 1 + 1
//  With the IR sensors (coinlevel.ino): an EMPTY tube is skipped and smaller
//  coins are used instead (P10 empty → P18 = 5 + 5 + 5 + 1 + 1 + 1).
//  If the change can't be made from the tubes that have coins, all three are
//  used as before (in case a sensor is wrong).
//
//  Wiring for each change servo (SG90 / MG90S):
//    Orange / yellow (signal) → GPIO listed below
//    Red   (+)                → 5V buck converter output (NOT the ESP32 5V/VIN pin)
//    Brown (−)                → GND — must be shared with the ESP32 GND
//
//  Library: ESP32Servo (Kevin Harrington / John K. Bennett)
// ─────────────────────────────────────────
#include <ESP32Servo.h>

#define PIN_1PESO      25    // P1 servo
#define PIN_5PESO      26    // P5 servo
#define PIN_10PESO     27    // P10 servo

// 0° = rest, 90° = push one coin out. Adjust here if a coin doesn't drop.
#define CHANGE_REST_US 732   // 0°
#define CHANGE_PUSH_US 1831  // 90°
#define CHANGE_HOLD_MS 400   // how long the servo stays pushed
#define CHANGE_WAIT_MS 300   // pause after returning, lets the next coin settle

Servo servo1, servo5, servo10;

void change_init() {
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);

  Servo* s[3]   = {&servo1, &servo5, &servo10};
  int    pin[3] = {PIN_1PESO, PIN_5PESO, PIN_10PESO};
  for (int i = 0; i < 3; i++) {
    s[i]->setPeriodHertz(50);
    s[i]->attach(pin[i], 500, 2500);
    s[i]->writeMicroseconds(CHANGE_REST_US);
  }
  delay(300);
  Serial.println("[Change] P1=GPIO25  P5=GPIO26  P10=GPIO27  at rest.");
}

// Push one coin: servo to 90°, then back to 0°
void change_push_once(Servo &sv, const char *label) {
  Serial.println(String("[Change] ") + label + " pushing...");
  sv.writeMicroseconds(CHANGE_PUSH_US);
  delay(CHANGE_HOLD_MS);
  sv.writeMicroseconds(CHANGE_REST_US);
  delay(CHANGE_WAIT_MS);
}

// How many of each coin (index 0 = P1, 1 = P5, 2 = P10). Returns what is left over.
int change_plan(int amount, const bool use[3], int count[3]) {
  const int value[3] = {1, 5, 10};
  count[0] = count[1] = count[2] = 0;
  for (int i = 2; i >= 0; i--) {
    if (!use[i]) continue;
    count[i] = amount / value[i];
    amount  -= count[i] * value[i];
  }
  return amount;
}

void change_dispense(int amount) {
  Serial.println("[Change] Dispensing P" + String(amount) + "...");

  bool haveCoins[3] = {!coinlevel_isEmpty(0), !coinlevel_isEmpty(1), !coinlevel_isEmpty(2)};
  int count[3];
  if (change_plan(amount, haveCoins, count) != 0) {
    Serial.println("[Change] Not enough tubes with coins for exact change — using all three.");
    const bool all[3] = {true, true, true};
    change_plan(amount, all, count);
  }

  for (int n = 0; n < count[2]; n++) { change_push_once(servo10, "P10"); delay(100); }
  for (int n = 0; n < count[1]; n++) { change_push_once(servo5,  "P5");  delay(100); }
  for (int n = 0; n < count[0]; n++) { change_push_once(servo1,  "P1");  delay(100); }

  Serial.println("[Change] Done.");
}
