package org.simulator.setup.setup_advisor;

import org.simulator.canale.Lap;

import java.util.*;

/**
 * Entry point pubblico per l’Advisor Setup.
 * API e tipi pubblici INVARIATI. Implementazione resa più robusta e con dedup interno.
 */
public final class SetupAdvisor {

    private SetupAdvisor(){}

    // ====== Types (invariati) ======
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
            String notes                // riassunto umano leggibile
    ) {}

    /** Risultato dell’analisi: stile, metriche, commento. */
    public record Assessment(DriverStyle primary, StyleMetrics metrics, String summary){}

    // ====== API pubblica (invariata) ======
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

    // =========================
    // UI helper opzionali (invariati)
    // =========================
    public enum UiCategory {
        TYRES("Gomme & Pressioni"),
        BRAKES("Freni & Ducts"),
        DIFF_POWER("Differenziale & Trasmissione"),
        SUSPENSION("Sospensioni & Barre"),
        AERO("Aero"),
        ELECTRONICS("Elettronica (TC/ABS/ERS/Boost)"),
        CHASSIS("Telaio & Kerb"),
        DAMAGE("Danni"),
        OTHER("Altro");

        public final String title;
        UiCategory(String t){ this.title = t; }
    }

    /** Mappa raccomandazione -> categoria UI */
    public static UiCategory uiCategoryOf(Recommendation r){
        String area = (r.area()==null?"":r.area().toLowerCase(Locale.ROOT));
        String s = (r.message()==null?"":r.message().toLowerCase(Locale.ROOT));
        String t = (area + " " + s);
        if (t.contains("gomm") || t.contains("press") || t.contains("psi") || t.contains("temperat")) return UiCategory.TYRES;
        if (t.contains("fren") || t.contains("duct") || t.contains("raffredd")) return UiCategory.BRAKES;
        if (t.contains("diff") || t.contains("differenzial") || t.contains("preload") || t.contains("power") || t.contains("cambio") || t.contains("rapporto") || t.contains("marcia") || t.contains("engine brake")) return UiCategory.DIFF_POWER;
        if (t.contains("ammortizz") || t.contains("sospension") || t.contains("bump") || t.contains("rebound") || t.contains("barra") || t.contains("antirollio") || t.contains("arb")) return UiCategory.SUSPENSION;
        if (t.contains("aero") || t.contains("ala") || t.contains("downforce")) return UiCategory.AERO;
        if (t.contains("tc") || t.contains("traction") || t.contains("abs") || t.contains("ers") || t.contains("boost") || t.contains("mappa")) return UiCategory.ELECTRONICS;
        if (t.contains("kerb") || t.contains("cordol") || t.contains("telaio")) return UiCategory.CHASSIS;
        if (t.contains("danno") || t.contains("damage")) return UiCategory.DAMAGE;
        return UiCategory.OTHER;
    }

    /** Filtra per categorie UI (dedup incluso). */
    public static List<Recommendation> filterByUiCategories(List<Recommendation> recs, Set<UiCategory> enabled){
        if (recs == null || recs.isEmpty()) return List.of();
        List<Recommendation> ded = dedup(recs);
        List<Recommendation> out = new ArrayList<>();
        for (Recommendation r : ded){
            if (enabled == null || enabled.isEmpty() || enabled.contains(uiCategoryOf(r))){
                out.add(r);
            }
        }
        return out;
    }

    // ======= JavaFX Pane pronto (invariato) =======
    public static javafx.scene.Node buildSetupPane(List<Recommendation> recs){
        // (identico al tuo: lasciato invariato)
        return SetupAdvisorPaneBuilder.buildSetupPane(recs);
    }
}
