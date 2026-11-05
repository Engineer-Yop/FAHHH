// ─────────────────────────────────────────
//  device.ino — this machine's ID and pairing code
//
//  ID:   "SV-" + last 6 hex digits of the ESP32's factory MAC address
//        (burned into the chip, so every machine has its own; never changes)
//  Code: random 6 digits, made once and kept in flash (Preferences),
//        shown on the LCD at start-up. The app needs ID + code to add the machine.
//
//  All Firebase data for this machine lives under /devices/<ID>/...
// ─────────────────────────────────────────

void device_init() {
  uint64_t mac = ESP.getEfuseMac();            // byte 0 = first byte of the MAC
  char id[12];
  snprintf(id, sizeof(id), "SV-%02X%02X%02X",
           (unsigned)((mac >> 24) & 0xFF),
           (unsigned)((mac >> 32) & 0xFF),
           (unsigned)((mac >> 40) & 0xFF));
  DEVICE_ID = String(id);

  Preferences prefs;
  prefs.begin("sanivend", false);
  PAIR_CODE = prefs.getString("pair", "");
  if (PAIR_CODE.length() != 6) {              // first start: make a code and keep it
    char code[8];
    snprintf(code, sizeof(code), "%06lu", (unsigned long)(esp_random() % 1000000UL));
    PAIR_CODE = String(code);
    prefs.putString("pair", PAIR_CODE);
  }
  prefs.end();

  Serial.println("[Device] Machine ID: " + DEVICE_ID + " | Pairing code: " + PAIR_CODE);
}
