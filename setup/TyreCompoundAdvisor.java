package org.simulator.setup;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.coach.CoachCore;
import org.simulator.setup.setup_advisor.SetupAdvisor;
import org.simulator.setup.setup_advisor.VehicleTraits;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Advisor gomme ottimizzato con target dinamici.
 */
public final class TyreCompoundAdvisor {

    private TyreCompoundAdvisor(){}

    public static final class Choice {
        private final String compound;
        private final String reason;
        public Choice(String compound, String reason){ this.compound = compound; this.reason = reason; }
        public String compound(){ return compound; }
        public String reason(){ return reason; }
        @Override public String toString(){ return compound + " — " + reason; }
    }

    public static Choice suggest(List<Lap> session, SetupAdvisor.DriverStyle style){
        if (session == null || session.isEmpty()) {
            return new Choice("Medium", "Dati non sufficienti: scelta neutra.");
        }

        VehicleTraits traits = VehicleTraits.detect(session);
        VehicleTraits.TargetWindow targets = traits.targets; // Target dinamici

        Stats roadT = new Stats(); Stats coreT = new Stats(); Stats gripS = new Stats();
        for (Lap lap : session){
            if (lap == null || lap.samples == null) continue;
            for (Sample s : lap.samples){
                addIfFinite(roadT, val(s, Channel.ROAD_TEMP));
                addIfFinite(gripS, val(s, Channel.SURFACE_GRIP));
                double n=0, sum=0;
                Double fl = val(s, Channel.TIRE_TEMP_CORE_FL);
                Double fr = val(s, Channel.TIRE_TEMP_CORE_FR);
                Double rl = val(s, Channel.TIRE_TEMP_CORE_RL);
                Double rr = val(s, Channel.TIRE_TEMP_CORE_RR);
                if (isF(fl)) { sum+=fl; n++; }
                if (isF(fr)) { sum+=fr; n++; }
                if (isF(rl)) { sum+=rl; n++; }
                if (isF(rr)) { sum+=rr; n++; }
                if (n>0) addIfFinite(coreT, sum/n);
            }
        }

        double tRoad = roadT.meanOr(Double.NaN);
        double tCore = coreT.meanOr(Double.NaN);
        double grip  = gripS.meanOr(Double.NaN);

        int score = 0;
        if (isF(tRoad)) {
            // Soglie ambientali
            if (tRoad >= (traits.category==VehicleTraits.Category.FORMULA ? 38 : 45)) score += 1; // Hot
            else if (tRoad <= (traits.category==VehicleTraits.Category.FORMULA ? 18 : 22)) score -= 1; // Cold
        }
        if (isF(tCore)) {
            double mid = 0.5*(targets.tempCoreMin() + targets.tempCoreMax());
            if (tCore >= mid+7) score += 1;
            else if (tCore <= mid-7) score -= 1;
        }
        if (style == SetupAdvisor.DriverStyle.AGGRESSIVE) score += 1;
        else if (style == SetupAdvisor.DriverStyle.SMOOTH) score -= 1;
        if (isF(grip) && grip < CoachCore.GRIP_LOW) score -= 1; // Low grip -> softer tyre

        if (score > 1) score = 1;
        if (score < -1) score = -1;

        String compound = switch (score){
            case -1 -> "Soft";
            case  1 -> "Hard";
            default -> "Medium";
        };

        String reason = buildReason(traits, targets, tRoad, tCore, grip, style, compound);
        return new Choice(compound, reason);
    }

    private static String buildReason(VehicleTraits tr, VehicleTraits.TargetWindow tt, double tRoad, double tCore, double grip,
                                      SetupAdvisor.DriverStyle style, String compound){
        String sStyle = switch (style){
            case AGGRESSIVE -> "guida aggressiva";
            case SMOOTH     -> "guida pulita";
            default         -> "guida neutra";
        };
        String tRoadTxt = isF(tRoad) ? fmt(tRoad,1)+"°C" : "n/d";
        String tCoreTxt = isF(tCore) ? fmt(tCore,0)+"°C" : "n/d";
        return String.format(Locale.ITALIAN,
                "%s • asfalto %s, core %s (target %d–%d°C) • %s → %s",
                tr.category, tRoadTxt, tCoreTxt, (int)tt.tempCoreMin(), (int)tt.tempCoreMax(), sStyle, compound);
    }


    // ==== helpers ====
    private static Double val(Sample s, Channel ch){
        Double v = s.values().get(ch);
        return (v!=null && !v.isNaN() && !v.isInfinite()) ? v : Double.NaN;
    }
    private static void addIfFinite(Stats st, Double v){
        if (v!=null && !v.isNaN() && !v.isInfinite()) st.add(v);
    }
    private static boolean isF(Double v){ return v!=null && !v.isNaN() && !v.isInfinite(); }



    private static String fmt(double v, int d){
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.ROOT);
        nf.setMinimumFractionDigits(d);
        nf.setMaximumFractionDigits(d);
        return nf.format(v);
    }

    private static final class Stats {
        int n = 0; double sum = 0.0;
        void add(double v){ n++; sum += v; }
        double mean(){ return n>0 ? sum / n : Double.NaN; }
        double meanOr(double fallback){ double m = mean(); return (Double.isNaN(m) ? fallback : m); }
    }
}