package org.simulator.ui.time_line_view.Track.Curve;

import javafx.scene.Node;
import org.simulator.canale.Channel;
import org.simulator.ui.time_line_view.Track.AC.AcCornerProjector;
import org.simulator.ui.time_line_view.Track.AC.AcSection;
import org.simulator.ui.time_line_view.Track.TrackGeometry;

import java.util.*;
import java.util.function.Consumer;

public final class CurveManager {
    private final CurveOverlay overlay = new CurveOverlay();
    private final CurveStatsAggregator stats = new CurveStatsAggregator();
    private List<CurveSegment> curves = new ArrayList<>();
    private Consumer<CurveSegment> onSelected;
    private List<? extends Map<Channel, Double>> currentSamples;

    public void setOnCurveSelected(Consumer<CurveSegment> cb){
        this.onSelected = cb;
        overlay.setOnSelected(cb);
    }

    public void resetSession() {
        stats.reset();
        curves.clear();
    }

    public void updateSamples(List<? extends Map<Channel, Double>> samples) {
        this.currentSamples = samples;
        if (this.curves != null && !this.curves.isEmpty()) {
            stats.addLapSpeeds(samples, null, this.curves);
            updateColors(samples);
        }
    }

    public void buildFromGeometry(TrackGeometry geom,
                                  List<? extends Map<Channel, Double>> samples) {
        this.currentSamples = samples;
        CurveRecognition.Result res = CurveRecognition.detect(geom);
        this.curves = res.curves;
        overlay.setCurves(curves);

        stats.reset();
        stats.addLapSpeeds(samples, geom.xAxis(), curves);
        updateColors(samples);
    }

    public java.util.List<CurveSegment> useAcSections(TrackGeometry g,
                                                      java.util.List<AcSection> sections,
                                                      java.util.List<? extends java.util.Map<Channel, Double>> samples,
                                                      int lapIndex){
        this.currentSamples = samples;

        List<CurveSegment> rawCurves = AcCornerProjector.project(g, sections);
        List<CurveSegment> finalCurves = new ArrayList<>();

        for (int i=0; i<rawCurves.size(); i++) {
            CurveSegment old = rawCurves.get(i);
            String name = old.name();
            if ((name == null || name.isBlank()) && sections != null && i < sections.size()) {
                name = sections.get(i).name;
            }
            finalCurves.add(new CurveSegment(old.index(), name,
                    old.inPct(), old.outPct(),
                    old.iStart(), old.iApex(), old.iEnd(),
                    old.sStart(), old.sApex(), old.sEnd(),
                    old.xStart(), old.yStart(), old.xApex(), old.yApex(), old.xEnd(), old.yEnd(),
                    old.dir(), old.kappaPeak()));
        }
        this.curves = finalCurves;

        overlay.setMarkers(g, curves, null);
        stats.reset();
        stats.addLapSpeeds(samples, null, curves);
        updateColors(samples);

        return curves;
    }

    private void updateColors(List<? extends Map<Channel, Double>> samples) {
        overlay.colorize(idx -> {
            CurveSegment c = curves.stream().filter(cc -> cc.index()==idx).findFirst().orElse(null);
            if (c == null) return 0;
            // Usa la velocità minima per il colore (più rappresentativa della performance in curva)
            double val = calculateMinSpeed(c, samples);
            return stats.deltaForCurve(idx, val);
        });
    }

    public Node getOverlayNode(){ return overlay.node(); }
    public List<CurveSegment> getCurves(){ return curves; }

    public LinkedHashMap<String,Object> toInfoMap(CurveSegment c){
        LinkedHashMap<String,Object> m = new LinkedHashMap<>();
        m.put("Index", c.index());
        m.put("Nome", c.name());
        m.put("Direzione", c.dir().name());
        m.put("Lunghezza", String.format(java.util.Locale.US, "%.1f m", c.length()));
        m.put("Raggio", (Double.isInfinite(c.radius())? "∞" : String.format(java.util.Locale.US, "%.1f m", c.radius())));

        // Qui calcoliamo la velocità MINIMA (Apex) del giro corrente
        double realCurrentSpeed = calculateMinSpeed(c, currentSamples);
        int gear = calculateDominantGear(c, currentSamples);

        CurveStatsAggregator.CurveStat s = stats.getCurveStats(c.index(), realCurrentSpeed, gear);
        m.put("_META_stats", s);
        return m;
    }

    private int[] findTelemetryRange(CurveSegment c, List<? extends Map<Channel, Double>> samples) {
        if (samples == null || samples.isEmpty()) return null;
        int n = samples.size();
        int idxStart = (int)(c.inPct() * (n - 1));
        int idxEnd   = (int)(c.outPct() * (n - 1));

        if (c.inPct() <= 0 && c.outPct() <= 0) return null;

        idxStart = Math.max(0, Math.min(n - 1, idxStart));
        idxEnd   = Math.max(idxStart, Math.min(n - 1, idxEnd));

        if (idxEnd >= idxStart) return new int[]{idxStart, idxEnd};
        return null;
    }

    // --- MODIFICATO: CALCOLA MINIMO, NON MEDIA ---
    private double calculateMinSpeed(CurveSegment c, List<? extends Map<Channel, Double>> samples) {
        int[] range = findTelemetryRange(c, samples);
        if (range == null) return Double.NaN;

        double minS = Double.MAX_VALUE;
        boolean found = false;

        for (int i = range[0]; i <= range[1]; i++){
            Double v = samples.get(i).get(Channel.SPEED);
            if (v != null && !v.isNaN()){
                if(v < minS) minS = v;
                found = true;
            }
        }
        return found ? minS : Double.NaN;
    }

    private int calculateDominantGear(CurveSegment c, List<? extends Map<Channel, Double>> samples) {
        int[] range = findTelemetryRange(c, samples);
        if (range == null) return 0;

        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = range[0]; i <= range[1]; i++){
            Double g = samples.get(i).get(Channel.GEAR);
            if (g != null && !Double.isNaN(g)) {
                int gear = (int)Math.round(g);
                if (gear > 0) counts.put(gear, counts.getOrDefault(gear, 0) + 1);
            }
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
    }
}