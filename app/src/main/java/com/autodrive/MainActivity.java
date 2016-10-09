package com.autodrive;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.skp.Tmap.TMapData;
import com.skp.Tmap.TMapPOIItem;
import com.skp.Tmap.TMapPoint;
import com.skp.Tmap.TMapPolyLine;
import com.skp.Tmap.TMapView;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by jaewoncho on 2016-09-18.
 */
public class MainActivity extends Activity implements View.OnClickListener,
        AutoDriveService.AutoDriveServiceCallback, EditText.OnEditorActionListener,
        LocationListDialog.OnLocationClickListener {

    Button mButton;
    TextView mConnectionText;
    EditText mLocationEditText;
    TMapView mMapView;

    ServiceConnection mConnection;
    AutoDriveService.AutoDriveServiceBinder mBinder;

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

        mButton = (Button) findViewById(R.id.main_bt_start);
        mButton.setOnClickListener(this);

        findViewById(R.id.main_button_search).setOnClickListener(this);

        mConnectionText = (TextView) findViewById(R.id.main_tv_connection);

        mLocationEditText = (EditText) findViewById(R.id.main_edit_text_location);
        mLocationEditText.setOnEditorActionListener(this);

        Intent service = new Intent(this, AutoDriveService.class);
        startService(service);

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mBinder = (AutoDriveService.AutoDriveServiceBinder) service;
                mBinder.registerCallback(MainActivity.this);
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
            }

            @Override
            public void SKPMapApikeyFailed(String s) {
                Log.d("cjw", "SKPMapApikeyFailed " + s);
            }
        });

        ViewGroup container = (ViewGroup) findViewById(R.id.main_map_container);
        container.addView(mMapView);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBinder != null) {
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
            case R.id.main_bt_start:
                mBinder.connect();
                break;
            case R.id.main_button_search:
                if (mLocationEditText.getText().length() > 0) {
                    new SearchThread().execute(mLocationEditText.getText().toString());
                }
                break;
        }

    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        return true;
    }

    @Override
    public void onLocationClicked(TMapPOIItem item) {
        TMapPoint point1 = mMapView.getLocationPoint();

        TMapData data = new TMapData();
        data.findPathDataWithType(TMapData.TMapPathType.CAR_PATH, point1, item.getPOIPoint(), new TMapData.FindPathDataListenerCallback() {
            @Override
            public void onFindPathData(TMapPolyLine polyLine) {
                //mMapView.addTMapPath(polyLine);

                ArrayList<TMapPoint> path = polyLine.getLinePoint();

                mMapView.removeAllTMapPolyLine();
                mMapView.addTMapPath(polyLine);
                for (int i = 0; i < path.size(); ++i) {
                    TMapPoint dat = path.get(i);
                    Log.d("tag", "lat : " + dat.getLatitude() + " lng : " + dat.getLongitude());

                }
            }
        });
    }

    @Override
    public void onConnecting() {
        mConnectionText.setText(R.string.main_text_connecting);
    }

    @Override
    public void onInitialized(BluetoothDevice device) {
        mConnectionText.setText(getString(R.string.main_text_initialized, device.getName()));
    }

    @Override
    public void onDisconnected() {
        mConnectionText.setText(R.string.main_text_connection);
    }
}
