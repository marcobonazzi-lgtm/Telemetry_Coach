package org.simulator.analisi_base.session_analysis;

import org.simulator.canale.Lap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Facade per analisi di sessione. API pubblica invariata. */
public final class SessionAnalysis {

    private SessionAnalysis() {}

    /** Statistiche medie su tutta la sessione (solo giri completi/validi). */
    public static Map<String, Double> averageStats(List<Lap> laps) {
        List<Lap> good = filterCompleteValidLaps(laps);
        return SessionAverages.compute(good);
    }

    // -------------------- helper --------------------

    /** Ritorna solo i giri "valido" secondo la logica di Lap.validityStatus(...) */
    private static List<Lap> filterCompleteValidLaps(List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return List.of();
        List<Lap> out = new ArrayList<>(laps.size());
        for (Lap l : laps) {
            if (l == null) continue;
            if (l.isComplete(laps)) out.add(l);
        }
        return out;
    }
}
