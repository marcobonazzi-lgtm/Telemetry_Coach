package org.simulator.analisi_base.session_analysis;

import org.simulator.canale.Lap;

import java.util.*;

/** Aggregazione delle note del coach per frequenza, con raggruppamento “soft” e ordinamento stabile. */
final class CoachNotesAggregator {

    private CoachNotesAggregator() {}

    static List<String> aggregate(List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return List.of();

        // stem -> exemplar text + count
        Map<String, Counter> freq = new LinkedHashMap<>();

        for (Lap lap : laps) {
            if (lap == null) continue;

            // Evita doppi conteggi nello stesso giro (per varianti minime)
            Set<String> seenThisLap = new HashSet<>();

            for (String note : org.simulator.coach.Coach.generateNotes(lap)) {
                String stem = stemOf(note);
                if (!seenThisLap.add(stem)) continue; // già contata su questo giro

                Counter c = freq.get(stem);
                if (c == null) {
                    c = new Counter(note);
                    freq.put(stem, c);
                } else {
                    c.count++;
                    // Mantieni l'esempio testuale "migliore": preferisci quello più corto (più sintetico)
                    if (note.length() < c.exemplar.length()) c.exemplar = note;
                }
            }
        }

        // Ordina: frequenza ↓, categoria (peso) ↓, testo ↑
        return freq.values().stream()
                .sorted((a, b) -> {
                    int byCnt = Integer.compare(b.count, a.count);
                    if (byCnt != 0) return byCnt;
                    int byCat = Integer.compare(categoryWeight(b.exemplar), categoryWeight(a.exemplar));
                    if (byCat != 0) return byCat;
                    return a.exemplar.compareToIgnoreCase(b.exemplar);
                })
                .map(c -> c.exemplar)
                .toList();
    }

    // ---- helpers ----

    /** Contenitore frequenza + testo rappresentativo. */
    private static final class Counter {
        int count = 1;
        String exemplar;
        Counter(String exemplar){ this.exemplar = exemplar; }
    }

    /** Normalizzazione “soft” allineata al dedup del coach (senza dipendenze). */
    private static String stemOf(String s){
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT);

        // Togli icone/spazi doppi e punteggiatura “debole”
        t = t.replaceAll("[^a-zàèéìòù0-9\\[\\]]+", " ").trim();

        // Rimuovi suffissi comuni che non cambiano il senso (come nel dedup del coach)
        t = t.replaceAll("( molto| spesso| in sessione| in [0-9]+/[0-9]+ giri| avg .*\\))$", "");

        // Comprimi spazi
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    /** Pesa la categoria estratta dal prefisso [Categoria]. Più alto = più importante nel tie-breaker. */
    private static int categoryWeight(String note){
        // Le note del coach partono con "[Guida] ", "[Gomme] ", ecc.
        // Mappa di priorità “soft” per l’ordinamento quando la frequenza è uguale.
        // Puoi ritoccare i pesi in base al tuo prodotto.
        final Map<String,Integer> w = Map.of(
                "[Guida]",      7,
                "[Trasmissione]",6,
                "[Freni]",      5,
                "[Gomme]",      4,
                "[FFB]",        3,
                "[Danni]",      2,
                "[Sessione]",   1
        );
        int lb = note.indexOf('[');
        int rb = note.indexOf(']');
        if (lb == 0 && rb > lb) {
            String tag = note.substring(0, rb+1);
            Integer v = w.get(tag);
            if (v != null) return v;
        }
        return 0;
    }
}
