package org.simulator.ui.export;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.*;
import java.util.function.ToDoubleFunction;

final class StatsCalculator {
    static class LapStats {
        public final int lapIndex;
        public final double lapTime;
        public final double vmax;
        public final double vavg;
        public final double latGmax;
        public final double longGmin; // più negativo (frenata)
        public final double longGmax; // più positivo (trazione)
        public final double throttleMean;
        public final double brakeDuty; // % campioni con freno > 5%

        LapStats(int lapIndex, double lapTime, double vmax, double vavg,
                 double latGmax, double longGmin, double longGmax,
                 double throttleMean, double brakeDuty) {
            this.lapIndex = lapIndex; this.lapTime = lapTime; this.vmax = vmax; this.vavg = vavg;
            this.latGmax = latGmax; this.longGmin = longGmin; this.longGmax = longGmax;
            this.throttleMean = throttleMean; this.brakeDuty = brakeDuty;
        }
    }

    static List<LapStats> perLap(List<Lap> laps){
        List<LapStats> out = new ArrayList<>();
        for (Lap l : laps){
            List<Sample> s = l.samples;
            if (s == null || s.isEmpty()) continue;
            double vmax = maxOf(s, Channel.SPEED);
            double vavg = meanOf(s, Channel.SPEED);
            double latGmax = maxAbsOf(s, Channel.CG_ACCEL_LATERAL);
            double longGmin = minOf(s, Channel.CG_ACCEL_LONGITUDINAL);
            double longGmax = maxOf(s, Channel.CG_ACCEL_LONGITUDINAL);
            double throttleMean = meanOf(s, Channel.THROTTLE);
            double brakeDuty = dutyCycle(s, Channel.BRAKE, 5.0);
            out.add(new LapStats(l.index, l.lapTimeSafe(), vmax, vavg, latGmax, longGmin, longGmax, throttleMean, brakeDuty));
        }
        return out;
    }


    // -------- helpers numerici sui sample
    private static double meanOf(List<Sample> s, Channel c){
        return s.stream().mapToDouble(x -> val(x, c)).filter(v -> !Double.isNaN(v)).average().orElse(Double.NaN);
    }
    private static double maxOf(List<Sample> s, Channel c){
        return s.stream().mapToDouble(x -> val(x, c)).filter(v -> !Double.isNaN(v)).max().orElse(Double.NaN);
    }
    private static double minOf(List<Sample> s, Channel c){
        return s.stream().mapToDouble(x -> val(x, c)).filter(v -> !Double.isNaN(v)).min().orElse(Double.NaN);
    }
    private static double maxAbsOf(List<Sample> s, Channel c){
        return s.stream().mapToDouble(x -> Math.abs(val(x, c))).filter(v -> !Double.isNaN(v)).max().orElse(Double.NaN);
    }
    private static double dutyCycle(List<Sample> s, Channel c, double threshold){
        long n = s.stream().filter(x -> !Double.isNaN(val(x,c))).count();
        if (n == 0) return Double.NaN;
        long on = s.stream().filter(x -> val(x,c) >= threshold).count();
        return 100.0 * on / (double) n;
    }
    private static double val(Sample s, Channel c){
        var map = s.values();
        if (map == null) return Double.NaN;
        Double v = map.get(c);
        return v == null ? Double.NaN : v;
    }
}
