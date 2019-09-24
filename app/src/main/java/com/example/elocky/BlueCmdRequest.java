package com.example.elocky;

import android.bluetooth.BluetoothDevice;

public class BlueCmdRequest {

    int cmd_type;
    String cmd;
    BluetoothDevice device;

    public BlueCmdRequest (int cmd_type, String cmd, BluetoothDevice device) {
        this.cmd_type = cmd_type;
        this.cmd = cmd;
        this.device = device;
    }

    public String getCmd () {
        return this.cmd;
    }

    public int getCmdType() {
        return this.cmd_type;
    }

    public BluetoothDevice getDevice () {
        return this.device;
    }

}
