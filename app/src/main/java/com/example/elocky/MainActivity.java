package com.example.elocky;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "PREFS";
    private static final String ELOCKY_ID_KEY = "ELOCKY_ID";
    private static final String ELOCKY_BT_ID = "ELOCKY_BT_ID";
    private static final String ELOCKY_SECRET_OPEN = "ELOCKY_SECRET_OPEN";
    private static final String ELOCKY_SECRET_CLOSE = "ELOCKY_SECRET_CLOSE";
    private static final String ELOCKY_SECRET_STATUS = "ELOCKY_SECRET_STATUS";
    private static final String ELOCKY_LOG = "ELOCKY";
    private static final int BT_DISABLED = 1;
    private static final int LOOKING_FOR_DEVICE = 2;
    private static final int INIT_WAITING = 3;
    private static final int CONNECT_FAILED = 4;
    private static final int WAITING_FOR_STATE = 5;
    private static final int OPEN_STATE = 6;
    private static final int CLOSED_STATE = 7;
    private static final int REQUEST_ENABLE_BT = 57895;
    private static final int CMD_OPEN = 1;
    private static final int CMD_CLOSE = 2;
    private static final int CMD_STATUS = 3;
    private static final int CMD_DBG_HIST = 4;

    private static final boolean DEBUG_MODE = false;

    // Vars
    SharedPreferences sharedPreferences;
    String elocky_id = "";
    String elocky_bt_id = "";
    String cmd_status = "doorstatus";
    String cmd_open = "openthisfuckingdoor";
    String cmd_close = "closedoor";
    String cmd_debug_hist = "dbgcmdhist";

    int state = BT_DISABLED;
    TextView changeDeviceButton = null;
    TextView infoTV = null;
    Button toggleButton = null;
    BluetoothAdapter bluetoothAdapter = null;
    BroadcastReceiver broadcastReceiver = null;
    Handler mainHandler = null;
    BluetoothDevice elocky_device = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = getBaseContext().getSharedPreferences(PREFS, MODE_PRIVATE);
        if (sharedPreferences.contains(ELOCKY_ID_KEY) && sharedPreferences.contains(ELOCKY_BT_ID) && sharedPreferences.contains(ELOCKY_SECRET_OPEN) && sharedPreferences.contains(ELOCKY_SECRET_CLOSE) && sharedPreferences.contains(ELOCKY_SECRET_STATUS)) {
            elocky_id = sharedPreferences.getString(ELOCKY_ID_KEY, null);
            elocky_bt_id = sharedPreferences.getString(ELOCKY_BT_ID, null);
            cmd_open = sharedPreferences.getString(ELOCKY_SECRET_OPEN, "openthisfuckingdoor");
            cmd_close = sharedPreferences.getString(ELOCKY_SECRET_CLOSE, "closedoor");
            cmd_status = sharedPreferences.getString(ELOCKY_SECRET_STATUS, "doorstatus");
            Log.e(ELOCKY_LOG,"Open cmd is "+cmd_open);
            Log.e(ELOCKY_LOG,"Close cmd is "+cmd_close);
            Log.e(ELOCKY_LOG,"Status cmd is "+cmd_status);

        } else {
            Intent i = new Intent(MainActivity.this, SetLocker.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
            //sharedPreferences.edit().putString(ELOCKY_ID_KEY, "florent").apply();
        }

        // Init layout
        setContentView(R.layout.activity_main);
        changeDeviceButton = (TextView)findViewById(R.id.changeDeviceButton);
        infoTV = (TextView)findViewById(R.id.info_tv);
        toggleButton = (Button)findViewById(R.id.toggleButton);

        // Init handler
        mainHandler = new Handler();

        // Set default state
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!is_bluetooth_enabled()) { // Check BT state
            apply_state_layout(BT_DISABLED);
        } else {
            apply_state_layout(LOOKING_FOR_DEVICE);
            search_elocky_device();
        }

        broadcastReceiver = new BroadcastReceiver() {
            public void onReceive (Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    if(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                        // Bluetooth is disconnected, do handling here
                        apply_state_layout(BT_DISABLED);
                        Log.e(ELOCKY_LOG,"Bluetooth has been disabled.");
                        error_snackbar("Bluetooth has been disabled");
                    }
                } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress(); // MAC address
                    Log.e(ELOCKY_LOG,deviceName+" has been discovered at "+deviceHardwareAddress);
                    if (deviceName != null) {
                        if (deviceName.equals(elocky_bt_id)) {
                            Log.e(ELOCKY_LOG,"eLocky has been discovered !");
                            elocky_device = device;
                            bluetoothAdapter.cancelDiscovery();
                            apply_state_layout(INIT_WAITING);
                            sendStatusCmd();
                        }
                    }
                }

            }
        };
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));


        // Set button listeners
        // Button to change eLocky ID
        changeDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, SetLocker.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(i);
            }
        });

        // Action button to enable bluetooth and open/close door.
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (state == BT_DISABLED) {
                    enable_bluetooth();
                }
                else if (state == OPEN_STATE) {
                    apply_state_layout(WAITING_FOR_STATE);
                    sendCloseCmd();
                }
                else if (state == CLOSED_STATE) {
                    apply_state_layout(WAITING_FOR_STATE);
                    sendOpenCmd();
                }
                else if (state == CONNECT_FAILED) {
                    apply_state_layout(INIT_WAITING);
                    sendStatusCmd();
                }
            }

        });

    }

    private void sendStatusCmd () {
        Log.e(ELOCKY_LOG,"Sending status command.");
        AsyncBluetoothCmd task = new AsyncBluetoothCmd();
        task.execute(new BlueCmdRequest(CMD_STATUS,cmd_status, elocky_device));
    }

    private void sendOpenCmd () {
        Log.e(ELOCKY_LOG,"Sending open command.");
        AsyncBluetoothCmd task = new AsyncBluetoothCmd();
        task.execute(new BlueCmdRequest(CMD_OPEN,cmd_open, elocky_device));
    }

    private void sendCloseCmd () {
        Log.e(ELOCKY_LOG,"Sending close command.");
        AsyncBluetoothCmd task = new AsyncBluetoothCmd();
        task.execute(new BlueCmdRequest(CMD_CLOSE,cmd_close, elocky_device));
    }

    private void sendHistoryCmd () {
        Log.e(ELOCKY_LOG,"Sending dbg command.");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        AsyncBluetoothCmd task = new AsyncBluetoothCmd();
        task.execute(new BlueCmdRequest(CMD_DBG_HIST,cmd_debug_hist, elocky_device));
    }


    private void apply_state_layout (int new_state) {
        Log.e(ELOCKY_LOG,"Changing layout to "+new_state);
        if (new_state == BT_DISABLED) {
            state = new_state;
            infoTV.setText("Bluetooth must be enabled to communicate with your eLocky.");
            toggleButton.setText("Enable Bluetooth");
            toggleButton.setEnabled(true);
        }
        else if (new_state == LOOKING_FOR_DEVICE) {
            state = new_state;
            infoTV.setText("Looking for eLocky device.");
            toggleButton.setText("Please wait...");
            toggleButton.setEnabled(false);
        }
        else if (new_state == INIT_WAITING) {
            state = new_state;
            infoTV.setText("Waiting for eLocky status.");
            toggleButton.setText("Please wait...");
            toggleButton.setEnabled(false);
        }
        else if (new_state == CONNECT_FAILED) {
            state = new_state;
            infoTV.setText("Cannot connect to eLocky.");
            toggleButton.setText("Try again");
            toggleButton.setEnabled(true);
        }
        else if (new_state == WAITING_FOR_STATE) {
            state = new_state;
            toggleButton.setEnabled(false);
        }
        else if (new_state == OPEN_STATE) {
            state = new_state;
            infoTV.setText("eLocky is currently open.");
            toggleButton.setText("Close eLocky");
            toggleButton.setEnabled(true);
        }
        else if (new_state == CLOSED_STATE) {
            state = new_state;
            infoTV.setText("eLocky is currently closed.");
            toggleButton.setText("Open eLocky");
            toggleButton.setEnabled(true);
        }
    }

    private boolean is_bluetooth_enabled() {
        boolean isEnabled = false;
        if (bluetoothAdapter != null) {
            isEnabled = bluetoothAdapter.isEnabled();
        }
        return isEnabled;
    }

    private void enable_bluetooth () {
        // Manage bluetooth connection
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Log.e(ELOCKY_LOG,"This device does not support bluetooth.");
            error_snackbar("Bluetooth not available");
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    private void error_snackbar(String message) {
        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinator);
        Snackbar snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG);
        View mView = snackbar.getView();
        TextView mTextView = (TextView) mView.findViewById(android.support.design.R.id.snackbar_text);
        mView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.snack_error));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            mTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        } else {
            mTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        snackbar.show();
    }

    private void success_snackbar(String message) {
        CoordinatorLayout coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinator);
        Snackbar snackbar = Snackbar.make(coordinatorLayout, message, Snackbar.LENGTH_LONG);
        View mView = snackbar.getView();
        TextView mTextView = (TextView) mView.findViewById(android.support.design.R.id.snackbar_text);
        mView.setBackgroundColor(ContextCompat.getColor(getApplicationContext(), R.color.snack_success));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            mTextView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        } else {
            mTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        snackbar.show();
    }

    // Callback function
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) { // BT successfully enabled
                Log.e(ELOCKY_LOG,"Bluetooth is now activated.");
                success_snackbar("Bluetooth is now activated");
                apply_state_layout(LOOKING_FOR_DEVICE);
                search_elocky_device();
            } else {
                Log.e(ELOCKY_LOG,"Bluetooth activation failed");
                success_snackbar("Bluetooth activation failed");
            }
        }
    }

    // Search elocky bluetooth device
    private void search_elocky_device() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        boolean found = false;

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.e(ELOCKY_LOG,"Paired with : "+deviceName+" at "+deviceHardwareAddress);
                if (device.getName().equals(elocky_bt_id)) {
                    Log.e(ELOCKY_LOG,"eLocky found on paired devices !");
                    elocky_device = device;
                    found = true;
                    break;
                }
            }
        }

        if (!found) { // If not already paired, let's discover it.
            Log.e(ELOCKY_LOG,"eLocky not found. Let's discover it.");
            bluetoothAdapter.startDiscovery();
        } else if (elocky_device != null){
            apply_state_layout(INIT_WAITING);
            sendStatusCmd();
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(broadcastReceiver);
    }

    private class AsyncBluetoothCmd extends AsyncTask<BlueCmdRequest,Void,BlueCmdResult> {


        @Override
        protected BlueCmdResult doInBackground(BlueCmdRequest... blueCmdRequests) {
            BlueCmdResult result = null;
            if (blueCmdRequests.length > 0) {
                BlueCmdRequest request = blueCmdRequests[0];
                if (request != null) {
                    if (request.getDevice() != null) {

                        String message = "";
                        String token = "";

                        try {
                            Method m = request.getDevice().getClass().getMethod("createRfcommSocket", int.class);
                            BluetoothSocket socket = (BluetoothSocket) m.invoke(request.getDevice(), 1);
                            socket.connect();

                            InputStream buffIn = socket.getInputStream();
                            OutputStream buffOut = socket.getOutputStream();

                            // Read token
                            int readByte;
                            for (int i=0; i<5; i++) {
                                try {
                                    readByte = buffIn.read();            //read bytes from input buffer
                                    token += (char) readByte;
                                } catch (IOException e) {
                                    break;
                                }
                            }
                            Log.e(ELOCKY_LOG, "Token is "+token);
                            String cmd = token.concat(request.getCmd());
                            buffOut.write(cmd.getBytes());
                            // Waiting for answer
                            while (true) {
                                try {
                                    readByte = buffIn.read();            //read bytes from input buffer
                                    message += (char) readByte;
                                } catch (IOException e) {
                                    break;
                                }
                            }

                        } catch (Exception e) {
                            Log.e(ELOCKY_LOG, "Cannot connect to eLocky");
                        }

                        // Sending output to UI
                        result = new BlueCmdResult(request.cmd_type, message);
                    }
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(BlueCmdResult blueCmdResult) {
            super.onPostExecute(blueCmdResult);
            if (blueCmdResult != null) {
                Log.e(ELOCKY_LOG, "Yeah, result is "+blueCmdResult.getOutput());
                String output = blueCmdResult.getOutput();
                if ("open".equals(output.trim())) {
                    apply_state_layout(OPEN_STATE);
                    if (DEBUG_MODE) {
                        sendHistoryCmd();
                    }
                    if (blueCmdResult.getCmdType()==CMD_CLOSE) {
                        error_snackbar("Cannot close eLocky");
                    }
                } else if ("closed".equals(output.trim())) {
                    apply_state_layout(CLOSED_STATE);
                    if (DEBUG_MODE) {
                        sendHistoryCmd();
                    }
                    if (blueCmdResult.getCmdType()==CMD_OPEN) {
                        error_snackbar("Cannot open eLocky");
                    }
                }
                else {
                    if (blueCmdResult.getCmdType()==CMD_DBG_HIST) {
                        success_snackbar(blueCmdResult.getOutput());
                    } else {
                        apply_state_layout(CONNECT_FAILED);
                    }
                }
            } else {
                Log.e(ELOCKY_LOG, "Cannot retrieve result.");
                error_snackbar("Command execution failed");
            }
        }
    }

}
