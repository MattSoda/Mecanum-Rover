package com.mecanumrover.controller;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_BLUETOOTH_CONNECT = 41;
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int PAGE = Color.rgb(23, 26, 25);
    private static final int PANEL = Color.rgb(30, 35, 32);
    private static final int BUTTON = Color.rgb(43, 48, 44);
    private static final int LINE = Color.rgb(64, 71, 65);
    private static final int TEXT = Color.rgb(240, 241, 235);
    private static final int MUTED = Color.rgb(137, 143, 135);
    private static final int ACCENT = Color.rgb(201, 243, 106);
    private static final int CYAN = Color.rgb(97, 203, 238);
    private static final int RED = Color.rgb(255, 109, 104);
    private static final int[] SERVO_ANGLES = {0, 22, 45, 68, 90, 112, 125, 148, 180};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService bluetoothExecutor = Executors.newSingleThreadExecutor();
    private volatile BluetoothSocket bluetoothSocket;
    private volatile OutputStream bluetoothOutput;
    private volatile MjpegStream mjpegStream;
    private boolean bluetoothConnected;
    private boolean headlightsOn;

    private TextView bluetoothStatus;
    private TextView bluetoothLed;
    private Button bluetoothButton;
    private TextView cameraStatus;
    private TextView cameraPlaceholder;
    private ImageView cameraImage;
    private EditText cameraAddress;
    private Button cameraButton;
    private Button stopButton;
    private Button[] movementButtons;
    private Button[] rollButtons;
    private Button headlightsButton;
    private SeekBar servoSlider;
    private TextView servoAngle;
    private TextView lastCommand;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(PAGE);
        getWindow().setNavigationBarColor(PAGE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences = getSharedPreferences("rover", MODE_PRIVATE);
        buildInterface();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAGE);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(10), dp(18), dp(16));
        scroll.addView(page);
        setContentView(scroll);

        page.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        page.addView(buildCameraCard(), margins(0, 12, 0, 11));
        page.addView(buildDriveCard(), margins(0, 0, 0, 11));
        page.addView(buildToolsCard(), margins(0, 0, 0, 10));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView footerBrand = label("●  MECANUM ROVER  /  REMOTE CONTROL", 9, MUTED);
        footerBrand.setLetterSpacing(0.06f);
        footer.addView(footerBrand, new LinearLayout.LayoutParams(0, dp(32), 1));
        lastCommand = label("READY", 9, MUTED);
        lastCommand.setLetterSpacing(0.08f);
        lastCommand.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
        footer.addView(lastCommand);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView logo = label("M", 18, ACCENT);
        logo.setTypeface(Typeface.SERIF, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(circle(0x00343434, ACCENT));
        header.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = label("MECANUM  ROVER", 12, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        titleParams.leftMargin = dp(10);
        header.addView(title, titleParams);

        bluetoothLed = label("●", 10, MUTED);
        header.addView(bluetoothLed);
        bluetoothStatus = label("  Disconnected", 11, MUTED);
        header.addView(bluetoothStatus);

        bluetoothButton = actionButton("Connect", ACCENT, PAGE);
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(dp(92), dp(36));
        connectParams.leftMargin = dp(10);
        header.addView(bluetoothButton, connectParams);
        bluetoothButton.setOnClickListener(v -> {
            if (bluetoothConnected) disconnectBluetooth(true);
            else requestBluetoothPermissionAndConnect();
        });
        return header;
    }

    private View buildCameraCard() {
        LinearLayout card = panel();
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("LIVE FEED  /  ESP32-CAM", 12, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, dp(32), 1));
        cameraStatus = label("●  OFFLINE", 9, MUTED);
        heading.addView(cameraStatus);
        card.addView(heading);

        FrameLayout preview = new FrameLayout(this);
        preview.setBackground(rounded(Color.rgb(17, 20, 18), LINE, 5));
        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        previewParams.topMargin = dp(9);
        card.addView(preview, previewParams);

        cameraImage = new ImageView(this);
        cameraImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cameraImage.setVisibility(View.GONE);
        preview.addView(cameraImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout waiting = new LinearLayout(this);
        waiting.setOrientation(LinearLayout.VERTICAL);
        waiting.setGravity(Gravity.CENTER);
        TextView cameraIcon = label("◉", 30, ACCENT);
        cameraIcon.setGravity(Gravity.CENTER);
        waiting.addView(cameraIcon);
        cameraPlaceholder = label("Waiting for camera\nConnect to the rover's camera feed", 12, MUTED);
        cameraPlaceholder.setGravity(Gravity.CENTER);
        cameraPlaceholder.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams waitingTextParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        waitingTextParams.topMargin = dp(4);
        waiting.addView(cameraPlaceholder, waitingTextParams);
        preview.addView(waiting, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout connectRow = new LinearLayout(this);
        connectRow.setGravity(Gravity.CENTER_VERTICAL);
        connectRow.setPadding(dp(8), dp(4), dp(5), dp(4));
        connectRow.setBackground(rounded(PAGE, LINE, 5));
        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45));
        rowParams.topMargin = dp(9);
        card.addView(connectRow, rowParams);

        TextView ipLabel = label("IP", 9, ACCENT);
        ipLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        connectRow.addView(ipLabel);
        cameraAddress = new EditText(this);
        cameraAddress.setSingleLine(true);
        cameraAddress.setTextSize(12);
        cameraAddress.setTextColor(TEXT);
        cameraAddress.setHintTextColor(MUTED);
        cameraAddress.setHint("Camera address  ·  http://192.168.1.50");
        cameraAddress.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        cameraAddress.setPadding(dp(8), 0, dp(4), 0);
        cameraAddress.setBackgroundColor(Color.TRANSPARENT);
        cameraAddress.setText(preferences.getString("cameraAddress", ""));
        connectRow.addView(cameraAddress, new LinearLayout.LayoutParams(0, dp(38), 1));
        cameraButton = actionButton("Start feed", ACCENT, PAGE);
        connectRow.addView(cameraButton, new LinearLayout.LayoutParams(dp(102), dp(34)));
        cameraButton.setOnClickListener(v -> {
            if (mjpegStream == null) startCameraStream();
            else stopCameraStream();
        });

        return card;
    }

    private View buildDriveCard() {
        LinearLayout card = panel();
        card.addView(sectionTitle("01 / MOVEMENT", "Drive"));
        TextView hint = label("Hold a direction to move", 10, MUTED);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24));
        hintParams.topMargin = dp(3);
        card.addView(hint, hintParams);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.gravity = Gravity.CENTER_HORIZONTAL;
        gridParams.topMargin = dp(4);
        card.addView(grid, gridParams);

        movementButtons = new Button[9];
        addDriveButton(grid, "↖", "Q", "Forward left", 0, 0, false);
        addDriveButton(grid, "↑", "F", "Forward", 1, 0, false);
        addDriveButton(grid, "↗", "E", "Forward right", 2, 0, false);
        addDriveButton(grid, "←", "X", "Strafe left", 0, 1, false);
        stopButton = driveButton("STOP", true);
        stopButton.setOnClickListener(v -> sendCommand("S", "Stop"));
        addGridButton(grid, stopButton, 1, 1);
        addDriveButton(grid, "→", "Y", "Strafe right", 2, 1, false);
        addDriveButton(grid, "↙", "Z", "Back left", 0, 2, false);
        addDriveButton(grid, "↓", "B", "Backward", 1, 2, false);
        addDriveButton(grid, "↘", "C", "Back right", 2, 2, false);

        LinearLayout rolls = new LinearLayout(this);
        rolls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rollsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        rollsParams.topMargin = dp(8);
        card.addView(rolls, rollsParams);
        rollButtons = new Button[2];
        rollButtons[0] = rollButton("↶  ROLL LEFT", "L", "Roll left");
        rollButtons[1] = rollButton("↷  ROLL RIGHT", "R", "Roll right");
        rolls.addView(rollButtons[0], new LinearLayout.LayoutParams(0, dp(34), 1));
        LinearLayout.LayoutParams rightRoll = new LinearLayout.LayoutParams(0, dp(34), 1);
        rightRoll.leftMargin = dp(7);
        rolls.addView(rollButtons[1], rightRoll);

        TextView footer = label("ARROWS / WASD                         RELEASE TO STOP", 8, MUTED);
        footer.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26));
        footerParams.topMargin = dp(7);
        card.addView(footer, footerParams);
        return card;
    }

    private View buildToolsCard() {
        LinearLayout card = panel();
        card.addView(sectionTitle("03 / ROVER", "Tools"));

        LinearLayout lights = new LinearLayout(this);
        lights.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lightsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(55));
        lightsParams.topMargin = dp(6);
        card.addView(lights, lightsParams);
        TextView lightIcon = toolIcon("☼", 0xffe5c668);
        lights.addView(lightIcon);
        LinearLayout lightCopy = new LinearLayout(this);
        lightCopy.setOrientation(LinearLayout.VERTICAL);
        TextView lightTitle = label("Headlights", 12, TEXT);
        lightTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        lightCopy.addView(lightTitle);
        lightCopy.addView(label("Front lamps", 9, MUTED));
        LinearLayout.LayoutParams lightCopyParams = new LinearLayout.LayoutParams(0, -2, 1);
        lightCopyParams.leftMargin = dp(10);
        lights.addView(lightCopy, lightCopyParams);
        headlightsButton = actionButton("OFF", BUTTON, MUTED);
        lights.addView(headlightsButton, new LinearLayout.LayoutParams(dp(58), dp(34)));
        headlightsButton.setOnClickListener(v -> {
            if (!bluetoothConnected) return;
            boolean next = !headlightsOn;
            headlightsButton.setEnabled(false);
            sendCommand("M", next ? "Headlights on" : "Headlights off", success -> {
                if (success) {
                    headlightsOn = next;
                    headlightsButton.setText(headlightsOn ? "ON" : "OFF");
                    headlightsButton.setBackground(rounded(headlightsOn ? 0xff4b6630 : BUTTON,
                            headlightsOn ? ACCENT : LINE, 5));
                    headlightsButton.setTextColor(headlightsOn ? ACCENT : MUTED);
                }
                headlightsButton.setEnabled(bluetoothConnected);
            });
        });

        View divider = new View(this);
        divider.setBackgroundColor(0x22ffffff);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(10);
        dividerParams.bottomMargin = dp(12);
        card.addView(divider, dividerParams);

        LinearLayout mountHeader = new LinearLayout(this);
        mountHeader.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(mountHeader);
        mountHeader.addView(toolIcon("⌖", CYAN));
        LinearLayout mountCopy = new LinearLayout(this);
        mountCopy.setOrientation(LinearLayout.VERTICAL);
        TextView mountTitle = label("Camera mount", 12, TEXT);
        mountTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mountCopy.addView(mountTitle);
        mountCopy.addView(label("Servo position", 9, MUTED));
        LinearLayout.LayoutParams mountCopyParams = new LinearLayout.LayoutParams(0, -2, 1);
        mountCopyParams.leftMargin = dp(10);
        mountHeader.addView(mountCopy, mountCopyParams);

        LinearLayout angleRow = new LinearLayout(this);
        angleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams angleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        angleParams.topMargin = dp(8);
        card.addView(angleRow, angleParams);
        TextView angleLabel = label("ANGLE", 9, MUTED);
        angleLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        angleLabel.setLetterSpacing(0.12f);
        angleRow.addView(angleLabel, new LinearLayout.LayoutParams(0, -2, 1));
        servoAngle = label("90°", 19, CYAN);
        servoAngle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        angleRow.addView(servoAngle);

        servoSlider = new SeekBar(this);
        servoSlider.setMax(8);
        servoSlider.setProgress(4);
        servoSlider.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        servoSlider.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        card.addView(servoSlider, sliderParams);
        servoSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                servoAngle.setText(SERVO_ANGLES[progress] + "°");
                if (fromUser) sendCommand(String.valueOf(progress),
                        "Camera mount " + SERVO_ANGLES[progress] + " degrees");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        LinearLayout scale = new LinearLayout(this);
        scale.setGravity(Gravity.CENTER_VERTICAL);
        scale.addView(label("0°", 9, MUTED), new LinearLayout.LayoutParams(0, dp(18), 1));
        TextView center = label("90°", 9, MUTED);
        center.setGravity(Gravity.CENTER);
        scale.addView(center, new LinearLayout.LayoutParams(0, dp(18), 1));
        TextView end = label("180°", 9, MUTED);
        end.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        scale.addView(end, new LinearLayout.LayoutParams(0, dp(18), 1));
        card.addView(scale);
        return card;
    }

    private LinearLayout panel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(14), dp(16), dp(12));
        layout.setBackground(rounded(PANEL, 0xff343a36, 8));
        return layout;
    }

    private View sectionTitle(String eyebrow, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView top = label(eyebrow, 9, ACCENT);
        top.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.setLetterSpacing(0.12f);
        words.addView(top);
        TextView main = label(title, 20, TEXT);
        main.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        words.addView(main);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mark = label("⌁", 20, MUTED);
        row.addView(mark);
        return row;
    }

    private void addDriveButton(GridLayout grid, String icon, String command, String name,
                                int column, int row, boolean stop) {
        Button button = driveButton(icon, stop);
        movementButtons[row * 3 + column] = button;
        installHold(button, command, name);
        addGridButton(grid, button, column, row);
    }

    private void addGridButton(GridLayout grid, Button button, int column, int row) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(row), GridLayout.spec(column));
        params.width = dp(64);
        params.height = dp(59);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(button, params);
    }

    private Button driveButton(String title, boolean stop) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(stop ? 9 : 23);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setTextColor(stop ? RED : TEXT);
        button.setBackground(rounded(stop ? 0xff362725 : BUTTON,
                stop ? 0xff663b39 : LINE, 8));
        button.setEnabled(stop);
        return button;
    }

    private Button rollButton(String text, String command, String name) {
        Button button = actionButton(text, BUTTON, TEXT);
        button.setTextSize(9);
        button.setEnabled(false);
        installHold(button, command, name);
        return button;
    }

    private void installHold(Button button, String command, String name) {
        button.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (!bluetoothConnected) return false;
                view.setPressed(true);
                sendCommand(command, name);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setPressed(false);
                sendCommand("S", "Stop");
                return true;
            }
            return true;
        });
    }

    private Button actionButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setTextColor(foreground);
        button.setBackground(rounded(background, background == ACCENT ? ACCENT : LINE, 5));
        return button;
    }

    private TextView toolIcon(String symbol, int color) {
        TextView icon = label(symbol, 20, color);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(0xff292e2a, 0xff3a403a, 6));
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        return icon;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        if (stroke != fill) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private GradientDrawable circle(int fill, int stroke) {
        GradientDrawable shape = rounded(fill, stroke, 50);
        shape.setShape(GradientDrawable.OVAL);
        return shape;
    }

    private LinearLayout.LayoutParams margins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void requestBluetoothPermissionAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_CONNECT);
            return;
        }
        showBondedDevices();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_BLUETOOTH_CONNECT) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            showBondedDevices();
        } else {
            showToast("Allow Nearby devices permission to connect to the HC-05.");
        }
    }

    private void showBondedDevices() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            showToast("This Android device does not support Bluetooth.");
            return;
        }
        try {
            if (!adapter.isEnabled()) {
                startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                showToast("Turn on Bluetooth and pair with HC-05, then tap Connect.");
                return;
            }
            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            List<BluetoothDevice> devices = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (BluetoothDevice device : paired) {
                devices.add(device);
                String name = device.getName();
                names.add(name == null || name.trim().isEmpty() ? device.getAddress() : name);
            }
            if (devices.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle("Pair the HC-05 first")
                        .setMessage("Open Android Bluetooth settings, pair with the HC-05, then return and tap Connect.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Bluetooth settings", (dialog, which) ->
                                startActivity(new Intent(
                                        android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)))
                        .show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Choose the rover's HC-05")
                    .setItems(names.toArray(new String[0]),
                            (dialog, which) -> connectToDevice(devices.get(which)))
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (SecurityException error) {
            showToast("Bluetooth permission is required to list paired devices.");
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        setBluetoothState(false, "Connecting…");
        bluetoothExecutor.execute(() -> {
            BluetoothSocket socket = null;
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) adapter.cancelDiscovery();
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket = socket;
                socket.connect();
                bluetoothOutput = socket.getOutputStream();
                BluetoothSocket connectedSocket = socket;
                mainHandler.post(() -> {
                    if (bluetoothSocket != connectedSocket) return;
                    setBluetoothState(true, "Connected");
                    showToast("Connected to HC-05 at 9600 baud.");
                });
            } catch (IOException | SecurityException error) {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
                bluetoothSocket = null;
                bluetoothOutput = null;
                mainHandler.post(() -> {
                    setBluetoothState(false, "Disconnected");
                    showToast("Could not connect. Confirm HC-05 is paired and not in use.");
                });
            }
        });
    }

    private void disconnectBluetooth(boolean sendStop) {
        BluetoothSocket socket = bluetoothSocket;
        OutputStream output = bluetoothOutput;
        bluetoothSocket = null;
        bluetoothOutput = null;
        setBluetoothState(false, "Disconnected");
        bluetoothExecutor.execute(() -> {
            if (sendStop && output != null) {
                try {
                    output.write('S');
                    output.flush();
                } catch (IOException ignored) {
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    private interface CommandCallback {
        void onComplete(boolean success);
    }

    private void sendCommand(String command, String description) {
        sendCommand(command, description, null);
    }

    private void sendCommand(String command, String description, CommandCallback callback) {
        BluetoothSocket socket = bluetoothSocket;
        OutputStream output = bluetoothOutput;
        if (!bluetoothConnected || socket == null || output == null) {
            if (callback != null) mainHandler.post(() -> callback.onComplete(false));
            else showToast("Connect the HC-05 before controlling the rover.");
            return;
        }
        bluetoothExecutor.execute(() -> {
            boolean success = false;
            try {
                if (!socket.isConnected()) throw new IOException("Bluetooth socket is closed");
                output.write(command.getBytes(StandardCharsets.US_ASCII));
                output.flush();
                success = true;
            } catch (IOException | SecurityException error) {
                BluetoothSocket failedSocket = socket;
                if (bluetoothSocket == failedSocket) {
                    bluetoothSocket = null;
                    bluetoothOutput = null;
                    try {
                        failedSocket.close();
                    } catch (IOException closeError) {
                        error.addSuppressed(closeError);
                    }
                    mainHandler.post(() -> {
                        setBluetoothState(false, "Disconnected");
                        showToast("HC-05 disconnected. Verify the rover has stopped.");
                    });
                }
            }
            boolean commandSucceeded = success;
            mainHandler.post(() -> {
                if (commandSucceeded) lastCommand.setText("SENT  /  " + description.toUpperCase());
                if (callback != null) callback.onComplete(commandSucceeded);
            });
        });
    }

    private void setBluetoothState(boolean connected, String status) {
        bluetoothConnected = connected;
        if (bluetoothStatus != null) {
            bluetoothStatus.setText("  " + status);
            bluetoothStatus.setTextColor(connected ? ACCENT : MUTED);
            bluetoothLed.setTextColor(connected ? ACCENT : MUTED);
        }
        if (bluetoothButton != null) {
            bluetoothButton.setText(connected ? "Disconnect" : "Connect");
            bluetoothButton.setEnabled(!"Connecting…".equals(status));
        }
        if (movementButtons != null) {
            for (Button button : movementButtons) {
                if (button != null && button != stopButton) button.setEnabled(connected);
            }
        }
        if (rollButtons != null) {
            for (Button button : rollButtons) button.setEnabled(connected);
        }
        if (stopButton != null) stopButton.setEnabled(true);
        if (headlightsButton != null) headlightsButton.setEnabled(connected);
        if (servoSlider != null) servoSlider.setEnabled(connected);
    }

    private void startCameraStream() {
        final String baseUrl;
        try {
            baseUrl = normalizeCameraAddress(cameraAddress.getText().toString());
        } catch (IllegalArgumentException error) {
            showToast(error.getMessage());
            return;
        }
        preferences.edit().putString("cameraAddress", baseUrl).apply();
        stopCameraConnection();
        cameraPlaceholder.setVisibility(View.GONE);
        cameraImage.setVisibility(View.VISIBLE);
        cameraButton.setText("Stop feed");
        setCameraStatus("CONNECTING", MUTED);

        MjpegStream stream = new MjpegStream(baseUrl + "/stream");
        mjpegStream = stream;
        stream.start(bitmap -> mainHandler.post(() -> {
            if (mjpegStream != stream) {
                bitmap.recycle();
                return;
            }
            Bitmap previous = null;
            if (cameraImage.getDrawable() instanceof BitmapDrawable) {
                previous = ((BitmapDrawable) cameraImage.getDrawable()).getBitmap();
            }
            cameraImage.setImageBitmap(bitmap);
            if (previous != null && previous != bitmap && !previous.isRecycled()) previous.recycle();
            cameraPlaceholder.setVisibility(View.GONE);
            setCameraStatus("STREAMING", ACCENT);
        }), error -> mainHandler.post(() -> {
            if (mjpegStream != stream) return;
            stopCameraConnection();
            clearCameraImage();
            cameraImage.setVisibility(View.GONE);
            cameraPlaceholder.setText("Camera unavailable\nCheck the address and Wi-Fi connection");
            cameraPlaceholder.setVisibility(View.VISIBLE);
            cameraButton.setText("Start feed");
            setCameraStatus("UNAVAILABLE", RED);
            showToast("Camera feed unavailable. Check the IP and Wi-Fi network.");
        }));
    }

    private String normalizeCameraAddress(String address) {
        String value = address.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter the ESP32-CAM address first.");
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        try {
            URL url = new URL(value);
            if (!"http".equals(url.getProtocol()) && !"https".equals(url.getProtocol())) {
                throw new IllegalArgumentException("Camera address must use HTTP or HTTPS.");
            }
            if (url.getHost().isEmpty()) throw new IllegalArgumentException("Enter a valid camera IP or host.");
        } catch (IOException error) {
            throw new IllegalArgumentException("Enter a valid camera IP or host.");
        }
        return value;
    }

    private void stopCameraStream() {
        stopCameraConnection();
        clearCameraImage();
        cameraImage.setVisibility(View.GONE);
        cameraPlaceholder.setVisibility(View.VISIBLE);
        cameraButton.setText("Start feed");
        setCameraStatus("OFFLINE", MUTED);
    }

    private void stopCameraConnection() {
        MjpegStream stream = mjpegStream;
        mjpegStream = null;
        if (stream != null) stream.stop();
    }

    private void clearCameraImage() {
        Bitmap previous = null;
        if (cameraImage.getDrawable() instanceof BitmapDrawable) {
            previous = ((BitmapDrawable) cameraImage.getDrawable()).getBitmap();
        }
        cameraImage.setImageDrawable(null);
        if (previous != null && !previous.isRecycled()) previous.recycle();
    }

    private void setCameraStatus(String text, int color) {
        cameraStatus.setText("●  " + text);
        cameraStatus.setTextColor(color);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onPause() {
        if (bluetoothConnected) sendCommand("S", "Stop");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopCameraConnection();
        disconnectBluetooth(true);
        bluetoothExecutor.shutdown();
        super.onDestroy();
    }

    private static final class MjpegStream {
        private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;
        private final String streamUrl;
        private volatile boolean running = true;
        private volatile HttpURLConnection connection;
        private Thread thread;

        MjpegStream(String streamUrl) {
            this.streamUrl = streamUrl;
        }

        interface FrameListener {
            void onFrame(Bitmap bitmap);
        }

        interface ErrorListener {
            void onError(IOException error);
        }

        void start(FrameListener frames, ErrorListener errors) {
            thread = new Thread(() -> {
                try {
                    readFrames(frames);
                    if (running) errors.onError(new IOException("Camera stream ended"));
                } catch (IOException error) {
                    if (running) errors.onError(error);
                }
            }, "mjpeg-stream");
            thread.start();
        }

        private void readFrames(FrameListener frames) throws IOException {
            HttpURLConnection activeConnection = (HttpURLConnection) new URL(streamUrl).openConnection();
            connection = activeConnection;
            activeConnection.setConnectTimeout(6000);
            activeConnection.setReadTimeout(15000);
            activeConnection.setUseCaches(false);
            activeConnection.setRequestProperty("Accept", "multipart/x-mixed-replace");
            activeConnection.connect();
            int responseCode = activeConnection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Camera returned HTTP " + responseCode);
            }

            BufferedInputStream input = new BufferedInputStream(activeConnection.getInputStream(), 16 * 1024);
            while (running) {
                int contentLength = readPartLength(input);
                if (contentLength < 1 || contentLength > MAX_FRAME_BYTES) {
                    throw new IOException("Invalid camera frame size");
                }
                byte[] jpeg = new byte[contentLength];
                readFully(input, jpeg);
                Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                if (bitmap == null) throw new IOException("Could not decode camera frame");
                if (running) frames.onFrame(bitmap);
                else bitmap.recycle();
            }
            input.close();
        }

        private int readPartLength(InputStream input) throws IOException {
            int length = -1;
            int lines = 0;
            while (running && lines++ < 32) {
                String line = readAsciiLine(input);
                if (line == null) throw new IOException("Camera closed the stream");
                if (line.isEmpty()) {
                    if (length >= 0) return length;
                    continue;
                }
                int separator = line.indexOf(':');
                if (separator > 0 && "Content-Length".equalsIgnoreCase(line.substring(0, separator).trim())) {
                    try {
                        length = Integer.parseInt(line.substring(separator + 1).trim());
                    } catch (NumberFormatException error) {
                        throw new IOException("Invalid camera frame header", error);
                    }
                }
            }
            if (!running) throw new IOException("Camera stream stopped");
            throw new IOException("Camera frame header is incomplete");
        }

        private String readAsciiLine(InputStream input) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream(96);
            while (true) {
                int value = input.read();
                if (value == -1) return line.size() == 0 ? null
                        : new String(line.toByteArray(), StandardCharsets.US_ASCII);
                if (value == '\n') {
                    byte[] bytes = line.toByteArray();
                    int count = bytes.length;
                    if (count > 0 && bytes[count - 1] == '\r') count--;
                    return new String(bytes, 0, count, StandardCharsets.US_ASCII);
                }
                if (line.size() >= 512) throw new IOException("Camera frame header is too long");
                line.write(value);
            }
        }

        private void readFully(InputStream input, byte[] destination) throws IOException {
            int offset = 0;
            while (offset < destination.length && running) {
                int count = input.read(destination, offset, destination.length - offset);
                if (count < 0) throw new IOException("Camera closed during a frame");
                offset += count;
            }
            if (!running) throw new IOException("Camera stream stopped");
        }

        void stop() {
            running = false;
            HttpURLConnection activeConnection = connection;
            if (activeConnection != null) activeConnection.disconnect();
            if (thread != null) thread.interrupt();
        }
    }
}
