/*
  SaniVend - change servo test (upload to ESP32 #1)
  Same pins and angles as vending_esp1_change/change.ino.

  Serial Monitor 115200, "Newline". Commands:
    1  = push P1 once        5 = push P5 once       0 = push P10 once
    a  = push all three, one after another
    c18 = dispense P18 the real way (10 + 5 + 1 + 1 + 1)
    r  = move all to rest (0 deg)
    i  = show the 3 coin-tube IR sensors live for 10 s (adjust the blue knobs)
*/
#include <ESP32Servo.h>

#define PIN_1PESO      25
#define PIN_5PESO      26
#define PIN_10PESO     27
#define CHANGE_REST_US 732    // 0 deg
#define CHANGE_PUSH_US 1831   // 90 deg
#define CHANGE_HOLD_MS 400
#define CHANGE_WAIT_MS 300

Servo servo1, servo5, servo10;

#define IR_PIN_1PESO    32
#define IR_PIN_5PESO    33
#define IR_PIN_10PESO   4
#define IR_COIN_PRESENT LOW
const int irPin[3] = {IR_PIN_1PESO, IR_PIN_5PESO, IR_PIN_10PESO};

void showIr() {
  Serial.println("IR live for 10 s (ok = coins seen, EMPTY = nothing seen):");
  unsigned long t0 = millis();
  while (millis() - t0 < 10000) {
    Serial.printf("  P1:%-6s P5:%-6s P10:%-6s\n",
      digitalRead(irPin[0]) == IR_COIN_PRESENT ? "ok" : "EMPTY",
      digitalRead(irPin[1]) == IR_COIN_PRESENT ? "ok" : "EMPTY",
      digitalRead(irPin[2]) == IR_COIN_PRESENT ? "ok" : "EMPTY");
    delay(500);
  }
}

void pushOnce(Servo &sv, const char *label) {
  Serial.println(String("  push ") + label);
  sv.writeMicroseconds(CHANGE_PUSH_US);
  delay(CHANGE_HOLD_MS);
  sv.writeMicroseconds(CHANGE_REST_US);
  delay(CHANGE_WAIT_MS);
}

void dispense(int amount) {
  Serial.println("Dispensing P" + String(amount));
  while (amount >= 10) { pushOnce(servo10, "P10"); amount -= 10; delay(100); }
  while (amount >= 5)  { pushOnce(servo5,  "P5");  amount -= 5;  delay(100); }
  while (amount >= 1)  { pushOnce(servo1,  "P1");  amount -= 1;  delay(100); }
  Serial.println("Done.");
}

void setup() {
  Serial.begin(115200);
  delay(500);
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  Servo* s[3] = {&servo1, &servo5, &servo10};
  int pin[3]  = {PIN_1PESO, PIN_5PESO, PIN_10PESO};
  for (int i = 0; i < 3; i++) {
    s[i]->setPeriodHertz(50);
    s[i]->attach(pin[i], 500, 2500);
    s[i]->writeMicroseconds(CHANGE_REST_US);
  }
  for (int i = 0; i < 3; i++) pinMode(irPin[i], INPUT_PULLUP);
  Serial.println("\n=== SaniVend change servo test ===");
  Serial.println("P1=GPIO25  P5=GPIO26  P10=GPIO27   (all at rest)");
  Serial.println("Commands: 1 / 5 / 0 = push one coin,  a = all,  c18 = give P18,  r = rest,  i = IR sensors");
}

void loop() {
  if (!Serial.available()) return;
  String cmd = Serial.readStringUntil('\n');
  cmd.trim();
  if (cmd == "1") pushOnce(servo1, "P1");
  else if (cmd == "5") pushOnce(servo5, "P5");
  else if (cmd == "0") pushOnce(servo10, "P10");
  else if (cmd == "a") { pushOnce(servo1, "P1"); pushOnce(servo5, "P5"); pushOnce(servo10, "P10"); }
  else if (cmd.startsWith("c")) { int n = cmd.substring(1).toInt(); if (n > 0) dispense(n); }
  else if (cmd == "r") {
    servo1.writeMicroseconds(CHANGE_REST_US);
    servo5.writeMicroseconds(CHANGE_REST_US);
    servo10.writeMicroseconds(CHANGE_REST_US);
    Serial.println("All at rest.");
  }
  else if (cmd == "i") showIr();
  else if (cmd.length()) Serial.println("? use 1, 5, 0, a, c<amount>, r, i");
}
