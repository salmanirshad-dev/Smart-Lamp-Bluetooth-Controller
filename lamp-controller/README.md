# Bluetooth Smart Lamp Controller 💡

Hi there! I built this Android application to seamlessly control a physical lamp using an HC-05 Bluetooth module and a microcontroller. It is built with modern Android technologies (Kotlin, Jetpack Compose, Room Database) and features a beautiful, themeable user interface with floating panels designed for easy one-handed use.

## Features ✨
* **Bluetooth HC-05 Integration:** Connect and control physical hardware/relays reliably via Bluetooth.
* **Auto-Connection Module:** Automatically restores the connection to your lamp on app startup or if it drops (e.g., due to voltage sags or electrical back-EMF noise).
* **Smart Alarms:** Schedule your lamp to turn on or off at specific times. Perfect for waking up or setting a sleep routine!
* **Dipper Timer:** A quick countdown timer to trigger the lamp after a set duration.
* **Multiple Themes:** Cycle between handcrafted themes: Neon Sunset, Ocean Breeze, Forest Glow, Cosmic Night, and Black & White.
* **Modern UI:** Features translucent background blurs, glowing floating panels, and smooth Jetpack Compose animations.

## How to Use the App 📱

### 1. Initial Setup & Pairing
Before using the app, you need to pair your Android device with the HC-05 Bluetooth module:
1. Power on your Bluetooth lamp hardware.
2. Go to your Android phone's **Settings > Bluetooth**.
3. Find your HC-05 module in the list of available devices and pair it (the default PIN is usually `1234` or `0000`).

### 2. Connecting in the App
1. Open the app.
2. Tap the **Bluetooth connection panel** or icon.
3. Select your HC-05 module from the list of paired devices.
4. Once connected, you can use the main power button to toggle your lamp ON or OFF!
*(Tip: Open settings and enable **Auto-connect on App Start** so the app instantly connects to the lamp the next time you open it).*

### 3. Step-by-Step Guide: Setting an Alarm ⏰
You can schedule your lamp to automatically turn on or off at a specific time:
1. Tap the **Alarm icon** on the main screen to open the Alarm Settings floating panel.
2. Choose your desired time (Hour and Minute).
3. Select the desired lamp state (Turn ON or Turn OFF) for when the alarm triggers.
4. Save the alarm. 
5. The app will actively track the countdown on the home screen. Thanks to the background scheduling, it will trigger exactly on time, reusing the active Bluetooth connection or silently waking up to reconnect to the HC-05 module if needed!

### 4. Customizing Themes 🎨
1. Open the **Settings panel** from the home screen.
2. Find the Theme section.
3. Tap to cycle through the available beautiful gradients and colors until you find your favorite one.

## Technologies Used 🛠️
* **Kotlin:** Core programming language.
* **Jetpack Compose:** For building the reactive, responsive, and animated UI.
* **Room Database:** For storing local configurations, theme preferences, and alarm schedules persistently.
* **Android Bluetooth APIs:** To manage sockets and background connections securely.
* **Coroutines & Flow:** For asynchronous background tasks and real-time state updates.

---
**Developed by Salman Irshad**
