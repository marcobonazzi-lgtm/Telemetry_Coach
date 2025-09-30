package org.simulator.analisi_base.lap_analysis;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

/** Helpers numerici comuni, estratti da LapAnalysis (nessun cambiamento di comportamento). */
final class SampleMath {

    private SampleMath(){}

    static double avg(Lap lap, Channel ch){
        double sum=0; int n=0;
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()){ sum+=v; n++; }
        }
        return n>0 ? sum/n : Double.NaN;
    }

    static double max(Lap lap, Channel ch){
        double best = Double.NaN;
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v==null || v.isNaN()) continue;
            if (Double.isNaN(best) || v > best) best = v;
        }
        return best;
    }

    static double firstNonNaN(Lap lap, Channel ch){
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()) return v;
        }
        return Double.NaN;
    }

    static double lastNonNaN(Lap lap, Channel ch){
        var ss = lap.samples;
        for (int i=ss.size()-1; i>=0; i--){
            Double v = ss.get(i).values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()) return v;
        }
        return Double.NaN;
    }

    /** percentuale di campioni sopra una soglia (0..1), NaN se canale mancante */
    static double fractionAbove(Lap lap, Channel ch, double thr){
        int n=0, hit=0; boolean any=false;
        for (Sample s: lap.samples){
            Double v = s.values().get(ch);
            if (v==null || v.isNaN()) continue;
            any = true; n++;
            double vv = v;
            if (vv <= 1.0001) vv *= 100.0;   // adatta 0..1 -> %
            if (vv > thr) hit++;
        }
        if (!any || n==0) return Double.NaN;
        return (double)hit/n;
    }

    /** percentuale di campioni "attivi": per TC/ABS accetta 0/1 o % (0..100). -1 se canale assente. */
    static double fractionActive(Lap lap, Channel flag){
        int n=0, on=0; boolean found=false;
        for (Sample s: lap.samples){
            Double v = s.values().get(flag);
            if (v == null || v.isNaN()) continue;
            found = true; n++;
            double vv = v;
            if (vv > 1.0001) vv = vv / 100.0; // se è in % (0..100) → 0..1
            if (vv >= 0.5) on++;
        }
        return found && n>0 ? (double)on/n : -1.0;
    }

    /** normalizza danno a % e fa media sul giro */
    static double damageAvg(Lap lap, Channel ch){
        double sum=0; int n=0; boolean any=false;
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v==null || v.isNaN()) continue;
            any = true; n++;
            double vv = v <= 1.0 ? v*100.0 : v; // 0..1 -> %
            sum += vv;
        }
        if (!any || n==0) return Double.NaN;
        return sum/n;
    }

    static boolean allNaN(double... arr){
        for (double d : arr) if (!Double.isNaN(d)) return false;
        return true;
    }

    static double meanIgnoringNaN(double... arr){
        double sum=0; int n=0;
        for (double d: arr){ if (!Double.isNaN(d)){ sum+=d; n++; } }
        return n>0 ? sum/n : Double.NaN;
    }
    public static double speedKmh(Sample s){
        Double v = s.values().get(Channel.GROUND_SPEED);
        if (v == null || Double.isNaN(v)) v = s.values().get(Channel.SPEED);
        return v == null ? Double.NaN : v;
    }
    public static double steeringDeg(Sample s){ return s.values().getOrDefault(Channel.STEER_ANGLE, 0.0); }
    public static double throttlePct(Sample s){ return s.values().getOrDefault(Channel.THROTTLE, 0.0); }
    public static double brakePct(Sample s){ return s.values().getOrDefault(Channel.BRAKE, 0.0); }

}
