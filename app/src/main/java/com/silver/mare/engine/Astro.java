package com.silver.mare.engine;

/**
 * Argumentos astronômicos (longitudes médias da Lua, Sol, perigeus e nodo)
 * para um instante em UTC. Polinômios de Meeus, mesma convenção do Neaps/Schureman.
 */
final class Astro {
    static final double D2R = Math.PI / 180.0;

    /** tau = hora lunar média (T + h - s), graus */
    final double tau;
    final double s, h, p, n, pp;

    private static final double[] LUNAR_LONGITUDE = {218.3164591, 481267.88134236, -0.0013268, 1.0 / 538841.0 - 1.0 / 65194000.0};
    private static final double[] SOLAR_LONGITUDE = {280.46645, 36000.76983, 0.0003032};
    private static final double[] LUNAR_PERIGEE = {83.353243, 4069.0137111, -0.0103238, -1.0 / 80053.0, 1.0 / 18999000.0};
    private static final double[] LUNAR_NODE = {125.044555, -1934.1361849, 0.0020762, 1.0 / 467410.0, -1.0 / 60616000.0};
    private static final double[] SOLAR_PERIGEE = {-77.06265000000002, 1.7190199999968172, 4.591e-7, 4.8e-7};

    Astro(long epochMillis) {
        double jd = epochMillis / 86400000.0 + 2440587.5;
        double t = (jd - 2451545.0) / 36525.0;
        s = mod360(poly(LUNAR_LONGITUDE, t));
        h = mod360(poly(SOLAR_LONGITUDE, t));
        p = mod360(poly(LUNAR_PERIGEE, t));
        n = mod360(poly(LUNAR_NODE, t));
        pp = mod360(poly(SOLAR_PERIGEE, t));
        double hourAngle = (jd - Math.floor(jd)) * 360.0; // 0 ao meio-dia UT => T = 180 + 15*UT
        tau = hourAngle + h - s;
    }

    /** V0 em graus para os coeficientes de Doodson [tau, s, h, p, N', p1, 90]. */
    double v0(int[] d) {
        return d[0] * tau + d[1] * s + d[2] * h + d[3] * p + d[4] * (-n) + d[5] * pp + d[6] * 90.0;
    }

    private static double poly(double[] c, double x) {
        double r = 0, xp = 1;
        for (double v : c) {
            r += v * xp;
            xp *= x;
        }
        return r;
    }

    static double mod360(double a) {
        double r = a % 360.0;
        return r < 0 ? r + 360.0 : r;
    }
}
