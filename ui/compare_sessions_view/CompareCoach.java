package org.simulator.ui.compare_sessions_view;

import org.simulator.analisi_base.lap_analysis.CornerDetector;
import org.simulator.analisi_base.lap_analysis.CornerKPIs;
import org.simulator.analisi_base.lap_analysis.LapAnalysis;
import org.simulator.canale.Lap;
import org.simulator.ui.analysis_view.LapForceStatsAggregator;
import org.simulator.ui.time_line_view.DeltaAligner;

import java.util.*;

/** Coach comparativo SX (base) vs DX (comparata). */
class CompareCoach {

    static record Item(String name, double sx, double dx, double delta) {}

    static class Result {
        double deltaLapTime; // dx - sx (negativo = migliorato)
        List<Item> improvements = new ArrayList<>();
        List<Item> regressions  = new ArrayList<>();
        List<Item> topTable     = new ArrayList<>();
        List<Item> setupDiffs   = new ArrayList<>();
        List<CornerFinding> cornerFindings = new ArrayList<>();
    }

    static record CornerFinding(
            int cornerId,
            double deltaTime,                // Δt sul segmento (dx-sx)
            CornerKPIs.KPIs left,            // KPIs SX (attuale)
            CornerKPIs.KPIs right,           // KPIs DX (comparata)
            int severity,
            String message
    ) {}

