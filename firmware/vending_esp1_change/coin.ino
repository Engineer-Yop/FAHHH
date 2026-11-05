// ─────────────────────────────────────────
//  coin.ino — Allan 1239 PRO MAX (1 pulse = P1)
//  YELLOW → BSS138 HV1 → LV1 → GPIO34
//  No inhibit wire: accepting is switched in software
// ─────────────────────────────────────────

#define PULSE_TIMEOUT  300   // ms of silence before the pulses are counted

volatile int           pulseCount    = 0;
volatile unsigned long lastPulseTime = 0;

void IRAM_ATTR coin_ISR();

void coin_init() {
  pinMode(COIN_PIN, INPUT);  // GPIO34 is input-only, no internal pull resistors
  Serial.println("Coin acceptor initialized.");
}

void IRAM_ATTR coin_ISR() {
  pulseCount++;
  lastPulseTime = millis();
}

// Returns pesos added this call, 0 if nothing new
int coin_update() {
  if (pulseCount > 0 && (millis() - lastPulseTime > PULSE_TIMEOUT)) {
    noInterrupts();
    int counted = pulseCount;
    pulseCount  = 0;
    interrupts();

    coinBalance += counted;
    Serial.println("[Coin] Counted: P" + String(counted) +
                   " | Total: P" + String(coinBalance) +
                   " | Need: P" + String(getTrayPrice(selectedTray)));
    return counted;
  }
  return 0;
}

// true = stop counting coins, false = start counting
void coin_inhibit(bool inhibit) {
  if (inhibit) {
    detachInterrupt(digitalPinToInterrupt(COIN_PIN));
    noInterrupts();
    pulseCount = 0;
    interrupts();
    Serial.println("[Coin] Acceptor DISABLED");
  } else {
    noInterrupts();
    pulseCount = 0;
    interrupts();
    delay(50);
    attachInterrupt(digitalPinToInterrupt(COIN_PIN), coin_ISR, FALLING);
    Serial.println("[Coin] Acceptor ENABLED");
  }
}
