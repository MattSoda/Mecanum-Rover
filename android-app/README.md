# Mecanum Rover Android Controller

Native Android app for the rover's HC-05 serial commands and ESP32-CAM MJPEG stream. The camera view is centered in the layout; driving, headlight, and servo controls follow the web controller's dark/green design. No Wi-Fi credentials are stored in the project.

## Requirements

- Android Studio with Android SDK Platform 35 and a Java 17 runtime.
- An Android phone with Bluetooth Classic and Wi-Fi.
- The phone paired with the rover's HC-05 in Android Bluetooth settings.
- The phone connected to the same Wi-Fi network as the ESP32-CAM.

## Open and install

1. Open the `android-app` folder in Android Studio and let Gradle sync.
2. Connect an Android phone with USB debugging enabled, or start an emulator with Bluetooth support.
3. Use **Run** in Android Studio to install and launch **Mecanum Rover**.
4. On the phone, tap **Connect**, allow the **Nearby devices** permission, and select the paired HC-05.
5. Enter the ESP32-CAM IP address shown in its Serial Monitor, then tap **Start feed**.

To make a debug APK, use **Build > Build APK(s)** in Android Studio. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The app uses the HC-05's standard Bluetooth Classic SPP service at 9600 baud. The camera stream is read from `http://<camera-ip>/stream`; plain HTTP is allowed for this local camera connection. The app does not change or reflash either microcontroller.

The current Arduino rover firmware has no motor-stop watchdog if Bluetooth disconnects while a movement command is active. The app sends `S` when a movement button is released, when it is backgrounded, and before disconnecting; test with the wheels raised and keep a way to cut motor power available. Keep the unauthenticated camera stream on a trusted local network.
