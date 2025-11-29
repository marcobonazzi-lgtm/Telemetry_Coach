package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.setup.setup_advisor.VehicleTraits;
import org.simulator.widget.oggetti3D.ShockAbsorber3D;

import java.util.*;

public final class SuspensionWidget {

    private static final String MODEL_PATH = "/shock.obj";

    // Stili Base
    private static final String BG_DARK = "-fx-background-color:#0f172a;";
    private static final String CARD_BASE = "-fx-background-color:#1e293b; -fx-background-radius:8;";

    // Colori Stato
    private static final String COL_OK = "#22c55e";
    private static final String COL_WARN = "#facc15";
    private static final String COL_CRIT = "#ef4444";

    private static final Color FX_COL_OK = Color.web(COL_OK);
    private static final Color FX_COL_WARN = Color.web(COL_WARN);
    private static final Color FX_COL_CRIT = Color.web(COL_CRIT);

    private static final Wheel[] WHEELS = new Wheel[]{
            new Wheel("FL", Channel.SUSP_TRAVEL_FL, Channel.RIDE_HEIGHT_FL),
            new Wheel("FR", Channel.SUSP_TRAVEL_FR, Channel.RIDE_HEIGHT_FR),
            new Wheel("RL", Channel.SUSP_TRAVEL_RL, Channel.RIDE_HEIGHT_RL),
            new Wheel("RR", Channel.SUSP_TRAVEL_RR, Channel.RIDE_HEIGHT_RR)
    };

    public static TitledPane build(Lap lap) {
        List<Lap> laps = (lap == null) ? Collections.emptyList() : Collections.singletonList(lap);
        return buildInternal(laps, "Sospensioni (Giro)");
    }

    public static TitledPane buildFromLaps(List<Lap> laps) {
        return buildInternal(laps, "Sospensioni (Media Sessione)");
    }

    private static TitledPane buildInternal(List<Lap> laps, String title) {
        VehicleTraits traits = VehicleTraits.detect(laps);
        Map<String, Stats> stats = calculateStats(laps);

        boolean isRideHeight = detectModeIsRideHeight(laps);
        String modeLabel = isRideHeight ? "Ride Height (mm)" : "Travel (mm)";

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.setStyle(BG_DARK);

        ColumnConstraints colC = new ColumnConstraints();
        colC.setPercentWidth(50);
        grid.getColumnConstraints().addAll(colC, colC);

        RowConstraints rowC = new RowConstraints();
        rowC.setPercentHeight(50);
        grid.getRowConstraints().addAll(rowC, rowC);

        int i = 0;
        for (Wheel w : WHEELS) {
            Stats s = stats.getOrDefault(w.name, new Stats(Double.NaN, Double.NaN));
            Node card = createCard(w.name, s, traits, isRideHeight);
            grid.add(card, i % 2, i / 2);
            i++;
        }

        TitledPane tp = new TitledPane(title + " • " + modeLabel, grid);
        tp.setCollapsible(false);
        tp.setAnimated(false);
        tp.setStyle("-fx-base: #0f172a; -fx-box-border: transparent; -fx-text-fill: white; -fx-font-weight:bold;");
        tp.setMaxHeight(Double.MAX_VALUE);

        return tp;
    }

    private static Node createCard(String name, Stats s, VehicleTraits traits, boolean isRideHeight) {
        HBox cardLayout = new HBox(10);
        cardLayout.setAlignment(Pos.CENTER_LEFT);
        cardLayout.setPadding(new Insets(10));

        VBox textPart = new VBox(4);
        textPart.setAlignment(Pos.CENTER_LEFT);
        // Il testo prende lo spazio necessario, il 3D prende il resto
        HBox.setHgrow(textPart, Priority.SOMETIMES);

        Label lblName = new Label(name);
        lblName.setStyle("-fx-text-fill:#94a3b8; -fx-font-weight:bold; -fx-font-size:11px;");

        String borderColorString = "#475569";
        Color statusColor3D = Color.GRAY;
        String mainTextColor = "#e2e8f0";

        if (!Double.isNaN(s.mean)) {
            if (isRideHeight) {
                // Implementazione della logica di calibrazione di SuspensionsTL (RideMin)
                double rhMin;
                switch (traits.category) {
                    case FORMULA:
                    case PROTOTYPE:
                        rhMin = 0.0;
                        break;
                    case GT:
                        rhMin = 30.0;
                        break;
                    case ROAD:
                        rhMin = 90.0;
                        break;
                    default:
                        rhMin = 20.0; // Fallback
                }
                double rhWarnThreshold = rhMin + 15.0; // Soglia Giallo (come in SuspensionsTL)

                if (s.mean <= rhMin) {
                    borderColorString = COL_CRIT; statusColor3D = FX_COL_CRIT; mainTextColor = COL_CRIT;
                } else if (s.mean < rhWarnThreshold) {
                    borderColorString = COL_WARN; statusColor3D = FX_COL_WARN; mainTextColor = COL_WARN;
                } else {
                    borderColorString = COL_OK; statusColor3D = FX_COL_OK; mainTextColor = COL_OK;
                }
            } else {
                // *** Travel (Corsa Sospensione) ***
                // Implementazione della logica percentuale di SuspensionsTL sul valore di picco (s.peak)
                // Usiamo calculateCompressionFactor per ottenere un valore normalizzato 0-1 basato sul picco
                double travelNorm = calculateCompressionFactor(s.peak, false, traits);

                if (travelNorm > 0.95) { // Oltre il 95% della corsa massima
                    borderColorString = COL_CRIT; statusColor3D = FX_COL_CRIT; mainTextColor = COL_CRIT;
                } else if (travelNorm > 0.80) { // Oltre l'80% della corsa massima
                    borderColorString = COL_WARN; statusColor3D = FX_COL_WARN; mainTextColor = COL_WARN;
                } else {
                    borderColorString = COL_OK; statusColor3D = FX_COL_OK; mainTextColor = COL_OK;
                }
            }
        }

        Label lblMean = new Label(Double.isNaN(s.mean) ? "--" : String.format("%.0f", s.mean));
        lblMean.setStyle("-fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:24px; -fx-text-fill:" + mainTextColor + ";");

        Label lblPeak = new Label(Double.isNaN(s.peak) ? "Peak: --" : String.format("Peak: %.0f", s.peak));
        lblPeak.setStyle("-fx-text-fill:#64748b; -fx-font-size:10px;");

        textPart.getChildren().addAll(lblName, lblMean, lblPeak);

        // Larghezza 220 (molto più largo), Altezza 100 (un po' più alto)
        ShockAbsorber3D shock3D = new ShockAbsorber3D(MODEL_PATH, 220, 100);

        double compressionFactor = calculateCompressionFactor(isRideHeight ? s.mean : s.peak, isRideHeight, traits);
        shock3D.updateState(compressionFactor, statusColor3D);

        cardLayout.getChildren().addAll(textPart, shock3D);

        cardLayout.setStyle(CARD_BASE + "-fx-border-color:" + borderColorString + "; -fx-border-width:2; -fx-border-radius:8;");
        cardLayout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        GridPane.setFillWidth(cardLayout, true);
        GridPane.setFillHeight(cardLayout, true);

        return cardLayout;
    }

