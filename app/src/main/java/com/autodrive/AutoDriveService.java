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
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.autodrive.connector.Connector;
import com.autodrive.message.Autodrive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    int mLastSeg;
    private  Autodrive.SegmentList mList;
    Set<AutoDriveServiceCallback> mCallbacks;
    PathCollector collector;
    long tick;

    Handler handler;

    class AutoDriveServiceBinder extends Binder {
        public void connect() {
            connectToHeadUnit();
        }

        public void disconnect() {
            if (mConnector != null) {
                mConnector.destroy(true);
            }
        }

        public void startGpsUpdate() {
            startAutoDrive();
        }

        public void endGpsUpdate() {
            finishAutoDrive();
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
            mList = list;
            mLastSeg = -1;

            if (mConnector != null) {
                mConnector.sendSegmentList(list);

                if (collector != null) {
                    collector.emitSegments(list);
                }
            }
        }

        public void sendDriveStart() {
            if (mConnector != null) {
                mConnector.sendDriveStart();
            }
        }

        public void sendDriveEnd() {
            clearDriveData();
            if (mConnector != null) {
                mConnector.sendDriveEnd();
            }
        }

        public void testGPS(double lat, double lng) {
            if (mList != null) {
                mLocation = new Location("gps");
                mLocation.setLatitude(lat);
                mLocation.setLongitude(lng);
                handleLocationChange();
            }

        }

        public void go() {
            if (mConnector != null) {
                mConnector.sendGo();
            }

            Log.d("cjw", "sendGo");
        }

        public void back() {
            if (mConnector != null) {
                mConnector.sendBack();
            }

            Log.d("cjw", "sendBack");
        }

        public void stop() {
            if (mConnector != null) {
                mConnector.sendStop();
            }

            Log.d("cjw", "sendStop");
        }

        public void left() {
            if (mConnector != null) {
                mConnector.sendLeft();
            }

            Log.d("cjw", "sendLeft");
        }

        public void right() {
            if (mConnector != null) {
                mConnector.sendRight();
            }

            Log.d("cjw", "sendRight");
        }

    }

    public interface AutoDriveServiceCallback {
        public void onConnecting();
        public void onInitialized();
        public void onDisconnected();
        public void onLocationChanged(Location l);
        public void onRequestPrintLog(String str);
        public void onPointOnSegementCalcuated(double oLat, double oLng, double pLat, double pLng);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return new AutoDriveServiceBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mCallbacks = new HashSet<>();
        handler = new Handler();
        collector = new PathCollector();

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
        collector.init(this);
        tick = System.currentTimeMillis();

        if (mList != null) {
            collector.emitSegments(mList);
        }

        String provider = mLocationManager.getBestProvider(criteria, true);
        mLocation = mLocationManager.getLastKnownLocation(provider);

        if (mLocation != null) {
            handleLocationChange();

            for (AutoDriveServiceCallback c : mCallbacks) {
                c.onLocationChanged(mLocation);
            }

            collector.emitGps(mLocation.getLatitude(), mLocation.getLongitude(), System.currentTimeMillis() - tick);
        }

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100l, 1.0f, this);
        // mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 100l, 1.0f, this);
        sendCurrentLocation();
    }

    public void clearDriveData() {
        mLastSeg = -1;
        mList = null;
    }

    public void finishAutoDrive() {
        clearDriveData();
        mLocationManager.removeUpdates(this);

        if (mConnector != null) {
            mConnector.destroy();
        }
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

        if (location != null) {
            for (AutoDriveServiceCallback c : mCallbacks) {
                c.onLocationChanged(location);
            }
        }

        handler.post(new Runnable() {
            @Override
            public void run() {
                handleLocationChange();
            }
        });


        sendCurrentLocation();

        if (mConnector != null && collector != null) {
            collector.emitGps(mLocation.getLatitude(), mLocation.getLongitude(), System.currentTimeMillis() - tick);
        }
    }

    void handleLocationChange() {
        List<Autodrive.LocationMessage> messages;
        if (mLocation != null &&
                mList != null &&
                (messages = mList.getLocationsList()) != null &&
                messages.size() > 0) {

            int seg = -1;
            final int N = messages.size();
            double min = Double.MAX_VALUE;

            List<double[]> datas = new ArrayList<>();
            for (int idx = 0; idx < N - 1; idx++) {
                // get point on line segment
                Autodrive.LocationMessage current = messages.get(idx);
                Autodrive.LocationMessage next = messages.get(idx + 1);
                double y1 = current.getLatitude();
                double x1 = current.getLongitude();
                double y2 = next.getLatitude();
                double x2 = next.getLongitude();

                double[] p = new double[2];
                Util.pointOnLine(x1, y1, x2, y2, mLocation.getLongitude(), mLocation.getLatitude(), p);

                // check in 2 points
                boolean in = Util.pointInLineSeg(x1, y1, x2, y2, p[0], p[1]);
                double distance = Util.dist(p[1], p[0], mLocation.getLatitude(), mLocation.getLongitude());

                datas.add(p);

                if (in && distance < min) {
                    min = distance;
                    seg = idx;
                }
            }

            if (seg >= 0 && seg < N - 1) {
                Autodrive.LocationMessage current = messages.get(seg);
                Autodrive.LocationMessage next = messages.get(seg + 1);
                double y1 = current.getLatitude();
                double x1 = current.getLongitude();
                double y2 = next.getLatitude();
                double x2 = next.getLongitude();

                double[] p = datas.get(seg);
                double d = Util.dist(p[1], p[0], y2, x2);
                Log.d("cjw", "in segment " + seg + " - " + (seg + 1) +  " check distance " + d);

                for (AutoDriveServiceCallback c : mCallbacks) {
                    c.onPointOnSegementCalcuated(mLocation.getLatitude(), mLocation.getLongitude(),
                            p[1], p[0]);
                }

                if (d < 5.0) {
                    // arrived at next segment
                    if (mLastSeg != seg) {
                        mLastSeg = seg;
                        if (seg + 2 < N) {
                            Autodrive.LocationMessage np = messages.get(seg + 2);

                            double vx1 = (x2- x1);
                            double vy1 = (y2 - y1);
                            double vx2 = (np.getLongitude() - x2);
                            double vy2 = (np.getLatitude() - y2);

                            double dot = vx1 * vx2 + vy1 * vy2;
                            double det = vx1 * vy2 - vy1 * vx2;
                            double angle = Math.atan2(det, dot) * 180.0 / Math.PI;

                            if (mConnector != null) {
                                Autodrive.SegmentArrived arrived =
                                        Autodrive.SegmentArrived.newBuilder().
                                                setAngle(angle).setIndex(seg).build();
                                mConnector.sendSegmentArrived(arrived);
                            }

                            for (AutoDriveServiceCallback c : mCallbacks) {
                                c.onRequestPrintLog("arrived at segment " + (seg + 1) + " next angle " + angle);
                            }
                            // Log.d("cjw", "arrived at segment " + (idx + 1) + " next angle " + angle);
                        } else {
                            // finish driving
                            if (mConnector != null) {
                                mConnector.sendDestinationArrived();
                            }

                            for (AutoDriveServiceCallback c : mCallbacks) {
                                c.onRequestPrintLog("finish driving");
                            }
                        }
                    }
                } else {
                    for (AutoDriveServiceCallback c : mCallbacks) {
                        // c.onRequestPrintLog("Go straight");
                    }
                }
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
