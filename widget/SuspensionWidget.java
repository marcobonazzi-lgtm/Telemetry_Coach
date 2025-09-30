package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Widget Sospensioni: mostra travel medio e picco per FL/FR/RL/RR.
 * - build(lap)           → 1 giro
 * - buildFromLaps(laps)  → media sessione (su tutti i giri validi)
 *
 * Nota: usa i canali travel se presenti; se assenti prova con ride height.
 * Se nessun canale è disponibile, mostra "--".
 */
public final class SuspensionWidget {

    // Ordine ruote come nel resto dei widget
    private static final Wheel[] WHEELS = new Wheel[]{
            new Wheel("FL", Channel.SUSP_TRAVEL_FL, Channel.RIDE_HEIGHT_FL),
            new Wheel("FR", Channel.SUSP_TRAVEL_FR, Channel.RIDE_HEIGHT_FR),
            new Wheel("RL", Channel.SUSP_TRAVEL_RL, Channel.RIDE_HEIGHT_RL),
            new Wheel("RR", Channel.SUSP_TRAVEL_RR, Channel.RIDE_HEIGHT_RR)
    };

    // ============ API pubbliche ============

    public static Node build(Lap lap) {
        GridPane g = grid4();
        Map<String, Stats> stats = perWheelStats(singletonList(lap));
        addCells(g, stats);
        return titled("Sospensioni (travel)", g);
    }

    public static Node buildFromLaps(List<Lap> laps) {
        GridPane g = grid4();
        Map<String, Stats> stats = perWheelStats(laps);
        addCells(g, stats);
        return titled("Sospensioni (media sessione)", g);
    }

    // ============ Logica ============

    private static Map<String, Stats> perWheelStats(List<Lap> laps) {
        Map<String, List<Double>> values = new LinkedHashMap<>();
        Map<String, List<Double>> peaks  = new LinkedHashMap<>();
        for (Wheel w : WHEELS) {
            values.put(w.name, new ArrayList<>());
            peaks.put(w.name,  new ArrayList<>());
        }

        for (Lap l : laps) {
            if (l == null || l.samples == null || l.samples.isEmpty()) continue;
            Map<String, List<Double>> perLap = new HashMap<>();
            for (Wheel w : WHEELS) perLap.put(w.name, new ArrayList<>());

            for (Sample s : l.samples) {
                for (Wheel w : WHEELS) {
                    double v = resolveValue(s, w); // mm (travel) oppure mm (ride height) come fallback
                    if (!Double.isNaN(v)) perLap.get(w.name).add(v);
                }
            }
            // aggrega lap → media e picco
            for (Wheel w : WHEELS) {
                List<Double> arr = perLap.get(w.name);
                if (arr.isEmpty()) continue;
                values.get(w.name).add(mean(arr));
                peaks.get(w.name).add(max(arr));
            }
        }

        Map<String, Stats> out = new LinkedHashMap<>();
        for (Wheel w : WHEELS) {
            List<Double> vs = values.get(w.name);
            List<Double> ps = peaks.get(w.name);
            out.put(w.name, new Stats(statMean(vs), statMean(ps)));
        }
        return out;
    }

    private static double resolveValue(Sample s, Wheel w) {
        // prova travel; se mancante prova ride height; altrimenti NaN
        Double v = s.values().get(w.travel);
        if (v == null || Double.isNaN(v)) {
            v = s.values().get(w.rideHeight);
        }
        return v == null ? Double.NaN : v;
    }

    // ============ UI helpers ============

    private static void addCells(GridPane g, Map<String, Stats> data) {
        // normalizza colore usando il picco medio della sessione per dare indicazione qualitativa
        double globalPeak = data.values().stream()
                .mapToDouble(st -> Double.isNaN(st.peak) ? 0 : st.peak)
                .max().orElse(0);

        int i = 0;
        for (Wheel w : WHEELS) {
            Stats st = data.getOrDefault(w.name, new Stats(Double.NaN, Double.NaN));
            StackPane cell = cell(w.name, st.mean, st.peak, globalPeak);
            g.add(cell, i % 2, i / 2);
            i++;
        }
    }

    private static StackPane cell(String name, double mean, double peak, double globalPeak) {
        Rectangle rect = new Rectangle(140, 110);
        rect.setArcWidth(8); rect.setArcHeight(8);
        rect.setStroke(Color.BLACK);

        Color c = colorForTravel(mean, peak, globalPeak);
        rect.setFill(c);

        String tMean = Double.isNaN(mean) ? "--" : String.format("%.0f mm", mean);
        String tPeak = Double.isNaN(peak) ? "--" : String.format("%.0f mm", peak);

        Label lbl = new Label(name + "\n" + tMean + " (avg)\n" + tPeak + " (peak)");
        lbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-alignment: center;");
        return new StackPane(rect, lbl);
    }

    private static GridPane grid4() {
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10);
        g.setPadding(new Insets(6));
        return g;
    }

    private static TitledPane titled(String title, Node content) {
        TitledPane tp = new TitledPane(title, content);
        tp.setCollapsible(false);
        return tp;
    }

    private static List<Lap> singletonList(Lap l) { return Collections.singletonList(l); }

    // ============ colori ============

    /**
     * Colore qualitativo:
     * - se non abbiamo un riferimento (globalPeak==0) → grigio
     * - 0–50% del picco globale → verde
     * - 50–80%              → gold
     * - >80%                → tomato (vicino a bumpstop)
     */
    private static Color colorForTravel(double mean, double peak, double globalPeak) {
        if (Double.isNaN(mean) && Double.isNaN(peak)) return Color.GRAY;
        if (globalPeak <= 0) return Color.LIGHTGRAY;

        double ref = Double.isNaN(peak) ? mean : peak;
        double ratio = clamp(ref / globalPeak, 0, 1);

        if (ratio < 0.50) return Color.LIMEGREEN;
        if (ratio < 0.80) return Color.GOLD;
        return Color.TOMATO;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ============ math helpers ============

    private static double mean(List<Double> a) {
        if (a == null || a.isEmpty()) return Double.NaN;
        double s = 0; int n = 0;
        for (double v : a) { if (!Double.isNaN(v)) { s += v; n++; } }
        return n == 0 ? Double.NaN : s / n;
    }

    private static double max(List<Double> a) {
        if (a == null || a.isEmpty()) return Double.NaN;
        double m = Double.NEGATIVE_INFINITY; boolean any=false;
        for (double v : a) { if (!Double.isNaN(v)) { m = Math.max(m, v); any=true; } }
        return any ? m : Double.NaN;
    }

    private static double statMean(List<Double> a) { return mean(a); }

    // ============ tipi ============

    private record Wheel(String name, Channel travel, Channel rideHeight) {}
    private record Stats(double mean, double peak) {}
}
