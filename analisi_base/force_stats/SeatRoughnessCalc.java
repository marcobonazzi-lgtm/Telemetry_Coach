package org.simulator.analisi_base.force_stats;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.ArrayList;
import java.util.List;

/** Calcolo della roughness del sedile (estratto da ForceStats senza modifiche funzionali). */
final class SeatRoughnessCalc {

    private SeatRoughnessCalc(){}

    static double compute(Lap lap) {
        if (lap == null || lap.samples == null || lap.samples.size() < 3) return 0.0;

        List<Double> jerks = new ArrayList<>();
        double prevSeat = Double.NaN, prevT = Double.NaN;

        for (Sample s : lap.samples) {
            double seat = ForceMath.val(s, Channel.SEAT_FORCE);
            double t    = ForceMath.val(s, Channel.TIME); // può essere NaN
            if (Double.isNaN(seat)) { prevSeat = seat; prevT = t; continue; }

            if (!Double.isNaN(prevSeat)) {
                double dSeat = Math.abs(seat - prevSeat);
                double dt    = (!Double.isNaN(t) && !Double.isNaN(prevT)) ? Math.max(1e-3, t - prevT) : 0.02; // 50 Hz fallback
                jerks.add(dSeat / dt); // N/s
            }
            prevSeat = seat; prevT = t;
        }
        if (jerks.isEmpty()) return 0.0;

        // Soglia adattiva più permissiva: q80, con un minimo assoluto
        double q80 = ForceMath.quantile(jerks, 0.80);
        double minThr = 400.0; // N/s
        double thr = Math.max(minThr, q80);

        int n = jerks.size(), hit = 0;
        for (double j : jerks) if (j >= thr) hit++;

        double frac = n > 0 ? (double) hit / n : 0.0;
        if (frac > 0) return frac;

        // ---- Fallback continuo quando non ci sono "hit" ----
        double q50 = ForceMath.quantile(jerks, 0.50);
        double q95 = ForceMath.quantile(jerks, 0.95);
        double mean = 0.0; for (double j : jerks) mean += j; mean /= jerks.size();
        double var = 0.0; for (double j : jerks) { double d=j-mean; var += d*d; } var /= Math.max(1, jerks.size()-1);
        double std = Math.sqrt(var);

        double scale = Math.max(1e-6, q95);
        double continuous = ForceMath.clamp01((std + Math.abs(mean - q50)) / scale);

        return Math.max(continuous * 0.6, 0.02);
    }
}
