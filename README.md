# SaniVend

Automatic-inventory vending machine for hygiene products (dual ESP32) with an Android app for sales monitoring, inventory tracking and product settings, connected through Firebase Realtime Database.

## What's in this repo

| Folder | What it is |
|---|---|
| `app/` | Android app (Android Studio, Java + Kotlin) |
| `firmware/vending_esp1_change/` | ESP32 #1 — buttons, coin acceptor, LCD, change servos, Firebase logging |
| `firmware/vending_esp2/` | ESP32 #2 — vend servos (PCA9685), optional ToF sensors, vend log |

## Trays

| Tray | Product | Tray | Product |
|---|---|---|---|
| 1 | Sisters (sanitary napkin) | 5 | Femme (facial tissue) |
| 2 | Charmee (sanitary napkin) | 6 | Blest (facial tissue) |
| 3 | Kleenfant (wet wipes) | 7 | Hygienix (hand sanitizer) |
| 4 | Giggles (wet wipes) | 8 | Alcosafe (hand sanitizer) |

## Firebase structure

| Path | Written by | Contents |
|---|---|---|
| `/trays/tray_N` | app: `price`, `expiry`, `product` · ESP32 #1: `stock`, `capacity` | price in whole pesos, expiry `yyyy-MM-dd` |
| `/orders/order_N` | ESP32 #1 | tray, amount_paid, change, price, datetime, date, week, status |
| `/orders/summary` | ESP32 #1 | total_revenue, total_orders, last_updated (server-side increments) |
| `/totals/daily/<yyyy-MM-dd>`, `/totals/weekly/<yyyy-Www>` | ESP32 #1 | total, orders (server-side increments) |
| `/notifications/expiry_tray_N` | ESP32 #1 | written when a tray is 7 days or less from expiry |
| `/vend_logs/log_N` | ESP32 #2 | vend / change results |

## Features in this version

- **Change the price from the app, no re-upload.** Products → Edit Price saves to `/trays/tray_N/price`. ESP32 #1 reads the tray's price every time its button is pressed (LCD shows "Checking price...") and re-syncs all trays every minute while idle. The price is locked for the rest of that order.
- **Expiry dates from the app.** Edit Expiry uses a date picker and saves `yyyy-MM-dd`, the format the ESP32 reads.
- **Stock and expiry notifications.**
  - Low stock (2 or fewer left) and out of stock, per tray.
  - Expiring within 7 days, expiring today, and expired (reminds once a day).
  - Live while the app is running; every 15 minutes in the background when it's closed.
  - Each alert is sent once per change (one fewer item, one fewer day) and cleared when the tray is refilled or given a new date. Tapping it opens the Inventory or Products screen.
- **Inventory screen** reads the live stock the ESP32 writes.
- **Clock fix on the ESP32.** Time is set on every (late) WiFi connection with three NTP servers, so orders no longer get "unknown" dates; totals use Firebase server-side increments and never go under an "unknown" key.

## Building

- **App:** open the project in Android Studio and press Run.
- **Unit tests:** right-click `app/src/test/java/com/example/sanivend/AlertRulesTest.java` → Run.
- **Firmware:** open each sketch folder in Arduino IDE (board: ESP32 Dev Module).
  - ESP32 #1 needs LiquidCrystal_I2C, PCF8574 and ESP32Servo.
  - ESP32 #2 needs Adafruit PWM Servo Driver (and Pololu VL53L0X when `USE_TOF` is 1).
  - Set your WiFi name and password in each `firebase.ino`.
