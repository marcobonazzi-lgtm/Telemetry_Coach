package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.setup.setup_advisor.VehicleTraits;

import java.util.List;
import java.util.Locale;

public class TyrePressureWidget {

    // Stili Base
    private static final String BG_DARK = "-fx-background-color:#0f172a;";
    private static final String CARD_BASE = "-fx-background-color:#1e293b; -fx-background-radius:8; -fx-border-color:#334155; -fx-border-radius:8;";

    public static TitledPane build(Lap lap) {
        List<Lap> laps = (lap == null) ? java.util.Collections.emptyList() : java.util.Collections.singletonList(lap);
        return buildInternal(laps, "Pressioni (Giro)");
    }

    public static TitledPane buildFromLaps(List<Lap> laps) {
        return buildInternal(laps, "Pressioni (Media Sessione)");
    }

    // --- CORE UI ---

    private static TitledPane buildInternal(List<Lap> laps, String title) {
        VehicleTraits traits = VehicleTraits.detect(laps);

        double fl = avgLaps(laps, Channel.TIRE_PRESSURE_FL);
        double fr = avgLaps(laps, Channel.TIRE_PRESSURE_FR);
        double rl = avgLaps(laps, Channel.TIRE_PRESSURE_RL);
        double rr = avgLaps(laps, Channel.TIRE_PRESSURE_RR);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.setStyle(BG_DARK);

        ColumnConstraints colC = new ColumnConstraints(); colC.setPercentWidth(50);
        grid.getColumnConstraints().addAll(colC, colC);
        RowConstraints rowC = new RowConstraints(); rowC.setPercentHeight(50);
        grid.getRowConstraints().addAll(rowC, rowC);

        grid.add(createCard("FL", fl, traits), 0, 0);
        grid.add(createCard("FR", fr, traits), 1, 0);
        grid.add(createCard("RL", rl, traits), 0, 1);
        grid.add(createCard("RR", rr, traits), 1, 1);

        TitledPane tp = new TitledPane(title, grid);
        tp.setCollapsible(false);
        tp.setAnimated(false);
        tp.setStyle("-fx-base: #0f172a; -fx-box-border: transparent; -fx-text-fill: white; -fx-font-weight:bold;");
        tp.setMaxHeight(Double.MAX_VALUE);

        return tp;
    }

    private static Node createCard(String name, double psi, VehicleTraits traits) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10));
        card.setStyle(CARD_BASE);

        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        GridPane.setFillWidth(card, true);
        GridPane.setFillHeight(card, true);

        // --- 1. BARRA VISUALE (Modificata) ---
        // Aumentata la larghezza (barW) da 12 a 32 per renderla più spessa
        // Aumentata leggermente l'altezza (barH) da 45 a 48
        double barW = 32;
        double barH = 48;
        Color barColor = getColor(psi, traits);
        Rectangle bar = new Rectangle(barW, barH, barColor);
        bar.setArcWidth(8);
        bar.setArcHeight(8);
        // --- 2. DATI TESTUALI ---
        VBox texts = new VBox(2);
        texts.setAlignment(Pos.CENTER_LEFT);

        Label lblName = new Label(name);
        lblName.setStyle("-fx-text-fill:#94a3b8; -fx-font-weight:800; -fx-font-size:11px;");

        Label lblVal = new Label();
        if (Double.isNaN(psi)) {
            lblVal.setText("--");
            lblVal.setStyle("-fx-text-fill:#64748b; -fx-font-size:22px; -fx-font-weight:bold; -fx-font-family:'Monospaced';");
        } else {
            lblVal.setText(String.format(Locale.ROOT, "%.1f psi", psi));
            String hexColor = toHex(barColor);
            lblVal.setStyle("-fx-text-fill:" + hexColor + "; -fx-font-size:22px; -fx-font-weight:bold; -fx-font-family:'Monospaced';");
        }

        texts.getChildren().addAll(lblName, lblVal);

        card.getChildren().addAll(bar, texts);
        return card;
    }

    // --- MATH LOGIC ---

    private static double avgLaps(List<Lap> laps, Channel ch) {
        if (laps == null || laps.isEmpty()) return Double.NaN;
        double s = 0; int n = 0;
        for (Lap l : laps) {
            double v = avgLap(l, ch);
            if (!Double.isNaN(v)) { s += v; n++; }
        }
        return n > 0 ? s / n : Double.NaN;
    }

    private static double avgLap(Lap lap, Channel ch) {
        if (lap == null || lap.samples == null) return Double.NaN;
        double s = 0; int n = 0;
        for (Sample sm : lap.samples) {
            Double v = sm.values().getOrDefault(ch, Double.NaN);
            if (v != null && !Double.isNaN(v)) { s += v; n++; }
        }
        return n > 0 ? s / n : Double.NaN;
    }

    // --- COLOR LOGIC ---

    private static Color getColor(double psi, VehicleTraits traits) {
        if (Double.isNaN(psi)) return Color.web("#334155");

        double optMin = traits.targets.psiMin();
        double optMax = traits.targets.psiMax();
        double coldLim = optMin - 2.0;
        double hotLim  = optMax + 2.0;

        Color cLow  = Color.web("#22d3ee");
        Color cOk   = Color.web("#22c55e");
        Color cHigh = Color.web("#ef4444");

        if (psi <= coldLim) return cLow;
        if (psi >= hotLim) return cHigh;
        if (psi >= optMin && psi <= optMax) return cOk;

        if (psi < optMin) {
            double range = optMin - coldLim;
            double pos = psi - coldLim;
            return cLow.interpolate(cOk, Math.max(0, Math.min(1, pos / range)));
        } else {
            double range = hotLim - optMax;
            double pos = psi - optMax;
            return cOk.interpolate(cHigh, Math.max(0, Math.min(1, pos / range)));
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }
}