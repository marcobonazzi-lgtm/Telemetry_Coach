package org.simulator.ui.compare_sessions_view;

import org.simulator.analisi_base.lap_analysis.LapAnalysis;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.ui.analysis_view.LapForceStatsAggregator;
import org.simulator.ui.time_line_view.DeltaAligner;
import org.simulator.ui.time_line_view.Track.Curve.CurveSegment;

import java.util.*;


/**
 * Coach comparativo SX (Base) vs DX (Comparata).
 */
class CompareCoach {

    public record Item(String name, double sx, double dx, double delta) {}

    public static class Result {
        public double deltaLapTime = Double.NaN; // Default NaN
        public String narrativeSummary;
        public List<Item> improvements = new ArrayList<>();
        public List<Item> regressions  = new ArrayList<>();
        public List<Item> topTable     = new ArrayList<>();
        public List<Item> setupDiffs   = new ArrayList<>();
        public List<CornerFinding> cornerFindings = new ArrayList<>();
        public String warning = null;
    }

    public record CornerFinding(
            int cornerId,
            String cornerName,
            double deltaTime,
            double deltaSpeedMin,
            int gearSx,
            int gearDx,
            String advice,
            int severity
    ) {}

    public Result compare(Lap sx, Lap dx, List<CurveSegment> trackCurves) {
        Result r = new Result();
        if (sx == null || dx == null) return r;

        // --- 1. Calcolo Delta Tempo ROBUSTO ---
        double tSx = sx.lapTime;
        double tDx = dx.lapTime;

        // Fallback per giri incompleti/non validi
        if (Double.isNaN(tSx) || tSx <= 0) tSx = calcLapTimeFromSamples(sx);
        if (Double.isNaN(tDx) || tDx <= 0) tDx = calcLapTimeFromSamples(dx);

        if (!Double.isNaN(tSx) && !Double.isNaN(tDx)) {
            r.deltaLapTime = tDx - tSx;
        }
        // ----------------------------------------

        // 2. Stats Generali
        analyzeGeneralStats(sx, dx, r);

        // 3. Check lunghezza
        double lenSx = safeDistNormalized(sx);
        double lenDx = safeDistNormalized(dx);
        if (Math.abs(lenSx - lenDx) > 300) {
            r.warning = "Attenzione: Lunghezze giri diverse (" + (int)lenSx + "m vs " + (int)lenDx + "m). Analisi curve disabilitata.";
        }

        // 4. Narrativa
        r.narrativeSummary = generateNarrative(r);

        // Check Giri Identici
        boolean areIdentical = !Double.isNaN(r.deltaLapTime) && Math.abs(r.deltaLapTime) < 0.001 && r.topTable.isEmpty();

        // 5. Analisi Curve
        if (trackCurves != null && !trackCurves.isEmpty() && r.warning == null && !areIdentical) {
            analyzeRealCorners(sx, dx, trackCurves, r);
        }

        return r;
    }

    private void analyzeGeneralStats(Lap sx, Lap dx, Result r) {
        Map<String, Double> a = new LinkedHashMap<>(LapAnalysis.basicStats(sx));
        Map<String, Double> b = new LinkedHashMap<>(LapAnalysis.basicStats(dx));
        try {
            a.putAll(LapForceStatsAggregator.build(sx));
            b.putAll(LapForceStatsAggregator.build(dx));
        } catch (Exception ignored) {}

        // Nota: Non sovrascriviamo r.deltaLapTime qui, usiamo quello calcolato sopra

        Set<String> common = new LinkedHashSet<>(a.keySet());
        common.retainAll(b.keySet());

        List<Item> ranked = new ArrayList<>();
        for (String k : common) {
            double va = safe(a.get(k)); double vb = safe(b.get(k));
            if (!Double.isFinite(va) || !Double.isFinite(vb)) continue;
            ranked.add(new Item(k, va, vb, vb - va));
        }
        categorizeItems(ranked, r);
    }

