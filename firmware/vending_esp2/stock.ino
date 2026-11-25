// ─────────────────────────────────────────
//  stock.ino — real tray stock from the vend count + the ToF sensors
//
//  Two sources, checked against each other:
//    1. Vend count: every successful vend takes 1 off straight away.
//    2. ToF distance: says which counts are possible (tof_window in tof.ino).
//       full tray (100–140 mm)  -> refilled: stock = capacity (7)
//       7, 6, 5, 4 items        -> the sensor knows the count, it corrects the vend count
//       3, 2, 1, 0 items        -> all read near the back wall: the vend count decides
//  A sensor result is used only after it is the same TOF_STABLE_COUNT times in a row,
//  so a hand in the tray or a falling item doesn't change the stock.
//
//  Sensor not working (no MUX, timeouts, 8000) for TOF_FAIL_READS readings in a row:
//    that tray goes back to "stock_source":"count" and ESP32 #1 counts its sales,
//    exactly like before. When the sensor works again, ESP32 #2 takes over from the
//    stock ESP32 #1 left in Firebase.
//
//  Changes made in the app (Inventory "Fill to 7" or a typed count) are picked up
//  from Firebase every STOCK_SYNC_MS and then checked by the sensor like any count.
//
//  Writes to Firebase:
//    /devices/<ID>/trays/tray_N  {stock, capacity, stock_source:"sensor", sensor_mm}
//                                {stock_source:"count"}  when the sensor fails
//    /devices/<ID>/tof_readings  {tray_1..tray_8 (mm), updated}  every 5 s
// ─────────────────────────────────────────

int  stockValue[8];          // stock for this tray, -1 = not known yet
int  stockCandidate[8];      // sensor result waiting to be confirmed
int  stockCandidateCount[8];
int  stockPublished[8];      // last stock written to Firebase, -2 = never
int  stockMode[8];           // -1 = not decided yet, 1 = this ESP32 owns the stock ("sensor"), 0 = ESP32 #1 counts it
bool stockModeSent[8];       // Firebase already has the current stock_source
int  stockFailStreak[8];
int  stockGoodStreak[8];
int  stockDistance[8];       // last good distance in mm
int  tofRawMm[8];            // last raw distance of every tray, -1 = no reading
unsigned long tofRawLastPublish = 0;
int  stockScanTray = 0;
unsigned long stockLastScan = 0;
unsigned long stockLastRepublish = 0;
unsigned long stockLastSync = 0;
bool stockSynced = false;    // counts loaded from Firebase at least once

void stock_init() {
  for (int i = 0; i < 8; i++) {
    stockValue[i] = -1;
    stockCandidate[i] = -1;
    stockCandidateCount[i] = 0;
    stockPublished[i] = -2;
    stockMode[i] = -1;
    stockModeSent[i] = false;
    stockFailStreak[i] = 0;
    stockGoodStreak[i] = 0;
    stockDistance[i] = -1;
    tofRawMm[i] = -1;
  }
}

void stock_set(int tray, int n, const char* why) {
  if (n < 0 || stockValue[tray] == n) return;
  Serial.println("[Stock] Tray " + String(tray + 1) + ": " +
                 (stockValue[tray] < 0 ? String("?") : String(stockValue[tray])) + " -> " +
                 String(n) + " (" + why + ", " + String(stockDistance[tray]) + " mm)");
  stockValue[tray] = n;
}

// Sensor went bad or came back: switch who owns this tray's stock
bool stock_isSensor(int tray) { return stockMode[tray] == 1; }

void stock_setMode(int tray, bool sensor) {
  int m = sensor ? 1 : 0;
  if (stockMode[tray] == m) return;
  stockMode[tray] = m;
  stockModeSent[tray] = false;
  stockCandidateCount[tray] = 0;
  if (sensor) {
    // ESP32 #1 may have counted sales meanwhile: take its stock from Firebase first
    stockLastSync = 0;
    Serial.println("[Stock] Tray " + String(tray + 1) + ": sensor OK again -> measured stock");
  } else {
    Serial.println("[Stock] Tray " + String(tray + 1) + ": sensor not working -> ESP32 #1 counts sales");
  }
}

void stock_measure(int tray) {
  int d = tof_readMedian(tray, TOF_SAMPLES);
  tofRawMm[tray] = d;

  if (!tof_valid(d)) {
    stockGoodStreak[tray] = 0;
    if (++stockFailStreak[tray] >= TOF_FAIL_READS) stock_setMode(tray, false);
    return;
  }
  stockFailStreak[tray] = 0;
  stockDistance[tray] = d;
  if (!stock_isSensor(tray)) {
    if (++stockGoodStreak[tray] >= TOF_STABLE_COUNT) stock_setMode(tray, true);
    return;
  }
  if (stockValue[tray] < 0) {
    // No count yet (Firebase not read, or no stock saved): use the sensor alone if it is sure
    int n = tof_fuse(d, -1, TRAY_CAPACITY);
    if (n >= 0) stock_set(tray, n, "sensor");
    return;
  }

  int n = tof_fuse(d, stockValue[tray], TRAY_CAPACITY);
  if (n < 0 || n == stockValue[tray]) { stockCandidateCount[tray] = 0; return; }
  if (n == stockCandidate[tray]) {
    if (++stockCandidateCount[tray] >= TOF_STABLE_COUNT) {
      stock_set(tray, n, n == TRAY_CAPACITY ? "refilled" : "sensor");
      stockCandidateCount[tray] = 0;
    }
  } else {
    stockCandidate[tray] = n;
    stockCandidateCount[tray] = 1;
  }
}

