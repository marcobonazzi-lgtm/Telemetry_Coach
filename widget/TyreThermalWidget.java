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

public class TyreThermalWidget {

    // Stili Base
    private static final String BG_DARK = "-fx-background-color:#0f172a;";
    private static final String CARD_BASE = "-fx-background-color:#1e293b; -fx-background-radius:8; -fx-border-color:#334155; -fx-border-radius:8;";

    // Enum Ruote
    private enum Wheel {
        FL("FL", Channel.TIRE_TEMP_CORE_FL, Channel.TIRE_TEMP_INNER_FL, Channel.TIRE_TEMP_MIDDLE_FL, Channel.TIRE_TEMP_OUTER_FL),
        FR("FR", Channel.TIRE_TEMP_CORE_FR, Channel.TIRE_TEMP_INNER_FR, Channel.TIRE_TEMP_MIDDLE_FR, Channel.TIRE_TEMP_OUTER_FR),
        RL("RL", Channel.TIRE_TEMP_CORE_RL, Channel.TIRE_TEMP_INNER_RL, Channel.TIRE_TEMP_MIDDLE_RL, Channel.TIRE_TEMP_OUTER_RL),
        RR("RR", Channel.TIRE_TEMP_CORE_RR, Channel.TIRE_TEMP_INNER_RR, Channel.TIRE_TEMP_MIDDLE_RR, Channel.TIRE_TEMP_OUTER_RR);

        final String label;
        final Channel core, in, mid, out;
        Wheel(String label, Channel core, Channel in, Channel mid, Channel out){
            this.label = label; this.core=core; this.in=in; this.mid=mid; this.out=out;
        }
    }

    // Record di supporto per i dati aggregati
    private record TyreTemps(double i, double m, double o, double avg) {}

    public static TitledPane build(Lap lap) {
        List<Lap> laps = (lap == null) ? java.util.Collections.emptyList() : java.util.Collections.singletonList(lap);
        return buildInternal(laps, "Pneumatici (Giro)");
    }

    public static TitledPane buildFromLaps(List<Lap> laps) {
        return buildInternal(laps, "Pneumatici (Media Sessione)");
    }

    // --- CORE RENDERING ---

    private static TitledPane buildInternal(List<Lap> laps, String title) {
        VehicleTraits traits = VehicleTraits.detect(laps);

        TyreTemps fl = calcStats(laps, Wheel.FL);
        TyreTemps fr = calcStats(laps, Wheel.FR);
        TyreTemps rl = calcStats(laps, Wheel.RL);
        TyreTemps rr = calcStats(laps, Wheel.RR);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.setStyle(BG_DARK);

        ColumnConstraints colC = new ColumnConstraints(); colC.setPercentWidth(50);
        grid.getColumnConstraints().addAll(colC, colC);
        RowConstraints rowC = new RowConstraints(); rowC.setPercentHeight(50);
        grid.getRowConstraints().addAll(rowC, rowC);

        grid.add(createCard(Wheel.FL.label, fl, traits), 0, 0);
        grid.add(createCard(Wheel.FR.label, fr, traits), 1, 0);
        grid.add(createCard(Wheel.RL.label, rl, traits), 0, 1);
        grid.add(createCard(Wheel.RR.label, rr, traits), 1, 1);

        TitledPane tp = new TitledPane(title, grid);
        tp.setCollapsible(false);
        tp.setAnimated(false);
        tp.setStyle("-fx-base: #0f172a; -fx-box-border: transparent; -fx-text-fill: white; -fx-font-weight:bold;");
        tp.setMaxHeight(Double.MAX_VALUE);

        return tp;
    }

