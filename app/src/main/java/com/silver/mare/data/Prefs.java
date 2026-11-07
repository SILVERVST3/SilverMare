package com.silver.mare.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferências: estação escolhida e última posição do GPS (usadas também pelo widget). */
public final class Prefs {
    private static final String FILE = "silvermare";
    private static final String K_STATION = "station_id";
    private static final String K_LAT = "last_lat";
    private static final String K_LON = "last_lon";
    private static final String K_FIX_TIME = "last_fix_time";
    private static final String K_MANUAL = "manual";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String stationId(Context c) {
        return sp(c).getString(K_STATION, null);
    }

    public static boolean isManual(Context c) {
        return sp(c).getBoolean(K_MANUAL, false);
    }

    public static void setStation(Context c, String id, boolean manual) {
        sp(c).edit().putString(K_STATION, id).putBoolean(K_MANUAL, manual).apply();
    }

    public static void setLocation(Context c, double lat, double lon, long time) {
        sp(c).edit()
                .putString(K_LAT, Double.toString(lat))
                .putString(K_LON, Double.toString(lon))
                .putLong(K_FIX_TIME, time)
                .apply();
    }

    public static Double lastLat(Context c) {
        String v = sp(c).getString(K_LAT, null);
        return v == null ? null : Double.valueOf(v);
    }

    public static Double lastLon(Context c) {
        String v = sp(c).getString(K_LON, null);
        return v == null ? null : Double.valueOf(v);
    }

    public static long lastFixTime(Context c) {
        return sp(c).getLong(K_FIX_TIME, 0);
    }
}
