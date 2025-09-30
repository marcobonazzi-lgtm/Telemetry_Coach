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
 * Advisor gomme/compound (adattivo per categoria/trazione/powertrain).
 * API:
 *  - suggest(session, style) -> Choice(compound, reason)
 *  - suggest(lap) -> String (dettaglio testo)
 *  - suggestList(lap) -> List<String> (messaggi sintetici)
 */
public final class TyreCompoundAdvisor {

    private TyreCompoundAdvisor(){}

    // Range PSI "universali" conservativi (molti sim GT/road): lasciare invariati se non hai dati specifici.
    private static final double PSI_MIN = 26.0;
    private static final double PSI_MAX = 28.0;

    // Finestra temperatura CORE target per categoria (°C)
    private static final class TempTarget {
        final double min, max;
        TempTarget(double min, double max){ this.min=min; this.max=max; }
    }
    private static TempTarget targetFor(VehicleTraits.Category c){
        return switch (c){
            case FORMULA   -> new TempTarget(90, 110);
            case PROTOTYPE -> new TempTarget(85, 105);
            case GT        -> new TempTarget(80, 100);
            case ROAD      -> new TempTarget(75, 95);
            default        -> new TempTarget(80, 100);
        };
    }

    /** Oggetto compatibile con i builder originali. */
    public static final class Choice {
        private final String compound;
        private final String reason;
        public Choice(String compound, String reason){ this.compound = compound; this.reason = reason; }
        public String compound(){ return compound; }
        public String reason(){ return reason; }
        @Override public String toString(){ return compound + " — " + reason; }
    }

