package org.simulator.ui;

import org.simulator.ui.asix_pack.AxisChoice;


import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

class DeltaTimeService {
    static class DeltaTimeBundle { List<Double> x; List<Double> deltaTime; }


    static DeltaTimeBundle delta(SeriesBundle ref, SeriesBundle cmp, AxisChoice axis) {
        double step = axis.useDist ? 1.0 : (axis.useLapTime || axis.useAbsTime ? 0.02 : 1.0);
        double minX = Math.max(min(ref.x), min(cmp.x));
        double maxX = Math.min(max(ref.x), max(cmp.x));
        List<Double> xgrid = new ArrayList<>();
        for (double x = minX; x <= maxX; x += step) xgrid.add(x);

        List<Double> vR = resample(ref.x, ref.speed, xgrid);
        List<Double> vC = resample(cmp.x, cmp.speed, xgrid);

        List<Double> out = new ArrayList<>(xgrid.size());
        double acc = 0.0;
        for (int i = 0; i < xgrid.size(); i++) {
            Double a = vR.get(i), b = vC.get(i);
            if (a == null || b == null || a.isNaN() || b.isNaN() || a <= 0 || b <= 0) { out.add(acc); continue; }
            if (axis.useDist) {
                double vRm = a / 3.6, vCm = b / 3.6;
                double ds = step;
                acc += (1.0 / vRm - 1.0 / vCm) * ds;
            } else {
                double dt = step;
                acc += (a - b) / Math.max(a, b) * dt;
            }
            out.add(acc);
        }
        DeltaTimeBundle d = new DeltaTimeBundle();
        d.x = xgrid; d.deltaTime = out;
        return d;
    }

    private static double min(List<Double> a){ return a.stream().filter(Objects::nonNull).filter(d->!d.isNaN()).min(Double::compare).orElse(Double.NaN); }
    private static double max(List<Double> a){ return a.stream().filter(Objects::nonNull).filter(d->!d.isNaN()).max(Double::compare).orElse(Double.NaN); }

    private static List<Double> resample(List<Double> x, List<Double> y, List<Double> xq) {
        int n = x.size();
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) order.add(i);
        order.sort(Comparator.comparingDouble(x::get));
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) { xs[i] = x.get(order.get(i)); ys[i] = y.get(order.get(i)); }

        List<Double> out = new ArrayList<>(xq.size());
        int j = 0;
        for (double q : xq) {
            while (j < n - 2 && xs[j+1] < q) j++;
            if (q < xs[0] || q > xs[n-1]) { out.add(Double.NaN); continue; }
            while (j < n - 1 && xs[j+1] < q) j++;
            int k = Math.min(j+1, n-1);
            double x1 = xs[j], x2 = xs[k];
            double y1 = ys[j], y2 = ys[k];
            if (x2 == x1) { out.add(Double.NaN); continue; }
            double t = (q - x1) / (x2 - x1);
            out.add(y1 + t * (y2 - y1));
        }
        return out;
    }
}
