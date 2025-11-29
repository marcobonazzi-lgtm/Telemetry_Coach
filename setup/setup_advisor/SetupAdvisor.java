package org.simulator.setup.setup_advisor;

import org.simulator.canale.Lap;

import java.util.*;

/**
 * Entry point pubblico per l’Advisor Setup.
 */
public final class SetupAdvisor {

    private SetupAdvisor(){}

    // ====== Types ======
    public enum Severity { LOW, MEDIUM, HIGH }
    public record Recommendation(Severity sev, String area, String message) {}

    public enum DriverStyle { SMOOTH, NEUTRAL, AGGRESSIVE }

    /** Metriche dettagliate sullo stile/tecnica rilevate dal giro. */
    public record StyleMetrics(
            double thrOscPct,           // oscillazioni gas (CoachCore)
            double steerHarshPct,       // sterzate brusche/inversioni (CoachCore)
            boolean oversteerLike,      // proxy di sovrasterzo (CoachCore)
            double brakeStompPct,       // % eventi "stomp" sul freno
            double throttleBrakeOverlap,// % tempo con gas & freno insieme
            double coastingPct,         // % tempo senza gas e senza freno
            double steerReversalsPerMin,// inversioni sterzo / min
            double ffbClipPct,          // % tempo in clipping FFB
            double avgTyreTemp,         // media 4 gomme
            double avgBrakeTemp,        // media 4 freni
            String notes
    ) {}

    /** Risultato dell’analisi: stile, metriche, commento. */
    public record Assessment(DriverStyle primary, StyleMetrics metrics, String summary){}

    // ====== API pubblica======
    public static DriverStyle analyzeStyle(List<Lap> laps){
        return SetupStyleAnalysis.analyzeStyle(laps);
    }

    public static Assessment analyzeStyleDetailed(List<Lap> laps){
        return SetupStyleAnalysis.analyzeStyleDetailed(laps);
    }

    public static List<Recommendation> forLap(Lap lap, DriverStyle style){
        return dedup(SetupLapRecommender.forLap(lap, style));
    }

    public static List<Recommendation> forSession(List<Lap> laps, DriverStyle style){
        return dedup(SetupSessionRecommender.forSession(laps, style));
    }

    public static List<Recommendation> forSession(List<Lap> laps){
        return dedup(SetupSessionRecommender.forSession(laps));
    }

    // ====== Dedup interno (per area/contenuto) ======
    private static List<Recommendation> dedup(List<Recommendation> in){
        if (in == null || in.isEmpty()) return in;
        Map<String, Recommendation> best = new LinkedHashMap<>();
        for (Recommendation r : in){
            String key = conceptKey(r);
            Recommendation prev = best.get(key);
            if (prev == null || severityRank(r.sev) > severityRank(prev.sev)){
                best.put(key, r);
            }
        }
        // Ordina per severità (HIGH→LOW) mantenendo l'ordine d'inserimento per pari livello
        List<Recommendation> out = new ArrayList<>(best.values());
        out.sort((a,b) -> Integer.compare(severityRank(b.sev), severityRank(a.sev)));
        return out;
    }

    private static int severityRank(Severity s){
        return switch (s){
            case HIGH -> 3; case MEDIUM -> 2; case LOW -> 1;
        };
    }

    /** Normalizza messaggi in una chiave concettuale robusta. */
    private static String conceptKey(Recommendation r){
        String area = (r.area()==null?"":r.area().toLowerCase(Locale.ROOT));
        String s = (r.message()==null?"":r.message().toLowerCase(Locale.ROOT));
        s = s.replace('à','a').replace('è','e').replace('é','e').replace('ì','i').replace('ò','o').replace('ù','u');
        s = s.replaceAll("[\\[\\]\\(\\)\\.,:;!\\?]"," ").replaceAll("\\s+"," ").trim();

        // gruppi principali — evita ripetizioni tra mappature diverse che propongono la stessa azione
        if (s.contains(" tc ") || s.contains("traction")) {
            if (s.contains("+1") || s.contains("aumenta") || s.contains("piu")) return area+"|TC_UP";
            if (s.contains("-1") || s.contains("riduci") || s.contains("meno")) return area+"|TC_DOWN";
            return area+"|TC";
        }
        if (s.contains("abs")) return area+"|ABS";
        if (s.contains("psi") || s.contains("pressioni") || s.contains("pressione")) return area+"|TYRE_PRESSURE";
        if (s.contains("gomme fredde") || s.contains("gomme calde") || s.contains("temperat") ) return area+"|TYRE_TEMP";
        if (s.contains("duct") || s.contains("raffredd") ) {
            if (s.contains("apri")) return area+"|BRAKE_DUCTS_OPEN";
            if (s.contains("chiudi")) return area+"|BRAKE_DUCTS_CLOSE";
            return area+"|BRAKE_DUCTS";
        }
        if (s.contains("freni") && s.contains("bias")) return area+"|BRAKE_BIAS";
        if (s.contains("differenziale") || s.contains("preload") || s.contains("power")) return area+"|DIFF";
        if (s.contains("barra") || s.contains("antirollio") || s.contains("arb")) return area+"|ARB";
        if (s.contains("ammortizz") || s.contains("bump") || s.contains("rebound")) return area+"|DAMPERS";
        if (s.contains("ala") || s.contains("aero")) return area+"|AERO";
        if (s.contains("curva gas") || s.contains("mappa gas") || s.contains("mappe")) return area+"|THROTTLE_MAP";
        if (s.contains("trail") && s.contains("brak")) return area+"|TRAIL_BRAKING";
        if (s.contains("kerb") || s.contains("cordol")) return area+"|KERB";
        if (s.contains("coast") ) return area+"|COASTING";
        if (s.contains("ffb") || s.contains("force feedback")) return area+"|FFB";
        // fallback: area + inizio messaggio (5 parole)
        String[] tok = s.split(" ");
        int n = Math.min(tok.length, 5);
        StringBuilder sb = new StringBuilder(area).append("|");
        for (int i=0;i<n;i++){ sb.append(tok[i]).append(' '); }
        return sb.toString().trim();
    }

}
