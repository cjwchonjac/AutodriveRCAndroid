package com.autodrive;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import com.autodrive.connector.Connector;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Created by jaewoncho on 2016. 9. 9..
 */
public class AutoDriveService extends Service implements SensorEventListener, LocationListener, Connector.Callback {

    private SensorManager mSensorManager;
    private Sensor mSensor;

    private LocationManager mLocationManager;
    private Location mLocation;

    private Connector mConnector;

    Set<AutoDriveServiceCallback> mCallbacks;

    class AutoDriveServiceBinder extends Binder {
        public void connect() {
            connectToHeadUnit();
        }

        public void disconnect() {
            if (mConnector != null) {
                mConnector.destroy(true);
            }
        }

        public boolean isConnected() {
            return mConnector != null && mConnector.isConnected();
        }

        public void registerCallback(AutoDriveServiceCallback c) {
            mCallbacks.add(c);
        }

        public void unregisterCallback(AutoDriveServiceCallback c) {
            mCallbacks.remove(c);
        }
    }

    public interface AutoDriveServiceCallback {
        public void onConnecting();
        public void onInitialized(BluetoothDevice device);
        public void onDisconnected();
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mCallbacks = new HashSet<>();

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_FASTEST);

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void connectToHeadUnit() {
        if (mConnector == null || !mConnector.isConnected()) {
            mConnector = new Connector();
            mConnector.start();

            for (AutoDriveServiceCallback c : mCallbacks) {
                c.onConnecting();
            }
        }
    }

    public void startAutoDrive() {
        Criteria criteria = new Criteria();

        String provider = mLocationManager.getBestProvider(criteria, true);
        mLocation = mLocationManager.getLastKnownLocation(provider);

        mLocationManager.requestLocationUpdates(provider, 100l, 1.0f, this);
    }

    public void finishAutoDrive() {
        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        mLocation = location;

        if (mConnector != null) {
            mConnector.sendLocation(location);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onInitialize(BluetoothDevice device) {
        for (AutoDriveServiceCallback c : mCallbacks) {
            c.onInitialized(device);
        }

        startAutoDrive();
    }

    @Override
    public void onDestroyed() {
        mConnector = null;

        for (AutoDriveServiceCallback c : mCallbacks) {
            c.onDisconnected();
        }
    }
}
