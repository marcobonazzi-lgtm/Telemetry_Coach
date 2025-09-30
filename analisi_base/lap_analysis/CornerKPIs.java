package org.simulator.analisi_base.lap_analysis;

import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import java.util.List;

import static org.simulator.analisi_base.lap_analysis.SampleMath.*;

/** Calcola KPI per curva su un lap segmentato. */
public final class CornerKPIs {

    public static final class KPIs {
        private final double vMinKmh;
        private final double maxBrakePct;
        private final double firstThrottlePct;
        private final double throttleHoldTime;

        public KPIs(double vMinKmh, double maxBrakePct, double firstThrottlePct, double throttleHoldTime, double unused1, double unused2, int unused3) {
            this.vMinKmh = vMinKmh;
            this.maxBrakePct = maxBrakePct;
            this.firstThrottlePct = firstThrottlePct;
            this.throttleHoldTime = throttleHoldTime;
        }

        public double minSpeedKmh()  { return vMinKmh; }
        public double brakePeak()    { return maxBrakePct; }
        public double firstThrottle(){ return firstThrottlePct; }
        public double throttleTime() { return throttleHoldTime; }
    }


    public KPIs compute(Lap lap, CornerDetector.CornerSegment c) {
        List<Sample> s = lap.samples;
        int i0 = indexAtX(s, c.xStart), i1 = indexAtX(s, c.xEnd);
        i0 = Math.max(0, Math.min(i0, s.size()-1));
        i1 = Math.max(i0, Math.min(i1, s.size()-1));

        double minV = Double.POSITIVE_INFINITY;
        double brakePk=0, firstTh=0, thTime=Double.NaN;

        boolean gotFirstTh=false;

        for (int i=i0;i<=i1;i++){
            Sample sm=s.get(i);
            minV = Math.min(minV, speedKmh(sm));
            double br = clampPct(brakePct(sm));
            if (br > brakePk) brakePk = br;

            double th = clampPct(throttlePct(sm));
            if (!gotFirstTh && th > 5){
                firstTh = th;
                thTime = sm.timestamp() - s.get(i0).timestamp();
                gotFirstTh = true;
            }
        }

        if (!Double.isFinite(minV)) minV = Double.NaN;
        if (!Double.isFinite(brakePk)) brakePk = Double.NaN;
        if (!Double.isFinite(firstTh)) firstTh = 0;
        if (!Double.isFinite(thTime)) thTime = Double.NaN;

        return new KPIs(minV, brakePk, firstTh, thTime, 0, 0, 0);
    }

    private static double clampPct(double v){
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0;
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private static int indexAtX(List<Sample> s, double x){
        int lo=0, hi=s.size()-1;
        while (lo<hi){
            int mid=(lo+hi)/2;
            if (s.get(mid).distance() < x) lo=mid+1; else hi=mid;
        }
        return lo;
    }

}
