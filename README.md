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

Each machine has its own folder, named after its **machine ID** (e.g. `SV-A1B2C3`, made from the ESP32's factory MAC address).

| Path | Written by | Contents |
|---|---|---|
| `/devices/<ID>/info` | ESP32 #1 | device_id, pair_code, firmware, last_boot, last_seen (heartbeat every minute) |
| `/devices/<ID>/owner` | app | uid of the account that paired the machine |
| `/devices/<ID>/trays/tray_N` | app: `price`, `expiry`, `product`, refill `stock` · ESP32 #1: `stock`, `capacity` | price in whole pesos, expiry `yyyy-MM-dd` |
| `/devices/<ID>/orders/order_N` | ESP32 #1 | tray, amount_paid, change, price, datetime, date, week, status |
| `/devices/<ID>/orders/summary` | ESP32 #1 | total_revenue, total_orders, last_updated (server-side increments) |
| `/devices/<ID>/totals/daily/<yyyy-MM-dd>`, `/devices/<ID>/totals/weekly/<yyyy-Www>` | ESP32 #1 | total, orders (server-side increments) |
| `/devices/<ID>/notifications/expiry_tray_N` | ESP32 #1 | written when a tray is 7 days or less from expiry |
| `/devices/<ID>/vend_logs/log_N` | ESP32 #2 | vend / change results (gets the ID from ESP32 #1 over UART) |
| `/users/<uid>` | app | username, email, `devices/<ID>` = {name, added} |

## Features

**Accounts**
- Email and password sign-up with **email verification**; unverified accounts can't log in and can resend the link.
- Forgot password sends a reset link. Users stay signed in until they log out.

**Machines**
- Every machine has a unique ID (shown on the LCD) and a 6-digit pairing code (shown at start-up).
- **My Machines → Add Machine** needs both. A machine already paired to another account is refused.
- Each machine shows Online/Offline from its heartbeat. Long-press a machine to remove it.

**Per-machine dashboard**
- Greeting, machine status and today's sales.
- Screens: Sales Monitoring, Inventory Tracking (tap a count to refill), Products (price and expiry) and Sales Graph (Today / This Week / This Year).

**Price changes without re-upload**
- Products → Edit Price saves to the machine's `trays`. ESP32 #1 reads the price on every button press (LCD shows "Checking price...") and re-syncs every minute. The price is locked for the rest of an order.

**Stock and expiry notifications for every paired machine**
- Low stock (2 or fewer), out of stock, expiring within 7 days, expiring today, and expired.
- Live while the app runs; every 15 minutes in the background.
- Sent once per change and cleared when fixed. The machine's name is shown in the notification.

**Reliable logging**
- The clock is set on every WiFi connect (3 NTP servers), so there are no "unknown" dates.
- Totals and stock use Firebase server-side increments, so a refill in the app and a sale on the machine never overwrite each other.

## Building

- **App:** open the project in Android Studio and press Run.
- **Unit tests:** right-click the `app/src/test/java` folder → Run Tests (AlertRules, DeviceIds, SalesBuckets — 34 tests).
- **Firmware:** open each sketch folder in Arduino IDE (board: ESP32 Dev Module). Upload both boards; ESP32 #1 shows its machine ID and pairing code on the LCD at start-up.
  - ESP32 #1 needs LiquidCrystal_I2C, PCF8574 and ESP32Servo.
  - ESP32 #2 needs Adafruit PWM Servo Driver (and Pololu VL53L0X when `USE_TOF` is 1).
  - Set your WiFi name and password in each `firebase.ino`.
