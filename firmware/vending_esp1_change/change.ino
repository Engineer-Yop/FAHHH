// ─────────────────────────────────────────
//  change.ino — coin change dispenser, driven DIRECTLY by ESP32 #1
//  (no PCA9685 needed — the servos plug into ESP32 #1 GPIOs)
//
//  Greedy: P10 → P5 (alternating A/B) → P1
//
//  Wiring for each change servo (SG90 / MG90S):
//    Orange / yellow (signal) → GPIO listed below
//    Red   (+)                → 5V buck converter output (NOT the ESP32 5V/VIN pin)
//    Brown (−)                → GND — must be shared with the ESP32 GND
//
//  Library: ESP32Servo (Kevin Harrington / John K. Bennett)
// ─────────────────────────────────────────
#include <ESP32Servo.h>

#define PIN_1PESO      25    // SG90   (was PCA9685 CH12)
#define PIN_5PESO_A    26    // SG90   (was PCA9685 CH13)
#define PIN_10PESO     27    // MG90S  (was PCA9685 CH14)
#define PIN_5PESO_B    32    // MG90S  (was PCA9685 CH15)

// Same angles as the old PCA9685 values (150 → 0°, 375 → 90° at 50 Hz)
#define CHANGE_REST_US 732   // 0°
#define CHANGE_PUSH_US 1831  // 90°
#define CHANGE_HOLD_MS 400
#define CHANGE_WAIT_MS 300

Servo servo1, servo5A, servo10, servo5B;
bool use5A = true;   // remembered between orders so A and B wear evenly

void change_init() {
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);

  Servo* s[4]  = {&servo1, &servo5A, &servo10, &servo5B};
  int    pin[4] = {PIN_1PESO, PIN_5PESO_A, PIN_10PESO, PIN_5PESO_B};
  for (int i = 0; i < 4; i++) {
    s[i]->setPeriodHertz(50);
    s[i]->attach(pin[i], 500, 2500);
    s[i]->writeMicroseconds(CHANGE_REST_US);
  }
  delay(300);
  Serial.println("[Change] Servos on GPIO 25/26/27/32 at rest.");
}

// Push one coin: servo to 90°, then back to 0°
void change_push_once(Servo &sv, const char *label) {
  Serial.println(String("[Change] ") + label + " pushing...");
  sv.writeMicroseconds(CHANGE_PUSH_US);
  delay(CHANGE_HOLD_MS);
  sv.writeMicroseconds(CHANGE_REST_US);
  delay(CHANGE_WAIT_MS);
}

void change_dispense(int amount) {
  Serial.println("[Change] Dispensing P" + String(amount) + "...");
  int remaining = amount;

  while (remaining >= 10) {
    change_push_once(servo10, "P10");
    remaining -= 10;
    delay(100);
  }

  while (remaining >= 5) {
    if (use5A) change_push_once(servo5A, "P5 (A)");
    else       change_push_once(servo5B, "P5 (B)");
    use5A = !use5A;
    remaining -= 5;
    delay(100);
  }

  while (remaining >= 1) {
    change_push_once(servo1, "P1");
    remaining -= 1;
    delay(100);
  }

  Serial.println("[Change] Done.");
}
