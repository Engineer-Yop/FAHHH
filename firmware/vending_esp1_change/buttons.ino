// ─────────────────────────────────────────
//  buttons.ino — PCF8574 buttons P0–P7 = Tray 1–8
//  Active LOW (other leg to GND). Non-blocking.
// ─────────────────────────────────────────

#define BUTTON_DEBOUNCE_MS  50

bool          buttonWasPressed[8] = {false};
unsigned long buttonLastChange[8] = {0};

void buttons_init() {
  if (!pcf.begin()) {
    Serial.println("PCF8574 not found! Check wiring.");
  } else {
    Serial.println("PCF8574 buttons initialized.");
  }
}

// Returns 0–7 on a new press, -1 if none
int buttons_read() {
  for (int i = 0; i < 8; i++) {
    bool isPressed = (pcf.readButton(i) == LOW);

    if (isPressed != buttonWasPressed[i] &&
        millis() - buttonLastChange[i] > BUTTON_DEBOUNCE_MS) {
      buttonWasPressed[i] = isPressed;
      buttonLastChange[i] = millis();
      if (isPressed) return i;     // trigger on press, not on release
    }
  }
  return -1;
}
