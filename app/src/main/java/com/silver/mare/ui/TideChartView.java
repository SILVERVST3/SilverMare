package com.silver.mare.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import com.silver.mare.engine.Station;
import com.silver.mare.engine.TideExtreme;
import com.silver.mare.engine.TidePredictor;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Gráfico da curva de maré de um dia, com toque para consultar qualquer horário. */
public class TideChartView extends View {
    private static final long HOUR = 3_600_000L;

    private TidePredictor predictor;
    private ZoneId zone;
    private long dayStart;
    private long dayEnd;
    private List<TideExtreme> extremes = new ArrayList<>();
    private long[] sun;
    private boolean showNow;
    private long touchTime = -1;

    private double[] samples = new double[0];
    private double yMin, yMax;

    private final float d;
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nightPaint = new Paint();
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extremeLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fill = new Path();
    private final RectF rect = new RectF();
    private Shader fillShader;
    private float shaderTop = -1, shaderBottom = -1;

    public TideChartView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;
        gridPaint.setColor(Theme.GRID);
        gridPaint.setStrokeWidth(1 * d);
        labelPaint.setColor(Theme.TEXT_DIM);
        labelPaint.setTextSize(11 * d);
        curvePaint.setColor(Theme.SEA);
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(2.5f * d);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        nightPaint.setColor(Theme.NIGHT);
        dotPaint.setStyle(Paint.Style.FILL);
        nowPaint.setColor(Theme.SILVER);
        nowPaint.setStrokeWidth(1.5f * d);
        extremeLabel.setColor(Theme.TEXT);
        extremeLabel.setTextSize(11 * d);
        extremeLabel.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        extremeLabel.setTextAlign(Paint.Align.CENTER);
        bubblePaint.setColor(Theme.CARD_HI);
        setMinimumHeight((int) (230 * d));
    }

    public void setData(TidePredictor predictor, ZoneId zone, long dayStart, long dayEnd,
                        List<TideExtreme> extremes, long[] sun, boolean showNow) {
        this.predictor = predictor;
        this.zone = zone;
        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
        this.extremes = extremes;
        this.sun = sun;
        this.showNow = showNow;
        this.touchTime = -1;
        samples = predictor.series(dayStart, dayEnd, 5);
        Station st = predictor.getStation();
        double lo = st.lowestAstronomical, hi = st.highestAstronomical;
        for (double v : samples) {
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        double pad = Math.max(0.1, (hi - lo) * 0.12);
        yMin = Math.floor((lo - pad) * 10) / 10.0;
        yMax = Math.ceil((hi + pad * 1.6) * 10) / 10.0;
        fillShader = null;
        invalidate();
    }

    private float left() { return 40 * d; }
    private float right() { return getWidth() - 12 * d; }
    private float top() { return 14 * d; }
    private float bottom() { return getHeight() - 24 * d; }

    private float xOf(long t) {
        return left() + (float) ((t - dayStart) / (double) (dayEnd - dayStart)) * (right() - left());
    }

    private float yOf(double h) {
        return bottom() - (float) ((h - yMin) / (yMax - yMin)) * (bottom() - top());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, (int) (240 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (predictor == null || samples.length < 2) return;
        float l = left(), r = right(), t = top(), b = bottom();

        // Noite (antes do nascer e depois do pôr do sol)
        if (sun != null) {
            float xr = Math.max(l, Math.min(r, xOf(sun[0])));
            float xs = Math.max(l, Math.min(r, xOf(sun[1])));
            c.drawRect(l, t, xr, b, nightPaint);
            c.drawRect(xs, t, r, b, nightPaint);
        }

        // Grade horizontal
        double step = niceStep(yMax - yMin);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        for (double v = Math.ceil(yMin / step) * step; v <= yMax + 1e-9; v += step) {
            float y = yOf(v);
            c.drawLine(l, y, r, y, gridPaint);
            c.drawText(Format.meters(v, Math.abs(step - 0.25) < 1e-9 ? 2 : 1), l - 5 * d, y + 4 * d, labelPaint);
        }
        // Grade vertical a cada 3 h
        labelPaint.setTextAlign(Paint.Align.CENTER);
        for (int h = 0; h <= 24; h += 3) {
            long tt = dayStart + h * HOUR;
            if (tt > dayEnd) break;
            float x = xOf(tt);
            c.drawLine(x, t, x, b, gridPaint);
            c.drawText(String.format(java.util.Locale.ROOT, "%02dh", h % 24 == 0 && h > 0 ? 24 : h), x, b + 16 * d, labelPaint);
        }

        // Curva
        path.reset();
        fill.reset();
        long stepMs = (dayEnd - dayStart) / (samples.length - 1);
        for (int i = 0; i < samples.length; i++) {
            float x = xOf(dayStart + i * stepMs);
            float y = yOf(samples[i]);
            if (i == 0) {
                path.moveTo(x, y);
                fill.moveTo(x, b);
                fill.lineTo(x, y);
            } else {
                path.lineTo(x, y);
                fill.lineTo(x, y);
            }
        }
        fill.lineTo(xOf(dayEnd), b);
        fill.close();
        if (fillShader == null || shaderTop != t || shaderBottom != b) {
            fillShader = new LinearGradient(0, t, 0, b, Theme.SEA_FILL_TOP, Theme.SEA_FILL_BOTTOM, Shader.TileMode.CLAMP);
            shaderTop = t;
            shaderBottom = b;
        }
        fillPaint.setShader(fillShader);
        c.drawPath(fill, fillPaint);
        c.drawPath(path, curvePaint);

        // Extremos
        for (TideExtreme e : extremes) {
            float x = xOf(e.time), y = yOf(e.height);
            dotPaint.setColor(e.high ? Theme.HIGH : Theme.LOW);
            c.drawCircle(x, y, 4.5f * d, dotPaint);
            String txt = Format.time(e.time, zone) + " · " + Format.meters(e.height, 1);
            float ty = e.high ? y - 9 * d : y + 17 * d;
            float tw = extremeLabel.measureText(txt);
            float cx = Math.max(l + tw / 2 + 2 * d, Math.min(r - tw / 2 - 2 * d, x));
            c.drawText(txt, cx, ty, extremeLabel);
        }

        // Agora
        long now = System.currentTimeMillis();
        if (showNow && now >= dayStart && now <= dayEnd) {
            float x = xOf(now);
            c.drawLine(x, t, x, b, nowPaint);
            dotPaint.setColor(Theme.SILVER);
            c.drawCircle(x, yOf(predictor.heightAt(now)), 5.5f * d, dotPaint);
        }

        // Consulta por toque
        if (touchTime >= 0) {
            float x = xOf(touchTime);
            double h = predictor.heightAt(touchTime);
            float y = yOf(h);
            nowPaint.setAlpha(140);
            c.drawLine(x, t, x, b, nowPaint);
            nowPaint.setAlpha(255);
            dotPaint.setColor(Theme.TEXT);
            c.drawCircle(x, y, 5 * d, dotPaint);
            String txt = Format.time(touchTime, zone) + "  " + Format.meters(h, 2);
            float tw = extremeLabel.measureText(txt) + 16 * d;
            float bx = Math.max(l, Math.min(r - tw, x - tw / 2));
            float by = t;
            rect.set(bx, by, bx + tw, by + 22 * d);
            c.drawRoundRect(rect, 8 * d, 8 * d, bubblePaint);
            c.drawText(txt, bx + tw / 2, by + 15 * d, extremeLabel);
        }
    }

    private static double niceStep(double range) {
        double[] steps = {0.1, 0.2, 0.25, 0.5, 1, 2};
        for (double s : steps) if (range / s <= 6) return s;
        return 2;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (predictor == null) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                float x = Math.max(left(), Math.min(right(), ev.getX()));
                double frac = (x - left()) / (right() - left());
                touchTime = dayStart + (long) (frac * (dayEnd - dayStart));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return super.onTouchEvent(ev);
        }
    }
}
