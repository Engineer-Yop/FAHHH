// ─────────────────────────────────────────
//  lcd.ino — LCD 2004 screens (20 columns x 4 rows)
//  Every line fits in 20 characters
// ─────────────────────────────────────────

void lcd_init() {
  lcd.init();
  lcd.backlight();
  lcd.clear();
  Serial.println("LCD initialized.");
}

// Generic two-line message (used while booting)
void lcd_showMessage(String line1, String line2) {
  lcd.clear();
  lcd.setCursor(0, 1);  lcd.print(line1);
  lcd.setCursor(0, 2);  lcd.print(line2);
}

void lcd_showWelcome() {
  lcd.clear();
  lcd.setCursor(0, 0);  lcd.print("** VENDO MACHINE **");
  lcd.setCursor(2, 1);  lcd.print("Select your tray");
  lcd.setCursor(2, 2);  lcd.print("then insert coins");
  lcd.setCursor(6, 3);  lcd.print("Ready...");
}

void lcd_showSelected(int tray) {
  lcd.clear();
  lcd.setCursor(0, 0);  lcd.print("Tray " + String(tray + 1) + " selected");
  lcd.setCursor(0, 1);  lcd.print("Price:   P" + String(getTrayPrice(tray)));
  lcd.setCursor(0, 2);  lcd.print("Balance: P" + String(coinBalance));
  lcd.setCursor(0, 3);  lcd.print("Insert coins...");
}

// Shown for about a second while the price is read from Firebase
void lcd_showCheckingPrice(int tray) {
  lcd.clear();
  lcd.setCursor(0, 0);  lcd.print("Tray " + String(tray + 1) + " selected");
  lcd.setCursor(0, 2);  lcd.print("Checking price...");
}

// Only rewrites the balance row
void lcd_showBalance() {
  lcd.setCursor(0, 2);
  lcd.print("Balance: P" + String(coinBalance) + "     ");
}

void lcd_showVending(int tray) {
  lcd.clear();
  lcd.setCursor(3, 1);  lcd.print("Please wait...");
  lcd.setCursor(2, 2);  lcd.print("Dispensing Tray " + String(tray + 1));
}

void lcd_showSuccess(int tray) {
  lcd.clear();
  lcd.setCursor(2, 1);  lcd.print("Thank you! Enjoy!");
  lcd.setCursor(2, 2);  lcd.print("Tray " + String(tray + 1) + " dispensed!");
}

void lcd_showFailed(int tray) {
  lcd.clear();
  lcd.setCursor(4, 1);  lcd.print("!! SORRY !!");
  lcd.setCursor(2, 2);  lcd.print("Tray " + String(tray + 1) + " jam/empty?");
}

void lcd_showChange(int change) {
  lcd.clear();
  lcd.setCursor(2, 1);  lcd.print("Change: P" + String(change));
  lcd.setCursor(2, 2);  lcd.print("Please collect");
}

void lcd_showRefund(int amount) {
  lcd.clear();
  lcd.setCursor(2, 1);  lcd.print("Refund: P" + String(amount));
  lcd.setCursor(2, 2);  lcd.print("Please collect");
}
