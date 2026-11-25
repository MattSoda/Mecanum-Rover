Mecanum Rover is an Arduino-based bluetooth controlled vehicle with mecanum wheels.
The addition of an ESP32 camera module, allowing us to capture and stream live video footage directly to our smartphone or tablet.

Features
   ✅Mecanum wheel movement (omnidirectional movement)
   ✅Bluetooth & voice control (upgradable to AI-based auto-navigation)
   ✅Live camera streaming using ESP32-CAM
   
Components Used
1. Arduino Uno
2. L293D Motor Shield
3. HC-05 Bluetooth Module
4. Esp-32 Camera Module
5. Mecanum Wheels * 4
6. Li-Po Batteries
7. Micro Servo 180'
8. 5mm white led *2 (Front light)
9. 5mm red led *2 (Back light)

Softwares Used
1. Arduino IDE
2. Arduino Car (To control Mecanum Rover with mobile phone)
   Get this app here: https://play.google.com/store/apps/details?id=com.electro_tex.bluetoothcar

Web Controller
--------------
An interactive browser dashboard for rover movement, lights, the servo camera mount, and ESP32-CAM streaming is available in [`controller/`](controller/). Follow its [setup instructions](controller/README.md). It uses Web Serial for the HC-05, so pair the module with your computer and run the dashboard from localhost in Chrome or Edge.

ESP32-CAM Controller Firmware
-----------------------------
The standalone AI-Thinker-compatible camera firmware for the web controller is in [`src/esp32_camera_controller/`](src/esp32_camera_controller/). It serves the live feed on `/stream` at port 80; see its [upload instructions](src/esp32_camera_controller/README.md).

Android Controller
------------------
A native Android app for controlling the rover over HC-05 Bluetooth Classic and viewing the ESP32-CAM stream is in [`android-app/`](android-app/). See its [Android Studio setup and install instructions](android-app/README.md).
