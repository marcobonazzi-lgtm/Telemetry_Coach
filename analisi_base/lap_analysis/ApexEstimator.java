package org.simulator.analisi_base.lap_analysis;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;

import java.util.ArrayList;
import java.util.List;

/** Stima degli apex come minimi locali della velocità smussata (estratto da LapAnalysis). */
final class ApexEstimator {

    private ApexEstimator(){}

    static List<Integer> estimate(Lap lap, int smoothWindow) {
        List<Integer> out = new ArrayList<>();
        if (lap == null || lap.samples == null || lap.samples.size() < 3) return out;

        int n = lap.samples.size();
        double[] v = new double[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            Double s = lap.samples.get(i).values().get(Channel.SPEED);
            v[i] = (s == null || s.isNaN()) ? Double.NaN : s;
            if (!Double.isNaN(v[i])) any = true;
        }
        if (!any) return out;

        int W = Math.max(1, smoothWindow);
        double[] sm = new double[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - W);
            int b = Math.min(n - 1, i + W);
            double sum = 0; int cnt = 0;
            for (int k = a; k <= b; k++) {
                if (!Double.isNaN(v[k])) { sum += v[k]; cnt++; }
            }
            sm[i] = cnt > 0 ? sum / cnt : Double.NaN;
        }

        int minGap = Math.max(5, n / 100);
        int lastIdx = -minGap;

        for (int i = 1; i < n - 1; i++) {
            double a = sm[i - 1], b = sm[i], c = sm[i + 1];
            if (Double.isNaN(a) || Double.isNaN(b) || Double.isNaN(c)) continue;
            if (b <= a && b <= c && (i - lastIdx) >= minGap) {
                out.add(i);
                lastIdx = i;
            }
        }
        return out;
    }
}
