package org.simulator.canale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Lap {
    public final int index;
    public final List<Sample> samples;
    /** LAP_TIME letto dal CSV (può essere NaN). */
    public final double lapTime;

    // cache invalid flag (lazy)
    private Boolean invalidCached = null;

    // ----------------- Soglie/euristiche per invalidazione -----------------
    /** Min. campioni consecutivi con >=3 gomme fuori pista per invalidare. */
    private static final int MIN_CONSEC_OFFTRACK_SAMPLES = 4;
    /** Quante gomme devono risultare “off track” per trattarlo come cut serio. */
    private static final int OFFTRACK_TIRES_THRESHOLD = 3;
    /** Min. campioni consecutivi con FLAGS != 0 per considerare reale un evento di infrazione/bandiera. */
    private static final int MIN_CONSEC_FLAGS_SAMPLES    = 5;
    /** Valore soglia per considerare IN_PIT attivo. */
    private static final double IN_PIT_THRESHOLD = 0.5;
    /** Porzione iniziale/finale del giro in cui ignorare IN_PIT per evitare falsi positivi (outlap/inlap). */
    private static final double PIT_IGNORE_EDGE_FRACTION = 0.10;

    public Lap(int index, List<Sample> samples) {
        this.index = index;
        this.samples = samples;
        this.lapTime = (samples == null || samples.isEmpty())
                ? Double.NaN
                : samples.get(samples.size() - 1).values().getOrDefault(Channel.LAP_TIME, Double.NaN);
    }

    /** Lap time "safe": usa LAP_TIME se presente, altrimenti TIME(last)-TIME(first). */
    public double lapTimeSafe() {
        if (!Double.isNaN(lapTime) && lapTime > 0) return lapTime;
        if (samples == null || samples.size() < 2) return Double.NaN;
        Double t0 = firstValid(samples, Channel.TIME);
        Double t1 = lastValid(samples, Channel.TIME);
        if (t0 == null || t1 == null) return Double.NaN;
        double dt = t1 - t0;
        return (dt > 0) ? dt : Double.NaN;
    }

    /** Distanza percorsa nel giro (se presente) = DISTANCE(last)-DISTANCE(first). */
    public double distanceDelta() {
        if (samples == null || samples.size() < 2) return Double.NaN;
        Double d0 = firstValid(samples, Channel.DISTANCE);
        Double d1 = lastValid(samples, Channel.DISTANCE);
        if (d0 == null || d1 == null) return Double.NaN;
        double dd = d1 - d0;
        return (dd >= 0) ? dd : Double.NaN;
    }

    /**
     * Vero se il giro è invalidato:
     * - LAP_INVALIDATED > 0.001 su almeno un sample (come prima)
     * - OPPURE >=3 gomme fuori pista per N campioni consecutivi (euristico robusto)
     * - OPPURE FLAGS != 0 per N campioni consecutivi (se il log lo fornisce)
     * - OPPURE IN_PIT > 0.5 nella parte centrale del giro (evitando inizio/fine giro)
     *
     * Non richiede allLaps.
     */
    public boolean isInvalid() {
        if (invalidCached != null) return invalidCached;
        boolean inv = computeInvalid();
        invalidCached = inv;
        return inv;
    }

    /**
     * Restituisce lo stato di validità del giro:
     * - "valido"
     * - "non valido" (invalidato)
     * - "giro non terminato" (tempo/distanza troppo bassi rispetto alla sessione, o dati insufficienti)
     */
    public String validityStatus(List<Lap> allLaps) {
        // dati minimi
        if (samples == null || samples.size() < 2) return "giro non terminato";

        // tempo e distanza del giro
        double t = lapTimeSafe();
        double d = distanceDelta();

        // se non abbiamo né tempo né distanza affidabili -> non terminato
        if (Double.isNaN(t) && Double.isNaN(d)) return "giro non terminato";

        // soglie assolute minime (robuste contro sessioni corte)
        if (!Double.isNaN(t) && t < 5.0) return "giro non terminato";   // < 5s: sicuramente incompleto
        if (!Double.isNaN(d) && d < 50.0) return "giro non terminato";  // < 50 m: sicuramente incompleto

        // confronta con la MEDIANA di sessione (più robusta della media)
        double tMed = medianLapTime(allLaps);
        double dMed = medianLapDistance(allLaps);

        // se il giro è troppo corto rispetto alla sessione → non terminato
        // soglie conservative: 0.7 su tempo, 0.6 su distanza
        if (!Double.isNaN(tMed) && !Double.isNaN(t) && t < tMed * 0.7) return "giro non terminato";
        if (!Double.isNaN(dMed) && !Double.isNaN(d) && d < dMed * 0.6) return "giro non terminato";

        // invalidazione esplicita/euristica
        if (isInvalid()) return "non valido";

        return "valido";
    }

    /** Restituisce true se il giro è valido e completo. */
    public boolean isComplete(List<Lap> allLaps) {
        return "valido".equals(validityStatus(allLaps));
    }

    // ----------------- helper interni -----------------

    private boolean computeInvalid() {
        if (samples == null || samples.isEmpty()) return false;

        // 1) Segnale nativo di invalidazione (come prima)
        for (Sample s : samples) {
            Double v = s.values().getOrDefault(Channel.LAP_INVALIDATED, 0.0);
            if (v != null && Math.abs(v) > 0.001) return true;
        }

        // 2) Uscita pista: >=3 gomme fuori per MIN_CONSEC_OFFTRACK_SAMPLES consecutivi
        int consecOff = 0;
        for (Sample s : samples) {
            Double off = s.values().getOrDefault(Channel.NUM_TIRES_OFF_TRACK, 0.0);
            if (off != null && off >= OFFTRACK_TIRES_THRESHOLD) {
                consecOff++;
                if (consecOff >= MIN_CONSEC_OFFTRACK_SAMPLES) return true;
            } else {
                consecOff = 0;
            }
        }

        // 3) FLAGS euristico: FLAGS != 0 per un certo numero di campioni consecutivi
        int consecFlags = 0;
        for (Sample s : samples) {
            Double f = s.values().get(Channel.FLAGS);
            // Se FLAGS non esiste, skip; se esiste e diverso da 0 → possibile infrazione/taglio/penalità
            if (f != null && Math.abs(f) > 0.0001) {
                consecFlags++;
                if (consecFlags >= MIN_CONSEC_FLAGS_SAMPLES) return true;
            } else {
                consecFlags = 0;
            }
        }

        // 4) Pit in mezzo al giro (evita falsi positivi su outlap/inlap)
        int n = samples.size();
        int startIdx = (int) Math.floor(n * PIT_IGNORE_EDGE_FRACTION);
        int endIdx   = (int) Math.ceil(n * (1.0 - PIT_IGNORE_EDGE_FRACTION));
        startIdx = Math.max(0, Math.min(startIdx, n - 1));
        endIdx   = Math.max(startIdx, Math.min(endIdx, n)); // end esclusivo

        for (int i = startIdx; i < endIdx; i++) {
            Sample s = samples.get(i);
            Double inPit = s.values().get(Channel.IN_PIT);
            if (inPit != null && inPit > IN_PIT_THRESHOLD) {
                return true;
            }
        }

        return false;
    }

    private static Double firstValid(List<Sample> samples, Channel ch) {
        for (Sample s : samples) {
            Double v = s.values().get(ch);
            if (v != null && !v.isNaN() && !v.isInfinite()) return v;
        }
        return null;
    }

    private static Double lastValid(List<Sample> samples, Channel ch) {
        for (int i = samples.size() - 1; i >= 0; i--) {
            Double v = samples.get(i).values().get(ch);
            if (v != null && !v.isNaN() && !v.isInfinite()) return v;
        }
        return null;
    }

    /** Mediana dei lap time "safe" (>0) nella sessione. */
    private static double medianLapTime(List<Lap> all) {
        if (all == null || all.isEmpty()) return Double.NaN;
        List<Double> v = new ArrayList<>();
        for (Lap l : all) {
            if (l == null || l.samples == null || l.samples.size() < 2) continue;
            double t = l.lapTimeSafe();
            if (!Double.isNaN(t) && t > 0) v.add(t);
        }
        if (v.isEmpty()) return Double.NaN;
        Collections.sort(v);
        int n = v.size();
        return (n % 2 == 1) ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2.0;
    }

    /** Mediana della distanza percorsa nel giro (>0) nella sessione. */
    private static double medianLapDistance(List<Lap> all) {
        if (all == null || all.isEmpty()) return Double.NaN;
        List<Double> v = new ArrayList<>();
        for (Lap l : all) {
            if (l == null || l.samples == null || l.samples.size() < 2) continue;
            double d = l.distanceDelta();
            if (!Double.isNaN(d) && d > 0) v.add(d);
        }
        if (v.isEmpty()) return Double.NaN;
        Collections.sort(v);
        int n = v.size();
        return (n % 2 == 1) ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2.0;
    }
}
