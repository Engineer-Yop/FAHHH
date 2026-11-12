// ─────────────────────────────────────────
//  Vending Machine — ESP32 #1 (UI & Payment)
//  Flow: Select tray → Insert coins → Vend → Log to Firebase   (no change is given)
//
//  Tabs in this sketch folder:
//    vending_esp1.ino  (this file — settings, state machine)
//    buttons.ino       PCF8574 buttons (non-blocking)
//    coin.ino          Allan 1239 PRO MAX coin acceptor
//    firebase.ino      WiFi, NTP time, Firebase logging, price sync
//                      (price is re-read from the app each time a tray button is pressed)
//    lcd.ino           LCD 2004 screens
//    uart.ino          UART2 link to ESP32 #2
//    device.ino        machine ID (from the chip's MAC) + pairing code for the app
//
//  Libraries: LiquidCrystal_I2C, PCF8574 (with readButton)
// ─────────────────────────────────────────
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <PCF8574.h>
#include <Preferences.h>  // keeps the pairing code in flash (device.ino)

#define FIRMWARE_VERSION "2.3"

// ── This machine (set in device.ino) ─────
String DEVICE_ID = "";    // e.g. "SV-A1B2C3"
String PAIR_CODE = "";    // 6 digits, shown on the LCD at start-up

// ── LCD (change to 0x3F if 0x27 doesn't work) ──
LiquidCrystal_I2C lcd(0x27, 20, 4);

// ── PCF8574 buttons ──────────────────────
PCF8574 pcf(0x20);

// ── Coin acceptor ────────────────────────
#define COIN_PIN       34    // via BSS138 LV1, no inhibit wire

// ── UART to ESP32 #2 ─────────────────────
#define UART_RX        16
#define UART_TX        17
#define UART_BAUD      9600

// ── Timeouts ─────────────────────────────
#define VEND_TIMEOUT_MS    15000   // no reply from ESP32 #2 → treat as failed
#define SELECT_TIMEOUT_MS  120000  // tray chosen but no coin for 2 min → back to "select a tray"

// ── Trays (prices are overwritten by Firebase) ──
#define TRAY_COUNT     8
#define TRAY_CAPACITY  7
int    trayPrices[TRAY_COUNT] = {5, 5, 5, 5, 10, 10, 10, 10};
int    trayStock[TRAY_COUNT]  = {7, 7, 7, 7, 7, 7, 7, 7};
String trayExpiry[TRAY_COUNT] = {"", "", "", "", "", "", "", ""};
// Product name set in the app (Products → Change product); saved with each order so the
// app's order notification and Sales Monitoring show what was actually sold
String trayProduct[TRAY_COUNT] = {"", "", "", "", "", "", "", ""};
// true = ESP32 #2 measures this tray with its ToF sensor ("stock_source":"sensor" in Firebase):
// this board then doesn't count it down, and won't sell it when it reads 0
bool   traySensor[TRAY_COUNT] = {false, false, false, false, false, false, false, false};
#define ID_RESEND_MS   30000       // remind ESP32 #2 of the machine ID while idle

// ── State machine ────────────────────────
enum State { IDLE, TRAY_SELECTED, PAYING, WAITING, CREDIT };
State currentState = IDLE;

int  selectedTray = -1;
int  coinBalance  = 0;
unsigned long stateStart = 0;
unsigned long lastIdSend = 0;

void setup() {
  Serial.begin(115200);
  Serial.println("ESP32 #1 booting...");

  Wire.begin(21, 22);

  device_init();                   // machine ID + pairing code
  lcd_init();
  lcd_showSplash();                // "Welcome to SaniVend Machine"
  delay(3000);
  buttons_init();
  coin_init();
  uart_init();
  uart_sendDeviceId();             // ESP32 #2 logs under the same machine ID
  coin_inhibit(true);              // coins off until a tray is selected

  lcd_showDeviceInfo();            // ID + pairing code, so the owner can add it in the app
  delay(8000);

  lcd_showMessage("Connecting WiFi...", "Please wait");
  firebase_init();                 // WiFi + NTP + register machine + load trays (price/stock/expiry)

  Serial.println("ESP32 #1 ready.");
  lcd_showWelcome();
  currentState = IDLE;
}

