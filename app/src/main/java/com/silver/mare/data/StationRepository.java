package com.silver.mare.data;

import android.content.Context;

import com.silver.mare.engine.Station;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Carrega as estações embutidas no app (assets/stations.json). Nada é baixado da internet. */
public final class StationRepository {
    private static List<Station> cache;

    private StationRepository() {}

    public static synchronized List<Station> all(Context context) {
        if (cache != null) return cache;
        List<Station> list = new ArrayList<>();
        try (InputStream in = context.getApplicationContext().getAssets().open("stations.json")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) > 0) buf.write(b, 0, r);
            JSONArray arr = new JSONArray(new String(buf.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray hc = o.getJSONArray("hc");
                String[] names = new String[hc.length()];
                double[] amps = new double[hc.length()];
                double[] phases = new double[hc.length()];
                for (int k = 0; k < hc.length(); k++) {
                    JSONArray c = hc.getJSONArray(k);
                    names[k] = c.getString(0);
                    amps[k] = c.getDouble(1);
                    phases[k] = c.getDouble(2);
                }
                list.add(new Station(
                        o.getString("id"),
                        o.getString("name"),
                        o.optString("uf", ""),
                        o.getDouble("lat"),
                        o.getDouble("lon"),
                        o.optString("tz", "America/Sao_Paulo"),
                        o.getDouble("msl"),
                        o.optDouble("hat", o.getDouble("msl") * 2),
                        o.optDouble("lat_", 0),
                        o.optString("period", ""),
                        o.optString("source", ""),
                        names, amps, phases));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler stations.json", e);
        }
        cache = Collections.unmodifiableList(list);
        return cache;
    }

    public static Station byId(Context context, String id) {
        if (id == null) return null;
        for (Station s : all(context)) if (s.id.equals(id)) return s;
        return null;
    }

    public static Station nearest(Context context, double lat, double lon) {
        Station best = null;
        double bestD = Double.MAX_VALUE;
        for (Station s : all(context)) {
            double d = s.distanceKm(lat, lon);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** Lista ordenada por distância (se houver posição) ou de norte a sul. */
    public static List<Station> sorted(Context context, final Double lat, final Double lon) {
        List<Station> list = new ArrayList<>(all(context));
        if (lat != null && lon != null) {
            Collections.sort(list, (a, b) -> Double.compare(a.distanceKm(lat, lon), b.distanceKm(lat, lon)));
        } else {
            Collections.sort(list, (a, b) -> Double.compare(b.latitude, a.latitude));
        }
        return list;
    }
}
