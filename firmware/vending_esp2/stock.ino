// ─────────────────────────────────────────
//  stock.ino — tray stock measured by the ToF sensors
//
//  Calibrated trays are measured one at a time in the background
//  (all 8 about every 2.4 s). A new value is accepted only when the
//  same count is seen TOF_STABLE_COUNT times in a row, so a hand or a
//  falling item doesn't change it. Right after a vend the tray is
//  measured again straight away.
//
//  Writes to Firebase: /devices/<ID>/trays/tray_N
//    {stock, capacity, stock_source:"sensor", sensor_mm}   (calibrated trays)
//    {stock_source:"count"}                                (other trays, once)
//  ESP32 #1 reads stock_source: "sensor" trays are not counted down by it,
//  and a "sensor" tray at 0 can't be selected.
// ─────────────────────────────────────────

int  stockValue[8];          // accepted count, -1 = not known yet
int  stockCandidate[8];
int  stockCandidateCount[8];
int  stockPublished[8];      // last value written to Firebase, -2 = never
int  stockDistance[8];       // last distance in mm
bool stockSourceSent[8];     // "count" marker written for uncalibrated trays
int  stockScanTray = 0;
unsigned long stockLastScan = 0;
unsigned long stockLastRepublish = 0;

void stock_init() {
  for (int i = 0; i < 8; i++) {
    stockValue[i] = -1;
    stockCandidate[i] = -1;
    stockCandidateCount[i] = 0;
    stockPublished[i] = -2;
    stockDistance[i] = -1;
    stockSourceSent[i] = false;
  }
  // First full reading at start-up, so Firebase gets real values quickly
  for (int i = 0; i < 8; i++) stock_measure(i, 7, true);
}

void stock_set(int tray, int n) {
  if (stockValue[tray] != n) {
    Serial.println("[Stock] Tray " + String(tray + 1) + ": " +
                   (stockValue[tray] < 0 ? String("?") : String(stockValue[tray])) + " -> " +
                   String(n) + " (" + String(stockDistance[tray]) + " mm)");
    stockValue[tray] = n;
  }
  stockCandidate[tray] = n;
  stockCandidateCount[tray] = TOF_STABLE_COUNT;
}

// immediate = accept this reading now (start-up, after a vend)
void stock_measure(int tray, int samples, bool immediate) {
  if (!tof_isCalibrated(tray)) return;
  int d = tof_readMedian(tray, samples);
  int n = tof_stockFromDistance(tray, d);
  if (n < 0) return;                     // no usable reading: keep the last value
  stockDistance[tray] = d;
  if (immediate) { stock_set(tray, n); return; }
  if (n == stockCandidate[tray]) {
    if (++stockCandidateCount[tray] >= TOF_STABLE_COUNT) stock_set(tray, n);
  } else {
    stockCandidate[tray] = n;
    stockCandidateCount[tray] = 1;
  }
}

// Called after every vend
void stock_afterVend(int tray, bool ok) {
  if (!ok || !tof_isCalibrated(tray)) return;
  delay(TOF_AFTER_VEND_MS);              // let the item finish falling
  stock_measure(tray, 7, true);
}

void stock_tick() {
  if (millis() - stockLastScan >= TOF_SCAN_MS) {
    stockLastScan = millis();
    stock_measure(stockScanTray, TOF_SAMPLES, false);
    stockScanTray = (stockScanTray + 1) % 8;
  }
  stock_publish();
}

void stock_publish() {
#if USE_FIREBASE
  if (deviceId.length() == 0 || WiFi.status() != WL_CONNECTED) return;
  bool all = (millis() - stockLastRepublish >= STOCK_REPUBLISH_MS);
  if (all) stockLastRepublish = millis();

  for (int i = 0; i < 8; i++) {
    String path = "/trays/tray_" + String(i + 1);
    if (!tof_isCalibrated(i)) {
      // Tell ESP32 #1 it must keep counting this tray (once per start-up)
      if (!stockSourceSent[i] && fb2_patch(path, "{\"stock_source\":\"count\"}") == 200)
        stockSourceSent[i] = true;
      continue;
    }
    if (stockValue[i] < 0) continue;
    if (!all && stockValue[i] == stockPublished[i]) continue;
    String payload = "{\"stock\":" + String(stockValue[i]) +
                     ",\"capacity\":" + String(TRAY_CAPACITY) +
                     ",\"stock_source\":\"sensor\"" +
                     ",\"sensor_mm\":" + String(stockDistance[i]) + "}";
    if (fb2_patch(path, payload) == 200) stockPublished[i] = stockValue[i];
  }
#endif
}
