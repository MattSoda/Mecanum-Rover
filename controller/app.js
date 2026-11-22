"use strict";

const movementButtons = [...document.querySelectorAll(".drive-button[data-command]")];
const bluetoothButton = document.querySelector("#bluetooth-button");
const bluetoothStatus = document.querySelector("#bluetooth-status");
const bluetoothLed = document.querySelector("#bluetooth-led");
const cameraForm = document.querySelector("#camera-form");
const cameraUrl = document.querySelector("#camera-url");
const streamButton = document.querySelector("#stream-button");
const cameraStream = document.querySelector("#camera-stream");
const cameraFrame = document.querySelector(".camera-frame");
const cameraPlaceholder = document.querySelector("#camera-placeholder");
const cameraStatus = document.querySelector("#camera-status-text");
const cameraLed = document.querySelector("#camera-led");
const lastCommand = document.querySelector("#last-command");
const toast = document.querySelector("#toast");
const servoAngles = [0, 22, 45, 68, 90, 112, 125, 148, 180];

let serialPort = null;
let writer = null;
let streamActive = false;
let toastTimer = 0;
let headlightOn = false;

function showToast(message, isError = false) {
  toast.textContent = message;
  toast.classList.toggle("error", isError);
  toast.classList.add("visible");
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => toast.classList.remove("visible"), 3200);
}

function setBluetoothState(connected, message) {
  bluetoothStatus.textContent = message;
  bluetoothLed.classList.toggle("online", connected);
  bluetoothLed.classList.toggle("error", !connected && message.toLowerCase().includes("error"));
  bluetoothButton.textContent = connected ? "Disconnect" : "Connect";
  movementButtons.forEach((button) => {
    if (button.dataset.command !== "S") button.disabled = !connected;
  });
  document.querySelector("#headlight-button").disabled = !connected;
  document.querySelector("#servo-position").disabled = !connected;
}

async function connectBluetooth() {
  if (!("serial" in navigator)) {
    showToast("Web Serial is unavailable. Use Chrome or Edge on localhost.", true);
    return;
  }

  try {
    serialPort = await navigator.serial.requestPort();
    await serialPort.open({ baudRate: 9600 });
    writer = serialPort.writable.getWriter();
    setBluetoothState(true, "Connected");
    showToast("HC-05 connected at 9600 baud.");
  } catch (error) {
    serialPort = null;
    writer = null;
    setBluetoothState(false, error.name === "NotFoundError" ? "Disconnected" : "Connection error");
    if (error.name !== "NotFoundError") showToast(`Could not connect: ${error.message}`, true);
  }
}

async function disconnectBluetooth() {
  if (!serialPort) return;
  const port = serialPort;
  try {
    if (writer) {
      try {
        await writer.write(new Uint8Array([83]));
      } catch (error) {
        showToast(`Could not send stop before disconnect: ${error.message}`, true);
      }
      writer.releaseLock();
      writer = null;
    }
    await port.close();
  } catch (error) {
    showToast(`Disconnect failed: ${error.message}`, true);
  } finally {
    serialPort = null;
    setBluetoothState(false, "Disconnected");
  }
}

async function sendCommand(command, label = command) {
  if (!writer) {
    showToast("Connect the HC-05 before sending rover commands.", true);
    return false;
  }
  try {
    await writer.write(new TextEncoder().encode(command));
    lastCommand.textContent = `SENT / ${label.toUpperCase()}`;
    return true;
  } catch (error) {
    setBluetoothState(false, "Connection error");
    showToast(`Command failed: ${error.message}`, true);
    return false;
  }
}

bluetoothButton.addEventListener("click", () => {
  if (serialPort) void disconnectBluetooth();
  else void connectBluetooth();
});

if ("serial" in navigator) {
  navigator.serial.addEventListener("disconnect", (event) => {
    if (event.target !== serialPort) return;
    serialPort = null;
    if (writer) {
      try {
        writer.releaseLock();
      } catch (error) {
        showToast(`HC-05 disconnected: ${error.message}`, true);
      }
      writer = null;
    }
    setBluetoothState(false, "Disconnected");
    showToast("HC-05 disconnected. Verify the rover has stopped.", true);
  });
}

for (const button of movementButtons) {
  if (button.dataset.command === "S") {
    button.addEventListener("click", () => void sendCommand("S", "Stop"));
    continue;
  }
  button.addEventListener("pointerdown", (event) => {
    if (button.disabled) return;
    event.preventDefault();
    button.setPointerCapture(event.pointerId);
    button.classList.add("pressed");
    void sendCommand(button.dataset.command, button.dataset.label);
  });
  const release = () => {
    if (!button.classList.contains("pressed")) return;
    button.classList.remove("pressed");
    void sendCommand("S", "Stop");
  };
  button.addEventListener("pointerup", release);
  button.addEventListener("pointercancel", release);
  button.addEventListener("lostpointercapture", release);
}

