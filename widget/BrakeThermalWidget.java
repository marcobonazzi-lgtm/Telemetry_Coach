package org.simulator.widget;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.setup.setup_advisor.VehicleTraits;
import org.simulator.widget.oggetti3D.BrakeDisc3D;
import java.util.List;
import java.util.Locale;

public class BrakeThermalWidget {

    private static final String MODEL_PATH = "/Brakes.obj";
    private static final String BG_DARK = "-fx-background-color:#0f172a;";
    private static final String CARD_BASE = "-fx-background-color:#1e293b; -fx-background-radius:8;";

    // --- COLORI UI (Sync con 3D logic) ---
    private static final String HEX_COLD = "#64748b"; // Grigio/Bluastro (Freddo)
    private static final String HEX_WARM = "#ea580c"; // Arancione Ruggine (Warm)
    private static final String HEX_HOT  = "#ef4444"; // Rosso Acceso (Hot)

    public static TitledPane build(Lap lap) {
        List<Lap> laps = (lap == null) ? java.util.Collections.emptyList() : java.util.Collections.singletonList(lap);
        return buildInternal(laps, "Freni (Giro)");
    }

    public static TitledPane buildFromLaps(List<Lap> laps) {
        return buildInternal(laps, "Freni (Media Sessione)");
    }

    private static TitledPane buildInternal(List<Lap> laps, String title) {
        VehicleTraits traits = VehicleTraits.detect(laps);

        double fl = avgLaps(laps, Channel.BRAKE_TEMP_FL);
        double fr = avgLaps(laps, Channel.BRAKE_TEMP_FR);
        double rl = avgLaps(laps, Channel.BRAKE_TEMP_RL);
        double rr = avgLaps(laps, Channel.BRAKE_TEMP_RR);

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

    private static Node createCard(String name, double temp, VehicleTraits traits) {
        HBox cardLayout = new HBox(10);
        cardLayout.setAlignment(Pos.CENTER_LEFT);
        cardLayout.setPadding(new Insets(10));

        VBox textPart = new VBox(2);
        textPart.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textPart, Priority.ALWAYS);

        Label lblName = new Label(name);
        lblName.setStyle("-fx-text-fill:#94a3b8; -fx-font-weight:bold; -fx-font-size:11px;");

        Label lblVal = new Label();
        String activeColor;

        // Parametri per il 3D
        double tMin = traits.targets.tempBrakeMin();
        double tMax = traits.targets.tempBrakeMax();
        // Nota: Il 3D usa una logica interna visiva (150 -> 400 -> 700)
        // Replichiamo quella logica qui per sincronizzare il testo

        if (Double.isNaN(temp)) {
            lblVal.setText("--");
            activeColor = "#64748b";
        } else {
            lblVal.setText(String.format(Locale.ROOT, "%.0f°", temp));

            // --- LOGICA COLORI ADATTIVA (Stessi step del 3D) ---
            double startHeat = 150.0;
            double midHeat   = 400.0;

            if (temp <= startHeat) {
                // Freddo
                activeColor = HEX_COLD; // Grigio/Blu scuro
            } else if (temp <= midHeat) {
                // Si sta scaldando (Arancione)
                activeColor = HEX_WARM;
            } else {
                // Caldo/Rovente (Rosso)
                activeColor = HEX_HOT;
            }
        }

        lblVal.setStyle("-fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:24px; -fx-text-fill:" + activeColor + ";");
        textPart.getChildren().addAll(lblName, lblVal);

        // Modello 3D
        BrakeDisc3D brake3D = new BrakeDisc3D(MODEL_PATH, 160, 90);
        brake3D.updateHeat(temp, tMin, tMax);

        cardLayout.getChildren().addAll(textPart, brake3D);

        // Bordo sincronizzato col colore del testo
        cardLayout.setStyle(CARD_BASE + "-fx-border-color:" + activeColor + "; -fx-border-width:2; -fx-border-radius:8;");

        cardLayout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        GridPane.setFillWidth(cardLayout, true);
        GridPane.setFillHeight(cardLayout, true);

        return cardLayout;
    }

    private static double avgLaps(List<Lap> laps, Channel ch) {
        if (laps == null || laps.isEmpty()) return Double.NaN;
        double s=0; int n=0;
        for(Lap l: laps) {
            double v = avgLap(l, ch);
            if(!Double.isNaN(v)){ s+=v; n++; }
        }
        return n>0 ? s/n : Double.NaN;
    }

    private static double avgLap(Lap lap, Channel ch){
        if(lap==null || lap.samples==null) return Double.NaN;
        double s=0; int n=0;
        for(Sample sm : lap.samples){
            Double v = sm.values().getOrDefault(ch, Double.NaN);
            if(v!=null && !Double.isNaN(v)){ s+=v; n++; }
        }
        return n>0 ? s/n : Double.NaN;
    }
}