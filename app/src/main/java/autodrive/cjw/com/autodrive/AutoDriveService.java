package autodrive.cjw.com.autodrive;

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
import android.os.Bundle;
import android.os.IBinder;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Created by jaewoncho on 2016. 9. 9..
 */
public class AutoDriveService extends Service implements SensorEventListener, LocationListener {

    private SensorManager mSensorManager;
    private Sensor mSensor;

    private LocationManager mLocationManager;
    private Location mLocation;

    private static final UUID AUTODRIVE_UUID = UUID.fromString("autodrive");

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

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
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> devices = adapter.getBondedDevices();

        for (BluetoothDevice device : devices) {
            try {
                String name = device.getName();

                device.createRfcommSocketToServiceRecord(AUTODRIVE_UUID);
            } catch (IOException e) {
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
}
