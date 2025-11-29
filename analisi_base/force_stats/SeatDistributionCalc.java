package org.simulator.analisi_base.force_stats;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

/** Calcolo della ripartizione sedile SX/POST/DX (estratto da ForceStats). */
final class SeatDistributionCalc {

    private SeatDistributionCalc(){}

    static ForceStats.SeatDistribution compute(Lap lap) {
        ForceStats.SeatDistribution sd = new ForceStats.SeatDistribution();
        if (lap == null || lap.samples == null || lap.samples.size() < 2) return sd;

        double prevSeat = Double.NaN, prevT = Double.NaN;
        double sumL = 0, sumR = 0, sumRear = 0, sumW = 0;

        for (Sample s : lap.samples) {
            double seat  = ForceMath.val(s, Channel.SEAT_FORCE);
            double t     = ForceMath.val(s, Channel.TIME);
            double lat   = ForceMath.val(s, Channel.ACC_LAT);
            double lon   = ForceMath.val(s, Channel.ACC_LONG);
            double steer = ForceMath.val(s, Channel.STEER_ANGLE);

            if (Double.isNaN(seat)) { prevSeat = seat; prevT = t; continue; }

            double dSeat = (!Double.isNaN(prevSeat)) ? Math.abs(seat - prevSeat) : 0.0;
            double dt    = (!Double.isNaN(t) && !Double.isNaN(prevT)) ? Math.max(1e-3, t - prevT) : 0.02;
            prevSeat = seat; prevT = t;

            // Intensità del colpo: enfatizza jerk alto con smoothstep
            double seatN = ForceMath.clamp01(dSeat / 200.0);            // 200 N delta → 1.0
            double jerk  = dSeat / dt;                                  // N/s
            double inten = seatN * ForceMath.smoothstep(800.0, 3000.0, jerk);

            if (inten <= 0) continue;

            // Normalizzazioni con fallback (se manca → 1.0)
            double latNorm = Double.isNaN(lat) ? 1.0 : ForceMath.clamp01(Math.abs(lat) / 1.5);
            double lonNorm = Double.isNaN(lon) ? 1.0 : ForceMath.clamp01(Math.abs(lon) / 1.5);

            // Segno laterale da steer, fallback su lat
            double steerSign = (!Double.isNaN(steer) && Math.abs(steer) > 1.0) ? Math.signum(steer)
                    : ((!Double.isNaN(lat) && Math.abs(lat) > 0.02) ? Math.signum(lat) : 0.0);

            double wL = (steerSign > 0 ? 1.0 : (steerSign < 0 ? 0.0 : 0.5)) * latNorm;
            double wR = (steerSign < 0 ? 1.0 : (steerSign > 0 ? 0.0 : 0.5)) * latNorm;
            double wRear = lonNorm;

            sumL    += inten * wL;
            sumR    += inten * wR;
            sumRear += inten * wRear;
            sumW    += inten;
        }

        if (sumW > 0) {
            sd.left  = ForceMath.clamp01(sumL    / sumW);
            sd.right = ForceMath.clamp01(sumR    / sumW);
            sd.rear  = ForceMath.clamp01(sumRear / sumW);
        }
        return sd;
    }
}
