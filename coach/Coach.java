package org.simulator.coach;

import org.simulator.canale.Lap;

import java.util.*;

import static org.simulator.coach.CoachCore.*;

/** Coach “base” usato in AllLapsView/AnalysisView: guida + dedup. */
public final class Coach {

    private Coach(){}

    // ====================== API ======================

    /** Note per un singolo giro (guida), con merge anti-duplicati. */
    public static List<String> generateNotes(Lap lap){
        List<Note> notes = new ArrayList<>();
        if (lap == null) return List.of();

        // Riconoscimento mezzo (invisibile alla UI, per futuri rami mezzo-specifici)
        VehicleKind kind = CoachCore.detectVehicleKind(lap);

        // Base (già include guida + FFB/cordoli)
        notes.addAll(CoachDriving.generateLapDrivingNotes(lap));

        // Merge anti-duplicati a concetto (sceglie priorità più alta)
        notes = mergeByConcept(notes);

        // Ordina per priorità (HIGH → LOW)
        notes.sort(java.util.Comparator.comparingInt((Note n) -> priorityRank(n.priority)).reversed());

        // Rendering finale + dedup testuale di rifinitura
        List<String> out = new ArrayList<>(notes.size());
        for (Note n: notes) out.add(n.render());
        return dedupByStem(out);
    }

    /** Riepilogo sessione con dedup (CoachSession) e regole extra già incluse lì. */
    public static List<String> generateSessionNotes(List<org.simulator.canale.Lap> laps){
        if (laps == null || laps.isEmpty()) return List.of();

        // Riconoscimento mezzo (invisibile alla UI)
        VehicleKind kind = CoachCore.detectVehicleKind(laps);

        // Base esistente
        List<String> base = CoachSession.generateSessionNotes(laps);

        // Reimporta base come Note LOW/SESSIONE per dedup concettuale
        List<Note> notes = new ArrayList<>();
        for (String s : base) notes.add(new Note(Priority.LOW, Category.SESSIONE, s));

        // Dedup per concetto e ordinamento
        notes = mergeByConcept(notes);
        notes.sort(java.util.Comparator.comparingInt((Note n) -> priorityRank(n.priority)).reversed());

        List<String> out = new ArrayList<>(notes.size());
        for (Note n: notes) out.add(n.render());
        return dedupByStem(out);
    }

    // =================== Dedup "a concetto" ===================

    /** Concetti espliciti che vogliamo unici. */
    private enum Concept { FFB_CLIP, PEDAL_STOMP, KERB_OVERUSE, COASTING, TRAIL_BRAKING, APEX_SLOW, OPEN_THROTTLE_DELAY, ABS_ACTIVE, TC_UP, TC_DOWN, TYRE_PRESS, TYRE_TEMP, BRAKE_DUCTS_OPEN, BRAKE_DUCTS_CLOSE, BRAKE_BIAS, DIFFERENTIAL, DAMPERS, ARB, AERO, OTHER }

    /** Restituisce il concetto della nota (FFB_CLIP / PEDAL_STOMP / KERB_OVERUSE / OTHER). */
    private static Concept conceptOf(Note n){
        String s = normText(n.text);

        // FFB clipping / saturazione
        if ((s.contains("ffb") || s.contains("force feedback")) &&
                (s.contains("clip") || s.contains("satur"))) return Concept.FFB_CLIP;

        // Pedal stomp / spike / frenate a colpi
        if ((s.contains("frenate a colpi") || s.contains("spike") || s.contains("stomp")) &&
                (s.contains("pedal") || s.contains("pedale") || s.contains("brake"))) return Concept.PEDAL_STOMP;

        // Cordoli
        if (s.contains("cordol") || s.contains("kerb")) return Concept.KERB_OVERUSE;

        // Coasting
        if (s.contains("coast")) return Concept.COASTING;

        // Trail-braking
        if (s.contains("trail") && s.contains("brak")) return Concept.TRAIL_BRAKING;

        // Apex lenti
        if (s.contains("apex") && (s.contains("lenti") || s.contains("lento") || s.contains("slow"))) return Concept.APEX_SLOW;

        // Ritardo apertura gas
        if (s.contains("apertura del gas") || (s.contains("ritardo") && s.contains("gas"))) return Concept.OPEN_THROTTLE_DELAY;

        // ABS spesso attivo
        if (s.contains("abs") && (s.contains("spesso") || s.contains("alto") || s.contains("frequente"))) return Concept.ABS_ACTIVE;

        // TC variazioni
        if (s.contains(" tc ") || s.contains("traction")) {
            if (s.contains("+1") || s.contains("piu") || s.contains("aumenta")) return Concept.TC_UP;
            if (s.contains("-1") || s.contains("meno") || s.contains("riduci")) return Concept.TC_DOWN;
        }

        // Pressioni / temperature gomme
        if (s.contains("psi") || s.contains("pression")) return Concept.TYRE_PRESS;
        if ((s.contains("gomme") && s.contains("cald")) || (s.contains("gomme") && s.contains("fredd")) || s.contains("temperat")) return Concept.TYRE_TEMP;

        // Ducts freni
        if (s.contains("duct")) {
            if (s.contains("apri")) return Concept.BRAKE_DUCTS_OPEN;
            if (s.contains("chiudi")) return Concept.BRAKE_DUCTS_CLOSE;
        }

        // Bias freni
        if (s.contains("bias") && s.contains("fren")) return Concept.BRAKE_BIAS;

        // Diff / sospensioni / aero
        if (s.contains("differenziale") || s.contains("preload") || s.contains("power")) return Concept.DIFFERENTIAL;
        if (s.contains("ammortizz") || s.contains("bump") || s.contains("rebound")) return Concept.DAMPERS;
        if (s.contains("barra") || s.contains("antirollio") || s.contains("arb")) return Concept.ARB;
        if (s.contains("ala") || s.contains("aero")) return Concept.AERO;

        return Concept.OTHER;
    }

    private static List<Note> mergeByConcept(List<Note> in){
        Map<Concept, Note> best = new LinkedHashMap<>();
        List<Note> others = new ArrayList<>();

        for (Note n : in) {
            Concept c = conceptOf(n);
            if (c == Concept.OTHER) {
                others.add(n);
                continue;
            }
            Note prev = best.get(c);
            if (prev == null || priorityRank(n.priority) > priorityRank(prev.priority)) best.put(c, n);
        }

        List<Note> out = new ArrayList<>(best.values());
        out.addAll(others);
        return out;
    }

    static int priorityRank(Priority p){
        return switch (p){
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    /** Normalizza testo per i matcher (casefold + semplificazione). */
    static String normText(String s){
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT);
        t = t.replace('à','a').replace('è','e').replace('é','e').replace('ì','i').replace('ò','o').replace('ù','u');
        t = t.replaceAll("[\\[\\]\\(\\)\\.,:;!\\?]", " ");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }
}
