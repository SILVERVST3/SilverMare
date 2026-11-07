package com.silver.mare.ui;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatação em português do Brasil. */
public final class Format {
    private Format() {}

    public static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm", PT_BR);
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", PT_BR);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("EEE, dd/MM", PT_BR);

    public static String meters(double v, int decimals) {
        NumberFormat nf = NumberFormat.getNumberInstance(PT_BR);
        nf.setMinimumFractionDigits(decimals);
        nf.setMaximumFractionDigits(decimals);
        double r = Math.abs(v) < 0.5 * Math.pow(10, -decimals) ? 0.0 : v; // evita "-0,0"
        return nf.format(r) + " m";
    }

    public static String time(long epochMillis, ZoneId zone) {
        return HM.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }

    public static String dayLong(java.time.LocalDate date) {
        return capitalize(DAY_LONG.format(date));
    }

    public static String dayShort(java.time.LocalDate date) {
        return capitalize(DAY_SHORT.format(date)).replace(".", "");
    }

    public static String km(double km) {
        if (km < 1) return "menos de 1 km";
        if (km < 10) return String.format(PT_BR, "%.1f km", km);
        return String.format(PT_BR, "%.0f km", km);
    }

    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(PT_BR) + s.substring(1);
    }
}