const keyCommands = new Map([
  ["ArrowUp", ["F", "Forward"]], ["w", ["F", "Forward"]], ["W", ["F", "Forward"]],
  ["ArrowDown", ["B", "Backward"]], ["s", ["B", "Backward"]], ["S", ["B", "Backward"]],
  ["ArrowLeft", ["X", "Strafe left"]], ["a", ["X", "Strafe left"]], ["A", ["X", "Strafe left"]],
  ["ArrowRight", ["Y", "Strafe right"]], ["d", ["Y", "Strafe right"]], ["D", ["Y", "Strafe right"]],
]);
const heldKeys = new Set();
document.addEventListener("keydown", (event) => {
  if (event.target instanceof HTMLElement && ["INPUT", "TEXTAREA"].includes(event.target.tagName)) return;
  if (event.code === "Space") {
    event.preventDefault();
    if (!event.repeat) void sendCommand("S", "Stop");
    return;
  }
  const command = keyCommands.get(event.key);
  if (!command) return;
  event.preventDefault();
  if (event.repeat || heldKeys.has(event.key)) return;
  heldKeys.add(event.key);
  void sendCommand(command[0], command[1]);
});
document.addEventListener("keyup", (event) => {
  if (!heldKeys.delete(event.key)) return;
  if (heldKeys.size === 0) void sendCommand("S", "Stop");
});
window.addEventListener("blur", () => {
  if (!heldKeys.size) return;
  heldKeys.clear();
  void sendCommand("S", "Stop");
});

function normalizeBaseUrl() {
  const value = cameraUrl.value.trim();
  if (!value) throw new Error("Enter the ESP32-CAM address first.");
  const url = new URL(value);
  if (!["http:", "https:"].includes(url.protocol)) throw new Error("Camera address must start with http:// or https://.");
  return url.href.replace(/\/+$/, "");
}

function setCameraStatus(state, message) {
  cameraStatus.textContent = message;
  cameraLed.classList.toggle("online", state === "online");
  cameraLed.classList.toggle("error", state === "error");
}

function startStream() {
  try {
    const base = normalizeBaseUrl();
    localStorage.setItem("mecanum-camera-url", base);
    streamActive = true;
    cameraPlaceholder.hidden = true;
    cameraStream.hidden = false;
    cameraStream.removeAttribute("src");
    cameraStream.src = `${base}/stream`;
    cameraFrame.classList.add("streaming");
    streamButton.innerHTML = 'Stop feed <span aria-hidden="true">■</span>';
    setCameraStatus("connecting", "CONNECTING");
  } catch (error) {
    showToast(error.message, true);
  }
}

function stopStream() {
  streamActive = false;
  cameraStream.removeAttribute("src");
  cameraStream.hidden = true;
  cameraPlaceholder.hidden = false;
  cameraFrame.classList.remove("streaming");
  streamButton.innerHTML = 'Start feed <span aria-hidden="true">↗</span>';
  setCameraStatus("offline", "OFFLINE");
}

cameraForm.addEventListener("submit", (event) => {
  event.preventDefault();
  if (streamActive) stopStream();
  else startStream();
});
cameraStream.addEventListener("load", () => {
  if (streamActive) setCameraStatus("online", "STREAMING");
});
cameraStream.addEventListener("error", () => {
  if (!streamActive) return;
  setCameraStatus("error", "FEED UNAVAILABLE");
  showToast("Could not load camera feed. Check the camera IP and Wi-Fi network.", true);
});

cameraUrl.value = localStorage.getItem("mecanum-camera-url") || "";

document.querySelector("#headlight-button").addEventListener("click", async (event) => {
  const button = event.currentTarget;
  const nextState = !headlightOn;
  if (!await sendCommand("M", nextState ? "Headlights on" : "Headlights off")) return;
  headlightOn = nextState;
  button.setAttribute("aria-pressed", String(headlightOn));
  button.setAttribute("aria-label", `Turn headlights ${headlightOn ? "off" : "on"}`);
});

document.querySelector("#servo-position").addEventListener("input", (event) => {
  const index = Number(event.currentTarget.value);
  const angle = servoAngles[index];
  document.querySelector("#servo-angle").value = `${angle}°`;
  void sendCommand(String(index), `Camera mount ${angle} degrees`);
});

setBluetoothState(false, "Disconnected");
