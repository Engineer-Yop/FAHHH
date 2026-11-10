// ─────────────────────────────────────────
//  firebase.ino — Firebase Realtime Database (HTTP, no library)
//  ESP32 #1 (UI & Payment)
//
//  Everything is under this machine's own folder: /devices/<DEVICE_ID>/...
//  Writes:  info (id, pairing code, last_seen heartbeat every minute)
//           orders/order_N, orders/summary
//           totals/daily/YYYY-MM-DD, totals/weekly/YYYY-Www
//           trays/tray_N/stock, trays/tray_N/capacity, notifications/expiry_tray_N
//  Reads:   trays            all trays (price, expiry, stock) in one request, every minute while idle
//           trays/tray_N          again each time that tray's button is pressed (price, stock, stock_source)
//  Stock: trays with "stock_source":"sensor" are measured by ESP32 #2 (ToF), not counted here.
//
//  Price and expiry are set from the Android app. A new price is used on the
//  next button press — no need to upload a new program.
// ─────────────────────────────────────────
#include <WiFi.h>
#include <HTTPClient.h>
#include <time.h>

#define WIFI_SSID      "Bhem"
#define WIFI_PASSWORD  "bababababa"
#define FIREBASE_HOST  "vending-machine-g7-default-rtdb.asia-southeast1.firebasedatabase.app"
#define FIREBASE_AUTH  "1:190857652616:android:f5de024e608f7fb02cf23d"

// Three time servers: if the hotspot can't reach one, another may answer
#define NTP_SERVER_1   "pool.ntp.org"
#define NTP_SERVER_2   "time.google.com"
#define NTP_SERVER_3   "ph.pool.ntp.org"
#define UTC_OFFSET_SEC 28800          // UTC+8 Philippines
#define DST_OFFSET     0

#define TRAY_SYNC_MS      60000       // re-read all tray prices/expiry/stock every minute while idle
#define PRICE_CHECK_MS    3000        // longest wait for a price when a tray button is pressed
#define WIFI_RETRY_MS     30000
#define NTP_RETRY_MS      30000
#define HTTP_TIMEOUT_MS   5000

int    orderCount    = 0;             // last order_N used
bool   cloudReady    = false;         // clock started + order count and trays loaded
String lastExpiryCheckDate = "";
unsigned long lastTraySync = 0;
unsigned long lastWifiTry  = 0;
unsigned long lastNtpTry   = 0;

// ─────────────────────────────────────────

void firebase_init() {
  Serial.print("[WiFi] Connecting to " + String(WIFI_SSID));
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int retries = 0;
  while (WiFi.status() != WL_CONNECTED && retries < 20) { delay(500); Serial.print("."); retries++; }
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("\n[WiFi] Not connected yet — machine still works, will keep retrying.");
    return;                            // firebase_tick() finishes the setup once WiFi comes up
  }
  Serial.println("\n[WiFi] Connected! IP: " + WiFi.localIP().toString());
  firebase_onConnected();
}

// Runs the first time WiFi is up — at boot, or later if WiFi connected late.
// (Before, a late connection skipped the clock setup, which caused the "unknown" dates.)
void firebase_onConnected() {
  startNtp();
  if (waitForTime(10000)) Serial.println("[NTP] Time: " + getFormattedDateTime());
  else                    Serial.println("[NTP] No time yet — will keep retrying.");

  firebase_registerDevice();          // so the app can find and pair this machine
  firebase_loadOrderCount();
  firebase_syncTrays();                // prices, expiry (+ expiry check), stock
  lastExpiryCheckDate = getFormattedDate();
  lastTraySync = millis();
  cloudReady = true;
}

