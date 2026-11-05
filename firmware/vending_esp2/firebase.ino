  // ─────────────────────────────────────────
//  firebase.ino — Firebase log for ESP32 #2 (HTTP, no library)
//  Writes: /vend_logs/log_N   {type, tray/amount, result, datetime}
//          /vend_logs/count
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

  String v = fb2_get("/vend_logs/count");
  if (v != "null") logCount = v.toInt();
  Serial.println("[Firebase] Log count: " + String(logCount));
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
  return "https://" + String(FIREBASE_HOST) + path + ".json?auth=" + String(FIREBASE_AUTH);
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

void fb2_write(String payload) {
  if (WiFi.status() != WL_CONNECTED) return;
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