    /**
     * Scelta compound per l'intera sessione + stile pilota (adattivo ai traits).
     * Heuristics:
     *  - temperatura asfalto/core vs target categoria
     *  - stile: AGGRESSIVE -> più hard; SMOOTH -> più soft
     *  - grip pista basso -> più soft
     *  - powertrain/drivetrain piccoli bias
     */
    public static Choice suggest(List<Lap> session, SetupAdvisor.DriverStyle style){
        if (session == null || session.isEmpty()) {
            return new Choice("Medium", "Dati non sufficienti: scelta neutra.");
        }

        VehicleTraits traits = VehicleTraits.detect(session);
        TempTarget tt = targetFor(traits.category);

        // Stat sessione
        Stats roadT = new Stats(); Stats coreT = new Stats(); Stats gripS = new Stats();
        for (Lap lap : session){
            if (lap == null || lap.samples == null) continue;
            for (Sample s : lap.samples){
                addIfFinite(roadT, val(s, Channel.ROAD_TEMP));
                addIfFinite(gripS, val(s, Channel.SURFACE_GRIP));
                // media core 4 gomme (se presente)
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

        // Score: <0 -> Soft, 0 -> Medium, >0 -> Hard
        int score = 0;

        // Asfalto
        if (isF(tRoad)) {
            if (tRoad >= (traits.category==VehicleTraits.Category.FORMULA ? 38 : 42)) score += 1;
            else if (tRoad <= (traits.category==VehicleTraits.Category.FORMULA ? 18 : 24)) score -= 1;
        }
        // Core vs target categoria
        if (isF(tCore)) {
            double mid = 0.5*(tt.min+tt.max);
            if (tCore >= mid+5) score += 1;
            else if (tCore <= mid-5) score -= 1;
        }
        // Stile
        if (style == SetupAdvisor.DriverStyle.AGGRESSIVE) score += 1;
        else if (style == SetupAdvisor.DriverStyle.SMOOTH) score -= 1;

        // Grip pista basso -> soft
        if (isF(grip) && grip < CoachCore.GRIP_LOW) score -= 1;

        // Piccoli bias per powertrain/drivetrain
        // Turbo tende a scaldare -> spingi verso Hard se già caldo
        if (traits.powertrain == VehicleTraits.Powertrain.TURBO && isF(tCore) && tCore > tt.min+5) score += 1;
        // FWD tende a scaldare l’avantreno: se caldo, spingi Hard; se freddo, niente
        if (traits.drivetrain == VehicleTraits.Drivetrain.FWD && isF(tCore) && tCore > tt.max-5) score += 1;

        // Clamp
        if (score > 1) score = 1;
        if (score < -1) score = -1;

        String compound = switch (score){
            case -1 -> "Soft";
            case  1 -> "Hard";
            default -> "Medium";
        };

        String reason = buildReason(traits, tt, tRoad, tCore, grip, style, compound);
        return new Choice(compound, reason);
    }

    private static String buildReason(VehicleTraits tr, TempTarget tt, double tRoad, double tCore, double grip,
                                      SetupAdvisor.DriverStyle style, String compound){
        String sStyle = switch (style){
            case AGGRESSIVE -> "stile aggressivo";
            case SMOOTH     -> "stile pulito";
            default         -> "stile neutro";
        };
        String tRoadTxt = isF(tRoad) ? fmt(tRoad,1)+"°C" : "n/d";
        String tCoreTxt = isF(tCore) ? fmt(tCore,0)+"°C" : "n/d";
        String gripTxt  = isF(grip)  ? fmt(grip,2)      : "n/d";
        return String.format(Locale.ITALIAN,
                "%s/%s/%s • asfalto %s, core %s (target %d–%d°C), grip %s, %s → %s",
                tr.category, tr.drivetrain, tr.powertrain,
                tRoadTxt, tCoreTxt, (int)tt.min, (int)tt.max, gripTxt, sStyle, compound);
    }

    /** Versione descrittiva testuale (per singolo giro). */
    public static String suggest(Lap lap) {
        List<String> lines = suggestList(lap);
        if (lines.isEmpty()) return "Nessun suggerimento: dati gomme non disponibili.";
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<lines.size();i++){
            if (i>0) sb.append('\n');
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /** Descrizione dettagliata corner-by-corner (adattata su categoria). */
    public static List<String> suggestList(Lap lap) {
        List<String> out = new ArrayList<>();
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) {
            out.add("Nessun dato disponibile per il giro selezionato.");
            return out;
        }

        VehicleTraits traits = VehicleTraits.detect(java.util.List.of(lap));
        TempTarget tt = targetFor(traits.category);

        Stats pFL = new Stats(), pFR = new Stats(), pRL = new Stats(), pRR = new Stats();
        Stats tFL = new Stats(), tFR = new Stats(), tRL = new Stats(), tRR = new Stats();
        Stats air = new Stats(), road = new Stats();

        for (Sample s : lap.samples){
            addIfFinite(pFL, val(s, Channel.TIRE_PRESSURE_FL));
            addIfFinite(pFR, val(s, Channel.TIRE_PRESSURE_FR));
            addIfFinite(pRL, val(s, Channel.TIRE_PRESSURE_RL));
            addIfFinite(pRR, val(s, Channel.TIRE_PRESSURE_RR));

            addIfFinite(tFL, val(s, Channel.TIRE_TEMP_CORE_FL));
            addIfFinite(tFR, val(s, Channel.TIRE_TEMP_CORE_FR));
            addIfFinite(tRL, val(s, Channel.TIRE_TEMP_CORE_RL));
            addIfFinite(tRR, val(s, Channel.TIRE_TEMP_CORE_RR));

            addIfFinite(air,  val(s, Channel.AIR_TEMP));
            addIfFinite(road, val(s, Channel.ROAD_TEMP));
        }

        if (air.n>0 || road.n>0) {
            String sAir  = (air.n>0)  ? (fmt(air.mean(),1)  + "°C") : "n/d";
            String sRoad = (road.n>0) ? (fmt(road.mean(),1) + "°C") : "n/d";
            out.add("Meteo: aria " + sAir + ", asfalto " + sRoad + ".");
        }

        addPsiMsg(out, "FL", pFL);
        addPsiMsg(out, "FR", pFR);
        addPsiMsg(out, "RL", pRL);
        addPsiMsg(out, "RR", pRR);

        addTempMsg(out, "FL", tFL, tt);
        addTempMsg(out, "FR", tFR, tt);
        addTempMsg(out, "RL", tRL, tt);
        addTempMsg(out, "RR", tRR, tt);

        // sbilanci L/R e assi
        if (pFL.n>0 && pFR.n>0) {
            double d = Math.abs(pFL.mean() - pFR.mean());
            if (d > 0.3) out.add("Pressione avantreno sbilanciata L/R (" + fmt(d,1) + " psi): verifica camber/linea o equalizza di ±0.1–0.2.");
        }
        if (pRL.n>0 && pRR.n>0) {
            double d = Math.abs(pRL.mean() - pRR.mean());
            if (d > 0.3) out.add("Pressione retrotreno sbilanciata L/R (" + fmt(d,1) + " psi): controlla trazione in appoggio e cordoli.");
        }
        if (tFL.n>0 && tFR.n>0) {
            double d = Math.abs(tFL.mean() - tFR.mean());
            if (d > 8) out.add("Asimmetria termica avantreno (" + Math.round(d) + "°C): rivedi linea/camber/pressioni.");
        }
        if (tRL.n>0 && tRR.n>0) {
            double d = Math.abs(tRL.mean() - tRR.mean());
            if (d > 8) out.add("Asimmetria termica retrotreno (" + Math.round(d) + "°C): attenzione alla trazione in appoggio.");
        }

        // nota categoria/tratti
        out.add(String.format(Locale.ITALIAN, "Target temp core (%s): ~%d–%d°C.",
                traits.category, (int)tt.min, (int)tt.max));

        if (out.isEmpty()) out.add("Tutto ok: pressioni e temperature nei range attesi.");
        return out;
    }

    // ==== helpers ===========================================================

    private static Double val(Sample s, Channel ch){
        Double v = s.values().get(ch);
        return (v!=null && !v.isNaN() && !v.isInfinite()) ? v : Double.NaN;
    }
    private static void addIfFinite(Stats st, Double v){
        if (v!=null && !v.isNaN() && !v.isInfinite()) st.add(v);
    }
    private static boolean isF(Double v){ return v!=null && !v.isNaN() && !v.isInfinite(); }

    private static void addPsiMsg(List<String> out, String pos, Stats psi){
        if (psi.n==0) return;
        double p = psi.mean();
        if (p < PSI_MIN) out.add("Pressione " + pos + " bassa (" + fmt(p,1) + " psi): +0.2/+0.4 psi.");
        else if (p > PSI_MAX) out.add("Pressione " + pos + " alta (" + fmt(p,1) + " psi): -0.2/-0.4 psi.");
        else out.add("Pressione " + pos + " ok (" + fmt(p,1) + " psi).");
    }
    private static void addTempMsg(List<String> out, String pos, Stats t, TempTarget tt){
        if (t.n==0) return;
        double v = t.mean();
        if (v < tt.min) out.add("Gomme " + pos + " fredde (" + Math.round(v) + "°C): scalda o alza leggermente la pressione.");
        else if (v > tt.max) out.add("Gomme " + pos + " calde (" + Math.round(v) + "°C): alleggerisci il carico o scendi di 0.2–0.4 psi.");
        else out.add("Temperatura " + pos + " ok (" + Math.round(v) + "°C).");
    }

    private static String fmt(double v, int d){
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.ROOT);
        nf.setGroupingUsed(false);
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