// Call from loop() while IDLE
void firebase_tick() {
  if (WiFi.status() != WL_CONNECTED) {
    if (millis() - lastWifiTry > WIFI_RETRY_MS) {
      lastWifiTry = millis();
      Serial.println("[WiFi] Reconnecting...");
      WiFi.reconnect();
    }
    return;
  }

  if (!cloudReady) {
    Serial.println("[WiFi] Connected — setting the clock and loading data.");
    firebase_onConnected();
    return;
  }

  if (!timeIsSet() && millis() - lastNtpTry > NTP_RETRY_MS) {
    Serial.println("[NTP] Still no time, asking again...");
    startNtp();
  }

  if (millis() - lastTraySync > TRAY_SYNC_MS) {
    firebase_heartbeat();              // app shows "Online" while this keeps coming
    firebase_syncTrays();
    lastTraySync = millis();
  }

  String today = getFormattedDate();
  if (today != "unknown" && today != lastExpiryCheckDate) {
    firebase_checkExpiry();            // new day → days left changed
    lastExpiryCheckDate = today;
  }
}

// ── Time helpers ──────────────────────────

void startNtp() {
  configTime(UTC_OFFSET_SEC, DST_OFFSET, NTP_SERVER_1, NTP_SERVER_2, NTP_SERVER_3);
  lastNtpTry = millis();
}

// The clock starts at 1970 until NTP answers
bool timeIsSet() {
  return time(nullptr) > 1700000000;   // any date after Nov 2023
}

bool waitForTime(unsigned long ms) {
  unsigned long start = millis();
  while (!timeIsSet() && millis() - start < ms) delay(200);
  return timeIsSet();
}

String getFormattedDate() {
  struct tm t; if (!timeIsSet() || !getLocalTime(&t, 100)) return "unknown";
  char buf[12]; strftime(buf, sizeof(buf), "%Y-%m-%d", &t); return String(buf);
}
String getFormattedTime() {
  struct tm t; if (!timeIsSet() || !getLocalTime(&t, 100)) return "unknown";
  char buf[10]; strftime(buf, sizeof(buf), "%H:%M:%S", &t); return String(buf);
}
String getFormattedDateTime() { return getFormattedDate() + " " + getFormattedTime(); }
String getWeekKey() {
  struct tm t; if (!timeIsSet() || !getLocalTime(&t, 100)) return "unknown";
  char buf[12]; strftime(buf, sizeof(buf), "%Y-W%V", &t); return String(buf);
}

// ── HTTP helpers ──────────────────────────

// Sends one request. Returns the HTTP code (200 = OK, below 0 = no connection).
// The reply is put in 'response'.
int firebase_send(const char *method, String path, String query, String payload,
                  String &response, int timeoutMs) {
  response = "";
  if (WiFi.status() != WL_CONNECTED) return -1;
  HTTPClient http;
  http.setConnectTimeout(timeoutMs);
  http.setTimeout(timeoutMs);
  // Every path is inside this machine's folder: /devices/<DEVICE_ID><path>
  http.begin("https://" + String(FIREBASE_HOST) + "/devices/" + DEVICE_ID + path +
             ".json?auth=" + String(FIREBASE_AUTH) + query);
  if (payload.length() > 0) http.addHeader("Content-Type", "application/json");
  int code = http.sendRequest(method, payload);
  if (code == 200) { response = http.getString(); response.trim(); }
  http.end();
  if (code != 200) Serial.println("[Firebase] " + String(method) + " " + path + " failed, HTTP " + String(code));
  return code;
}

// "null" when the value is missing or Firebase can't be reached
String firebase_get(String path) {
  String r;
  return (firebase_send("GET", path, "", "", r, HTTP_TIMEOUT_MS) == 200) ? r : "null";
}

int firebase_put(String path, String payload) {
  String r;
  return firebase_send("PUT", path, "", payload, r, HTTP_TIMEOUT_MS);
}

// PATCH changes only the fields given and keeps the rest
int firebase_patch(String path, String payload, String &response) {
  return firebase_send("PATCH", path, "", payload, response, HTTP_TIMEOUT_MS);
}

