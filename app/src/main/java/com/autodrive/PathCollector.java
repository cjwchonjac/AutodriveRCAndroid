package com.autodrive;

import android.content.Context;
import android.util.Log;

import com.autodrive.message.Autodrive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

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

    public static void dump(String path) {
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

                for (double[] gps : log.gps) {
                    Log.d("cjw", gps[0] + ", " + gps[1] + " at " + (long) gps[2]);
                }
            }
            ois.close();
        } catch (IOException e) {
        } catch (ClassNotFoundException e) {
        }
    }
}