    private static Node createCard(String name, TyreTemps temps, VehicleTraits traits) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10));
        card.setStyle(CARD_BASE);

        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        GridPane.setFillWidth(card, true);
        GridPane.setFillHeight(card, true);

        // --- 1. PARTE VISUALE (Barre) ---
        HBox bars = new HBox(1);
        bars.setAlignment(Pos.CENTER);
        double barW = 12;
        double barH = 45;

        Rectangle rI = new Rectangle(barW, barH, getColor(temps.i, traits));
        Rectangle rM = new Rectangle(barW, barH, getColor(temps.m, traits));
        Rectangle rO = new Rectangle(barW, barH, getColor(temps.o, traits));

        rI.setArcWidth(5); rI.setArcHeight(5);
        rM.setArcWidth(0); rM.setArcHeight(0);
        rO.setArcWidth(5); rO.setArcHeight(5);

        bars.getChildren().addAll(rI, rM, rO);

        // --- 2. PARTE TESTUALE (Ristrutturata) ---
        VBox textsContainer = new VBox(0); // Gap verticale ridotto per compattezza
        textsContainer.setAlignment(Pos.CENTER_LEFT);

        // A. Header (FL/FR...)
        Label lblName = new Label(name);
        lblName.setStyle("-fx-text-fill:#94a3b8; -fx-font-weight:800; -fx-font-size:11px;");
        // Piccolo margine sotto l'header
        VBox.setMargin(lblName, new Insets(0, 0, 2, 0));

        // B. Riga Contenuto: [Media Grande] + [Lista Dettagli]
        HBox contentRow = new HBox(12); // Spazio orizzontale tra Media e Dettagli
        contentRow.setAlignment(Pos.CENTER_LEFT);

        // B1. Media Principale
        Label lblAvg = new Label();
        if (Double.isNaN(temps.avg)) {
            lblAvg.setText("--");
            lblAvg.setStyle("-fx-text-fill:#64748b; -fx-font-size:26px; -fx-font-weight:bold; -fx-font-family:'Monospaced';");
        } else {
            lblAvg.setText(String.format(Locale.ROOT, "%.0f°", temps.avg));
            String hexColor = toHex(getColor(temps.avg, traits));
            lblAvg.setStyle("-fx-text-fill:" + hexColor + "; -fx-font-size:26px; -fx-font-weight:bold; -fx-font-family:'Monospaced';");
        }

        // B2. Colonna Dettagli (Uno sotto l'altro)
        VBox detailsCol = new VBox(-2); // Gap negativo o nullo per tenerli vicini
        detailsCol.setAlignment(Pos.CENTER_LEFT);

        detailsCol.getChildren().addAll(
                mkDetailRow("I", temps.i, traits),
                mkDetailRow("M", temps.m, traits),
                mkDetailRow("O", temps.o, traits)
        );

        contentRow.getChildren().addAll(lblAvg, detailsCol);
        textsContainer.getChildren().addAll(lblName, contentRow);

        card.getChildren().addAll(bars, textsContainer);
        return card;
    }

    // Crea una riga compatta: "I 99" colorata dinamicamente
    private static Label mkDetailRow(String prefix, double val, VehicleTraits traits) {
        String txt;
        String colorStyle;

        if (Double.isNaN(val)) {
            txt = String.format("%s --", prefix);
            colorStyle = "-fx-text-fill:#64748b;";
        } else {
            // "%-2s" allinea il prefisso, "%.0f" il numero
            txt = String.format(Locale.ROOT, "%s %.0f", prefix, val);
            colorStyle = "-fx-text-fill:" + toHex(getColor(val, traits)) + ";";
        }

        Label l = new Label(txt);
        // Font Monospaced è fondamentale per l'allineamento verticale dei numeri
        // Font size 11px o 12px è ideale per i dettagli secondari
        l.setStyle(colorStyle + " -fx-font-size:11px; -fx-font-weight:bold; -fx-font-family:'Monospaced';");
        return l;
    }

    // --- LOGICA CALCOLO ---
    private static TyreTemps calcStats(List<Lap> laps, Wheel w) {
        double i = avgLapsChannel(laps, w.in);
        double m = avgLapsChannel(laps, w.mid);
        double o = avgLapsChannel(laps, w.out);
        double core = avgLapsChannel(laps, w.core);

        boolean hasI = !Double.isNaN(i);
        boolean hasM = !Double.isNaN(m);
        boolean hasO = !Double.isNaN(o);

        if (!hasI && !hasM && !hasO) return new TyreTemps(core, core, core, core);

        double sum = 0; int count = 0;
        if (hasI) { sum += i; count++; }
        if (hasM) { sum += m; count++; }
        if (hasO) { sum += o; count++; }
        double avg = (count > 0) ? sum / count : core;

        return new TyreTemps(hasI ? i : avg, hasM ? m : avg, hasO ? o : avg, avg);
    }

    private static double avgLapsChannel(List<Lap> laps, Channel ch) {
        if (laps == null || laps.isEmpty()) return Double.NaN;
        double s = 0; int n = 0;
        for (Lap lap : laps) {
            double val = avgLap(lap, ch);
            if (!Double.isNaN(val)) { s += val; n++; }
        }
        return n > 0 ? s / n : Double.NaN;
    }

    private static double avgLap(Lap lap, Channel ch) {
        if (lap == null || lap.samples == null) return Double.NaN;
        double sum = 0; int n = 0;
        for (Sample s : lap.samples) {
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v != null && !Double.isNaN(v)) { sum += v; n++; }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    // --- COLORI (INVARIATO) ---
    private static Color getColor(double temp, VehicleTraits traits) {
        if (Double.isNaN(temp)) return Color.web("#334155");

        double optMin = traits.targets.tempCoreMin();
        double optMax = traits.targets.tempCoreMax();
        double coldLim = Math.max(20.0, optMin - 15.0);
        double hotLim  = optMax + 15.0;

        Color cCold = Color.web("#3b82f6");
        Color cOpt  = Color.web("#22c55e");
        Color cHot  = Color.web("#ef4444");

        if (temp <= coldLim) return cCold;
        if (temp >= hotLim) return cHot;
        if (temp >= optMin && temp <= optMax) return cOpt;

        if (temp < optMin) {
            double range = optMin - coldLim;
            double pos = temp - coldLim;
            return cCold.interpolate(cOpt, Math.max(0, Math.min(1, pos / range)));
        } else {
            double range = hotLim - optMax;
            double pos = temp - optMax;
            return cOpt.interpolate(cHot, Math.max(0, Math.min(1, pos / range)));
        }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }
}