    private void categorizeItems(List<Item> ranked, Result r) {
        Set<String> higherIsBetter = Set.of("speed","velocità","apex speed","vmax","throttle","ers","grip","downforce");
        Set<String> lowerIsBetter  = Set.of("lap time","tempo giro","brake time","freno","fuel","consumo","tyre wear","wear","danno");

        for (Item it : ranked) {
            String n = it.name().toLowerCase(Locale.ROOT);
            double absDelta = Math.abs(it.delta());
            if (absDelta < 0.05 && !n.contains("time")) continue;
            if (n.contains("instant")) continue;

            boolean hib = containsAny(n, higherIsBetter);
            boolean lib = containsAny(n, lowerIsBetter);
            if (lib) hib = false;

            boolean improved;
            if (hib) improved = it.delta() > 0;
            else if (lib) improved = it.delta() < 0;
            else improved = absDelta >= 1.0;

            (improved ? r.improvements : r.regressions).add(it);
        }
        Comparator<Item> byAbsDesc = Comparator.comparingDouble(i -> -Math.abs(i.delta()));
        r.improvements.sort(byAbsDesc); r.regressions.sort(byAbsDesc);
        r.topTable.addAll(r.improvements.stream().limit(5).toList());
        r.topTable.addAll(r.regressions.stream().limit(5).toList());
        r.topTable.sort(byAbsDesc);

        List<String> setupWords = List.of("ala","wing","spring","molla","bar","arb","damper","bump","rebound","toe","caster","camber","press","ride","rake","diff","gear");
        r.setupDiffs = ranked.stream().filter(i -> containsAny(i.name().toLowerCase(Locale.ROOT), new HashSet<>(setupWords))).filter(i -> Math.abs(i.delta()) > 0.01).sorted(byAbsDesc).toList();
    }

    private String generateNarrative(Result r) {
        // Se il delta è ancora NaN, non possiamo dire nulla
        if (Double.isNaN(r.deltaLapTime)) return "Confronto tempi non disponibile (giri incompleti o dati mancanti).";

        boolean identicalTime = Math.abs(r.deltaLapTime) < 0.001;
        boolean noDiffs = r.topTable.isEmpty();

        if (identicalTime && noDiffs) {
            return "⚠ STESSO GIRO RILEVATO\n\nI due giri selezionati sono identici. Seleziona un giro diverso per il confronto.";
        }

        StringBuilder sb = new StringBuilder();
        if (Math.abs(r.deltaLapTime) < 0.05) sb.append("Tempi quasi identici. ");
        else if (r.deltaLapTime < 0) sb.append(String.format("Comparata (DX) più veloce di %.3fs. ", Math.abs(r.deltaLapTime)));
        else sb.append(String.format("Comparata (DX) più lenta di %.3fs. ", r.deltaLapTime));

        return sb.toString();
    }

