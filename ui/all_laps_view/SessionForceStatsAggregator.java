package org.simulator.ui.all_laps_view;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;

import java.util.*;

/** Calcolo statistiche “forze” a livello sessione (stessa logica di prima). */
final class SessionForceStatsAggregator {

    static Map<String, Double> build(List<Lap> laps){
        var m = new LinkedHashMap<String, Double>();
        if (laps == null || laps.isEmpty()) return m;

        int nL=0, spikesTot=0;
        double clipSum=0, ffbMeanSum=0, pedalAvgSum=0, pedalMaxBest=Double.NaN, roughSum=0;

        for (var lap : laps){
            if (lap == null || lap.samples == null || lap.samples.isEmpty()) continue;
            nL++;

            var ffb   = new ArrayList<Double>();
            var pedal = new ArrayList<Double>();
            var seat  = new ArrayList<Double>();
            var time  = new ArrayList<Double>();
            for (var s: lap.samples){
                ffb.add(s.values().getOrDefault(Channel.FFB, Double.NaN));
                pedal.add(s.values().getOrDefault(Channel.PEDAL_FORCE, Double.NaN));
                seat.add(s.values().getOrDefault(Channel.SEAT_FORCE, Double.NaN));
                time.add(s.values().getOrDefault(Channel.TIME, Double.NaN));
            }
            double clip = fraction(ffb, v -> !v.isNaN() && v >= 0.92);
            clipSum    += clip;
            ffbMeanSum += mean(ffb);
            pedalAvgSum+= mean(pedal);
            double pMax = max(pedal);
            pedalMaxBest = Double.isNaN(pedalMaxBest) ? pMax : Math.max(pedalMaxBest, pMax);
            spikesTot  += countSpikes(pedal, time, 250.0, 0.20);
            roughSum   += roughFraction(seat, time, 120.0, 0.04);
        }
        if (nL>0){
            m.put("FFB clipping (avg) [%]", Math.round((clipSum/nL)*1000.0)/10.0);
            m.put("FFB medio (avg) [0..1]", Math.round((ffbMeanSum/nL)*1000.0)/1000.0);
            m.put("Pedal force medio (avg) [N]", Math.round((pedalAvgSum/nL)*10.0)/10.0);
            m.put("Pedal force max [N]", Math.round(pedalMaxBest*10.0)/10.0);
            m.put("Pedal \"stomp\" (tot) [#]", (double)spikesTot);
            m.put("Seat roughness (avg) [%]", Math.round((roughSum/nL)*1000.0)/10.0);
        }
        return m;
    }

    // helpers
    private static double mean(List<Double> a){ double s=0; int n=0; for (var v:a){ if (v!=null && !v.isNaN()){ s+=v; n++; } } return n>0?s/n:Double.NaN; }
    private static double max(List<Double> a){ double mx=Double.NaN; for (var v:a){ if (v!=null && !v.isNaN()){ mx = Double.isNaN(mx)?v:Math.max(mx,v);} } return mx; }
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
