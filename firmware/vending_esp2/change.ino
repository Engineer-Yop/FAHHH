// ─────────────────────────────────────────
//  change.ino — coin change dispenser
//  Greedy: P10 → P5 (alternating A/B) → P1
// ─────────────────────────────────────────

bool use5A = true;   // remembered between orders so A and B wear evenly

void change_dispense(int amount) {
  Serial.println("[Change] Dispensing P" + String(amount) + "...");
  int remaining = amount;

  while (remaining >= 10) {
    Serial.println("[Change] P10");
    change_push_once(CH_10PESO);
    remaining -= 10;
    delay(100);
  }

  while (remaining >= 5) {
    int ch = use5A ? CH_5PESO_A : CH_5PESO_B;
    Serial.println("[Change] P5 via CH" + String(ch));
    change_push_once(ch);
    remaining -= 5;
    use5A = !use5A;
    delay(100);
  }

  while (remaining >= 1) {
    Serial.println("[Change] P1");
    change_push_once(CH_1PESO);
    remaining -= 1;
    delay(100);
  }

  Serial.println("[Change] Done.");
}