    private void analyzeRealCorners(Lap sx, Lap dx, List<CurveSegment> curves, Result r) {
        try {
            double totalLen = safeDistNormalized(sx);

            for (CurveSegment c : curves) {
                double pStart = c.inPct();
                double pEnd = c.outPct();

                if (pStart <= 0 && pEnd <= 0) {
                    pStart = c.sStart() / totalLen;
                    pEnd = c.sEnd() / totalLen;
                }
                pStart = Math.max(0, Math.min(0.999, pStart));
                pEnd = Math.max(pStart + 0.001, Math.min(1.0, pEnd));

                double timeInCurveSx = getTimeSpentInSegment(sx, pStart, pEnd);
                double timeInCurveDx = getTimeSpentInSegment(dx, pStart, pEnd);

                if (Double.isNaN(timeInCurveSx) || Double.isNaN(timeInCurveDx)) continue;

                double dt = timeInCurveDx - timeInCurveSx;

                double vMinSx = getMinSpeedInSegment(sx, pStart, pEnd);
                double vMinDx = getMinSpeedInSegment(dx, pStart, pEnd);
                double dV = vMinDx - vMinSx;

                int gSx = getModeGearInSegment(sx, pStart, pEnd);
                int gDx = getModeGearInSegment(dx, pStart, pEnd);

                String advice = generateAdvice(dt, dV, gSx, gDx);
                int severity = severityFrom(dt);

                r.cornerFindings.add(new CornerFinding(
                        c.index(),
                        c.name(),
                        dt,
                        dV,
                        gSx, gDx,
                        advice,
                        severity
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double getTimeSpentInSegment(Lap l, double pStart, double pEnd) {
        if (l.samples == null || l.samples.isEmpty()) return Double.NaN;
        int n = l.samples.size();

        int iStart = (int)(pStart * (n - 1));
        int iEnd   = (int)(pEnd   * (n - 1));

        Double tStart = getSampleTime(l, iStart);
        Double tEnd   = getSampleTime(l, iEnd);

        if (tStart == null || tEnd == null) return Double.NaN;
        return tEnd - tStart;
    }

    private Double getSampleTime(Lap l, int idx) {
        if (idx < 0 || idx >= l.samples.size()) return null;
        Double t = l.samples.get(idx).values().get(Channel.LAP_TIME);
        if (t == null) t = l.samples.get(idx).values().get(Channel.TIME);
        return t;
    }

    private String generateAdvice(double dt, double dV, int gSx, int gDx) {
        if (Math.abs(dt) < 0.005) return "Tempo pari.";

        StringBuilder sb = new StringBuilder();
        boolean gained = dt < 0; // dt < 0 significa che DX ci ha messo MENO tempo -> GUADAGNO

        sb.append(gained ? "Guadagnati " : "Persi ")
                .append(String.format("%.3fs", Math.abs(dt)));

        if (Math.abs(dV) > 1.0) {
            sb.append(dV > 0 ? ", Apex più veloce" : ", Apex più lento");
            sb.append(String.format(" (%+.0f km/h)", dV));
        }

        if (gSx != gDx && gSx > 0 && gDx > 0) {
            sb.append(". Marcia ").append(gSx).append(" vs ").append(gDx);
        }

        return sb.toString();
    }

    private double getMinSpeedInSegment(Lap l, double pStart, double pEnd) {
        if (l.samples == null || l.samples.isEmpty()) return 0;
        int n = l.samples.size();
        int i0 = (int)(pStart * (n-1));
        int i1 = (int)(pEnd * (n-1));

        double min = Double.MAX_VALUE;
        for(int i=i0; i<=i1; i++) {
            Double v = l.samples.get(i).values().get(Channel.SPEED);
            if (v != null && v < min) min = v;
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    private int getModeGearInSegment(Lap l, double pStart, double pEnd) {
        if (l.samples == null || l.samples.isEmpty()) return 0;
        int n = l.samples.size();
        int i0 = (int)(pStart * (n-1));
        int i1 = (int)(pEnd * (n-1));

        Map<Integer, Integer> counts = new HashMap<>();
        for(int i=i0; i<=i1; i++) {
            Double g = l.samples.get(i).values().get(Channel.GEAR);
            if (g != null) {
                int gi = (int)Math.round(g);
                if (gi > 0) counts.put(gi, counts.getOrDefault(gi, 0) + 1);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
    }

    private static double safeDistNormalized(Lap l) {
        if(l.samples == null || l.samples.isEmpty()) return 1;
        double start = l.samples.get(0).distance();
        double end = l.samples.get(l.samples.size() - 1).distance();
        if (Double.isNaN(start)) start = 0;
        if (Double.isNaN(end)) end = 0;
        return Math.max(1.0, end - start);
    }

    private static int severityFrom(double dt) { return (Math.abs(dt)>0.5)?90 : (Math.abs(dt)>0.2)?70 : 30; }
    private static double safe(Double d) { return d==null?Double.NaN:d; }
    private static boolean containsAny(String name, Set<String> keys) {
        for (String k : keys) if (name.contains(k)) return true;
        return false;
    }


    // Metodo helper per calcolare il lap time dai sample se il metadato manca
    private double calcLapTimeFromSamples(Lap l) {
        if (l.samples == null || l.samples.isEmpty()) return Double.NaN;
        Double start = l.samples.get(0).values().get(Channel.TIME);
        Double end = l.samples.get(l.samples.size() - 1).values().get(Channel.TIME);
        if (start != null && end != null) return end - start;
        return Double.NaN;
    }
}