    private static double calculateCompressionFactor(double value, boolean isRideHeight, VehicleTraits traits) {
        if (Double.isNaN(value)) return 0.0;
        boolean isFormula = traits != null && traits.category == VehicleTraits.Category.FORMULA;
        double maxRange;

        if (isRideHeight) {
            maxRange = isFormula ? 50.0 : 120.0;
            double normalized = Math.min(1.0, Math.max(0.0, value / maxRange));
            return 1.0 - normalized;
        } else {
            maxRange = isFormula ? 60.0 : 160.0;
            return Math.min(1.0, Math.max(0.0, value / maxRange));
        }
    }

    // --- Helpers Standard ---
    private static boolean detectModeIsRideHeight(List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return false;
        for (Lap l : laps) {
            if (l.samples != null && !l.samples.isEmpty()) {
                if (l.samples.get(0).values().containsKey(Channel.SUSP_TRAVEL_FL)) return false;
                if (l.samples.get(0).values().containsKey(Channel.RIDE_HEIGHT_FL)) return true;
            }
        }
        return false;
    }

    private static Map<String, Stats> calculateStats(List<Lap> laps) {
        Map<String, List<Double>> vals = new HashMap<>();
        Map<String, List<Double>> peaks = new HashMap<>();
        for (Wheel w : WHEELS) {
            vals.put(w.name, new ArrayList<>());
            peaks.put(w.name, new ArrayList<>());
        }

        if (laps != null) {
            for (Lap l : laps) {
                if (l == null || l.samples == null) continue;
                for (Wheel w : WHEELS) {
                    List<Double> lapVals = new ArrayList<>();
                    for (Sample s : l.samples) {
                        Double v = resolveValue(s, w);
                        if (v != null && !Double.isNaN(v)) lapVals.add(v);
                    }
                    if (!lapVals.isEmpty()) {
                        vals.get(w.name).add(mean(lapVals));
                        peaks.get(w.name).add(isRideHeightChannel(l, w) ? min(lapVals) : max(lapVals));
                    }
                }
            }
        }

        Map<String, Stats> res = new HashMap<>();
        for (Wheel w : WHEELS) res.put(w.name, new Stats(mean(vals.get(w.name)), mean(peaks.get(w.name))));
        return res;
    }

    private static Double resolveValue(Sample s, Wheel w) {
        Double v = s.values().get(w.travel);
        if (v == null || Double.isNaN(v)) v = s.values().get(w.rideHeight);
        return v;
    }

    private static boolean isRideHeightChannel(Lap l, Wheel w) {
        if (l.samples.isEmpty()) return false;
        return !l.samples.get(0).values().containsKey(w.travel) && l.samples.get(0).values().containsKey(w.rideHeight);
    }

    private static double mean(List<Double> a) {
        if (a == null || a.isEmpty()) return Double.NaN;
        double s = 0; for (double v : a) s += v;
        return s / a.size();
    }
    private static double max(List<Double> a) {
        if (a == null || a.isEmpty()) return Double.NaN;
        double m = -Double.MAX_VALUE; for (double v : a) m = Math.max(m, v);
        return m;
    }
    private static double min(List<Double> a) {
        if (a == null || a.isEmpty()) return Double.NaN;
        double m = Double.MAX_VALUE; for (double v : a) m = Math.min(m, v);
        return m;
    }
    private record Wheel(String name, Channel travel, Channel rideHeight) {}
    private record Stats(double mean, double peak) {}
}