// Tells Firebase to add 'amount' to the stored number (works even if a reboot happened)
String fbIncrement(int amount) {
  return "{\".sv\":{\"increment\":" + String(amount) + "}}";
}

// ── Small JSON readers (Firebase tray objects only hold plain values) ──

// Text inside "key":{ ... }
String jsonObject(const String &json, const String &key) {
  int k = json.indexOf("\"" + key + "\":{");
  if (k < 0) return "";
  int start = json.indexOf('{', k);
  int end   = json.indexOf('}', start);
  if (end < 0) return "";
  return json.substring(start + 1, end);
}

// Number after "key": (also accepts a number saved as text). 'fallback' if missing.
int jsonInt(const String &obj, const String &key, int fallback) {
  int k = obj.indexOf("\"" + key + "\":");
  if (k < 0) return fallback;
  int p = k + key.length() + 3;
  if (p < (int)obj.length() && obj[p] == '"') p++;
  if (p >= (int)obj.length() || !(isDigit(obj[p]) || obj[p] == '-')) return fallback;
  return obj.substring(p).toInt();
}

// Text after "key":"  ("" if missing)
String jsonString(const String &obj, const String &key) {
  int k = obj.indexOf("\"" + key + "\":\"");
  if (k < 0) return "";
  int start = k + key.length() + 4;
  int end   = obj.indexOf('"', start);
  if (end < 0) return "";
  return obj.substring(start, end);
}

// ── Load from Firebase ────────────────────

// Tells the app this machine exists: ID, pairing code, firmware, and a "last seen" time
void firebase_registerDevice() {
  String r;
  String payload = "{\"device_id\":\"" + DEVICE_ID + "\"" +
                   ",\"pair_code\":\"" + PAIR_CODE + "\"" +
                   ",\"model\":\"SaniVend\"" +
                   ",\"firmware\":\"" + String(FIRMWARE_VERSION) + "\"" +
                   ",\"trays\":" + String(TRAY_COUNT) +
                   ",\"last_boot\":\"" + getFormattedDateTime() + "\"" +
                   ",\"last_seen\":{\".sv\":\"timestamp\"}}";
  if (firebase_patch("/info", payload, r) == 200)
    Serial.println("[Firebase] Registered as /devices/" + DEVICE_ID);
}

// Every minute: Firebase stamps its own clock, so the app can show Online/Offline
void firebase_heartbeat() {
  firebase_put("/info/last_seen", "{\".sv\":\"timestamp\"}");
}

// Continue numbering after the highest order_N already in Firebase,
// so an old order is never overwritten (even if /orders/summary is missing)
void firebase_loadOrderCount() {
  String body;
  if (firebase_send("GET", "/orders", "&shallow=true", "", body, HTTP_TIMEOUT_MS) != 200) {
    Serial.println("[Firebase] Couldn't read orders — numbering stays at " + String(orderCount));
    return;
  }
  int highest = 0, pos = 0;
  while ((pos = body.indexOf("\"order_", pos)) >= 0) {
    pos += 7;
    int n = body.substring(pos, pos + 8).toInt();
    if (n > highest) highest = n;
  }
  if (highest > orderCount) orderCount = highest;
  Serial.println("[Firebase] Last order: order_" + String(orderCount));
}

