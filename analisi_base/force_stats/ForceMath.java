package org.simulator.analisi_base.force_stats;

import org.simulator.canale.Channel;
import org.simulator.canale.Sample;

import java.util.Arrays;
import java.util.List;

/** Helper matematici comuni ai calcoli forza/seat. Package-private per non inquinare l’API pubblica. */
final class ForceMath {

    private ForceMath(){}

    static double val(Sample s, Channel ch){
        Double v = s.values().getOrDefault(ch, Double.NaN);
        return (v == null) ? Double.NaN : v;
    }

    static double clamp01(double x){
        if (Double.isNaN(x)) return 0.0;
        if (x < 0) return 0.0;
        if (x > 1) return 1.0;
        return x;
    }

    /** smoothstep edge0..edge1 (clamp), utile per passare da 0 a 1 in modo dolce. */
    static double smoothstep(double edge0, double edge1, double x){
        if (Double.isNaN(x)) return 0.0;
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    /** quantile semplice [0..1] su lista di Double (copia e ordina). */
    static double quantile(List<Double> data, double q) {
        if (data == null || data.isEmpty()) return Double.NaN;
        double[] a = new double[data.size()];
        for (int i = 0; i < a.length; i++) a[i] = data.get(i);
        Arrays.sort(a);
        if (q <= 0) return a[0];
        if (q >= 1) return a[a.length - 1];
        double pos = q * (a.length - 1);
        int i = (int) Math.floor(pos);
        int j = Math.min(i + 1, a.length - 1);
        double t = pos - i;
        return a[i] * (1 - t) + a[j] * t;
    }
}
