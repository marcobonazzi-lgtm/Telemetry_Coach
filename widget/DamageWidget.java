package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.widget.oggetti3D.CarDamage3D;

import java.util.List;
import java.util.Locale;

public class DamageWidget {

    private static final String BG_DARK = "-fx-background-color:#0f172a;";
    private static final String COL_OK   = "#ffffff";
    private static final String COL_WARN = "#fbbf24";
    private static final String COL_CRIT = "#ef4444";

    public static TitledPane build(Lap lap) {
        List<Lap> laps = (lap == null) ? java.util.Collections.emptyList() : java.util.Collections.singletonList(lap);
        return buildInternal(laps, "Danni");
    }

    public static TitledPane buildFromLaps(List<Lap> laps) {
        return buildInternal(laps, "Danni (Sessione)");
    }

    private static TitledPane buildInternal(List<Lap> laps, String title) {
        // Recupero valori (0-100)
        double front = normalize(avgLaps(laps, Channel.CAR_DAMAGE_FRONT));
        double left  = normalize(avgLaps(laps, Channel.CAR_DAMAGE_LEFT));
        double rear  = normalize(avgLaps(laps, Channel.CAR_DAMAGE_REAR));
        double right = normalize(avgLaps(laps, Channel.CAR_DAMAGE_RIGHT));

        // 1. Modello 3D
        CarDamage3D car3D = new CarDamage3D(300, 200);

        // ORA PASSIAMO TUTTI E 4 I PARAMETRI
        // L'auto si colorerà a zone (Muso, Coda, Fianchi) in modo indipendente
        car3D.updateDamage(front, rear, left, right);

        // 2. Etichette (Labels)
        Label lblFront = createStyledLabel(front, "F");
        Label lblRear  = createStyledLabel(rear,  "R");
        Label lblLeft  = createStyledLabel(left,  "L");
        Label lblRight = createStyledLabel(right, "R");

        // 3. Layout
        StackPane stack = new StackPane();
        stack.setStyle(BG_DARK);
        stack.setPadding(new Insets(5));

        stack.getChildren().add(car3D);

        // Posizionamento
        StackPane.setAlignment(lblFront, Pos.CENTER_RIGHT);
        StackPane.setAlignment(lblRear,  Pos.CENTER_LEFT);
        StackPane.setAlignment(lblLeft,  Pos.TOP_CENTER);
        StackPane.setAlignment(lblRight, Pos.BOTTOM_CENTER);

        StackPane.setMargin(lblFront, new Insets(0, 40, 0, 0));
        StackPane.setMargin(lblRear,  new Insets(0, 0, 0, 40));
        StackPane.setMargin(lblLeft,  new Insets(8, 0, 0, 0));
        StackPane.setMargin(lblRight, new Insets(0, 0, 8, 0));

        stack.getChildren().addAll(lblFront, lblRear, lblLeft, lblRight);

        TitledPane tp = new TitledPane(title, stack);
        tp.setCollapsible(false);
        tp.setAnimated(false);
        tp.setStyle("-fx-base: #0f172a; -fx-text-fill: white; -fx-font-weight:bold;");
        tp.setMaxHeight(Double.MAX_VALUE);

        return tp;
    }

    private static Label createStyledLabel(double val, String prefix) {
        String textVal = Double.isNaN(val) ? "--" : String.format(Locale.ROOT, "%.0f%%", val);
        Label lbl = new Label(prefix + " " + textVal);

        String colorHex = COL_OK;
        if (!Double.isNaN(val)) {
            if (val >= 80.0) colorHex = COL_CRIT;
            else if (val >= 40.0) colorHex = COL_WARN;
        }

        lbl.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));
        String style = "-fx-text-fill: " + colorHex + ";"
                + "-fx-border-color: " + colorHex + ";"
                + "-fx-border-width: 1.5;"
                + "-fx-border-radius: 4;"
                + "-fx-background-radius: 4;"
                + "-fx-background-color: rgba(15, 23, 42, 0.9);"
                + "-fx-padding: 3px 6px;";
        lbl.setStyle(style);
        return lbl;
    }

    private static double normalize(double v) {
        if (Double.isNaN(v)) return Double.NaN;
        if (v <= 1.001 && v >= 0) return v * 100.0;
        return v;
    }

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
}