// One request for every tray: {"tray_1":{"price":5,"expiry":"2026-12-26","stock":7,...},...}
// Trays not set up in the app yet keep their default price.
void firebase_syncTrays() {
  String body;
  if (firebase_send("GET", "/trays", "", "", body, HTTP_TIMEOUT_MS) != 200) {
    Serial.println("[Firebase] Tray sync failed — keeping current prices.");
    return;
  }
  if (body == "null") body = "";
  bool expiryChanged = false;
  for (int i = 0; i < TRAY_COUNT; i++) {
    String obj = jsonObject(body, "tray_" + String(i + 1));
    if (obj.length() == 0) {           // new machine: create the tray so the app can show it
      String r;
      firebase_patch("/trays/tray_" + String(i + 1),
        "{\"stock\":" + String(trayStock[i]) + ",\"capacity\":" + String(TRAY_CAPACITY) +
        ",\"price\":" + String(trayPrices[i]) + "}", r);
      continue;
    }

    int price = jsonInt(obj, "price", -1);
    if (price > 0 && price != trayPrices[i]) {
      Serial.println("[Price] Tray " + String(i + 1) + ": P" + String(trayPrices[i]) + " -> P" + String(price));
      trayPrices[i] = price;
    }

    int stock = jsonInt(obj, "stock", -1);
    if (stock >= 0) trayStock[i] = stock;
    traySensor[i] = (jsonString(obj, "stock_source") == "sensor");
    String product = jsonString(obj, "product");
    if (product.length() && product != trayProduct[i]) {
      trayProduct[i] = product;
      Serial.println("[Product] Tray " + String(i + 1) + ": " + product);
    }

    String expiry = jsonString(obj, "expiry");
    if (expiry != trayExpiry[i]) {
      trayExpiry[i] = expiry;
      expiryChanged = true;
      Serial.println("[Expiry] Tray " + String(i + 1) + ": " + (expiry.length() ? expiry : String("(none)")));
    }
  }
  if (expiryChanged) firebase_checkExpiry();
}

// Called when a tray button is pressed: use the price the app has right now,
// and the latest stock (measured by ESP32 #2 for sensor trays).
// If Firebase doesn't answer in time, the last known values are used.
void firebase_refreshPrice(int tray) {
  if (tray < 0 || tray >= TRAY_COUNT) return;
  String body;
  int code = firebase_send("GET", "/trays/tray_" + String(tray + 1), "", "", body, PRICE_CHECK_MS);
  if (code != 200 || body == "null") {
    Serial.println("[Price] Tray " + String(tray + 1) + ": no answer, using P" + String(trayPrices[tray]));
    return;
  }
  int price = jsonInt(body, "price", -1);
  if (price > 0 && price != trayPrices[tray]) {
    Serial.println("[Price] Tray " + String(tray + 1) + ": P" + String(trayPrices[tray]) + " -> P" + String(price));
    trayPrices[tray] = price;
  }
  int stock = jsonInt(body, "stock", -1);
  if (stock >= 0) trayStock[tray] = stock;
  traySensor[tray] = (jsonString(body, "stock_source") == "sensor");
  String product = jsonString(body, "product");
  if (product.length()) trayProduct[tray] = product;
}

// ── Expiry check ──────────────────────────

int daysUntilExpiry(String expiryDate) {
  if (expiryDate.length() < 10) return 999;
  struct tm now = {};
  if (!timeIsSet() || !getLocalTime(&now, 100)) return 999;
  struct tm today = {};
  today.tm_year = now.tm_year; today.tm_mon = now.tm_mon; today.tm_mday = now.tm_mday;
  struct tm expiry = {};
  int y, m, d;
  if (sscanf(expiryDate.c_str(), "%d-%d-%d", &y, &m, &d) != 3) return 999;
  expiry.tm_year = y - 1900; expiry.tm_mon = m - 1; expiry.tm_mday = d;
  return (int)((mktime(&expiry) - mktime(&today)) / 86400);
}

void firebase_checkExpiry() {
  if (WiFi.status() != WL_CONNECTED || !timeIsSet()) return;
  Serial.println("[Firebase] Checking expiry...");
  for (int i = 0; i < TRAY_COUNT; i++) {
    if (trayExpiry[i].length() < 10) continue;
    int days = daysUntilExpiry(trayExpiry[i]);
    Serial.println("[Expiry] Tray " + String(i + 1) + ": " + String(days) + " day(s) left");
    if (days <= 7) {
      String msg = (days < 0) ? "Tray " + String(i + 1) + " is EXPIRED!"
                              : "Tray " + String(i + 1) + " expires in " + String(days) + " day(s)!";
      String payload = "{\"tray\":" + String(i + 1) +
                       ",\"expiry\":\"" + trayExpiry[i] + "\"" +
                       ",\"days_left\":" + String(days) +
                       ",\"checked\":\"" + getFormattedDate() + "\"" +
                       ",\"message\":\"" + msg + "\"}";
      if (firebase_put("/notifications/expiry_tray_" + String(i + 1), payload) == 200)
        Serial.println("[Firebase] Alert sent: " + msg);
    }
  }
}

