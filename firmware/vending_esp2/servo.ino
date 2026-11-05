// ─────────────────────────────────────────
//  servo.ino — PCA9685 servo control
//  Vend:   CH0–CH7   MG996R 360° continuous
//  Change: CH12–CH15 SG90 / MG90S positional
// ─────────────────────────────────────────

#define SERVO_FREQ  50   // 50 Hz servo signal

void servo_init() {
  Serial.println("[Servo] Initializing PCA9685...");
  pca9685.begin();
  pca9685.setPWMFreq(SERVO_FREQ);

  servo_stopAll();

  pca9685.setPWM(CH_1PESO,   0, CHANGE_REST);
  pca9685.setPWM(CH_5PESO_A, 0, CHANGE_REST);
  pca9685.setPWM(CH_10PESO,  0, CHANGE_REST);
  pca9685.setPWM(CH_5PESO_B, 0, CHANGE_REST);

  delay(300);
  Serial.println("[Servo] All servos stopped / at rest.");
}

// Spin one vend servo forward, then stop
void servo_vend(int tray) {
  Serial.println("[Servo] Tray " + String(tray + 1) + " spinning...");
  pca9685.setPWM(tray, 0, SERVO_FORWARD);
  delay(SERVO_VEND_MS);
  servo_stop(tray);
  Serial.println("[Servo] Tray " + String(tray + 1) + " stopped.");
}

void servo_stop(int tray) {
  pca9685.setPWM(tray, 0, SERVO_STOP);
}

void servo_stopAll() {
  for (int i = 0; i < 8; i++) servo_stop(i);
}

// Push a change servo to 90°, then back to 0°
void change_push_once(int channel) {
  Serial.println("[Change] CH" + String(channel) + " pushing...");
  pca9685.setPWM(channel, 0, CHANGE_PUSH);
  delay(CHANGE_HOLD_MS);
  pca9685.setPWM(channel, 0, CHANGE_REST);
  delay(CHANGE_WAIT_MS);
}
