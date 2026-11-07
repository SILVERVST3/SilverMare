package com.silver.mare.engine;

/**
 * Correções nodais (f, u) das constituintes fundamentais — fórmulas da IHO,
 * as mesmas usadas pelo Neaps. u em graus.
 */
final class Nodal {
    private Nodal() {}

    static void compute(Astro a, double[] f, double[] u) {
        double n = a.n * Astro.D2R;
        double p = a.p * Astro.D2R;
        double sinN = Math.sin(n), cosN = Math.cos(n);
        double sin2N = Math.sin(2 * n), cos2N = Math.cos(2 * n);
        double sin3N = Math.sin(3 * n), cos3N = Math.cos(3 * n);

        // M2
        f[ConstituentTable.F_M2] = 1.0007 - 0.0373 * cosN + 0.0002 * cos2N;
        u[ConstituentTable.F_M2] = -2.14 * sinN;
        // K2
        f[ConstituentTable.F_K2] = 1.0246 + 0.2863 * cosN + 0.0083 * cos2N - 0.0015 * cos3N;
        u[ConstituentTable.F_K2] = -17.74 * sinN + 0.68 * sin2N - 0.04 * sin3N;
        // K1
        f[ConstituentTable.F_K1] = 1.006 + 0.115 * cosN - 0.0088 * cos2N + 0.0006 * cos3N;
        u[ConstituentTable.F_K1] = -8.86 * sinN + 0.68 * sin2N - 0.07 * sin3N;
        // O1
        f[ConstituentTable.F_O1] = 1.0176 + 0.1871 * cosN - 0.0147 * cos2N;
        u[ConstituentTable.F_O1] = 10.8 * sinN - 1.34 * sin2N + 0.19 * sin3N;
        // M1
        fromSinCos(Math.sin(p) + 0.2 * Math.sin(p - n), 2 * (Math.cos(p) + 0.2 * Math.cos(p - n)), f, u, ConstituentTable.F_M1);
        // Mm
        f[ConstituentTable.F_MM] = 1 - 0.1311 * cosN + 0.0538 * Math.cos(2 * p) + 0.0205 * Math.cos(2 * p - n);
        u[ConstituentTable.F_MM] = 0;
        // Mf
        f[ConstituentTable.F_MF] = 1.084 + 0.415 * cosN + 0.039 * cos2N;
        u[ConstituentTable.F_MF] = -23.7 * sinN + 2.7 * sin2N - 0.4 * sin3N;
        // J1
        f[ConstituentTable.F_J1] = 1.1029 + 0.1676 * cosN - 0.017 * cos2N + 0.0016 * cos3N;
        u[ConstituentTable.F_J1] = -12.94 * sinN + 1.34 * sin2N - 0.19 * sin3N;
        // L2
        fromSinCos(-0.2505 * Math.sin(2 * p) - 0.1102 * Math.sin(2 * p - n) - 0.0156 * Math.sin(2 * p - 2 * n) - 0.037 * sinN,
                1 - 0.2505 * Math.cos(2 * p) - 0.1102 * Math.cos(2 * p - n) - 0.0156 * Math.cos(2 * p - 2 * n) - 0.037 * cosN,
                f, u, ConstituentTable.F_L2);
        // M3
        f[ConstituentTable.F_M3] = Math.pow(Math.sqrt(f[ConstituentTable.F_M2]), 3);
        u[ConstituentTable.F_M3] = -3.21 * sinN;
    }

    private static void fromSinCos(double fs, double fc, double[] f, double[] u, int idx) {
        f[idx] = Math.sqrt(fs * fs + fc * fc);
        u[idx] = Math.atan2(fs, fc) / Astro.D2R;
    }
}