// ── Log one completed order ───────────────

void firebase_logOrder(int tray, int amountPaid, int change) {
  if (WiFi.status() != WL_CONNECTED) { Serial.println("[Firebase] Not connected — order not logged."); return; }

  // Last chance to get the clock before saving
  if (!timeIsSet()) { startNtp(); waitForTime(3000); }
  bool   haveTime = timeIsSet();
  String date     = getFormattedDate();
  String weekKey  = getWeekKey();
  String dt       = getFormattedDateTime();

  int price = trayPrices[tray];        // same price the customer paid against
  orderCount++;
  bool takeOne = trayStock[tray] > 0;
  if (takeOne) trayStock[tray]--;        // local copy; sensor trays get the real value from ESP32 #2

  // 1. The order itself
  firebase_put("/orders/order_" + String(orderCount),
    "{\"tray\":" + String(tray + 1) +
    ",\"amount_paid\":" + String(amountPaid) +
    ",\"change\":" + String(change) +
    ",\"price\":" + String(price) +
    (trayProduct[tray].length() ? ",\"product\":\"" + trayProduct[tray] + "\"" : String("")) +
    ",\"datetime\":\"" + dt + "\"" +
    ",\"date\":\"" + date + "\"" +
    ",\"week\":\"" + weekKey + "\"" +
    ",\"status\":\"SUCCESS\"}");

  // 2. Running totals — Firebase adds the numbers, so a reboot can't reset them
  String resp;
  firebase_patch("/orders/summary",
    "{\"total_revenue\":" + fbIncrement(price) +
    ",\"total_orders\":"  + fbIncrement(1) +
    ",\"last_updated\":\"" + dt + "\"}", resp);

  int todayTotal = -1;
  if (haveTime) {
    firebase_patch("/totals/daily/" + date,
      "{\"total\":" + fbIncrement(price) + ",\"orders\":" + fbIncrement(1) +
      ",\"date\":\"" + date + "\"}", resp);
    todayTotal = jsonInt(resp, "total", -1);

    firebase_patch("/totals/weekly/" + weekKey,
      "{\"total\":" + fbIncrement(price) + ",\"orders\":" + fbIncrement(1) +
      ",\"week\":\"" + weekKey + "\"}", resp);
  } else {
    Serial.println("[Firebase] No clock — daily/weekly totals skipped for this order (no date to file it under).");
  }

  // 3. Stock only — price and expiry belong to the app, so they are not overwritten.
  //    Firebase subtracts 1 itself, so a refill done in the app at the same time isn't lost.
  //    Sensor trays are skipped: ESP32 #2 measures them and writes the stock.
  if (takeOne && !traySensor[tray]) {
    firebase_patch("/trays/tray_" + String(tray + 1),
      "{\"stock\":" + fbIncrement(-1) + ",\"capacity\":" + String(TRAY_CAPACITY) + "}", resp);
    int s = jsonInt(resp, "stock", -1);
    if (s >= 0) trayStock[tray] = s;
  }

  String line = "[Firebase] order_" + String(orderCount) + " logged | Tray " + String(tray + 1) +
                " | Price P" + String(price) + " | Paid P" + String(amountPaid) +
                " | Change P" + String(change);
  if (todayTotal >= 0) line += " | Today P" + String(todayTotal);
  line += " | Stock " + String(trayStock[tray]) + "/" + String(TRAY_CAPACITY);
  Serial.println(line);
}
