package com.example.elocky;

import android.bluetooth.BluetoothDevice;

public class BlueCmdResult {

    int cmd_type;
    String output;

    public BlueCmdResult(int cmd_type, String output) {
        this.cmd_type = cmd_type;
        this.output = output;
    }

    public int getCmdType () {
        return this.cmd_type;
    }

    public String getOutput() {
        return this.output;
    }


}
