package com.silver.mare.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Previsão harmônica de maré, 100% offline.
 *
 * h(t) = Z0 + Σ f·H·cos(ω·(t − t0) + V0(t0) + u − g)
 *
 * V0 é recalculado a cada dia (UTC) e as correções nodais (f, u) no meio de cada dia.
 */
public final class TidePredictor {
    private static final long DAY_MS = 86_400_000L;
    private static final long HOUR_MS = 3_600_000L;

    private final Station station;
    private final ConstituentTable.Def[] defs;
    private final double[] amp;
    private final double[] phaseRad;

    private long cachedDay = Long.MIN_VALUE;
    private double[] cA;     // amplitude corrigida (m)
    private double[] cW;     // velocidade (rad/h)
    private double[] cPhi;   // fase no início do dia (rad)

    public TidePredictor(Station station) {
        this.station = station;
        List<ConstituentTable.Def> d = new ArrayList<>();
        List<Double> a = new ArrayList<>();
        List<Double> g = new ArrayList<>();
        for (int i = 0; i < station.names.length; i++) {
            ConstituentTable.Def def = ConstituentTable.get(station.names[i]);
            if (def == null || station.amplitudes[i] == 0) continue; // constituinte desconhecida: ignorada
            if (def.speed == 0) continue; // Z0 já está em meanLevel
            d.add(def);
            a.add(station.amplitudes[i]);
            g.add(station.phases[i] * Astro.D2R);
        }
        defs = d.toArray(new ConstituentTable.Def[0]);
        amp = new double[defs.length];
        phaseRad = new double[defs.length];
        for (int i = 0; i < defs.length; i++) {
            amp[i] = a.get(i);
            phaseRad[i] = g.get(i);
        }
    }

    public Station getStation() {
        return station;
    }

    private synchronized void prepare(long day) {
        if (day == cachedDay) return;
        long t0 = day * DAY_MS;
        Astro base = new Astro(t0);
        Astro mid = new Astro(t0 + DAY_MS / 2);
        double[] fundF = new double[ConstituentTable.FUNDAMENTAL_COUNT];
        double[] fundU = new double[ConstituentTable.FUNDAMENTAL_COUNT];
        Nodal.compute(mid, fundF, fundU);
        int n = defs.length;
        double[] A = new double[n], W = new double[n], P = new double[n];
        for (int i = 0; i < n; i++) {
            ConstituentTable.Def def = defs[i];
            double f = 1, u = 0;
            for (int k = 0; k < def.fRecipe.length; k += 2) {
                f *= Math.pow(fundF[(int) def.fRecipe[k]], def.fRecipe[k + 1]);
            }
            for (int k = 0; k < def.uRecipe.length; k += 2) {
                u += fundU[(int) def.uRecipe[k]] * def.uRecipe[k + 1];
            }
            A[i] = amp[i] * f;
            W[i] = def.speed * Astro.D2R;
            P[i] = (base.v0(def.doodson) + u) * Astro.D2R - phaseRad[i];
        }
        cA = A;
        cW = W;
        cPhi = P;
        cachedDay = day;
    }

    /** Altura da maré (m, acima do NR) no instante dado (epoch ms). */
    public synchronized double heightAt(long epochMillis) {
        long day = Math.floorDiv(epochMillis, DAY_MS);
        prepare(day);
        double hours = (epochMillis - day * DAY_MS) / (double) HOUR_MS;
        double sum = station.meanLevel;
        for (int i = 0; i < cA.length; i++) {
            sum += cA[i] * Math.cos(cW[i] * hours + cPhi[i]);
        }
        return sum;
    }

    /** Taxa de variação (m/h) no instante dado. Positivo = enchendo. */
    public synchronized double rateAt(long epochMillis) {
        long day = Math.floorDiv(epochMillis, DAY_MS);
        prepare(day);
        double hours = (epochMillis - day * DAY_MS) / (double) HOUR_MS;
        double sum = 0;
        for (int i = 0; i < cA.length; i++) {
            sum -= cA[i] * cW[i] * Math.sin(cW[i] * hours + cPhi[i]);
        }
        return sum;
    }

    /** Série de alturas entre start e end, com passo em minutos. */
    public double[] series(long start, long end, int stepMinutes) {
        long step = stepMinutes * 60_000L;
        int count = (int) ((end - start) / step) + 1;
        double[] out = new double[count];
        for (int i = 0; i < count; i++) out[i] = heightAt(start + i * step);
        return out;
    }

    /** Preamares e baixa-mares entre start e end. */
    public List<TideExtreme> extremes(long start, long end) {
        // Margem para não perder extremos nas bordas; depois recorta.
        long from = start - 3 * HOUR_MS;
        long to = end + 3 * HOUR_MS;
        long step = 10 * 60_000L;
        List<TideExtreme> raw = new ArrayList<>();
        long t = from;
        double r0 = rateAt(t);
        while (t < to) {
            long t1 = t + step;
            double r1 = rateAt(t1);
            if (r0 == 0 || (r0 > 0) != (r1 > 0)) {
                long tx = bisect(t, t1, r0);
                boolean high = r0 > 0;
                raw.add(new TideExtreme(tx, heightAt(tx), high));
            }
            t = t1;
            r0 = r1;
        }
        List<TideExtreme> clean = removeSmallWiggles(raw, 0.015);
        List<TideExtreme> out = new ArrayList<>();
        for (TideExtreme e : clean) {
            if (e.time >= start && e.time < end) out.add(e);
        }
        return out;
    }

    private long bisect(long a, long b, double ra) {
        while (b - a > 1000) {
            long m = (a + b) / 2;
            double rm = rateAt(m);
            if ((rm > 0) == (ra > 0) && rm != 0) {
                a = m;
                ra = rm;
            } else {
                b = m;
            }
        }
        return (a + b) / 2;
    }

    /** Remove pares de extremos muito próximos em altura (oscilações de águas rasas). */
    private static List<TideExtreme> removeSmallWiggles(List<TideExtreme> in, double threshold) {
        List<TideExtreme> list = new ArrayList<>(in);
        boolean changed = true;
        while (changed && list.size() > 2) {
            changed = false;
            int best = -1;
            double bestDiff = threshold;
            for (int i = 1; i < list.size() - 2; i++) {
                double d = Math.abs(list.get(i).height - list.get(i + 1).height);
                if (d < bestDiff) {
                    bestDiff = d;
                    best = i;
                }
            }
            if (best >= 0) {
                list.remove(best + 1);
                list.remove(best);
                changed = true;
            }
        }
        return list;
    }
}
