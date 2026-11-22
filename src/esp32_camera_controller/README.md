# ESP32-CAM firmware for the rover controller

This standalone Arduino sketch is for an **ESP32-CAM AI Thinker** module. It provides the exact `http://<camera-ip>/stream` MJPEG endpoint used by the web controller, on the standard HTTP port 80. The root address also provides a small camera-only preview page.

## Upload

1. Install the Espressif **esp32** board support package in Arduino IDE.
2. Open `esp32_camera_controller.ino`.
3. In the sketch, set `WIFI_SSID` and `WIFI_PASSWORD` to the Wi-Fi network shared by the camera and the computer running the controller.
4. Select **AI Thinker ESP32-CAM** as the board. Select the matching serial port and enable PSRAM when that option is available.
5. Connect a USB-to-serial adapter: adapter TX to ESP32-CAM U0R, RX to U0T, and GND to GND. Power the board from a stable 5V supply. To flash, connect GPIO0 to GND, reset the board, and upload. After upload, disconnect GPIO0 from GND and reset again.
6. Open Serial Monitor at **115200 baud**. When Wi-Fi connects, note the printed camera IP address.
7. Start the dashboard and enter that IP address in its camera field. Choose **Start feed**.

The camera and dashboard computer must be on the same reachable network. The sketch serves an unauthenticated video stream, so use it only on a trusted local network and do not expose it directly to the internet.

If camera initialization fails, check the selected board, camera ribbon connection, stable power, and PSRAM setting. The existing `src/esp32_camera/` sketch is unchanged.
