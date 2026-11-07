package com.silver.mare.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.silver.mare.data.Prefs;
import com.silver.mare.data.StationRepository;
import com.silver.mare.engine.Station;
import com.silver.mare.engine.SunMoon;
import com.silver.mare.engine.TideExtreme;
import com.silver.mare.engine.TidePredictor;
import com.silver.mare.widget.TideWidgetProvider;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 42;
    private static final long GPS_TIMEOUT_MS = 45_000L;
    private static final double FAR_KM = 40;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout content;
    private TextView gpsStatus;

    private Station station;
    private TidePredictor predictor;
    private ZoneId zone;
    private LocalDate selected;

    private LocationManager locationManager;
    private LocationListener listener;
    private boolean locating;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (station != null && selected != null && selected.equals(LocalDate.now(zone))) render();
            handler.postDelayed(this, 60_000L);
        }
    };

    private final Runnable gpsTimeout = () -> {
        if (!locating) return;
        stopLocating();
        setGpsStatus("Não foi possível obter sinal do GPS agora. Tente ao ar livre ou escolha a estação na lista.");
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Theme.BG);
        getWindow().setNavigationBarColor(Theme.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.BG);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        content.setPadding(p, p, p, p);
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        applyInsets(scroll, p);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        station = StationRepository.byId(this, Prefs.stationId(this));
        if (station == null) {
            Double lat = Prefs.lastLat(this), lon = Prefs.lastLon(this);
            if (lat != null && lon != null) station = StationRepository.nearest(this, lat, lon);
        }
        if (station == null) {
            // Primeira abertura: começa por uma estação padrão e já tenta o GPS.
            List<Station> all = StationRepository.all(this);
            station = all.get(0);
            for (Station s : all) if (s.name.startsWith("Recife")) station = s;
            setStation(station, false, false);
            startLocating();
        } else {
            setStation(station, Prefs.isManual(this), false);
            if (!Prefs.isManual(this) && System.currentTimeMillis() - Prefs.lastFixTime(this) > 6 * 3_600_000L
                    && hasLocationPermission()) {
                startLocating(); // atualiza em silêncio se a última posição for antiga
            }
        }
    }

    private void applyInsets(View root, int basePad) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars());
                top = i.top;
                bottom = i.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            content.setPadding(basePad, basePad + top, basePad, basePad + bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 60_000L);
        if (station != null) render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        stopLocating();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ estação

    private void setStation(Station s, boolean manual, boolean save) {
        station = s;
        predictor = new TidePredictor(s);
        zone = ZoneId.of(s.timeZone);
        if (selected == null) selected = LocalDate.now(zone);
        if (save || Prefs.stationId(this) == null) {
            Prefs.setStation(this, s.id, manual);
            TideWidgetProvider.updateAll(this);
        }
        render();
    }

    private void showStationPicker() {
        final Double lat = Prefs.lastLat(this), lon = Prefs.lastLon(this);
        final List<Station> list = StationRepository.sorted(this, lat, lon);
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Station s = list.get(i);
            labels[i] = s.displayName() + (lat != null && lon != null ? "  ·  " + Format.km(s.distanceKm(lat, lon)) : "");
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(lat != null ? "Estações (mais próximas primeiro)" : "Estações (norte → sul)")
                .setItems(labels, (dlg, which) -> setStation(list.get(which), true, true))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------------ GPS

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocating() {
        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        boolean gpsOn = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean netOn = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsOn && !netOn) {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Localização desligada")
                    .setMessage("Ative a localização do celular para encontrar a estação de maré mais próxima. O GPS funciona sem internet.")
                    .setPositiveButton("Abrir configurações", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Agora não", null)
                    .show();
            return;
        }
        try {
            Location best = null;
            for (String prov : locationManager.getProviders(true)) {
                Location l = locationManager.getLastKnownLocation(prov);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 15 * 60_000L) {
                onLocation(best);
                return;
            }
            if (best != null) onLocation(best); // usa a última conhecida enquanto busca uma nova
            stopLocating();
            locating = true;
            setGpsStatus("Buscando sua posição pelo GPS…");
            listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location.hasAccuracy() && location.getAccuracy() > 5000) return;
                    stopLocating();
                    onLocation(location);
                    setGpsStatus(null);
                }

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {}

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}
            };
            if (gpsOn) locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, listener, Looper.getMainLooper());
            if (netOn) locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, listener, Looper.getMainLooper());
            handler.removeCallbacks(gpsTimeout);
            handler.postDelayed(gpsTimeout, GPS_TIMEOUT_MS);
        } catch (SecurityException e) {
            setGpsStatus("Permissão de localização negada.");
        }
    }

    private void stopLocating() {
        locating = false;
        handler.removeCallbacks(gpsTimeout);
        if (listener != null) {
            try {
                locationManager.removeUpdates(listener);
            } catch (Exception ignored) {
            }
            listener = null;
        }
    }

    private void onLocation(Location l) {
        Prefs.setLocation(this, l.getLatitude(), l.getLongitude(), l.getTime());
        Station nearest = StationRepository.nearest(this, l.getLatitude(), l.getLongitude());
        if (nearest != null) {
            boolean changed = station == null || !nearest.id.equals(station.id);
            Prefs.setStation(this, nearest.id, false);
            if (changed) {
                selected = null;
                setStation(nearest, false, true);
                Toast.makeText(this, "Estação mais próxima: " + nearest.displayName(), Toast.LENGTH_SHORT).show();
            } else {
                TideWidgetProvider.updateAll(this);
                render();
            }
        }
    }

    private void setGpsStatus(String msg) {
        if (gpsStatus == null) return;
        gpsStatus.setText(msg == null ? "" : msg);
        gpsStatus.setVisibility(msg == null ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        if (hasLocationPermission()) {
            startLocating();
        } else {
            Toast.makeText(this, "Sem permissão de localização. Escolha a estação na lista.", Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ tela

    private void render() {
        if (station == null) return;
        String keepStatus = gpsStatus != null && gpsStatus.getVisibility() == View.VISIBLE ? gpsStatus.getText().toString() : null;
        content.removeAllViews();
        LocalDate today = LocalDate.now(zone);
        if (selected == null) selected = today;
        boolean isToday = selected.equals(today);
        long dayStart = selected.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEnd = selected.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        List<TideExtreme> extremes = predictor.extremes(dayStart, dayEnd);

        // Cabeçalho
        LinearLayout head = Ui.row(this);
        TextView app = Ui.text(this, "SilverMaré", 24, Theme.SILVER, true);
        head.addView(app, Ui.weight(1));
        TextView offline = Ui.text(this, "OFFLINE", 11, Theme.SEA, true);
        offline.setBackground(Ui.rounded(this, 0x1A4FC3D9, 10, 0));
        offline.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        head.addView(offline);
        content.addView(head);
        content.addView(Ui.text(this, "Tábua de marés da costa brasileira", 13, Theme.TEXT_DIM, false));

        // Estação
        LinearLayout st = Ui.card(this);
        st.addView(Ui.title(this, "Estação maregráfica"));
        st.addView(Ui.text(this, station.displayName(), 20, Theme.TEXT, true));
        Double lat = Prefs.lastLat(this), lon = Prefs.lastLon(this);
        String sub;
        double dist = -1;
        if (lat != null && lon != null) {
            dist = station.distanceKm(lat, lon);
            sub = (Prefs.isManual(this) ? "Escolhida na lista · " : "Mais próxima de você · ") + "a " + Format.km(dist) + " de você";
        } else {
            sub = Prefs.isManual(this) ? "Escolhida na lista" : "Toque em \"Minha localização\" para usar o GPS";
        }
        TextView subT = Ui.text(this, sub, 13, Theme.TEXT_DIM, false);
        subT.setPadding(0, Ui.dp(this, 2), 0, 0);
        st.addView(subT);
        if (dist > FAR_KM) {
            TextView warn = Ui.text(this, "Atenção: você está a " + Format.km(dist)
                    + " desta estação. Horários e alturas no seu local podem ser diferentes.", 12, Theme.LOW, false);
            warn.setPadding(0, Ui.dp(this, 6), 0, 0);
            st.addView(warn);
        }
        gpsStatus = Ui.text(this, "", 12, Theme.SEA, false);
        gpsStatus.setPadding(0, Ui.dp(this, 6), 0, 0);
        st.addView(gpsStatus);
        setGpsStatus(keepStatus);
        LinearLayout btns = Ui.row(this);
        btns.setPadding(0, Ui.dp(this, 12), 0, 0);
        TextView gpsBtn = Ui.pill(this, "📍 Minha localização", true);
        gpsBtn.setOnClickListener(v -> startLocating());
        btns.addView(gpsBtn, Ui.weight(1));
        btns.addView(Ui.spacer(this, 8, 1));
        TextView listBtn = Ui.pill(this, "Estações", false);
        listBtn.setOnClickListener(v -> showStationPicker());
        btns.addView(listBtn, Ui.weight(0.6f));
        st.addView(btns);
        content.addView(st);

        // Data
        LinearLayout dc = Ui.card(this);
        dc.addView(Ui.title(this, "Previsão por data"));
        LinearLayout dr = Ui.row(this);
        TextView prev = Ui.pill(this, "◀", false);
        prev.setOnClickListener(v -> { selected = selected.minusDays(1); render(); });
        TextView next = Ui.pill(this, "▶", false);
        next.setOnClickListener(v -> { selected = selected.plusDays(1); render(); });
        TextView dateT = Ui.text(this, Format.dayLong(selected), 15, Theme.TEXT, true);
        dateT.setGravity(Gravity.CENTER);
        dateT.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        dateT.setOnClickListener(v -> pickDate());
        dr.addView(prev);
        dr.addView(dateT, Ui.weight(1));
        dr.addView(next);
        dc.addView(dr);
        LinearLayout dr2 = Ui.row(this);
        dr2.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView cal = Ui.pill(this, "📅 Escolher data", false);
        cal.setOnClickListener(v -> pickDate());
        dr2.addView(cal, Ui.weight(1));
        if (!isToday) {
            dr2.addView(Ui.spacer(this, 8, 1));
            TextView hoje = Ui.pill(this, "Hoje", true);
            hoje.setOnClickListener(v -> { selected = LocalDate.now(zone); render(); });
            dr2.addView(hoje, Ui.weight(0.5f));
        }
        dc.addView(dr2);
        content.addView(dc);

        // Agora (só para hoje)
        if (isToday) content.addView(nowCard());

        // Gráfico
        LinearLayout gc = Ui.card(this);
        gc.addView(Ui.title(this, "Curva da maré"));
        TideChartView chart = new TideChartView(this);
        long[] sun = SunMoon.sunriseSunset(selected, station.latitude, station.longitude);
        chart.setData(predictor, zone, dayStart, dayEnd, extremes, sun, isToday);
        gc.addView(chart);
        TextView hint = Ui.text(this, "Toque e arraste no gráfico para ver a altura em qualquer horário. Faixas escuras = noite.", 11, Theme.TEXT_DIM, false);
        hint.setPadding(0, Ui.dp(this, 6), 0, 0);
        gc.addView(hint);
        content.addView(gc);

        // Tábua do dia
        LinearLayout tc = Ui.card(this);
        tc.addView(Ui.title(this, "Tábua do dia"));
        if (extremes.isEmpty()) tc.addView(Ui.text(this, "Sem preamar/baixa-mar neste dia.", 14, Theme.TEXT_DIM, false));
        for (TideExtreme e : extremes) {
            LinearLayout r = Ui.row(this);
            r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            TextView icon = Ui.text(this, e.high ? "▲" : "▼", 16, e.high ? Theme.HIGH : Theme.LOW, true);
            icon.setMinWidth(Ui.dp(this, 26));
            r.addView(icon);
            r.addView(Ui.text(this, e.high ? "Preamar" : "Baixa-mar", 15, Theme.TEXT, false), Ui.weight(1));
            r.addView(Ui.text(this, Format.time(e.time, zone), 17, Theme.TEXT, true));
            TextView h = Ui.text(this, Format.meters(e.height, 1), 17, e.high ? Theme.HIGH : Theme.LOW, true);
            h.setGravity(Gravity.END);
            h.setMinWidth(Ui.dp(this, 84));
            r.addView(h);
            tc.addView(r);
        }
        content.addView(tc);

        // Sol e Lua
        LinearLayout sm = Ui.card(this);
        sm.addView(Ui.title(this, "Sol e Lua"));
        if (sun != null) {
            sm.addView(Ui.text(this, "☀ Nascer " + Format.time(sun[0], zone) + "   ·   Pôr " + Format.time(sun[1], zone), 15, Theme.TEXT, false));
        }
        long noon = dayStart + 12 * 3_600_000L;
        int illum = (int) Math.round(SunMoon.illumination(noon) * 100);
        TextView moon = Ui.text(this, SunMoon.phaseEmoji(noon) + " " + SunMoon.phaseName(noon) + " · " + illum + "% iluminada", 15, Theme.TEXT, false);
        moon.setPadding(0, Ui.dp(this, 6), 0, 0);
        sm.addView(moon);
        String sn = SunMoon.springNeap(noon);
        if (sn != null) {
            TextView snT = Ui.text(this, sn, 13, Theme.SEA, false);
            snT.setPadding(0, Ui.dp(this, 6), 0, 0);
            sm.addView(snT);
        }
        content.addView(sm);

        // Próximos dias
        LinearLayout nd = Ui.card(this);
        nd.addView(Ui.title(this, "Próximos 7 dias"));
        for (int i = 1; i <= 7; i++) {
            final LocalDate day = selected.plusDays(i);
            long s0 = day.atStartOfDay(zone).toInstant().toEpochMilli();
            long s1 = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            List<TideExtreme> ex = predictor.extremes(s0, s1);
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            r.addView(Ui.text(this, Format.dayShort(day), 13, Theme.SILVER, true));
            StringBuilder sb = new StringBuilder();
            for (TideExtreme e : ex) {
                if (sb.length() > 0) sb.append("   ");
                sb.append(e.high ? "▲ " : "▼ ").append(Format.time(e.time, zone)).append(' ')
                        .append(Format.meters(e.height, 1).replace(" m", ""));
            }
            r.addView(Ui.text(this, sb.toString(), 13, Theme.TEXT, false));
            r.setClickable(true);
            r.setOnClickListener(v -> { selected = day; render(); });
            nd.addView(r);
            if (i < 7) nd.addView(Ui.divider(this));
        }
        content.addView(nd);

        // Rodapé
        TextView foot = Ui.text(this,
                "Alturas em metros acima do nível de redução (aprox. média das baixa-mares de sizígia). "
                        + "Previsão astronômica calculada no aparelho, sem internet; não inclui efeitos de vento, "
                        + "pressão ou ressacas. Não use para navegação — consulte a tábua oficial da Marinha (DHN/CHM).\n\n"
                        + "Constantes harmônicas: " + station.source + (station.period.isEmpty() ? "" : ", série " + station.period)
                        + ". TICON-4 (Hart-Davis, Dettmering & Seitz, 2025), licença CC BY 4.0, via Neaps tide database.",
                11, Theme.TEXT_DIM, false);
        foot.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 8));
        content.addView(foot);
    }

    private View nowCard() {
        long now = System.currentTimeMillis();
        double h = predictor.heightAt(now);
        double rate = predictor.rateAt(now);
        List<TideExtreme> upcoming = predictor.extremes(now, now + 30 * 3_600_000L);

        LinearLayout c = Ui.card(this);
        c.addView(Ui.title(this, "Agora · " + Format.time(now, zone)));
        LinearLayout r = Ui.row(this);
        r.addView(Ui.text(this, Format.meters(h, 2), 40, Theme.TEXT, true));
        boolean rising = rate > 0;
        String trend = Math.abs(rate) < 0.02 ? "≈ Estofo (parada)" : (rising ? "↑ Enchendo" : "↓ Vazando");
        TextView tr = Ui.text(this, trend, 16, rising ? Theme.HIGH : Theme.LOW, true);
        tr.setPadding(Ui.dp(this, 14), 0, 0, 0);
        r.addView(tr);
        c.addView(r);
        int shown = 0;
        for (TideExtreme e : upcoming) {
            if (shown == 2) break;
            long mins = (e.time - now) / 60_000L;
            String in = mins >= 60 ? (mins / 60) + " h " + (mins % 60) + " min" : mins + " min";
            TextView t = Ui.text(this, (e.high ? "Próxima preamar: " : "Próxima baixa-mar: ") + Format.time(e.time, zone)
                    + " · " + Format.meters(e.height, 1) + "  (em " + in + ")", 14, Theme.TEXT_DIM, false);
            t.setPadding(0, Ui.dp(this, 4), 0, 0);
            c.addView(t);
            shown++;
        }
        return c;
    }

    private void pickDate() {
        DatePickerDialog dlg = new DatePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert,
                (view, y, m, d) -> { selected = LocalDate.of(y, m + 1, d); render(); },
                selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
        dlg.show();
    }
}
