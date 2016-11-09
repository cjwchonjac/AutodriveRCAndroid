package com.autodrive;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ZoomControls;

import com.autodrive.message.Autodrive;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;
import com.skp.Tmap.TMapData;
import com.skp.Tmap.TMapMarkerItem;
import com.skp.Tmap.TMapMarkerItem2;
import com.skp.Tmap.TMapPOIItem;
import com.skp.Tmap.TMapPoint;
import com.skp.Tmap.TMapPolyLine;
import com.skp.Tmap.TMapView;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by jaewoncho on 2016-09-18.
 */
public class MainActivity extends Activity implements View.OnClickListener,
        AutoDriveService.AutoDriveServiceCallback, EditText.OnEditorActionListener,
        LocationListDialog.OnLocationClickListener {

    Button mButton;
    Button mDriveStartButton;
    Button mDriveEndButton;
    TextView mConnectionText;
    TextView mStatusText;
    EditText mLocationEditText;
    TMapView mMapView;
    ZoomControls mZoomCtrl;

    Set<String> markers;

    Location mLocation;
    boolean mTracking = true;
    Runnable mTracker = new Runnable() {
        @Override
        public void run() {
            mTracking = true;
        }
    };

    ServiceConnection mConnection;
    AutoDriveService.AutoDriveServiceBinder mBinder;

    TMapPoint mStartPoint;
    TMapPoint mEndPoint;

    PointF mTouchDown;

    Handler mHandler = new Handler();
    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("Main Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        client.disconnect();
    }

    class SearchThread extends AsyncTask<String, Void, List<TMapPOIItem>> {

        @Override
        protected List<TMapPOIItem> doInBackground(String... params) {
            TMapData data = new TMapData();
            try {
                List<TMapPOIItem> found = data.findAddressPOI(params[0], 20);
                return found;


            } catch (IOException e) {
                Log.e("cjw", "IOException", e);
            } catch (ParserConfigurationException e) {
                Log.e("cjw", "ParserConfigurationException", e);
            } catch (SAXException e) {
                Log.e("cjw", "SAXException", e);
            }

            return null;
        }

        @Override
        protected void onPostExecute(List<TMapPOIItem> items) {
            if (!isFinishing() && !isDestroyed() && items != null && items.size() > 0) {
                if (items.size() > 1) {
                    onLocationClicked(items.get(0));
                } else {
                    LocationListDialog dialog = new LocationListDialog(items);
                    dialog.setOnLocationClickListener(MainActivity.this);
                    dialog.show(getFragmentManager(), "list");
                }
            }
        }
    }

    public MainActivity() {
        super();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mDriveStartButton = (Button) findViewById(R.id.main_bt_drive_start);
        mDriveStartButton.setOnClickListener(this);

        mDriveEndButton = (Button) findViewById(R.id.main_bt_drive_end);
        mDriveEndButton.setOnClickListener(this);

        mButton = (Button) findViewById(R.id.main_bt_start);
        mButton.setOnClickListener(this);

        markers = new HashSet<>();

        findViewById(R.id.main_button_search).setOnClickListener(this);

        mConnectionText = (TextView) findViewById(R.id.main_tv_connection);
        mStatusText = (TextView) findViewById(R.id.main_status_text);

        mLocationEditText = (EditText) findViewById(R.id.main_edit_text_location);
        mLocationEditText.setOnEditorActionListener(this);

        mZoomCtrl = (ZoomControls) findViewById(R.id.main_zoom_ctrl);
        mZoomCtrl.setOnZoomInClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMapView.setZoomLevel(mMapView.getZoomLevel() + 1);
            }
        });

        mZoomCtrl.setOnZoomOutClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMapView.setZoomLevel(mMapView.getZoomLevel() - 1);
            }
        });

        Intent service = new Intent(this, AutoDriveService.class);
        startService(service);

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mBinder = (AutoDriveService.AutoDriveServiceBinder) service;
                mBinder.registerCallback(MainActivity.this);
                mBinder.startGpsUpdate();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        bindService(service, mConnection, BIND_AUTO_CREATE);

        mMapView = new TMapView(this);
        mMapView.setSKPMapApiKey("6d964526-b31b-3b78-b0e7-80b0cc9c2fee");

        mMapView.setOnApiKeyListener(new TMapView.OnApiKeyListenerCallback() {
            @Override
            public void SKPMapApikeySucceed() {
                Log.d("cjw", "SKPMapApikeySucceed");
                // Yonsei Univ
                mMapView.setZoomLevel(16);
            }

            @Override
            public void SKPMapApikeyFailed(String s) {
                Log.d("cjw", "SKPMapApikeyFailed " + s);
            }
        });

        mMapView.setOnLongClickListenerCallback(new TMapView.OnLongClickListenerCallback() {
            @Override
            public void onLongPressEvent(ArrayList<TMapMarkerItem> arrayList, ArrayList<TMapPOIItem> arrayList1, TMapPoint tMapPoint) {
                if (mBinder != null) {
                    mBinder.testGPS(tMapPoint.getLatitude(), tMapPoint.getLongitude());
                }
            }
        });

        mMapView.setOnClickListenerCallBack(new TMapView.OnClickListenerCallback() {
            @Override
            public boolean onPressEvent(ArrayList<TMapMarkerItem> arrayList, ArrayList<TMapPOIItem> arrayList1, TMapPoint tMapPoint, PointF pointF) {
                mTracking = false;
                mTouchDown = pointF;
                /*if (mStartPoint != null && mEndPoint != null) {
                    mMapView.removeAllMarkerItem();
                    mStartPoint = null;
                    mEndPoint = null;
                }
                if (mStartPoint == null) {
                    mStartPoint = tMapPoint;
                    TMapMarkerItem marker = new TMapMarkerItem();
                    marker.setTMapPoint(tMapPoint);
                    mMapView.addMarkerItem("start", marker);
                } else if (mEndPoint == null) {
                    mEndPoint = tMapPoint;
                    TMapMarkerItem marker = new TMapMarkerItem();
                    marker.setTMapPoint(tMapPoint);
                    mMapView.addMarkerItem("end", marker);
                }*/

                Log.d("cjw", "point : " + tMapPoint);
                return false;
            }

            @Override
            public boolean onPressUpEvent(ArrayList<TMapMarkerItem> arrayList, ArrayList<TMapPOIItem> arrayList1, TMapPoint tMapPoint, PointF pointF) {
                if (Math.abs(pointF.x - mTouchDown.x) < 10.0 &&  Math.abs(pointF.y - mTouchDown.y) < 10) {
                    mHandler.removeCallbacks(mTracker);
                    mHandler.postDelayed(mTracker, 5000l);

                    mEndPoint = tMapPoint;
                    TMapMarkerItem marker = new TMapMarkerItem();
                    marker.setTMapPoint(tMapPoint);
                    mMapView.addMarkerItem("end", marker);
                }

                return false;
            }
        });

        ViewGroup container = (ViewGroup) findViewById(R.id.main_map_container);
        container.addView(mMapView);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBinder != null) {
            mBinder.endGpsUpdate();
            mBinder.unregisterCallback(MainActivity.this);
            mBinder = null;
        }

        if (mConnection != null) {
            unbindService(mConnection);
            mConnection = null;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.main_bt_drive_start:
                if (mBinder != null) {
                    mBinder.sendDriveStart();
                }
                break;
            case R.id.main_bt_drive_end:
                if (mBinder != null) {
                    mBinder.sendDriveEnd();
                }

                mMapView.removeAllTMapPolyLine();
                mMapView.removeAllMarkerItem();
                mMapView.removeTMapPath();
                markers.clear();
                break;
            case R.id.main_bt_start:
                mBinder.connect();
                break;
            case R.id.main_button_search:
                /*if (mLocationEditText.getText().length() > 0) {
                    new SearchThread().execute(mLocationEditText.getText().toString());
                } else */
                if (mStartPoint != null && mEndPoint != null) {
                    searchRoute(mStartPoint, mEndPoint);
                }

                break;
        }

    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        return true;
    }

    void searchRoute(TMapPoint start, TMapPoint end) {
        TMapData data = new TMapData();
        data.findPathDataWithType(TMapData.TMapPathType.BICYCLE_PATH, start, end, new TMapData.FindPathDataListenerCallback() {
            @Override
            public void onFindPathData(TMapPolyLine polyLine) {
                //mMapView.addTMapPath(polyLine);

                ArrayList<TMapPoint> path = polyLine.getLinePoint();

                mMapView.removeAllTMapPolyLine();
                mMapView.addTMapPath(polyLine);

                for (String m : markers) {
                    mMapView.removeMarkerItem2(m);
                }

                markers.clear();

                TMapPoint last = null;
                Autodrive.SegmentList.Builder builder = Autodrive.SegmentList.newBuilder();
                for (int i = 0; i < path.size(); ++i) {
                    TMapPoint dat = path.get(i);

                    boolean dup = false;
                    for (int idx = 0; idx < i; idx++) {
                        TMapPoint prev = path.get(idx);
                        if (prev.getLatitude() == dat.getLatitude() || prev.getLongitude() == dat.getLongitude()) {
                            dup = true;
                            break;
                        }
                    }

                    if (last != null) {
                        double dist = Util.dist(last.getLatitude(), last.getLongitude(), dat.getLatitude(), dat.getLongitude());

                        if (dist < 10.0) {
                            continue;
                        }
                    }

                    if (dup) {
                        continue;
                    }

                    last = dat;

                    TMapMarkerItem marker = new TMapMarkerItem();
                    marker.setTMapPoint(dat);
                    mMapView.addMarkerItem("seg" + i, marker);
                    markers.add("seg" + i);

                    Autodrive.LocationMessage message = Autodrive.LocationMessage.newBuilder().
                            setLatitude(dat.getLatitude()).setLongitude(dat.getLongitude()).build();
                    builder.addLocations(message);
                    Log.d("tag", "lat : " + dat.getLatitude() + " lng : " + dat.getLongitude());
                }

                if (mBinder != null) {
                    mBinder.sendSegmentList(builder.build());
                }
            }
        });
    }

    @Override
    public void onLocationClicked(TMapPOIItem item) {
        TMapPoint point1 = mMapView.getLocationPoint();
        searchRoute(point1, item.getPOIPoint());
    }

    @Override
    public void onConnecting() {
        mConnectionText.setText(R.string.main_text_connecting);
    }

    @Override
    public void onInitialized() {
        mLocation = null;
        mConnectionText.setText(getString(R.string.main_text_initialized, "SERVER"));
    }

    @Override
    public void onDisconnected() {
        mConnectionText.setText(R.string.main_text_connection);
    }

    @Override
    public void onLocationChanged(Location l) {
        mLocation = l;
        Log.d("cjw", "Main " + l.toString());
        if (mStartPoint == null) {
            mStartPoint = new TMapPoint(l.getLatitude(), l.getLongitude());
        } else {
            mStartPoint.setLatitude(l.getLatitude());
            mStartPoint.setLongitude(l.getLongitude());
        }

        TMapMarkerItem marker = new TMapMarkerItem();
        marker.setTMapPoint(mStartPoint);
        mMapView.addMarkerItem("start", marker);

        if (mTracking) {
            // mMapView.setCenterPoint(l.getLatitude(), l.getLongitude());
        }
    }

    @Override
    public void onRequestPrintLog(String str) {
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
        mStatusText.setText(str);
    }

    @Override
    public void onPointOnSegementCalcuated(double oLat, double oLng, double pLat, double pLng) {
        TMapPolyLine line = new TMapPolyLine();
        line.addLinePoint(new TMapPoint(oLat, oLng));
        line.addLinePoint(new TMapPoint(pLat, pLng));
        mMapView.addTMapPolyLine("vertical", line);
    }
}
