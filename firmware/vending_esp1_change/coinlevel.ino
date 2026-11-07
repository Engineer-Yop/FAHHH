// ─────────────────────────────────────────
//  coinlevel.ino — 3 IR sensors watch the change coin tubes (ESP32 #1)
//
//  One IR obstacle sensor (FC-51 / TCRT5000 module) per tube, looking through
//  a small hole near the BOTTOM of the tube:
//    coins in front of it → module output LOW  → "ok"
//    nothing in front     → module output HIGH → "empty"
//
//  Wiring (each module):  VCC → 3.3V (NOT 5V: the output would then be 5V)
//                         GND → GND
//                         OUT → GPIO below
//    P1  tube → GPIO 32
//    P5  tube → GPIO 33
//    P10 tube → GPIO 4
//  Turn the module's blue potentiometer until its LED is ON with coins in
//  front and OFF with the tube empty.
//
//  Firebase: /devices/<ID>/coins  {p1:"ok"|"empty", p5:..., p10:..., updated}
//  The app shows a notification when a tube becomes empty.
//
//  Change still works if a sensor is wrong: change.ino first tries to give
//  change from the tubes that have coins, and falls back to all three.
// ─────────────────────────────────────────

#define USE_COIN_IR      1       // 0 = no IR sensors fitted (always "ok", nothing sent)

#define IR_PIN_1PESO     32
#define IR_PIN_5PESO     33
#define IR_PIN_10PESO    4
#define IR_COIN_PRESENT  LOW     // most IR modules pull OUT low when they see something
#define IR_READ_MS       100     // read the sensors every 100 ms
#define IR_STABLE_MS     2000    // a new state must hold 2 s (coins rattle while dispensing)

const int   irPin[3]   = {IR_PIN_1PESO, IR_PIN_5PESO, IR_PIN_10PESO};
const char* coinKey[3] = {"p1", "p5", "p10"};
const int   coinValue[3] = {1, 5, 10};

bool coinEmpty[3]     = {false, false, false};   // accepted (debounced) state
bool coinRaw[3]       = {false, false, false};   // latest reading
unsigned long coinRawSince[3] = {0, 0, 0};
unsigned long lastIrRead = 0;
bool coinNeedPublish  = true;                    // send once after boot, then on every change

void coinlevel_init() {
#if USE_COIN_IR
  for (int i = 0; i < 3; i++) {
    pinMode(irPin[i], INPUT_PULLUP);   // unplugged sensor reads "empty", never a false "ok"
  }
  delay(20);
  for (int i = 0; i < 3; i++) {
    coinEmpty[i] = coinRaw[i] = (digitalRead(irPin[i]) != IR_COIN_PRESENT);
    coinRawSince[i] = millis();
  }
  Serial.println("[Coins] P1=" + String(coinEmpty[0] ? "EMPTY" : "ok") +
                 "  P5="  + String(coinEmpty[1] ? "EMPTY" : "ok") +
                 "  P10=" + String(coinEmpty[2] ? "EMPTY" : "ok"));
#else
  Serial.println("[Coins] IR sensors disabled (USE_COIN_IR 0)");
#endif
}

// Index 0 = P1, 1 = P5, 2 = P10
bool coinlevel_isEmpty(int i) {
#if USE_COIN_IR
  return coinEmpty[i];
#else
  return false;
#endif
}

// Call often. Reads the sensors (any state), sends to Firebase only when told it's safe.
void coinlevel_tick(bool canUpload) {
#if USE_COIN_IR
  if (millis() - lastIrRead >= IR_READ_MS) {
    lastIrRead = millis();
    for (int i = 0; i < 3; i++) {
      bool raw = (digitalRead(irPin[i]) != IR_COIN_PRESENT);
      if (raw != coinRaw[i]) { coinRaw[i] = raw; coinRawSince[i] = millis(); }
      if (coinRaw[i] != coinEmpty[i] && millis() - coinRawSince[i] >= IR_STABLE_MS) {
        coinEmpty[i] = coinRaw[i];
        coinNeedPublish = true;
        Serial.println("[Coins] P" + String(coinValue[i]) + " tube is now " +
                       (coinEmpty[i] ? "EMPTY" : "ok"));
      }
    }
  }
  if (canUpload && coinNeedPublish) coinlevel_publish();
#endif
}

void coinlevel_publish() {
#if USE_COIN_IR
  if (!firebase_isOnline()) return;
  String payload = "{";
  for (int i = 0; i < 3; i++) {
    payload += "\"" + String(coinKey[i]) + "\":\"" + (coinEmpty[i] ? "empty" : "ok") + "\",";
  }
  payload += "\"updated\":{\".sv\":\"timestamp\"}}";
  String r;
  if (firebase_patch("/coins", payload, r) == 200) {
    coinNeedPublish = false;
    Serial.println("[Coins] Sent to Firebase: " + payload);
  }
#endif
}
