package org.simulator.ui.time_line_view.Track;

import org.simulator.canale.Channel;
import java.util.List;
import java.util.Map;

/** Costruisce TrackGeometry da GPS oppure (fallback) da coordinate AC (Car pos X/Y o X/Z). */
public final class TrackBuilder {
    private TrackBuilder() {}

    public static TrackGeometry build(List<? extends Map<Channel, Double>> samples, double[] xAxis) {
        if (samples == null || samples.isEmpty()) return null;

        // 1) GPS
        TrackGeometry g = buildFromGps(samples, xAxis);
        if (g != null) return g;

        // 2) Assetto Corsa: Car coord X/Y (o X/Z)
        g = buildFromCarCoords(samples, xAxis);
        return g;
    }

    // ---- GPS LAT/LON in gradi → metri (equirettangolare centrata)
    private static TrackGeometry buildFromGps(List<? extends Map<Channel, Double>> smp, double[] xAxis) {
        int n = smp.size();
        double[] lat = new double[n], lon = new double[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            Map<Channel, Double> m = smp.get(i);
            Double la = m.get(Channel.GPS_LATITUDE);
            Double lo = m.get(Channel.GPS_LONGITUDE);
            lat[i] = (la == null) ? Double.NaN : la;
            lon[i] = (lo == null) ? Double.NaN : lo;
            any |= (la != null && lo != null && Double.isFinite(la) && Double.isFinite(lo));
        }
        if (!any) return null;

        double lat0 = medianFinite(lat);
        double lon0 = medianFinite(lon);
        final double kx = 111_320.0 * Math.cos(Math.toRadians(lat0));
        final double ky = 110_540.0;

        double[] px = new double[n], py = new double[n];
        for (int i = 0; i < n; i++) {
            if (Double.isFinite(lat[i]) && Double.isFinite(lon[i])) {
                px[i] = (lon[i] - lon0) * kx;
                py[i] = (lat[i] - lat0) * ky;
            } else {
                px[i] = (i > 0 ? px[i - 1] : 0.0);
                py[i] = (i > 0 ? py[i - 1] : 0.0);
            }
        }

        // NEW: rimuove salti irrealistici prima di smussare (soglia conservativa)
        clampJumps(px, py, 30.0);

        smooth(px, 7); smooth(py, 7);

        // NEW: bounds robusti ai fuori scala (quantili)
        return new TrackGeometry(xAxis, px, py, bounds(px, py));
    }

    // ---- AC: Car coord X/Y (o X/Z) già in metri → recentra e smussa
    private static TrackGeometry buildFromCarCoords(List<? extends Map<Channel, Double>> smp, double[] xAxis) {
        int n = smp.size();
        double[] cx = new double[n], cy = new double[n];
        boolean anyXY = false, anyXZ = false;

        // prova X/Y
        for (int i = 0; i < n; i++) {
            Map<Channel, Double> m = smp.get(i);
            Double x = m.get(Channel.CAR_COORD_X);
            Double y = m.get(Channel.CAR_COORD_Y);
            if (x != null && y != null && Double.isFinite(x) && Double.isFinite(y)) { anyXY = true; break; }
        }
        if (anyXY) {
            for (int i = 0; i < n; i++) {
                Map<Channel, Double> m = smp.get(i);
                cx[i] = safe(m.get(Channel.CAR_COORD_X), i>0? cx[i-1]:0);
                cy[i] = safe(m.get(Channel.CAR_COORD_Y), i>0? cy[i-1]:0);
            }
        } else {
            // prova X/Z
            for (int i = 0; i < n; i++) {
                Map<Channel, Double> m = smp.get(i);
                Double x = m.get(Channel.CAR_COORD_X);
                Double z = m.get(Channel.CAR_COORD_Z);
                if (x != null && z != null && Double.isFinite(x) && Double.isFinite(z)) { anyXZ = true; break; }
            }
            if (!anyXZ) return null;
            for (int i = 0; i < n; i++) {
                Map<Channel, Double> m = smp.get(i);
                cx[i] = safe(m.get(Channel.CAR_COORD_X), i>0? cx[i-1]:0);
                cy[i] = safe(m.get(Channel.CAR_COORD_Z), i>0? cy[i-1]:0);
            }
        }

        // centramento robusto già presente
        double x0 = medianFinite(cx), y0 = medianFinite(cy);
        for (int i = 0; i < n; i++) { cx[i] -= x0; cy[i] -= y0; }

        // NEW: de-glitch prima dello smoothing
        clampJumps(cx, cy, 30.0);

        smooth(cx, 5); smooth(cy, 5);

        // NEW: bounds robusti (quantili) per evitare “pista schiacciata”
        return new TrackGeometry(xAxis, cx, cy, bounds(cx, cy));
    }

