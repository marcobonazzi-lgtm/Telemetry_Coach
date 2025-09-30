package org.simulator.setup.setup_advisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Utility matematiche robuste per analisi setup/stile. */
final class SetupMath {
    private SetupMath(){}

    // ======== ESISTENTI (invariati) ========
    static double mean(List<Double> a){
        double s=0; int n=0;
        for (double v: a){ if(!Double.isNaN(v)){ s+=v; n++; } }
        return n>0 ? s/n : Double.NaN;
    }
    static double mad(List<Double> a, double mean){
        double s=0; int n=0;
        for (double v: a){ if(!Double.isNaN(v)){ s+=Math.abs(v-mean); n++; } }
        return n>0 ? s/n : 0.0;
    }
    static double clamp01(double x){ return Math.max(0.0, Math.min(1.0, x)); }

    // ======== NUOVE UTILITY ROBUSTE ========

    /** Media su array (NaN-safe). */
    static double mean(double[] a){
        if (a==null || a.length==0) return Double.NaN;
        double s=0; int n=0;
        for (double v: a){ if(!Double.isNaN(v)){ s+=v; n++; } }
        return n>0 ? s/n : Double.NaN;
    }

    /** Media pesata (pesi >=0). */
    static double weightedMean(List<Double> x, List<Double> w){
        if (x==null || w==null || x.size()!=w.size() || x.isEmpty()) return Double.NaN;
        double sw=0, s=0; int n=0;
        for (int i=0;i<x.size();i++){
            double xi=x.get(i), wi=w.get(i);
            if (Double.isNaN(xi) || Double.isNaN(wi) || wi<0) continue;
            s += xi*wi; sw += wi; n++;
        }
        return (n>0 && sw>0) ? s/sw : Double.NaN;
    }

    /** Varianza non-bias (popolazione). */
    static double variance(List<Double> a){
        double m = mean(a);
        if (Double.isNaN(m)) return Double.NaN;
        double s=0; int n=0;
        for (double v: a){ if(!Double.isNaN(v)){ double d=v-m; s+=d*d; n++; } }
        return n>0 ? s/n : Double.NaN;
    }

    /** Deviazione standard. */
    static double stddev(List<Double> a){
        double v = variance(a);
        return Double.isNaN(v) ? Double.NaN : Math.sqrt(v);
    }

    /** RMS (root mean square). */
    static double rms(List<Double> a){
        if (a==null || a.isEmpty()) return Double.NaN;
        double s=0; int n=0;
        for (double v: a){ if(!Double.isNaN(v)){ s+=v*v; n++; } }
        return n>0 ? Math.sqrt(s/n) : Double.NaN;
    }

    /** Mediana (NaN ignorati). */
    static double median(List<Double> a){
        List<Double> v = cleaned(a);
        if (v.isEmpty()) return Double.NaN;
        Collections.sort(v);
        int n=v.size();
        if ((n&1)==1) return v.get(n/2);
        return 0.5*(v.get(n/2-1)+v.get(n/2));
    }

    /** Percentile p in [0..1] (interpolazione lineare). */
    static double percentile(List<Double> a, double p){
        List<Double> v = cleaned(a);
        if (v.isEmpty()) return Double.NaN;
        p = clamp01(p);
        Collections.sort(v);
        double idx = p*(v.size()-1);
        int lo = (int)Math.floor(idx);
        int hi = (int)Math.ceil(idx);
        if (lo==hi) return v.get(lo);
        double t = idx - lo;
        return v.get(lo)*(1.0-t) + v.get(hi)*t;
    }

    /** Intervallo interquartile (Q3-Q1). */
    static double iqr(List<Double> a){
        double q1 = percentile(a, 0.25);
        double q3 = percentile(a, 0.75);
        if (Double.isNaN(q1) || Double.isNaN(q3)) return Double.NaN;
        return q3 - q1;
    }

    /** Media troncata (es. trim=0.1 => scarta 10% low/high). */
    static double trimmedMean(List<Double> a, double trim){
        List<Double> v = cleaned(a);
        if (v.isEmpty()) return Double.NaN;
        trim = Math.max(0.0, Math.min(0.49, trim));
        Collections.sort(v);
        int n = v.size();
        int cut = (int)Math.floor(n*trim);
        int from = cut, to = n - cut;
        if (from >= to) return mean(v);
        double s=0; int m=0;
        for (int i=from;i<to;i++){ s+=v.get(i); m++; }
        return m>0 ? s/m : Double.NaN;
    }

    /** Z-score (se std≈0 => 0). */
    static double zscore(double x, double mean, double std){
        if (Double.isNaN(x) || Double.isNaN(mean) || Double.isNaN(std) || std<=1e-9) return 0.0;
        return (x-mean)/std;
    }

    /** Normalizza in [0..1] con limiti, clamp incluso. */
    static double normalize(double x, double min, double max){
        if (Double.isNaN(x) || Double.isNaN(min) || Double.isNaN(max) || max<=min) return Double.NaN;
        return clamp01((x-min)/(max-min));
    }

    /** Mappa x in [a..b] da [min..max] con clamp. */
    static double mapRange(double x, double min, double max, double a, double b){
        double u = normalize(x, min, max);
        if (Double.isNaN(u)) return Double.NaN;
        return a + (b-a)*u;
    }

    /** Lerp lineare tra a e b con t in [0..1]. */
    static double lerp(double a, double b, double t){
        return a + (b-a)*t;
    }

    /** Divisione sicura: se denom≈0 => defaultVal. */
    static double safeDiv(double num, double den, double defaultVal){
        return Math.abs(den) > 1e-12 ? (num/den) : defaultVal;
    }

    // -------- helpers --------
    private static List<Double> cleaned(List<Double> a){
        List<Double> v = new ArrayList<>();
        if (a==null) return v;
        for (double x: a) if (!Double.isNaN(x)) v.add(x);
        return v;
    }
}
