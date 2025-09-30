package org.simulator.ui.time_line_view;

import org.simulator.canale.Sample;
import java.util.ArrayList;
import java.util.List;

public final class DeltaAligner {

    public enum AlignMode { DISTANCE, TIME, PERCENT_LAP }

    public static final class SeriesPoint {
        public final double x;   // ascissa comune (m, s, %)
        public final double dt;  // delta time (s) cumulativo
        public SeriesPoint(double x,double dt){ this.x=x; this.dt=dt; }
    }

    /** Calcola Δt allineando due serie (ref e cmp) nella stessa ascissa. */
    public List<SeriesPoint> compute(List<Sample> ref, List<Sample> cmp, AlignMode mode) {
        var out = new ArrayList<SeriesPoint>();
        if (ref == null || cmp == null) return out;
        if (ref.size() < 2 || cmp.size() < 2) return out;

        var ax = axis(ref, mode);
        var bx = axis(cmp, mode);
        int n = Math.min(ax.size(), ref.size());
        int m = Math.min(bx.size(), cmp.size());
        if (n < 2 || m < 2) return out;

        // j scorre la serie "cmp" per interpolare al valore x corrente di "ref"
        int j = 0;
        double prevAx = Double.NEGATIVE_INFINITY;
        for (int i=0; i<n; i++) {
            double x = ax.get(i);
            if (!Double.isFinite(x)) continue;
            // richiede x monotono NON decrescente: se non lo è, salta il punto
            if (x < prevAx) continue;
            prevAx = x;

            // porta j in modo che bx[j] <= x <= bx[j+1], evitando segmenti piatti
            while (j + 1 < m && (!Double.isFinite(bx.get(j+1)) || bx.get(j + 1) < x)) j++;
            if (j >= m - 1) { j = m - 2; }
            double x0 = bx.get(j),   x1 = bx.get(j + 1);
            if (!Double.isFinite(x0) || !Double.isFinite(x1) || x1 <= x0) continue;

            double tA = ref.get(i).timestamp();
            if (!Double.isFinite(tA)) continue;

            double t0 = cmp.get(j).timestamp(), t1 = cmp.get(j + 1).timestamp();
            if (!Double.isFinite(t0) || !Double.isFinite(t1)) continue;

            double u = (x - x0) / (x1 - x0);
            if (u < 0) u = 0; else if (u > 1) u = 1;

            double tB = t0 + u * (t1 - t0);
            if (!Double.isFinite(tB)) continue;

            out.add(new SeriesPoint(x, tB - tA)); // Δt cumulativo
        }
        return out;
    }

    // helpers
    private static List<Double> axis(List<Sample> s, AlignMode m){
        return switch (m){
            case DISTANCE -> s.stream().map(Sample::distance).toList();
            case TIME     -> s.stream().map(Sample::timestamp).toList();
            case PERCENT_LAP -> {
                double len = s.get(s.size()-1).distance();
                if (!Double.isFinite(len) || len <= 0) len = 1.0;
                final double flen = len;
                yield s.stream().map(a -> a.distance()/flen).toList();
            }
        };
    }

    private static int lowerBound(List<DeltaAligner.SeriesPoint> s, double x){
        int lo=0, hi=s.size()-1;
        while (lo<hi){
            int mid=(lo+hi)/2;
            if (s.get(mid).x < x) lo=mid+1; else hi=mid;
        }
        return lo;
    }

    // ==== Utility aggiuntive per click-lettura sui grafici ====
    public static double interpolateAtDistance(java.util.List<org.simulator.canale.Sample> s, double x, java.util.function.ToDoubleFunction<org.simulator.canale.Sample> getter){
        if (s==null || s.size()<2) return Double.NaN;
        int lo=0, hi=s.size()-1;
        while (lo<hi){
            int mid=(lo+hi)/2;
            if (s.get(mid).distance() < x) lo=mid+1; else hi=mid;
        }
        int i1 = Math.min(Math.max(1, lo), s.size()-1);
        int i0 = i1-1;
        double x0 = s.get(i0).distance(), x1 = s.get(i1).distance();
        double y0 = getter.applyAsDouble(s.get(i0)), y1 = getter.applyAsDouble(s.get(i1));
        double u = (x1>x0)? (x - x0)/(x1-x0) : 0.0;
        if (u<0) u=0; else if (u>1) u=1;
        return y0 + u*(y1-y0);
    }
    public static double percentToDistance(java.util.List<org.simulator.canale.Sample> s, double p){
        if (s==null || s.isEmpty()) return 0;
        double len = s.get(s.size()-1).distance();
        if (!Double.isFinite(len) || len <= 0) return 0;
        return Math.max(0, Math.min(1, p)) * len;
    }

}
