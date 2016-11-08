package com.autodrive;

/**
 * Created by lineplus on 2016. 11. 8..
 */

public class Util {
    public static boolean pointInLineSeg(double x1, double y1, double x2, double y2, double x, double y) {
        return ((x2 > x1 && x2 >= x && x >= x1) || (x2 <= x1 && x2 <= x && x <= x1)) &&
                ((y2 > y1 && y2 >= y && y >= y1) || (y2 <= y1 && y2 <= y && y <= y1));
    }

    public static void pointOnLine(double x1, double y1, double x2, double y2, double x, double y, double[] out) {
        if (x2 == x1) {
            out[0] = x1;
            out[1] = y;
            return;
        }

        if (y2 == y1) {
            out[0] = x;
            out[1] = y1;
        }

        double slope1 = (y2 - y1) / (x2 - x1);
        double slope2 = -1.0 / slope1;
        double X = (slope1 * x1 - slope2 * x + y - y1) / (slope1 - slope2);
        double Y = slope1 * (X - x1) + y1;

        out[0] = X;
        out[1] = Y;

    }

    public static double deg2rad(double deg) {
        return deg * (Math.PI / 180.0);
    }

    public static double dist(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371 * 1000; // Radius of the earth in meter
        double dLat = deg2rad(lat2 - lat1);  // deg2rad below
        double dLon = deg2rad(lng1 - lng2);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double d = R * c; // Distance in meter
        return d;
    }
}
