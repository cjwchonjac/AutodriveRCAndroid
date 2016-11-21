package com.autodrive;

import android.content.Context;
import android.util.Log;

import com.autodrive.message.Autodrive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jaewoncho on 2016. 11. 19..
 */
public class PathCollector {
    private String filePath;
    private PathLog log;

    static class PathLog implements Serializable {
        ArrayList<double[]> segments;
        ArrayList<double[]> gps;
    }

    public PathCollector() {

    }

    private void writeFile() {
        if (log != null && filePath != null) {
            try {
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath, false));
                oos.writeObject(log);
                oos.close();
            } catch (IOException e) {
            }
        }
    }

    public void init(Context ctx) {
        filePath = ctx.getExternalFilesDir(null).getAbsolutePath() + File.separator +
                "pathlog_" + (System.currentTimeMillis() / 1000) + ".txt";
    }

    public void emitSegments(Autodrive.SegmentList list) {
        if (log == null) {
            log = new PathLog();
        }

        log.segments = new ArrayList<>();

        for (Autodrive.LocationMessage l :list.getLocationsList()) {
            log.segments.add(new double[] {l.getLatitude(), l.getLongitude()});
        }

        writeFile();
    }

    public void emitGps(double latitude, double longitude, long tick) {
        if (log == null) {
            log = new PathLog();
        }

        if (log.gps == null) {
            log.gps = new ArrayList<>();
        }

        log.gps.add(new double[] {latitude, longitude, tick});

        writeFile();
    }

    public static List<double[]> dumpWithText(String path) {
        Log.d("cjw", "--path dump: " + path + " --");
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            List<double[]> list = new ArrayList<>();
            String buf;

            while ((buf = br.readLine()) != null) {
                int idx = buf.indexOf(',');
                if (idx >= 0) {
                    double latitude = Double.parseDouble(buf.substring(0, idx));

                    buf = buf.substring(idx + 1);
                    idx = buf.indexOf("at");

                    if (idx >= 0) {
                        double longitude = Double.parseDouble(buf.substring(0, idx));
                        buf = buf.substring(idx + 3);
                        long at = Long.parseLong(buf);

                        // Log.d("cjw", latitude + ", " + longitude + " at " + at);

                        list.add(new double[] {latitude, longitude, at });
                    }
                }
            }

            br.close();
            return list;
        } catch (IOException e) {
            Log.e("cjw", "", e);
        }

        return null;
    }

    public static PathLog dump(String path) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path));
            PathLog log = (PathLog) ois.readObject();
            Log.d("cjw", "--path dump: " + path + " --");



            if (log.segments != null) {
                Log.d("cjw", "--segments--");

                for (double[] segs : log.segments) {
                    Log.d("cjw", segs[0] + ", " + segs[1]);
                }
            }

            if (log.gps != null) {
                Log.d("cjw", "--gps--");

                int idx = 0;
                for (double[] gps : log.gps) {
                    Log.d("cjw", gps[0] + ", " + gps[1] + " at " + (long) gps[2] + ", idx: " + idx++);
                }
            }

            ois.close();
            return log;
        } catch (IOException e) {
            Log.e("cjw", "", e);
        } catch (ClassNotFoundException e) {
        }

        return null;
    }
}