    private static double safe(Double v, double prev) {
        return (v != null && Double.isFinite(v)) ? v : prev;
    }

    /**
     * Bounds ROBUSTI: invece di min/max crudi, usa i quantili 1%–99% sugli
     * soli valori finiti. Così outlier singoli/spike non “sparano” il box.
     */
    private static TrackGeometry.Bounds2D bounds(double[] px, double[] py) {
        // raccogli finiti
        java.util.ArrayList<Double> vx = new java.util.ArrayList<>(px.length);
        java.util.ArrayList<Double> vy = new java.util.ArrayList<>(py.length);
        for (int i = 0; i < px.length; i++) {
            double x = px[i], y = py[i];
            if (Double.isFinite(x) && Double.isFinite(y)) {
                vx.add(x); vy.add(y);
            }
        }
        if (vx.isEmpty()) return new TrackGeometry.Bounds2D(0,0,0,0);

        vx.sort(Double::compare); vy.sort(Double::compare);
        int n = vx.size();
        int i1 = Math.max(0, (int)Math.floor(0.01 * (n - 1))); // 1°
        int i2 = Math.min(n-1, (int)Math.ceil (0.99 * (n - 1))); // 99°

        double minX = vx.get(i1), maxX = vx.get(i2);
        double minY = vy.get(i1), maxY = vy.get(i2);
        return new TrackGeometry.Bounds2D(minX, minY, maxX, maxY);
    }

    private static void smooth(double[] a, int w) {
        if (w <= 1) return;
        int n = a.length, r = w/2;
        double[] src = a.clone();
        for (int i = 0; i < n; i++) {
            double s = 0; int c = 0;
            for (int k = i - r; k <= i + r; k++) {
                if (k >= 0 && k < n && Double.isFinite(src[k])) { s += src[k]; c++; }
            }
            a[i] = (c > 0 ? s / c : src[i]);
        }
    }

    private static double medianFinite(double[] a) {
        java.util.ArrayList<Double> v = new java.util.ArrayList<>();
        for (double d : a) if (Double.isFinite(d)) v.add(d);
        if (v.isEmpty()) return 0.0;
        v.sort(Double::compare);
        int n = v.size();
        return (n % 2 == 1) ? v.get(n/2) : 0.5 * (v.get(n/2 - 1) + v.get(n/2));
    }

    /** De-glitch: se lo spostamento tra campioni supera la soglia, clampa al valore precedente. */
    private static void clampJumps(double[] x, double[] y, double maxStepMeters) {
        if (x.length == 0) return;
        for (int i = 1; i < x.length; i++) {
            double xi = x[i], yi = y[i];
            if (!Double.isFinite(xi) || !Double.isFinite(yi)) {
                x[i] = x[i - 1];
                y[i] = y[i - 1];
                continue;
            }
            double dx = xi - x[i - 1];
            double dy = yi - y[i - 1];
            if (Math.hypot(dx, dy) > maxStepMeters) {
                x[i] = x[i - 1];
                y[i] = y[i - 1];
            }
        }
    }
}