// Called after every vend: a successful vend takes 1 off at once (the sensor
// confirms or corrects it a few seconds later)
void stock_afterVend(int tray, bool ok) {
  if (!ok || tray < 0 || tray > 7) return;
  stockCandidateCount[tray] = 0;
  if (stock_isSensor(tray) && stockValue[tray] > 0) stock_set(tray, stockValue[tray] - 1, "vend");
  stockLastScan = millis() + TOF_AFTER_VEND_MS;     // give the item time to fall
  stockScanTray = tray;                             // then measure this tray first
}

void stock_tick() {
  tof_checkBus();
  if ((long)(millis() - stockLastScan) >= TOF_SCAN_MS) {
    stockLastScan = millis();
    stock_measure(stockScanTray);
    stockScanTray = (stockScanTray + 1) % 8;
  }
  stock_sync();
  stock_publish();
}

// "tray_3":{"capacity":7,"price":15,"stock":4,...} -> 4 (or -1)
int stock_parse(const String& body, int tray) {
  String key = "\"tray_" + String(tray + 1) + "\":{";
  int a = body.indexOf(key);
  if (a < 0) return -1;
  int b = body.indexOf('}', a);
  int s = body.indexOf("\"stock\":", a);
  if (s < 0 || (b >= 0 && s > b)) return -1;
  String v = body.substring(s + 8, s + 12);
  v.trim();
  if (v.length() == 0 || !isDigit(v[0])) return -1;
  return v.toInt();
}

// Pick up stock changed outside this ESP32 (app refill, ESP32 #1 counting)
void stock_sync() {
#if USE_FIREBASE
  if (deviceId.length() == 0 || WiFi.status() != WL_CONNECTED) return;
  if (stockSynced && millis() - stockLastSync < STOCK_SYNC_MS) return;
  stockLastSync = millis();
  String body = fb2_get("/trays");
  if (body == "null" || body.length() < 5) return;
  for (int i = 0; i < 8; i++) {
    int fb = stock_parse(body, i);
    if (fb < 0) continue;
    if (stockValue[i] < 0 || !stock_isSensor(i) || stockPublished[i] == -2) {
      stockValue[i] = constrain(fb, 0, TRAY_CAPACITY);   // start from what Firebase has
      stockPublished[i] = fb;
    } else if (fb != stockPublished[i]) {
      stock_set(i, constrain(fb, 0, TRAY_CAPACITY), "changed in app");
      stockPublished[i] = fb;
    }
  }
  if (!stockSynced) Serial.println("[Stock] Loaded current stock from Firebase");
  stockSynced = true;
#endif
}

void stock_publish() {
#if USE_FIREBASE
  if (deviceId.length() == 0 || WiFi.status() != WL_CONNECTED) return;
  tof_publishRaw();
  bool all = (millis() - stockLastRepublish >= STOCK_REPUBLISH_MS);
  if (all) stockLastRepublish = millis();

  for (int i = 0; i < 8; i++) {
    String path = "/trays/tray_" + String(i + 1);
    if (stockMode[i] < 0) continue;                 // not decided yet: leave Firebase as it is
    if (stockMode[i] == 0) {
      // Tell ESP32 #1 to count this tray's sales (once per change)
      if (!stockModeSent[i] && fb2_patch(path, "{\"stock_source\":\"count\"}") == 200)
        stockModeSent[i] = true;
      continue;
    }
    if (stockValue[i] < 0 || !stockSynced) continue;
    if (!all && stockModeSent[i] && stockValue[i] == stockPublished[i]) continue;
    String payload = "{\"stock\":" + String(stockValue[i]) +
                     ",\"capacity\":" + String(TRAY_CAPACITY) +
                     ",\"stock_source\":\"sensor\"" +
                     ",\"sensor_mm\":" + String(stockDistance[i]) + "}";
    if (fb2_patch(path, payload) == 200) {
      stockPublished[i] = stockValue[i];
      stockModeSent[i] = true;
    }
  }
#endif
}

// Raw distances of all 8 trays -> /devices/<ID>/tof_readings (one write every 5 s)
void tof_publishRaw() {
#if USE_FIREBASE && USE_TOF
  if (millis() - tofRawLastPublish < TOF_RAW_PUBLISH_MS) return;
  tofRawLastPublish = millis();
  String p = "{";
  String line = "[ToF mm]";
  for (int i = 0; i < 8; i++) {
    p += "\"tray_" + String(i + 1) + "\":" + String(tofRawMm[i]) + ",";
    line += " T" + String(i + 1) + "=" + String(tofRawMm[i]);
    line += stock_isSensor(i) ? "(" + String(stockValue[i]) + ")" : (stockMode[i] == 0 ? "(count)" : "(?)");
  }
  p += "\"updated\":\"" + fb2_dateTime() + "\"}";
  Serial.println(line);
  fb2_patch("/tof_readings", p);
#endif
}