void loop() {
  switch (currentState) {

    // ── Waiting for a tray button ──────────
    case IDLE: {
      firebase_tick();             // price sync / expiry check, only while idle
      if (millis() - lastIdSend > ID_RESEND_MS) {   // ESP32 #2 may have restarted
        lastIdSend = millis();
        uart_sendDeviceId();
      }
      int pressed = buttons_read();
      if (pressed != -1) {
        selectedTray = pressed;
        coinBalance  = 0;
        lcd_showCheckingPrice(selectedTray);
        firebase_refreshPrice(selectedTray);   // latest price (and stock) from Firebase; locked until this order ends
        if (trayIsEmpty(selectedTray)) {        // sensor says 0 items: don't take coins
          Serial.println("Tray " + String(selectedTray + 1) + " is empty (sensor).");
          lcd_showEmpty(selectedTray);
          delay(2500);
          selectedTray = -1;
          lcd_showWelcome();
          break;
        }
        Serial.println("Tray selected: " + String(selectedTray + 1) +
                       " | Price: P" + String(getTrayPrice(selectedTray)));
        lcd_showSelected(selectedTray);
        coin_inhibit(false);
        stateStart   = millis();          // start the 2-minute no-coin timer
        currentState = TRAY_SELECTED;
      }
      break;
    }

    // ── Tray chosen, counting coins ────────
    case TRAY_SELECTED: {
      if (coin_update() > 0) {
        lcd_showBalance();
        stateStart = millis();           // a coin went in: customer is still here
      }

      // Customer walked away: tray chosen, no coin for 2 minutes → start over
      if (coinBalance == 0 && millis() - stateStart > SELECT_TIMEOUT_MS) {
        Serial.println("No coin for 2 min on tray " + String(selectedTray + 1) + " → reset");
        lcd_showMessage("No coins inserted", "Please select again");
        resetState();                    // waits 2 s, then the select-a-tray screen
        break;
      }

      // Switching trays keeps the coins already inserted
      int pressed = buttons_read();
      if (pressed != -1 && pressed != selectedTray) {
        lcd_showCheckingPrice(pressed);
        firebase_refreshPrice(pressed);        // coins inserted meanwhile are still counted
        if (trayIsEmpty(pressed)) {            // keep the tray that was already chosen
          lcd_showEmpty(pressed);
          delay(2000);
          lcd_showSelected(selectedTray);
          lcd_showBalance();
          break;
        }
        selectedTray = pressed;
        stateStart   = millis();           // new tray chosen: timer starts again
        Serial.println("Tray changed to: " + String(selectedTray + 1) +
                       " | Price: P" + String(getTrayPrice(selectedTray)));
        lcd_showSelected(selectedTray);
      }

      if (coinBalance >= getTrayPrice(selectedTray)) {
        coin_inhibit(true);
        currentState = PAYING;
      }
      break;
    }

    // ── Enough coins: send vend command ────
    case PAYING: {
      Serial.println("Enough coins. Sending vend command...");
      lcd_showVending(selectedTray);
      uart_sendVend(selectedTray);
      stateStart   = millis();
      currentState = WAITING;
      break;
    }

    // ── Waiting for SUCCESS / FAILED ───────
    case WAITING: {
      String result = uart_readResult();
      bool timedOut = (result == "" && millis() - stateStart > VEND_TIMEOUT_MS);

      if (result == "SUCCESS") {
        // No change: anything paid above the price is kept by the machine
        Serial.println("Vend SUCCESS | Paid: P" + String(coinBalance));
        lcd_showSuccess(selectedTray);
        delay(2000);
        firebase_logOrder(selectedTray, coinBalance, 0);
        resetState();

      } else if (result == "FAILED" || timedOut) {
        Serial.println(timedOut ? "No reply from ESP32 #2" : "Vend FAILED");
        lcd_showFailed(selectedTray);
        delay(2000);

        // No refund possible: the coins stay as credit for another tray
        selectedTray = -1;
        lcd_showCredit();
        currentState = CREDIT;
      }
      break;
    }

    // ── Vend failed: customer picks another tray with the credit ──
    case CREDIT: {
      int pressed = buttons_read();
      if (pressed == -1) break;
      lcd_showCheckingPrice(pressed);
      firebase_refreshPrice(pressed);
      if (trayIsEmpty(pressed)) {
        lcd_showEmpty(pressed);
        delay(2000);
        lcd_showCredit();
        break;
      }
      selectedTray = pressed;
      Serial.println("Credit P" + String(coinBalance) + " used on tray " + String(selectedTray + 1));
      if (coinBalance >= getTrayPrice(selectedTray)) {
        currentState = PAYING;               // credit already covers it
      } else {
        lcd_showSelected(selectedTray);      // add coins for the rest
        coin_inhibit(false);
        stateStart = millis();
        currentState = TRAY_SELECTED;
      }
      break;
    }
  }
}

// ── Helpers ───────────────────────────────

// Only sensor-measured trays are blocked at 0; counted trays keep selling
// (their count can be off if a refill wasn't entered in the app)
bool trayIsEmpty(int tray) {
  return tray >= 0 && tray < TRAY_COUNT && traySensor[tray] && trayStock[tray] <= 0;
}

int getTrayPrice(int tray) {
  if (tray < 0 || tray >= TRAY_COUNT) return 0;
  return trayPrices[tray];
}

void resetState() {
  selectedTray = -1;
  coinBalance  = 0;
  coin_inhibit(true);
  delay(2000);
  lcd_showWelcome();
  currentState = IDLE;
}
