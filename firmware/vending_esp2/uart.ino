// ─────────────────────────────────────────
//  uart.ino — UART2 from ESP32 #1
//  Receives: ID:SV-XXXXXX   VEND:X
//  Sends:    SUCCESS:X  FAILED:X
// ─────────────────────────────────────────

void uart_init() {
  Serial2.begin(UART_BAUD, SERIAL_8N1, UART_RX, UART_TX);
  Serial2.setTimeout(200);
  Serial.println("[UART] UART2 initialized.");
}

// Returns CMD_VEND (value = tray 0–7) or CMD_NONE
int uart_readCommand(int *value) {
  if (!Serial2.available()) return CMD_NONE;

  String cmd = Serial2.readStringUntil('\n');
  cmd.trim();
  if (cmd.length() == 0) return CMD_NONE;
  Serial.println("[UART] Received: " + cmd);

  if (cmd.startsWith("VEND:")) {
    int tray = cmd.substring(5).toInt();
    if (tray >= 0 && tray <= 7) { *value = tray; return CMD_VEND; }
    Serial.println("[UART] Invalid tray: " + String(tray));

  } else if (cmd.startsWith("ID:")) {          // which machine this board belongs to
    String id = cmd.substring(3);
    id.trim();
    if (id.length() > 0 && id != deviceId) {
      deviceId = id;
      deviceIdChanged = true;
      Serial.println("[UART] Machine ID: " + deviceId);
    }

  }
  return CMD_NONE;
}

void uart_sendResult(int tray, bool success) {
  String result = (success ? "SUCCESS:" : "FAILED:") + String(tray) + "\n";
  Serial2.print(result);
  Serial.print("[UART] Sent: " + result);
}
