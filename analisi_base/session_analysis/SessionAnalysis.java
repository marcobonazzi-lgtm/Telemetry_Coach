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

    /** Note del coach aggregate e ordinate per frequenza (solo giri completi/validi). */
    public static List<String> aggregatedCoachNotes(List<Lap> laps) {
        List<Lap> good = filterCompleteValidLaps(laps);
        return CoachNotesAggregator.aggregate(good);
    }

    /** Media (semplice) delle temperature Middle per una ruota (FL/FR/RL/RR) su tutti i giri completi/validi. */
    public static double avgTyreTemp(List<Lap> laps, String wheel) {
        List<Lap> good = filterCompleteValidLaps(laps);
        return SessionMath.avgTyreTemp(good, wheel);
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
