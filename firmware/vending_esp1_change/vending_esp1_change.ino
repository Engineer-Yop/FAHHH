// ─────────────────────────────────────────
//  Vending Machine — ESP32 #1 (UI & Payment)
//  Flow: Select tray → Insert coins → Vend → Change → Log to Firebase
//
//  Tabs in this sketch folder:
//    vending_esp1.ino  (this file — settings, state machine)
//    buttons.ino       PCF8574 buttons (non-blocking)
//    coin.ino          Allan 1239 PRO MAX coin acceptor
//    firebase.ino      WiFi, NTP time, Firebase logging, price sync
//                      (price is re-read from the app each time a tray button is pressed)
//    lcd.ino           LCD 2004 screens
//    uart.ino          UART2 link to ESP32 #2
//    change.ino        change servos on GPIO 25/26/27/32 (moved here from ESP32 #2)
//    device.ino        machine ID (from the chip's MAC) + pairing code for the app
//
//  Libraries: LiquidCrystal_I2C, PCF8574 (with readButton), ESP32Servo
// ─────────────────────────────────────────
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <PCF8574.h>
#include <ESP32Servo.h>   // here too: change.ino uses the Servo type in a function, and Arduino
                          // puts its function list above the other tabs' #includes
#include <Preferences.h>  // keeps the pairing code in flash (device.ino)

#define FIRMWARE_VERSION "2.0"

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
#define CHANGE_TIMEOUT_MS  60000   // no CHANGE_DONE → give up and reset

// ── Trays (prices are overwritten by Firebase) ──
#define TRAY_COUNT     8
#define TRAY_CAPACITY  7
int    trayPrices[TRAY_COUNT] = {5, 5, 5, 5, 10, 10, 10, 10};
int    trayStock[TRAY_COUNT]  = {7, 7, 7, 7, 7, 7, 7, 7};
String trayExpiry[TRAY_COUNT] = {"", "", "", "", "", "", "", ""};

// ── State machine ────────────────────────
enum State { IDLE, TRAY_SELECTED, PAYING, WAITING, CHANGING };
State currentState = IDLE;

int  selectedTray = -1;
int  coinBalance  = 0;
int  changeAmount = 0;
unsigned long stateStart = 0;

void setup() {
  Serial.begin(115200);
  Serial.println("ESP32 #1 booting...");

  Wire.begin(21, 22);

  device_init();                   // machine ID + pairing code
  lcd_init();
  buttons_init();
  coin_init();
  uart_init();
  uart_sendDeviceId();             // ESP32 #2 logs under the same machine ID
  change_init();                   // change servos on this board now
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
      int pressed = buttons_read();
      if (pressed != -1) {
        selectedTray = pressed;
        coinBalance  = 0;
        changeAmount = 0;
        lcd_showCheckingPrice(selectedTray);
        firebase_refreshPrice(selectedTray);   // latest price from the app; locked until this order ends
        Serial.println("Tray selected: " + String(selectedTray + 1) +
                       " | Price: P" + String(getTrayPrice(selectedTray)));
        lcd_showSelected(selectedTray);
        coin_inhibit(false);
        currentState = TRAY_SELECTED;
      }
      break;
    }

    // ── Tray chosen, counting coins ────────
    case TRAY_SELECTED: {
      if (coin_update() > 0) lcd_showBalance();

      // Switching trays keeps the coins already inserted
      int pressed = buttons_read();
      if (pressed != -1 && pressed != selectedTray) {
        selectedTray = pressed;
        lcd_showCheckingPrice(selectedTray);
        firebase_refreshPrice(selectedTray);   // coins inserted meanwhile are still counted
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
        changeAmount = coinBalance - getTrayPrice(selectedTray);
        Serial.println("Vend SUCCESS | Change: P" + String(changeAmount));
        lcd_showSuccess(selectedTray);
        delay(2000);

        if (changeAmount > 0) {
          lcd_showChange(changeAmount);
          change_dispense(changeAmount);       // servos are on this ESP32 now
        }
        firebase_logOrder(selectedTray, coinBalance, changeAmount);
        resetState();

      } else if (result == "FAILED" || timedOut) {
        Serial.println(timedOut ? "No reply from ESP32 #2" : "Vend FAILED");
        lcd_showFailed(selectedTray);
        delay(2000);

        // Refund everything that was inserted
        if (coinBalance > 0) {
          changeAmount = coinBalance;
          lcd_showRefund(changeAmount);
          change_dispense(changeAmount);
        }
        resetState();
      }
      break;
    }

    // ── (unused now: change is given directly in WAITING) ──
    case CHANGING: {
      resetState();
      break;
    }
  }
}

// ── Helpers ───────────────────────────────

int getTrayPrice(int tray) {
  if (tray < 0 || tray >= TRAY_COUNT) return 0;
  return trayPrices[tray];
}

void resetState() {
  selectedTray = -1;
  coinBalance  = 0;
  changeAmount = 0;
  coin_inhibit(true);
  delay(2000);
  lcd_showWelcome();
  currentState = IDLE;
}
