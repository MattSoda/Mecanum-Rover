# Mecanum Rover Controller

A no-build web dashboard for the rover's existing Arduino and ESP32-CAM firmware.

## Run it

1. Pair the HC-05 with your computer in the operating system's Bluetooth settings. Make sure a Bluetooth serial port is available.
2. In Chrome or Edge, open a terminal in this `controller` folder and run:

   ```powershell
   py -m http.server 8000
   ```

3. Visit `http://localhost:8000`.
4. Choose **Connect HC-05**, select its Bluetooth serial port, and connect. The firmware uses 9600 baud.
5. Enter the ESP32-CAM's IP address (the Arduino serial monitor prints it after Wi-Fi connects), then choose **Start feed**.

The browser's Web Serial API requires a supported browser and a secure context; `localhost` qualifies. The controller does not use Web Bluetooth: HC-05 uses Bluetooth Classic serial, so pair it with the computer first and select its serial port through Web Serial.

The camera preview uses the ESP32 server's `/stream` endpoint. The dashboard and camera need to be reachable on the same network.
For matching camera firmware, use [`src/esp32_camera_controller/`](../src/esp32_camera_controller/) and follow its upload instructions.

The current Arduino firmware has no motor-stop watchdog if Bluetooth disconnects while a movement command is active. Test with the wheels raised and keep a safe way to cut motor power available; releasing a movement button normally sends the stop command.

## Controls

The directional pad and roll buttons send the single-character commands used by `src/mecanum_rover/mecanum_rover.ino`. Hold a direction to move; releasing it sends the stop command. Arrow keys or WASD drive, and Space stops. The camera-mount slider selects the servo's nine existing positions, and the headlight switch toggles its existing command.
