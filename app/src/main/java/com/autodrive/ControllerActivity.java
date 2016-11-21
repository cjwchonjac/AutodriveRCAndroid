package com.autodrive;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

/**
 * Created by jaewoncho on 2016. 11. 11..
 */
public class ControllerActivity extends Activity implements View.OnClickListener {
    ServiceConnection mConnection;
    AutoDriveService.AutoDriveServiceBinder mBinder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.controller);

        findViewById(R.id.controller_bt_stop).setOnClickListener(this);
        findViewById(R.id.controller_bt_go).setOnClickListener(this);
        findViewById(R.id.controller_bt_back).setOnClickListener(this);
        findViewById(R.id.controller_bt_left).setOnClickListener(this);
        findViewById(R.id.controller_bt_right).setOnClickListener(this);


        Intent service = new Intent(this, AutoDriveService.class);
        startService(service);

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mBinder = (AutoDriveService.AutoDriveServiceBinder) service;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        bindService(service, mConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.controller_bt_go:
                mBinder.go();
                break;
            case R.id.controller_bt_stop:
                mBinder.stop();
                break;
            case R.id.controller_bt_back:
                mBinder.back();
                break;
            case R.id.controller_bt_left:
                mBinder.left();
                break;
            case R.id.controller_bt_right:
                mBinder.right();
                break;
        }
    }
}
