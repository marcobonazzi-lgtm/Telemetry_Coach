package org.simulator.setup.setup_advisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Utility matematiche robuste per analisi setup/stile. */
final class SetupMath {
    private SetupMath(){}

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


    /** Media su array (NaN-safe). */
    static double mean(double[] a){
        if (a==null || a.length==0) return Double.NaN;
        double s=0; int n=0;
        for (double v: a){ if(!Double.isNaN(v)){ s+=v; n++; } }
        return n>0 ? s/n : Double.NaN;
    }

}
