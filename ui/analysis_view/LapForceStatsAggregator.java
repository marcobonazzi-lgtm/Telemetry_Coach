package org.simulator.ui.analysis_view;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.*;

public final class LapForceStatsAggregator {

    private LapForceStatsAggregator(){}

    /** Stesse chiavi/valori che avevi in AnalysisView.buildForceStatsForLap(...) */
    public static Map<String, Double> build(Lap lap){
        var m = new LinkedHashMap<String, Double>();
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return m;

        var ffb   = new ArrayList<Double>();
        var pedal = new ArrayList<Double>();
        var seat  = new ArrayList<Double>();
        var time  = new ArrayList<Double>();

        for (Sample s : lap.samples) {
            ffb.add(s.values().getOrDefault(Channel.FFB, Double.NaN));
            pedal.add(s.values().getOrDefault(Channel.PEDAL_FORCE, Double.NaN));
            seat.add(s.values().getOrDefault(Channel.SEAT_FORCE, Double.NaN));
            time.add(s.values().getOrDefault(Channel.TIME, Double.NaN));
        }

        double clipPct      = 100.0 * fraction(ffb, v -> !v.isNaN() && v >= 0.92);
        double ffbMean      = mean(ffb);

        double pedalAvg     = mean(pedal);
        double pedalMax     = max(pedal);
        int    pedalSpikes  = countSpikes(pedal, time, 250.0, 0.20); // >250 N in 0.2 s

        double seatRoughPct = 100.0 * roughFraction(seat, time, 120.0, 0.04); // |Δseat|>120 N

        m.put("FFB clipping [%]", round1(clipPct));
        m.put("FFB medio [0..1]", round3(ffbMean));
        m.put("Pedal force medio [N]", round1(pedalAvg));
        m.put("Pedal force max [N]", round1(pedalMax));
        m.put("Pedal \"stomp\" [#]", (double) pedalSpikes);
        m.put("Seat roughness [%]", round1(seatRoughPct));
        return m;
    }

    // ----- helpers numerici (copiati 1:1 dall'originale) -----
    private static double mean(List<Double> a){ double s=0; int n=0; for (var v:a){ if (v!=null && !v.isNaN()){ s+=v; n++; } } return n>0?s/n:Double.NaN; }
    private static double max(List<Double> a){ double mx=Double.NaN; for (var v:a){ if (v!=null && !v.isNaN()){ mx = Double.isNaN(mx)?v:Math.max(mx,v);} } return mx; }
    private static double round1(double d){ return Double.isNaN(d)?Double.NaN:Math.round(d*10.0)/10.0; }
    private static double round3(double d){ return Double.isNaN(d)?Double.NaN:Math.round(d*1000.0)/1000.0; }
    private static double fraction(List<Double> a, java.util.function.Predicate<Double> p){
        int n=0, m=0; for (var v:a){ if (v==null || v.isNaN()) continue; n++; if (p.test(v)) m++; } return n>0 ? (double)m/n : 0.0;
    }
    private static int countSpikes(List<Double> pedal, List<Double> time, double spikeN, double windowS){
        int spikes = 0; boolean hasTime = time.stream().anyMatch(v -> v!=null && !v.isNaN());
        for (int i=1;i<pedal.size();i++){
            double pi = safe(pedal.get(i)), p0 = safe(pedal.get(i-1));
            if (Double.isNaN(pi) || Double.isNaN(p0)) continue;
            if (!hasTime){ if (pi - p0 > spikeN) spikes++; }
            else {
                double ti = safe(time.get(i)), t0 = safe(time.get(i-1));
                if (Double.isNaN(ti) || Double.isNaN(t0)) continue;
                double dt = Math.max(1e-3, ti - t0);
                if (pi - p0 > spikeN && dt <= windowS) spikes++;
            }
        }
        return spikes;
    }
    private static double roughFraction(List<Double> seat, List<Double> time, double thrN, double targetDt){
        int n = seat.size(); if (n<2) return 0.0;
        boolean hasTime = time.stream().anyMatch(v -> v!=null && !v.isNaN());
        int hit=0, base=0;
        for (int i=1;i<n;i++){
            double si = safe(seat.get(i)), s0 = safe(seat.get(i-1));
            if (Double.isNaN(si) || Double.isNaN(s0)) continue;
            if (!hasTime){ base++; if (Math.abs(si - s0) > thrN) hit++; }
            else {
                double ti = safe(time.get(i)), t0 = safe(time.get(i-1));
                if (Double.isNaN(ti) || Double.isNaN(t0)) continue;
                double dt = Math.max(1e-3, ti - t0);
                if (dt <= targetDt){ base++; if (Math.abs(si - s0) > thrN) hit++; }
            }
        }
        return base>0 ? (double)hit/base : 0.0;
    }
    private static double safe(Double d){ return (d==null)?Double.NaN:d; }
}
