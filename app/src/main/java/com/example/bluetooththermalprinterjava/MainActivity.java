package com.example.bluetooththermalprinterjava;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bluetooththermalprinterjava.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private boolean btPermission = false;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private BluetoothDevice bluetoothDevice;
    private OutputStream outputStream;
    private InputStream inputStream;
    private Thread workerThread;
    private byte[] readBuffer;
    private int readBufferPosition;
    private volatile boolean stopWorker;
    private ConnectionClass connectionClass = new ConnectionClass();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setTitle("Bluetooth Printer example Java");

        binding.scanButton.setOnClickListener(view -> scanBt(view));
        binding.printButton.setOnClickListener(view -> print(view));
    }

    private void scanBt(View view) {
        checkPermission();
    }

    private void print(View view) {
        if (btPermission) {
            initPrinter();
            printInvoice();
        } else {
            checkPermission();
        }
    }

    private void checkPermission() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            } else {
                blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADMIN);
            }
        }
    }

    private final ActivityResultLauncher<String> blueToothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), this::onActivityResult);

    private final ActivityResultLauncher<Intent> btActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    btScan();
                }
            });

    @SuppressLint("MissingPermission")
    private void btScan() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.scan_bt, null);
        builder.setCancelable(false);
        builder.setView(dialogView);
        ListView btlst = dialogView.findViewById(R.id.bt_lst);
        AlertDialog dialog = builder.create();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        List<Map<String, Object>> data = new ArrayList<>();
        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                Map<String, Object> datanum = new HashMap<>();
                datanum.put("A", device.getName());
                datanum.put("B", device.getAddress());
                data.add(datanum);
            }
            String[] from = {"A"};
            int[] to = {R.id.item_name};
            SimpleAdapter ADAhere = new SimpleAdapter(this, data, R.layout.list_item, from, to);
            btlst.setAdapter(ADAhere);
            ADAhere.notifyDataSetChanged();
            btlst.setOnItemClickListener((parent, view, position, id) -> {
                HashMap<String, String> string = (HashMap<String, String>) ADAhere.getItem(position);
                String prnName = string.get("A");
                binding.deviceName.setText(prnName);
                connectionClass.setPrinterName(prnName);
                dialog.dismiss();
            });
        } else {
            Toast.makeText(this, "No devices found", Toast.LENGTH_LONG).show();
        }
        dialog.show();
    }

    private void beginListenForData() {
        try {
            final Handler handler = new Handler();
            final byte delimiter = 10;
            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];
            workerThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = inputStream.available();
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            inputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    String data = new String(encodedBytes, Charset.forName("US-ASCII"));
                                    readBufferPosition = 0;
                                    handler.post(() -> Log.d("Data", data));
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        stopWorker = true;
                    }
                }
            });
            workerThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    private void initPrinter() {
        String prname = connectionClass.getPrinterName(); // Get printer name using getter
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        try {
            if (bluetoothAdapter != null) {
                if (!bluetoothAdapter.isEnabled()) {
                    Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    btActivityResultLauncher.launch(enableBluetooth);
                }
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                if (!pairedDevices.isEmpty()) {
                    for (BluetoothDevice device : pairedDevices) {
                        if (device.getName().equals(prname)) {
                            bluetoothDevice = device;
                            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                            // Create BluetoothSocket using reflection
                            BluetoothSocket tempSocket = null;
                            try {
                                tempSocket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", int.class).invoke(device, 1);
                            } catch (Exception e) {
                                Log.e("BluetoothError", "Error creating Bluetooth socket", e);
                            }
                            if (tempSocket != null) {
                                socket = tempSocket;
                                bluetoothAdapter.cancelDiscovery();
                                try {
                                    socket.connect();
                                    outputStream = socket.getOutputStream();
                                    inputStream = socket.getInputStream();
                                    beginListenForData();
                                } catch (IOException e) {
                                    Log.e("BluetoothError", "Error connecting to Bluetooth device", e);
                                    try {
                                        socket.close();
                                    } catch (IOException closeException) {
                                        Log.e("BluetoothError", "Error closing socket", closeException);
                                    }
                                }
                            }
                            break;
                        }
                    }
                } else {
                    Toast.makeText(this, "No Devices found", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Bluetooth Printer Not Connected", Toast.LENGTH_LONG).show();
            socket = null;
        }
    }

    private void printInvoice() {
        try {
            // Retrieve data from the database instead of hardcoding it here
            // Example: fetchInvoiceDataFromDatabase()
            // For demonstration, using static data
            String invhdr = "Tax Invoice";
            String cmpname = "Thermal Printer Sample";
            String mo = "98000000000";
            String gstin = "Gst no";
            String billno = "1001";
            String billdt = "06-09-2023";
            String tblno = "5";
            String amtwd = "One Hundred five Only";
            double amt = 100.00;
            double tax = 5.00;
            double total = amt + tax;

            // Header information
            String header = "Roman Catholic Bishop of Daet\n"
                    + "HOLY TRINITY COLLEGE SEMINARY FOUNDATION, Inc.\n"
                    + "Holy Trinity College Seminary, P.3 Bautista 4604, Labo, Camarines Norte, Philippines\n"
                    + "Non-VAT Reg. TIN: 628-911-376-00000\n\n";

            StringBuilder textData = new StringBuilder();
            textData.append(header);
            textData.append("  ").append(cmpname).append("\n");
            textData.append("  ").append(invhdr).append("\n\n");
            textData.append("  GSTIN: ").append(gstin).append("\n");
            textData.append("  Mob: ").append(mo).append("\n");
            textData.append("  Bill No: ").append(billno).append("\n");
            textData.append("  Date: ").append(billdt).append("\n");
            textData.append("  Table No: ").append(tblno).append("\n");
            textData.append("  Amount (words): ").append(amtwd).append("\n");
            textData.append("  Amount: ").append(String.format("%.2f", amt)).append("\n");
            textData.append("  Tax: ").append(String.format("%.2f", tax)).append("\n");
            textData.append("  Total: ").append(String.format("%.2f", total)).append("\n");
            textData.append("  Thank you for visiting us!\n");

            // Send the text data to the printer
            if (outputStream != null) {
                outputStream.write(textData.toString().getBytes(Charset.forName("US-ASCII")));
                outputStream.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onActivityResult(Boolean isGranted) {
        if (isGranted) {
            btPermission = true;
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                btActivityResultLauncher.launch(enableBtIntent);
            } else {
                btScan();
            }
        } else {
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}
