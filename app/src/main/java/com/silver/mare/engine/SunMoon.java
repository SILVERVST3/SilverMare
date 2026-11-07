package com.silver.mare.engine;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Nascer/pôr do sol (algoritmo NOAA) e fase da Lua, calculados offline. */
public final class SunMoon {
    private SunMoon() {}

    private static final double SYNODIC = 29.530588853;
    /** Lua nova de referência: 2000-01-06 18:14 UTC (JD 2451550.26). */
    private static final double NEW_MOON_JD = 2451550.26;

    /** Retorna {nascer, pôr} em epoch ms, ou null se não houver (regiões polares). */
    public static long[] sunriseSunset(LocalDate date, double lat, double lon) {
        long midnightUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        double jd = midnightUtc / 86400000.0 + 2440587.5 + 0.5 - lon / 360.0; // próximo ao meio-dia local
        double t = (jd - 2451545.0) / 36525.0;
        double l0 = mod360(280.46646 + t * (36000.76983 + t * 0.0003032));
        double m = 357.52911 + t * (35999.05029 - 0.0001537 * t);
        double e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t);
        double mr = Math.toRadians(m);
        double c = Math.sin(mr) * (1.914602 - t * (0.004817 + 0.000014 * t))
                + Math.sin(2 * mr) * (0.019993 - 0.000101 * t) + Math.sin(3 * mr) * 0.000289;
        double trueLong = l0 + c;
        double omega = 125.04 - 1934.136 * t;
        double lambda = trueLong - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));
        double eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0;
        double eps = eps0 + 0.00256 * Math.cos(Math.toRadians(omega));
        double decl = Math.asin(Math.sin(Math.toRadians(eps)) * Math.sin(Math.toRadians(lambda)));
        double y = Math.tan(Math.toRadians(eps / 2));
        y *= y;
        double l0r = Math.toRadians(l0);
        double eot = 4 * Math.toDegrees(y * Math.sin(2 * l0r) - 2 * e * Math.sin(mr)
                + 4 * e * y * Math.sin(mr) * Math.cos(2 * l0r)
                - 0.5 * y * y * Math.sin(4 * l0r) - 1.25 * e * e * Math.sin(2 * mr));
        double latR = Math.toRadians(lat);
        double cosHa = Math.cos(Math.toRadians(90.833)) / (Math.cos(latR) * Math.cos(decl)) - Math.tan(latR) * Math.tan(decl);
        if (cosHa < -1 || cosHa > 1) return null;
        double ha = Math.toDegrees(Math.acos(cosHa));
        double noon = 720 - 4 * lon - eot; // minutos UTC
        long rise = midnightUtc + Math.round((noon - 4 * ha) * 60000);
        long set = midnightUtc + Math.round((noon + 4 * ha) * 60000);
        return new long[]{rise, set};
    }

    /** Idade da Lua em dias (0 = nova, ~14,8 = cheia). */
    public static double moonAge(long epochMillis) {
        double jd = epochMillis / 86400000.0 + 2440587.5;
        double age = (jd - NEW_MOON_JD) % SYNODIC;
        if (age < 0) age += SYNODIC;
        return age;
    }

    /** Fração iluminada (0 a 1). */
    public static double illumination(long epochMillis) {
        return (1 - Math.cos(2 * Math.PI * moonAge(epochMillis) / SYNODIC)) / 2;
    }

    public static String phaseName(long epochMillis) {
        double a = moonAge(epochMillis) / SYNODIC; // 0..1
        if (a < 0.0339 || a >= 0.9661) return "Lua nova";
        if (a < 0.2161) return "Lua crescente";
        if (a < 0.2839) return "Quarto crescente";
        if (a < 0.4661) return "Crescente gibosa";
        if (a < 0.5339) return "Lua cheia";
        if (a < 0.7161) return "Minguante gibosa";
        if (a < 0.7839) return "Quarto minguante";
        return "Lua minguante";
    }

    /** Emoji da fase para exibição. */
    public static String phaseEmoji(long epochMillis) {
        double a = moonAge(epochMillis) / SYNODIC;
        int idx = (int) Math.floor(a * 8 + 0.5) % 8;
        // Hemisfério sul: a parte iluminada aparece invertida, mas mantemos os emojis padrão.
        String[] e = {"🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘"};
        return e[idx];
    }

    /** "Sizígia" (águas vivas), "Quadratura" (águas mortas) ou null. */
    public static String springNeap(long epochMillis) {
        double age = moonAge(epochMillis);
        double half = SYNODIC / 2, quarter = SYNODIC / 4;
        double toSpring = Math.min(Math.min(age, SYNODIC - age), Math.abs(age - half));
        double toNeap = Math.min(Math.abs(age - quarter), Math.abs(age - 3 * quarter));
        if (toSpring <= 2.5) return "Sizígia · águas vivas (marés maiores)";
        if (toNeap <= 2.5) return "Quadratura · águas mortas (marés menores)";
        return null;
    }

    private static double mod360(double a) {
        double r = a % 360;
        return r < 0 ? r + 360 : r;
    }
}
