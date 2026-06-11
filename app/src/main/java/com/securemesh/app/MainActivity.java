package com.securemesh.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String DEFAULT_NAME = "SecureMesh-GW-A7";
    private static final String OLD_NAME = "SecureMesh_BLE";

    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private TextView statusText;
    private TextView logText;
    private LinearLayout deviceListLayout;
    private EditText targetNameEdit, passwordEdit, targetEdit, messageEdit;
    private CheckBox autoConnectCheck, showAllCheck;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;

    private boolean scanning = false;
    private boolean connected = false;
    private boolean authOk = false;
    private String connectionState = "disconnected";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, DeviceRow> devices = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupBluetooth();
        setupButtons();
        updateStatus();
        log("App started. Press Permissions, then Start Scan.");
    }

    private void bindViews() {
        statusText = findViewById(R.id.statusText);
        logText = findViewById(R.id.logText);
        deviceListLayout = findViewById(R.id.deviceListLayout);
        targetNameEdit = findViewById(R.id.targetNameEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        targetEdit = findViewById(R.id.targetEdit);
        messageEdit = findViewById(R.id.messageEdit);
        autoConnectCheck = findViewById(R.id.autoConnectCheck);
        showAllCheck = findViewById(R.id.showAllCheck);
    }

    private void setupBluetooth() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) bluetoothAdapter = manager.getAdapter();
        if (bluetoothAdapter != null) scanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    private void setupButtons() {
        findViewById(R.id.permissionsButton).setOnClickListener(v -> requestNeededPermissions());
        findViewById(R.id.startScanButton).setOnClickListener(v -> startScan());
        findViewById(R.id.stopScanButton).setOnClickListener(v -> stopScan());
        findViewById(R.id.disconnectButton).setOnClickListener(v -> disconnectGatt());
        findViewById(R.id.authButton).setOnClickListener(v -> sendAuth());
        findViewById(R.id.sendButton).setOnClickListener(v -> sendCustom());
        findViewById(R.id.helpButton).setOnClickListener(v -> sendQuick("HELP"));
        findViewById(R.id.okButton).setOnClickListener(v -> sendQuick("OK"));
        findViewById(R.id.medicButton).setOnClickListener(v -> sendQuick("MEDIC"));
        findViewById(R.id.testButton).setOnClickListener(v -> sendQuick("TEST"));
    }

    private boolean bluetoothOn() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    private boolean hasNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestNeededPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN);
            perms.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        requestPermissions(perms.toArray(new String[0]), 1001);
        log("Permission request sent: " + TextUtils.join(", ", perms));
        updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        log("Permission result received. Permissions OK = " + hasNeededPermissions());
        updateStatus();
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        if (!bluetoothOn()) {
            toast("Bluetooth is OFF");
            log("ERROR: Bluetooth OFF");
            updateStatus();
            return;
        }
        if (!hasNeededPermissions()) {
            log("ERROR: Missing permissions. Press Permissions first.");
            requestNeededPermissions();
            return;
        }
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            log("ERROR: BluetoothLeScanner is null");
            return;
        }
        devices.clear();
        deviceListLayout.removeAllViews();
        scanning = true;
        connectionState = "scanning";
        authOk = false;
        scanner.startScan(scanCallback);
        log("Scan started. Show all = " + showAllCheck.isChecked() + ", auto-connect = " + autoConnectCheck.isChecked());
        updateStatus();
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (scanner != null && scanning && hasNeededPermissions()) {
            scanner.stopScan(scanCallback);
        }
        scanning = false;
        if (!connected) connectionState = "disconnected";
        log("Scan stopped");
        updateStatus();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult r : results) handleScanResult(r);
        }

        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(() -> {
                scanning = false;
                log("SCAN FAILED: errorCode=" + errorCode);
                updateStatus();
            });
        }
    };

    @SuppressLint("MissingPermission")
    private void handleScanResult(ScanResult result) {
        if (result == null || result.getDevice() == null) return;
        BluetoothDevice device = result.getDevice();
        String address = device.getAddress();
        String name = device.getName();
        if (name == null && result.getScanRecord() != null) name = result.getScanRecord().getDeviceName();
        if (name == null) name = "Unknown";
        int rssi = result.getRssi();
        boolean nameMatch = name.equals(targetName()) || name.equals(OLD_NAME) || name.startsWith("SecureMesh");
        boolean serviceMatch = false;
        if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
            serviceMatch = result.getScanRecord().getServiceUuids().toString().toLowerCase(Locale.US).contains(SERVICE_UUID.toString());
        }
        boolean shouldShow = showAllCheck.isChecked() || nameMatch || serviceMatch;
        boolean shouldAutoConnect = autoConnectCheck.isChecked() && (name.equals(targetName()) || name.equals(OLD_NAME) || serviceMatch);
        String finalName = name;
        boolean finalServiceMatch = serviceMatch;
        boolean finalNameMatch = nameMatch;
        runOnUiThread(() -> {
            if (shouldShow) addOrUpdateDeviceRow(finalName, address, rssi, finalNameMatch, finalServiceMatch, device);
            log("FOUND: name=" + finalName + " addr=" + address + " rssi=" + rssi + " nameOK=" + finalNameMatch + " serviceOK=" + finalServiceMatch);
            if (shouldAutoConnect && !connected && bluetoothGatt == null) {
                log("AUTO CONNECT MATCH: " + finalName + " " + address);
                connectDevice(device, finalName);
            }
        });
    }

    private void addOrUpdateDeviceRow(String name, String address, int rssi, boolean nameOk, boolean serviceOk, BluetoothDevice device) {
        DeviceRow row = devices.get(address);
        if (row == null) {
            TextView tv = new TextView(this);
            tv.setPadding(8, 8, 8, 8);
            tv.setTextSize(14);
            tv.setBackgroundColor(0xFFEFEFEF);
            tv.setOnClickListener(v -> connectDevice(device, name));
            deviceListLayout.addView(tv);
            row = new DeviceRow(tv);
            devices.put(address, row);
        }
        row.textView.setText("CONNECT → " + name + "\n" + address + " | RSSI " + rssi + " | nameOK=" + nameOk + " | serviceOK=" + serviceOk);
    }

    @SuppressLint("MissingPermission")
    private void connectDevice(BluetoothDevice device, String label) {
        if (!hasNeededPermissions()) {
            log("ERROR: Missing permissions for connect");
            requestNeededPermissions();
            return;
        }
        stopScan();
        disconnectGatt();
        connectionState = "connecting";
        log("Connecting to " + label + " " + device.getAddress());
        updateStatus();
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            runOnUiThread(() -> log("GATT state change: status=" + status + " newState=" + newState));
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected = true;
                connectionState = "connected";
                runOnUiThread(() -> {
                    log("Connected. Discovering services...");
                    updateStatus();
                });
                if (hasNeededPermissions()) gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected = false;
                authOk = false;
                connectionState = "disconnected";
                bluetoothGatt = null;
                writeCharacteristic = null;
                notifyCharacteristic = null;
                runOnUiThread(() -> {
                    log("Disconnected");
                    updateStatus();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            runOnUiThread(() -> log("Services discovered. status=" + status));
            writeCharacteristic = null;
            notifyCharacteristic = null;
            for (BluetoothGattService service : gatt.getServices()) {
                runOnUiThread(() -> log("SERVICE: " + service.getUuid()));
                for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                    int props = ch.getProperties();
                    boolean canWrite = (props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
                    boolean canNotify = (props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                    runOnUiThread(() -> log("  CHAR: " + ch.getUuid() + " write=" + canWrite + " notify=" + canNotify));
                    if (ch.getUuid().equals(RX_UUID)) writeCharacteristic = ch;
                    if (ch.getUuid().equals(TX_UUID)) notifyCharacteristic = ch;
                }
            }
            if (writeCharacteristic == null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                        int props = ch.getProperties();
                        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || (props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                            writeCharacteristic = ch;
                            break;
                        }
                    }
                    if (writeCharacteristic != null) break;
                }
            }
            if (notifyCharacteristic == null) {
                for (BluetoothGattService service : gatt.getServices()) {
                    for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
                        if ((ch.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            notifyCharacteristic = ch;
                            break;
                        }
                    }
                    if (notifyCharacteristic != null) break;
                }
            }
            runOnUiThread(() -> {
                log(writeCharacteristic != null ? "RX WRITE FOUND: " + writeCharacteristic.getUuid() : "ERROR: no writable characteristic");
                log(notifyCharacteristic != null ? "TX NOTIFY FOUND: " + notifyCharacteristic.getUuid() : "WARN: no notify characteristic");
                updateStatus();
            });
            if (notifyCharacteristic != null) enableNotify(gatt, notifyCharacteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] data = characteristic.getValue();
            String text = data == null ? "" : new String(data, StandardCharsets.UTF_8);
            runOnUiThread(() -> {
                log("RX: " + text);
                if (text.contains("AUTH OK")) {
                    authOk = true;
                    connectionState = "auth ok";
                    updateStatus();
                } else if (text.contains("AUTH FAIL")) {
                    authOk = false;
                    connectionState = "auth fail";
                    updateStatus();
                }
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            runOnUiThread(() -> log("Write result: status=" + status + " char=" + characteristic.getUuid()));
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            runOnUiThread(() -> log("Notify descriptor write: status=" + status));
        }
    };

    @SuppressLint("MissingPermission")
    private void enableNotify(BluetoothGatt gatt, BluetoothGattCharacteristic ch) {
        if (!hasNeededPermissions()) return;
        boolean ok = gatt.setCharacteristicNotification(ch, true);
        log("setCharacteristicNotification=" + ok);
        BluetoothGattDescriptor d = ch.getDescriptor(CCCD_UUID);
        if (d != null) {
            d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(d);
            log("Enabling CCCD notifications...");
        } else {
            log("WARN: CCCD descriptor not found");
        }
    }

    private void sendAuth() {
        sendRaw("AUTH:" + passwordEdit.getText().toString().trim());
    }

    private void sendQuick(String code) {
        if (!authOk) log("WARNING: Not authorized yet. Send may be ignored by ESP32.");
        sendRaw("TO:ALL;MSG:" + code);
    }

    private void sendCustom() {
        if (!authOk) log("WARNING: Not authorized yet. Send may be ignored by ESP32.");
        String target = targetEdit.getText().toString().trim();
        if (target.length() == 0) target = "ALL";
        String msg = messageEdit.getText().toString().trim();
        if (msg.length() == 0) msg = "TEST";
        sendRaw("TO:" + target + ";MSG:" + msg);
    }

    @SuppressLint("MissingPermission")
    private void sendRaw(String text) {
        if (bluetoothGatt == null || writeCharacteristic == null) {
            log("ERROR: Not connected or write characteristic not found");
            return;
        }
        if (!hasNeededPermissions()) {
            log("ERROR: Missing permissions for write");
            return;
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeCharacteristic.setValue(bytes);
        if ((writeCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
            writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        } else {
            writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        }
        boolean ok = bluetoothGatt.writeCharacteristic(writeCharacteristic);
        log("TX: " + text + " | queued=" + ok);
    }

    @SuppressLint("MissingPermission")
    private void disconnectGatt() {
        authOk = false;
        connected = false;
        connectionState = "disconnected";
        if (bluetoothGatt != null && hasNeededPermissions()) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
        bluetoothGatt = null;
        writeCharacteristic = null;
        notifyCharacteristic = null;
        updateStatus();
    }

    private String targetName() {
        String s = targetNameEdit.getText().toString().trim();
        return s.length() == 0 ? DEFAULT_NAME : s;
    }

    private void updateStatus() {
        String s = "Bluetooth: " + (bluetoothOn() ? "ON" : "OFF")
                + "\nPermissions: " + (hasNeededPermissions() ? "OK" : "MISSING")
                + "\nScan: " + (scanning ? "running" : "stopped")
                + "\nConnection: " + connectionState
                + "\nWrite char: " + (writeCharacteristic != null ? "OK" : "missing")
                + " | Notify char: " + (notifyCharacteristic != null ? "OK" : "missing");
        statusText.setText(s);
    }

    private void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logText.append(time + "  " + msg + "\n");
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        stopScan();
        disconnectGatt();
        super.onDestroy();
    }

    private static class DeviceRow {
        TextView textView;
        DeviceRow(TextView textView) { this.textView = textView; }
    }
}
