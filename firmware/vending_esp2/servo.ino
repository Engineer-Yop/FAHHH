// ─────────────────────────────────────────
//  servo.ino — PCA9685 servo control
//  Vend:   CH0–CH7   MG996R 360° continuous
// ─────────────────────────────────────────

#define SERVO_FREQ  50   // 50 Hz servo signal

void servo_init() {
  Serial.println("[Servo] Initializing PCA9685...");
  pca9685.begin();
  pca9685.setPWMFreq(SERVO_FREQ);

  servo_stopAll();

  delay(300);
  Serial.println("[Servo] All tray servos stopped.");
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
