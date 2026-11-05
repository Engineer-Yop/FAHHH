// ─────────────────────────────────────────
//  uart.ino — UART2 to ESP32 #2
//  Sends:    VEND:X   CHANGE:X
//  Receives: SUCCESS:X  FAILED:X  CHANGE_DONE
// ─────────────────────────────────────────

void uart_init() {
  Serial2.begin(UART_BAUD, SERIAL_8N1, UART_RX, UART_TX);
  Serial2.setTimeout(200);
  Serial.println("[UART] UART2 initialized.");
}

// Drop any leftover bytes before a new command
void uart_flush() {
  while (Serial2.available()) Serial2.read();
}

void uart_sendVend(int tray) {
  uart_flush();
  String cmd = "VEND:" + String(tray) + "\n";
  Serial2.print(cmd);
  Serial.print("[UART] Sent: " + cmd);
}

void uart_sendChange(int amount) {
  uart_flush();
  String cmd = "CHANGE:" + String(amount) + "\n";
  Serial2.print(cmd);
  Serial.print("[UART] Sent: " + cmd);
}

// Returns "SUCCESS", "FAILED", or "" if nothing yet
String uart_readResult() {
  if (Serial2.available()) {
    String response = Serial2.readStringUntil('\n');
    response.trim();
    if (response.length() == 0) return "";
    Serial.println("[UART] Received: " + response);
    if (response.startsWith("SUCCESS:")) return "SUCCESS";
    if (response.startsWith("FAILED:"))  return "FAILED";
  }
  return "";
}

bool uart_readChangeDone() {
  if (Serial2.available()) {
    String response = Serial2.readStringUntil('\n');
    response.trim();
    if (response.length() == 0) return false;
    Serial.println("[UART] Received: " + response);
    if (response == "CHANGE_DONE") return true;
  }
  return false;
}
