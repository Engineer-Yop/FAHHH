// ─────────────────────────────────────────
//  uart.ino — UART2 from ESP32 #1
//  Receives: VEND:X   CHANGE:X
//  Sends:    SUCCESS:X  FAILED:X  CHANGE_DONE
//
//  One reader handles both commands, so a CHANGE line
//  can never be swallowed while looking for VEND.
// ─────────────────────────────────────────

void uart_init() {
  Serial2.begin(UART_BAUD, SERIAL_8N1, UART_RX, UART_TX);
  Serial2.setTimeout(200);
  Serial.println("[UART] UART2 initialized.");
}

// Returns CMD_VEND (value = tray 0–7), CMD_CHANGE (value = pesos) or CMD_NONE
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

  } else if (cmd.startsWith("CHANGE:")) {
    int amount = cmd.substring(7).toInt();
    if (amount > 0) { *value = amount; return CMD_CHANGE; }
    Serial.println("[UART] Invalid change amount: " + String(amount));
  }
  return CMD_NONE;
}

void uart_sendResult(int tray, bool success) {
  String result = (success ? "SUCCESS:" : "FAILED:") + String(tray) + "\n";
  Serial2.print(result);
  Serial.print("[UART] Sent: " + result);
}

void uart_sendChangeDone() {
  Serial2.print("CHANGE_DONE\n");
  Serial.println("[UART] Sent: CHANGE_DONE");
}
