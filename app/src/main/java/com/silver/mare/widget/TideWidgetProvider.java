package com.silver.mare.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import com.silver.mare.R;
import com.silver.mare.data.Prefs;
import com.silver.mare.data.StationRepository;
import com.silver.mare.engine.Station;
import com.silver.mare.engine.TideExtreme;
import com.silver.mare.engine.TidePredictor;
import com.silver.mare.ui.Format;
import com.silver.mare.ui.MainActivity;
import com.silver.mare.ui.Theme;

import java.time.ZoneId;
import java.util.List;

/** Widget da tela inicial: altura atual, tendência, próximas preamar/baixa-mar e a curva das próximas horas. */
public class TideWidgetProvider extends AppWidgetProvider {
    private static final long HOUR = 3_600_000L;

    /** Atualiza todos os widgets (chamado pelo app quando a estação muda). */
    public static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, TideWidgetProvider.class));
        for (int id : ids) update(context, mgr, id);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(context, mgr, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr, int id, Bundle newOptions) {
        update(context, mgr, id);
    }

    private static Station resolveStation(Context context) {
        Station s = StationRepository.byId(context, Prefs.stationId(context));
        if (s == null) {
            Double lat = Prefs.lastLat(context), lon = Prefs.lastLon(context);
            if (lat != null && lon != null) s = StationRepository.nearest(context, lat, lon);
        }
        return s;
    }

    static void update(Context context, AppWidgetManager mgr, int id) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_tide);
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.w_root, pi);

        Station st = resolveStation(context);
        if (st == null) {
            rv.setTextViewText(R.id.w_station, "SilverMaré");
            rv.setTextViewText(R.id.w_height, "—");
            rv.setTextViewText(R.id.w_trend, "");
            rv.setTextViewText(R.id.w_time, "Abra o app para escolher a estação");
            rv.setTextViewText(R.id.w_next1, "");
            rv.setTextViewText(R.id.w_next2, "");
            rv.setViewVisibility(R.id.w_chart, View.GONE);
            mgr.updateAppWidget(id, rv);
            return;
        }

        ZoneId zone = ZoneId.of(st.timeZone);
        TidePredictor p = new TidePredictor(st);
        long now = System.currentTimeMillis();
        double h = p.heightAt(now);
        double rate = p.rateAt(now);
        List<TideExtreme> next = p.extremes(now, now + 30 * HOUR);

        rv.setTextViewText(R.id.w_station, st.displayName());
        rv.setTextViewText(R.id.w_height, Format.meters(h, 2));
        boolean rising = rate > 0;
        rv.setTextViewText(R.id.w_trend, Math.abs(rate) < 0.02 ? "≈ estofo" : (rising ? "↑ enchendo" : "↓ vazando"));
        rv.setTextColor(R.id.w_trend, rising ? Theme.HIGH : Theme.LOW);
        rv.setTextViewText(R.id.w_time, "às " + Format.time(now, zone));
        rv.setTextViewText(R.id.w_next1, next.size() > 0 ? label(next.get(0), zone) : "");
        rv.setTextViewText(R.id.w_next2, next.size() > 1 ? label(next.get(1), zone) : "");

        // Tamanho real do widget (dp) para desenhar o gráfico sem distorção
        Bundle o = mgr.getAppWidgetOptions(id);
        int wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 120);
        int chartHdp = hDp - 92;
        if (chartHdp >= 28 && wDp > 0) {
            float density = context.getResources().getDisplayMetrics().density;
            int wPx = Math.min(1400, Math.round((wDp - 24) * density));
            int hPx = Math.min(700, Math.round(chartHdp * density));
            if (wPx > 50 && hPx > 20) {
                rv.setImageViewBitmap(R.id.w_chart, drawChart(p, st, now, wPx, hPx, density));
                rv.setViewVisibility(R.id.w_chart, View.VISIBLE);
            } else {
                rv.setViewVisibility(R.id.w_chart, View.GONE);
            }
        } else {
            rv.setViewVisibility(R.id.w_chart, View.GONE);
        }
        mgr.updateAppWidget(id, rv);
    }

    private static String label(TideExtreme e, ZoneId zone) {
        return (e.high ? "▲ Preamar " : "▼ Baixa-mar ") + Format.time(e.time, zone) + " · " + Format.meters(e.height, 1);
    }

    /** Curva de (agora − 3 h) até (agora + 21 h). */
    private static Bitmap drawChart(TidePredictor p, Station st, long now, int w, int h, float d) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        long start = now - 3 * HOUR, end = now + 21 * HOUR;
        double[] s = p.series(start, end, 10);
        double lo = st.lowestAstronomical, hi = st.highestAstronomical;
        for (double v : s) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        double pad = (hi - lo) * 0.08 + 0.02;
        lo -= pad;
        hi += pad;
        float top = 4 * d, bottom = h - 4 * d;
        Path line = new Path(), fill = new Path();
        for (int i = 0; i < s.length; i++) {
            float x = (float) i / (s.length - 1) * w;
            float y = bottom - (float) ((s[i] - lo) / (hi - lo)) * (bottom - top);
            if (i == 0) {
                line.moveTo(x, y);
                fill.moveTo(x, h);
                fill.lineTo(x, y);
            } else {
                line.lineTo(x, y);
                fill.lineTo(x, y);
            }
        }
        fill.lineTo(w, h);
        fill.close();
        Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
        fp.setShader(new LinearGradient(0, top, 0, h, Theme.SEA_FILL_TOP, Theme.SEA_FILL_BOTTOM, Shader.TileMode.CLAMP));
        c.drawPath(fill, fp);
        Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
        lp.setStyle(Paint.Style.STROKE);
        lp.setStrokeWidth(2 * d);
        lp.setColor(Theme.SEA);
        c.drawPath(line, lp);
        // Marcador "agora"
        float xn = (float) (now - start) / (end - start) * w;
        float yn = bottom - (float) ((p.heightAt(now) - lo) / (hi - lo)) * (bottom - top);
        Paint np = new Paint(Paint.ANTI_ALIAS_FLAG);
        np.setColor(Theme.SILVER);
        np.setStrokeWidth(1.2f * d);
        c.drawLine(xn, 0, xn, h, np);
        c.drawCircle(xn, yn, 4 * d, np);
        // Extremos
        Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (TideExtreme e : p.extremes(start, end)) {
            float x = (float) (e.time - start) / (end - start) * w;
            float y = bottom - (float) ((e.height - lo) / (hi - lo)) * (bottom - top);
            dp.setColor(e.high ? Theme.HIGH : Theme.LOW);
            c.drawCircle(x, y, 3 * d, dp);
        }
        return bmp;
    }
}
