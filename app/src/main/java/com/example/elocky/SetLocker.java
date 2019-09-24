package com.example.elocky;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.UnsupportedEncodingException;

public class SetLocker extends AppCompatActivity {

    private static final String PREFS = "PREFS";
    private static final String ELOCKY_ID_KEY = "ELOCKY_ID";
    private static final String ELOCKY_BT_ID = "ELOCKY_BT_ID";
    private static final String ELOCKY_SECRET_OPEN = "ELOCKY_SECRET_OPEN";
    private static final String ELOCKY_SECRET_CLOSE = "ELOCKY_SECRET_CLOSE";
    private static final String ELOCKY_SECRET_STATUS = "ELOCKY_SECRET_STATUS";
    private static final String ELOCKY_CLEAR_OPEN = "openthisfuckingdoor";
    private static final String ELOCKY_CLEAR_CLOSE = "closedoor";
    private static final String ELOCKY_CLEAR_STATUS = "doorstatus";

    SharedPreferences sharedPreferences;
    String elocky_id = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_locker);

        EditText edit = (EditText)findViewById(R.id.editText);

        sharedPreferences = getBaseContext().getSharedPreferences(PREFS, MODE_PRIVATE);

        if (sharedPreferences.contains(ELOCKY_ID_KEY)) {
            elocky_id = sharedPreferences.getString(ELOCKY_ID_KEY, null);
            edit.setText(elocky_id);
        }

        Button pairLockerButton = (Button)findViewById(R.id.pairLockerButton);

        pairLockerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                elocky_id = ((EditText)findViewById(R.id.editText)).getText().toString();
                if (elocky_id.isEmpty()) {
                    error_snackbar("Pairing number cannot be empty");
                } else {
                    sharedPreferences.edit().putString(ELOCKY_ID_KEY, elocky_id).apply();
                    sharedPreferences.edit().putString(ELOCKY_BT_ID, parseBluetoothId(elocky_id)).apply();
                    calculate_secrets(elocky_id);
                    Intent i = new Intent(SetLocker.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(i);
                }
            }
        });
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

    private void calculate_secrets (String id) {

        try {
            byte[] open_xored = xorWithKey(ELOCKY_CLEAR_OPEN.getBytes("UTF-8"),id.getBytes("UTF-8"));
            byte[] close_xored = xorWithKey(ELOCKY_CLEAR_CLOSE.getBytes("UTF-8"),id.getBytes("UTF-8"));
            byte[] status_xored = xorWithKey(ELOCKY_CLEAR_STATUS.getBytes("UTF-8"),id.getBytes("UTF-8"));

            String open_b64 = Base64.encodeToString(open_xored, Base64.DEFAULT);
            String close_b64 = Base64.encodeToString(close_xored, Base64.DEFAULT);
            String status_b64 = Base64.encodeToString(status_xored, Base64.DEFAULT);

            sharedPreferences.edit().putString(ELOCKY_SECRET_OPEN, open_b64).apply();
            sharedPreferences.edit().putString(ELOCKY_SECRET_CLOSE, close_b64).apply();
            sharedPreferences.edit().putString(ELOCKY_SECRET_STATUS, status_b64).apply();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private byte[] xorWithKey(byte[] a, byte[] key) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ key[i%key.length]);
        }
        return out;
    }

    private String parseBluetoothId (String id) {
        String bt_id = "eLocky-";
        bt_id = bt_id.concat(id.substring(Math.max(id.length() - 2, 0)));
        return bt_id;
    }

}
