  // ─────────────────────────────────────────
//  firebase.ino — Firebase log for ESP32 #2 (HTTP, no library)
//  Writes: /devices/<ID>/vend_logs/log_N   {type, tray/amount, result, datetime}
//          /devices/<ID>/vend_logs/count
//          /devices/<ID>/trays/tray_N  {stock, stock_source, sensor_mm}  (stock.ino)
//  The machine ID comes from ESP32 #1 over UART ("ID:SV-XXXXXX").
//  Only used when USE_FIREBASE is 1 (vending_esp2.ino)
// ─────────────────────────────────────────
#if USE_FIREBASE
#include <WiFi.h>
#include <HTTPClient.h>
#include <time.h>

#define WIFI_SSID      "Bhem"
#define WIFI_PASSWORD  "bababababa"
#define FIREBASE_HOST  "vending-machine-g7-default-rtdb.asia-southeast1.firebasedatabase.app"
#define FIREBASE_AUTH  "1:190857652616:android:f5de024e608f7fb02cf23d"

#define NTP_SERVER     "pool.ntp.org"
#define UTC_OFFSET_SEC 28800
#define WIFI_RETRY_MS  30000

int logCount = 0;
unsigned long lastWifiTry2 = 0;

void firebase_init() {
  Serial.print("[WiFi] Connecting to " + String(WIFI_SSID));
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  int retries = 0;
  while (WiFi.status() != WL_CONNECTED && retries < 20) { delay(500); Serial.print("."); retries++; }
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("\n[WiFi] Failed — vending still works, logs skipped.");
    return;
  }
  Serial.println("\n[WiFi] Connected! IP: " + WiFi.localIP().toString());

  configTime(UTC_OFFSET_SEC, 0, NTP_SERVER);
  struct tm t; int nr = 0;
  while (!getLocalTime(&t) && nr < 10) { delay(1000); nr++; }

  Serial.println("[Firebase] Ready. Waiting for the machine ID from ESP32 #1...");
}

void firebase_tick() {
  if (WiFi.status() != WL_CONNECTED && millis() - lastWifiTry2 > WIFI_RETRY_MS) {
    lastWifiTry2 = millis();
    WiFi.reconnect();
  }
}

String fb2_dateTime() {
  struct tm t; if (!getLocalTime(&t, 100)) return "unknown";
  char buf[22]; strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", &t); return String(buf);
}

String fb2_url(String path) {
  return "https://" + String(FIREBASE_HOST) + "/devices/" + deviceId + path +
         ".json?auth=" + String(FIREBASE_AUTH);
}

String fb2_get(String path) {
  if (WiFi.status() != WL_CONNECTED) return "null";
  HTTPClient http; http.setTimeout(5000); http.begin(fb2_url(path));
  String r = (http.GET() == 200) ? http.getString() : "null";
  http.end(); r.trim(); return r;
}

int fb2_put(String path, String payload) {
  if (WiFi.status() != WL_CONNECTED) return -1;
  HTTPClient http; http.setTimeout(5000); http.begin(fb2_url(path));
  http.addHeader("Content-Type", "application/json");
  int code = http.PUT(payload); http.end();
  if (code != 200) Serial.println("[Firebase] PUT " + path + " failed, HTTP " + String(code));
  return code;
}

// PATCH changes only the fields given (used for tray stock)
int fb2_patch(String path, String payload) {
  if (WiFi.status() != WL_CONNECTED || deviceId.length() == 0) return -1;
  HTTPClient http; http.setTimeout(5000); http.begin(fb2_url(path));
  http.addHeader("Content-Type", "application/json");
  int code = http.PATCH(payload); http.end();
  if (code != 200) Serial.println("[Firebase] PATCH " + path + " failed, HTTP " + String(code));
  return code;
}

void fb2_write(String payload) {
  if (WiFi.status() != WL_CONNECTED) return;
  if (deviceId.length() == 0) {
    Serial.println("[Firebase] No machine ID from ESP32 #1 yet — log skipped.");
    return;
  }
  if (deviceIdChanged) {                       // first log for this machine: continue its count
    deviceIdChanged = false;
    String v = fb2_get("/vend_logs/count");
    logCount = (v != "null") ? v.toInt() : 0;
  }
  logCount++;
  fb2_put("/vend_logs/log_" + String(logCount), payload);
  fb2_put("/vend_logs/count", String(logCount));
  Serial.println("[Firebase] log_" + String(logCount) + " written");
}

void firebase_logVend(int tray, bool ok) {
  fb2_write("{\"type\":\"vend\",\"tray\":" + String(tray + 1) +
            ",\"result\":\"" + String(ok ? "SUCCESS" : "FAILED") + "\"" +
            ",\"datetime\":\"" + fb2_dateTime() + "\"}");
}

void firebase_logChange(int amount) {
  fb2_write("{\"type\":\"change\",\"amount\":" + String(amount) +
            ",\"result\":\"DONE\",\"datetime\":\"" + fb2_dateTime() + "\"}");
}
#endif
