#include "esp_camera.h"
#include <WiFi.h>
#include "esp_http_server.h"
#include <stdio.h>
#include <string.h>

const char *WIFI_SSID = "HONOR 200";
const char *WIFI_PASSWORD = "swampyae200574";

// ESP32-CAM AI Thinker pin map
static constexpr int CAMERA_PWDN = 32;
static constexpr int CAMERA_RESET = -1;
static constexpr int CAMERA_XCLK = 0;
static constexpr int CAMERA_SIOD = 26;
static constexpr int CAMERA_SIOC = 27;
static constexpr int CAMERA_Y9 = 35;
static constexpr int CAMERA_Y8 = 34;
static constexpr int CAMERA_Y7 = 39;
static constexpr int CAMERA_Y6 = 36;
static constexpr int CAMERA_Y5 = 21;
static constexpr int CAMERA_Y4 = 19;
static constexpr int CAMERA_Y3 = 18;
static constexpr int CAMERA_Y2 = 5;
static constexpr int CAMERA_VSYNC = 25;
static constexpr int CAMERA_HREF = 23;
static constexpr int CAMERA_PCLK = 22;

static const char *STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=mjpeg-frame";
static const char *STREAM_BOUNDARY = "--mjpeg-frame\r\n";
static const char *STREAM_PART_HEADER = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";
static const char *CAMERA_PAGE = R"rawliteral(
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ESP32 Rover Camera</title>
  <style>
    *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;background:#171a19;color:#f0f1eb;font:14px/1.5 Arial,sans-serif}
    main{width:min(900px,calc(100% - 32px));padding:16px;border:1px solid #343a36;border-radius:10px;background:#1e2320}
    header{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}
    h1{margin:0;font-size:16px;font-weight:600;letter-spacing:1px}.live{color:#c9f36a;font-size:10px;letter-spacing:1px}
    img{display:block;width:100%;aspect-ratio:4/3;object-fit:contain;background:#101311;border:1px solid #454c46;border-radius:5px}
    p{margin:10px 0 0;color:#898f87;font-size:11px}
  </style>
</head>
<body>
  <main>
    <header><h1>ESP32 CAM</h1><span class="live">● LIVE FEED</span></header>
    <img src="/stream" alt="ESP32-CAM live stream">
    <p>Rover camera stream is running.</p>
  </main>
</body>
</html>
)rawliteral";

static httpd_handle_t camera_httpd = nullptr;
static uint32_t lastReconnectAttempt = 0;
static uint32_t lastServerStartAttempt = 0;
static bool cameraInitialized = false;
static bool wasConnectedToWiFi = false;

static esp_err_t rootHandler(httpd_req_t *request) {
  httpd_resp_set_type(request, "text/html; charset=utf-8");
  httpd_resp_set_hdr(request, "Cache-Control", "no-store");
  httpd_resp_set_hdr(request, "Access-Control-Allow-Origin", "*");
  return httpd_resp_send(request, CAMERA_PAGE, HTTPD_RESP_USE_STRLEN);
}

static esp_err_t streamHandler(httpd_req_t *request) {
  esp_err_t result = httpd_resp_set_type(request, STREAM_CONTENT_TYPE);
  if (result != ESP_OK) {
    return result;
  }

  httpd_resp_set_hdr(request, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(request, "Cache-Control", "no-store");

  while (true) {
    camera_fb_t *frame = esp_camera_fb_get();
    if (frame == nullptr) {
      Serial.println("Camera frame capture failed");
      result = ESP_FAIL;
      break;
    }

    if (frame->format != PIXFORMAT_JPEG) {
      Serial.println("Camera returned a non-JPEG frame");
      esp_camera_fb_return(frame);
      result = ESP_FAIL;
      break;
    }

    char partHeader[80];
    const int partHeaderLength = snprintf(
      partHeader,
      sizeof(partHeader),
      STREAM_PART_HEADER,
      static_cast<unsigned int>(frame->len)
    );
    if (partHeaderLength < 0 || static_cast<size_t>(partHeaderLength) >= sizeof(partHeader)) {
      esp_camera_fb_return(frame);
      result = ESP_FAIL;
      break;
    }

    result = httpd_resp_send_chunk(request, STREAM_BOUNDARY, strlen(STREAM_BOUNDARY));
    if (result == ESP_OK) {
      result = httpd_resp_send_chunk(request, partHeader, partHeaderLength);
    }
    if (result == ESP_OK) {
      result = httpd_resp_send_chunk(request, reinterpret_cast<const char *>(frame->buf), frame->len);
    }
    if (result == ESP_OK) {
      result = httpd_resp_send_chunk(request, "\r\n", 2);
    }

    esp_camera_fb_return(frame);
    if (result != ESP_OK) {
      break;
    }
  }

  return result;
}

static bool startCameraServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = 80;
  config.max_uri_handlers = 2;

  if (httpd_start(&camera_httpd, &config) != ESP_OK) {
    Serial.println("Failed to start camera HTTP server");
    camera_httpd = nullptr;
    return false;
  }

  httpd_uri_t rootUri = {};
  rootUri.uri = "/";
  rootUri.method = HTTP_GET;
  rootUri.handler = rootHandler;

  httpd_uri_t streamUri = {};
  streamUri.uri = "/stream";
  streamUri.method = HTTP_GET;
  streamUri.handler = streamHandler;

  if (httpd_register_uri_handler(camera_httpd, &rootUri) != ESP_OK ||
      httpd_register_uri_handler(camera_httpd, &streamUri) != ESP_OK) {
    Serial.println("Failed to register camera HTTP endpoints");
    httpd_stop(camera_httpd);
    camera_httpd = nullptr;
    return false;
  }

  Serial.print("Camera ready at http://");
  Serial.println(WiFi.localIP());
  Serial.println("Stream endpoint: /stream");
  return true;
}

static bool initializeCamera() {
  const bool hasPsram = psramFound();

  camera_config_t config = {};
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = CAMERA_Y2;
  config.pin_d1 = CAMERA_Y3;
  config.pin_d2 = CAMERA_Y4;
  config.pin_d3 = CAMERA_Y5;
  config.pin_d4 = CAMERA_Y6;
  config.pin_d5 = CAMERA_Y7;
  config.pin_d6 = CAMERA_Y8;
  config.pin_d7 = CAMERA_Y9;
  config.pin_xclk = CAMERA_XCLK;
  config.pin_pclk = CAMERA_PCLK;
  config.pin_vsync = CAMERA_VSYNC;
  config.pin_href = CAMERA_HREF;
  config.pin_sccb_sda = CAMERA_SIOD;
  config.pin_sccb_scl = CAMERA_SIOC;
  config.pin_pwdn = CAMERA_PWDN;
  config.pin_reset = CAMERA_RESET;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_VGA;
  config.jpeg_quality = 12;
  config.fb_count = hasPsram ? 2 : 1;
  config.fb_location = hasPsram ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM;
  config.grab_mode = hasPsram ? CAMERA_GRAB_LATEST : CAMERA_GRAB_WHEN_EMPTY;

  const esp_err_t error = esp_camera_init(&config);
  if (error != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x\n", error);
    return false;
  }

  Serial.printf("Camera initialized (PSRAM: %s)\n", hasPsram ? "yes" : "no");
  return true;
}

static void connectToWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  lastReconnectAttempt = millis();
  Serial.print("Connecting to Wi-Fi");
}

void setup() {
  Serial.begin(115200);
  Serial.println();
  Serial.println("Mecanum Rover ESP32-CAM");

  if (strcmp(WIFI_SSID, "YOUR_WIFI_SSID") == 0 || strlen(WIFI_SSID) == 0) {
    Serial.println("Set WIFI_SSID and WIFI_PASSWORD in this sketch before uploading.");
    return;
  }

  if (!initializeCamera()) {
    return;
  }
  cameraInitialized = true;

  connectToWiFi();
}

void loop() {
  if (!cameraInitialized) {
    delay(1000);
    return;
  }

  if (WiFi.status() == WL_CONNECTED) {
    if (!wasConnectedToWiFi) {
      wasConnectedToWiFi = true;
      Serial.print("Wi-Fi connected: ");
      Serial.println(WiFi.localIP());
    }
    if (camera_httpd == nullptr && millis() - lastServerStartAttempt >= 5000) {
      lastServerStartAttempt = millis();
      startCameraServer();
    }
  } else {
    wasConnectedToWiFi = false;
    if (millis() - lastReconnectAttempt >= 10000) {
      lastReconnectAttempt = millis();
      Serial.println("Wi-Fi disconnected; retrying...");
      WiFi.reconnect();
    }
  }

  delay(250);
}
