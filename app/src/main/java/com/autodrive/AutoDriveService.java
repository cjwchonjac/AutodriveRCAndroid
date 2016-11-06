package com.autodrive;

import android.app.Service;
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
import android.util.Log;

import com.autodrive.connector.Connector;
import com.autodrive.message.Autodrive;

import java.util.HashSet;
import java.util.Set;

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

        public void sendSegmentList(Autodrive.SegmentList list) {
            if (mConnector != null) {
                mConnector.sendSegmentList(list);
            }
        }

    }

    public interface AutoDriveServiceCallback {
        public void onConnecting();
        public void onInitialized();
        public void onDisconnected();
        public void onLocationChanged(Location l);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return new AutoDriveServiceBinder();
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
        Log.d("cjw", "onStartCommnad");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void connectToHeadUnit() {
        startAutoDrive();

        if (mConnector == null || !mConnector.isConnected()) {
            mConnector = new Connector();
            mConnector.setCallback(this);
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

        if (mLocation != null) {
            for (AutoDriveServiceCallback c : mCallbacks) {
                c.onLocationChanged(mLocation);
            }
        }

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100l, 1.0f, this);

        sendCurrentLocation();
    }

    public void finishAutoDrive() {
        mLocationManager.removeUpdates(this);
    }

    public void sendCurrentLocation() {
        if (mConnector != null && mLocation != null) {
            mConnector.sendLocation(mLocation);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("cjw", "onLocationChanged " + location);
        mLocation = location;
        sendCurrentLocation();

        if (location != null) {
            for (AutoDriveServiceCallback c : mCallbacks) {
                c.onLocationChanged(location);
            }
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
    public void onInitialize() {
        for (AutoDriveServiceCallback c : mCallbacks) {
            c.onInitialized();
        }

        startAutoDrive();
    }

    @Override
    public void onDestroyed() {
        mConnector = null;

        finishAutoDrive();

        for (AutoDriveServiceCallback c : mCallbacks) {
            c.onDisconnected();
        }
    }
}