    Result compare(Lap sx, Lap dx) {
        Result r = new Result();
        if (sx == null || dx == null) return r;

        // ---- 1) Aggregati “di giro” ----
        Map<String, Double> a = new LinkedHashMap<>(LapAnalysis.basicStats(sx));
        Map<String, Double> b = new LinkedHashMap<>(LapAnalysis.basicStats(dx));
        a.putAll(LapForceStatsAggregator.build(sx));
        b.putAll(LapForceStatsAggregator.build(dx));

        String lapKey = findLapTimeKey(a, b);
        if (lapKey != null) r.deltaLapTime = safe(b.get(lapKey)) - safe(a.get(lapKey));

        Set<String> common = new LinkedHashSet<>(a.keySet()); common.retainAll(b.keySet());
        List<Item> ranked = new ArrayList<>();
        for (String k : common) {
            double va = safe(a.get(k)); double vb = safe(b.get(k));
            if (!Double.isFinite(va) || !Double.isFinite(vb)) continue;
            ranked.add(new Item(k, va, vb, vb - va));
        }

        Set<String> higherIsBetter = Set.of("speed","velocità","apex speed","vmax","throttle","ers","kers","drs","grip","downforce","power","torque");
        Set<String> lowerIsBetter  = Set.of("lap time","tempo giro","brake time","freno","fuel","consumo","tyre wear","tire wear","usura","wear","danno","danni","damage","damages","off-track","off track","penalty","penalties","cut","warnings","overheat","overheating");

        List<Item> improvements = new ArrayList<>(), regressions = new ArrayList<>();
        for (Item it : ranked) {
            String n = it.name().toLowerCase(Locale.ROOT);
            double absDelta = Math.abs(it.delta());
            if (absDelta < 0.5 || n.contains("instant")) continue;
            boolean hib = containsAny(n, higherIsBetter);
            boolean lib = containsAny(n, lowerIsBetter);
            if (lib) hib = false;
            boolean improved = hib ? it.delta() > 0 : lib ? it.delta() < 0 : absDelta >= 1.0;
            (improved ? improvements : regressions).add(it);
        }
        Comparator<Item> byAbsDesc = Comparator.comparingDouble(i -> -Math.abs(i.delta()));
        improvements.sort(byAbsDesc); regressions.sort(byAbsDesc);
        List<Item> top = new ArrayList<>();
        top.addAll(improvements.stream().limit(6).toList());
        top.addAll(regressions.stream().limit(6).toList());
        List<String> setupWords = List.of("ala","wing","spring","molla","bar","barra","arb","damper","bump","rebound","toe","caster","camber","campanatura","press","pressione","ride","rake","packers","heave","third","differ","diff","preload","gear","rapporti");
        List<Item> setup = ranked.stream().filter(i -> containsAny(i.name().toLowerCase(Locale.ROOT), new HashSet<>(setupWords))).sorted(byAbsDesc).toList();

        r.improvements = improvements; r.regressions = regressions; r.topTable = top; r.setupDiffs = setup;

        // ---- 2) Analisi per curva (Δt e KPIs) ----
        try {
            var detector = new CornerDetector();
            var kpicalc  = new CornerKPIs();
            var aligner  = new DeltaAligner();

            // Allineamento robusto: per l'analisi per curva usiamo SEMPRE l'asse %lap
            List<DeltaAligner.SeriesPoint> deltaSeries =
                    aligner.compute(sx.samples, dx.samples, DeltaAligner.AlignMode.PERCENT_LAP);
            if (deltaSeries.size() < 5) { // fallback hard
                r.cornerFindings = Collections.emptyList();
                return r;
            }

            // Sanity: se la serie è quasi costante/non monotona → fallback user-friendly
            if (isSeriesDegenerate(deltaSeries)) {
                r.cornerFindings = Collections.emptyList();
                return r;
            }

            var segments = detector.detect(sx);
            List<CornerFinding> findings = new ArrayList<>();
            double len = sx.samples.get(sx.samples.size()-1).distance();
            if (!Double.isFinite(len) || len<=0) len = 1.0;

            for (var seg : segments) {
                var aKpi = kpicalc.compute(sx, seg);
                var bKpi = kpicalc.compute(dx, seg);

                double p0 = Math.max(0, Math.min(1, seg.xStart/len));
                double p1 = Math.max(0, Math.min(1, seg.xEnd  /len));

                double dt = integrateDeltaInterpolated(deltaSeries, p0, p1);
                int sev   = severityFrom(dt, aKpi, bKpi);
                String msg = messageFrom(aKpi, bKpi, dt);
                findings.add(new CornerFinding(seg.id, dt, aKpi, bKpi, sev, msg));
            }
            findings.sort(Comparator.comparingInt(CornerFinding::cornerId));
            r.cornerFindings = findings;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return r;
    }

    private static boolean isSeriesDegenerate(List<DeltaAligner.SeriesPoint> s){
        if (s.size()<5) return true;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (var p : s){ if (Double.isFinite(p.dt)) { if (p.dt<min) min=p.dt; if (p.dt>max) max=p.dt; } }
        return !Double.isFinite(min) || (max-min) < 0.05; // meno di 0.05s di escursione = informazione insufficiente
    }

    private static String findLapTimeKey(Map<String,Double> a, Map<String,Double> b) {
        List<String> candidates = List.of("lap time [s]","lap time (s)","tempo giro [s]","laptime","lap time");
        for (String c : candidates) if (a.containsKey(c) && b.containsKey(c)) return c;
        for (String k : a.keySet())
            if (b.containsKey(k) && k.toLowerCase(Locale.ROOT).contains("lap") && k.toLowerCase(Locale.ROOT).contains("time"))
                return k;
        return null;
    }

    private static boolean containsAny(String name, Set<String> keys) {
        for (String k : keys) if (name.contains(k.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
    private static double safe(Double d) { return d == null ? Double.NaN : d; }

    private static double integrateDeltaInterpolated(List<DeltaAligner.SeriesPoint> s, double x0, double x1) {
        if (s == null || s.size() < 2) return Double.NaN;
        if (x1 < x0) { double tmp=x0; x0=x1; x1=tmp; }
        double dt0 = dtAt(s, x0);
        double dt1 = dtAt(s, x1);
        if (!Double.isFinite(dt0) || !Double.isFinite(dt1)) return Double.NaN;
        return dt1 - dt0;
    }
    private static double dtAt(List<DeltaAligner.SeriesPoint> s, double x){
        int n = s.size();
        if (n == 0) return Double.NaN;
        if (x <= s.get(0).x) return s.get(0).dt;
        if (x >= s.get(n-1).x) return s.get(n-1).dt;
        int lo=0, hi=n-1;
        while (hi-lo>1){
            int mid=(lo+hi)>>>1;
            if (s.get(mid).x >= x) hi=mid; else lo=mid;
        }
        var a = s.get(lo); var b = s.get(hi);
        double den = (b.x - a.x);
        if (den <= 0) return a.dt;
        double u = (x - a.x)/den;
        return a.dt + u*(b.dt - a.dt);
    }

    private static int severityFrom(double dt, CornerKPIs.KPIs a, CornerKPIs.KPIs b) {
        double sec = Math.abs(dt);
        if (sec > 0.5) return 90;
        if (sec > 0.2) return 70;
        if (sec > 0.1) return 50;
        return 30;
    }

    private static String messageFrom(CornerKPIs.KPIs sxK, CornerKPIs.KPIs dxK, double dt) {
        StringBuilder sb = new StringBuilder();
        if (dt > 0.02) sb.append(String.format("Persi %.2fs. ", dt));
        else if (dt < -0.02) sb.append(String.format("Guadagnati %.2fs. ", -dt));
        else sb.append("Tempo pari. ");

        double dvMin = dxK.minSpeedKmh() - sxK.minSpeedKmh();
        if (Double.isFinite(dvMin) && Math.abs(dvMin) >= 0.5){
            sb.append(String.format("Vmin Δ=%.1f km/h. ", dvMin));
        }

        double dBrake = dxK.brakePeak() - sxK.brakePeak();
        if (Double.isFinite(dBrake) && Math.abs(dBrake) >= 3){
            sb.append(dBrake > 0 ? "Freno DX più forte. " : "Freno DX più lieve. ");
        }

        if (dxK.firstThrottle() > 0 || sxK.firstThrottle() > 0) {
            double dTh = dxK.throttleTime() - sxK.throttleTime();
            if (Double.isFinite(dTh) && Math.abs(dTh) >= 0.2) {
                sb.append(dTh < 0 ? "Gas DX anticipato. " : "Gas DX ritardato. ");
            }
        }
        return sb.toString().trim();
    }
}
