package org.simulator.ui.time_line_view.Track.Curve;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.Locale;
import java.util.Map;

public final class CurveDetailsPane {

    private final TitledPane root;
    private final VBox container = new VBox(8);

    private final Label idxLbl = new Label("");
    private final Label nameLbl = new Label("Nome Curva");
    private final Label infoLbl = new Label("- m | R: - m");
    private final Label gearBox = new Label("-");

    private final StatRow timeRow = new StatRow("TEMPO");
    private final StatRow speedRow = new StatRow("VELOCITÀ (Min)");

    public CurveDetailsPane(){
        root = new TitledPane();

        // --- MODIFICA: Ora è richiudibile ---
        root.setCollapsible(true);
        root.setExpanded(true);    // Aperto di default
        root.setAnimated(true);    // Animazione fluida

        root.setText("Dettaglio Curva");

        // STILI AGGIORNATI PER TEMI (Usa classi CSS, non colori fissi)
        idxLbl.getStyleClass().add("side-panel-text");
        idxLbl.setStyle("-fx-font-weight: bold; -fx-opacity: 0.8;");

        nameLbl.getStyleClass().add("side-panel-title"); // Usa il colore del titolo definito nel CSS
        nameLbl.setWrapText(true);
        nameLbl.setStyle("-fx-font-size: 16px;"); // Solo la dimensione rimane fissa

        infoLbl.getStyleClass().add("side-panel-text");
        infoLbl.setStyle("-fx-opacity: 0.7; -fx-font-size: 11px;");

        gearBox.setMinSize(24, 24);
        gearBox.setMaxSize(24, 24);
        gearBox.setAlignment(Pos.CENTER);
        // Il gear box lo lasciamo scuro con testo bianco perché è un elemento grafico "pieno"
        gearBox.setStyle("-fx-background-color: #444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4;");
        gearBox.setTooltip(new javafx.scene.control.Tooltip("Marcia media in curva"));

        VBox titles = new VBox(0, idxLbl, nameLbl, infoLbl);
        HBox headerRow = new HBox(8, gearBox, titles);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(0,0,4,0));

        container.getChildren().addAll(headerRow, new Separator(), timeRow.root, new Separator(), speedRow.root);
        container.setPadding(new Insets(8));

        root.setContent(container);
    }

    public Node node(){ return root; }

    public void updateFromMap(Map<String, ?> data){
        if (data == null) return;

        Object idxObj = data.get("Index");
        String idxStr = (idxObj != null) ? "Curva " + idxObj : "";

        String name = str(data.get("Nome"));
        String len = str(data.get("Lunghezza"));
        String rad = str(data.get("Raggio"));

        idxLbl.setText(idxStr.toUpperCase());
        nameLbl.setText(name);
        infoLbl.setText(len + "  |  R: " + rad);
        root.setText(idxStr + ": " + name);

        Object statsObj = data.get("_META_stats");
        if (statsObj instanceof CurveStatsAggregator.CurveStat) {
            CurveStatsAggregator.CurveStat s = (CurveStatsAggregator.CurveStat) statsObj;
            timeRow.update(s.curTime, s.bestTime, true);
            speedRow.update(s.curSpeed, s.bestSpeed, false);

            if (s.gear > 0) {
                gearBox.setText(String.valueOf(s.gear));
                // Usa colore accento del tema se possibile, altrimenti un blu standard
                gearBox.setStyle("-fx-background-color: -fx-accent; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4;");
            } else {
                gearBox.setText("N");
                gearBox.setStyle("-fx-background-color: #777; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4;");
            }
        } else {
            timeRow.clear();
            speedRow.clear();
            gearBox.setText("-");
        }
    }

    private String str(Object o) { return o == null ? "-" : o.toString(); }

    private static class StatRow {
        final VBox root = new VBox(4);
        final Label title = new Label();
        final Label mainVal = new Label("-");
        final Label subVal = new Label("Best: -");
        final Label deltaLbl = new Label("Δ -");
        final Rectangle deltaBar = new Rectangle(0, 4, Color.GRAY);

        StatRow(String header){
            title.setText(header);
            // Usa classi CSS per il testo
            title.getStyleClass().add("side-panel-text");
            title.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-opacity: 0.6;");

            mainVal.getStyleClass().add("side-panel-text"); // Importante per il valore principale
            mainVal.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: 'Monospaced';");

            subVal.getStyleClass().add("side-panel-text");
            subVal.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");

            deltaLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

            HBox top = new HBox(10, mainVal, deltaLbl);
            top.setAlignment(Pos.BASELINE_LEFT);
            root.getChildren().addAll(title, top, subVal, deltaBar);
        }

        void update(double current, double best, boolean lowerIsBetter){
            if (Double.isNaN(current) || current == 0) {
                clear();
                if (current == 0) mainVal.setText(lowerIsBetter ? "- s" : "0.0 km/h");
                return;
            }
            String unit = lowerIsBetter ? "s" : "km/h";
            mainVal.setText(String.format(Locale.US, "%.2f %s", current, unit));

            if (Double.isNaN(best) || best == 0) {
                subVal.setText("Best: -");
                deltaLbl.setText("");
                deltaBar.setWidth(0);
                return;
            }
            subVal.setText(String.format(Locale.US, "Best: %.2f %s", best, unit));

            double diff = current - best;
            double absDiff = Math.abs(diff);
            boolean isGood;
            String sign;

            if (lowerIsBetter) {
                isGood = diff <= 0.001;
                sign = (diff > 0) ? "+" : "";
            } else {
                isGood = diff >= -0.01;
                sign = (diff > 0) ? "+" : "";
            }

            deltaLbl.setText(String.format(Locale.US, "%s%.2f", sign, diff));

            // Rimuovi vecchie classi di stato
            deltaLbl.getStyleClass().removeAll("delta-good", "delta-bad");
            // Aggiungi la nuova classe corretta (definita nei CSS)
            deltaLbl.getStyleClass().add(isGood ? "delta-good" : "delta-bad");

            // Logica barra grafica
            double maxBarW = 120;
            double factor = Math.min(1.0, absDiff / (lowerIsBetter ? 1.0 : 10.0));
            deltaBar.setWidth(factor * maxBarW);

            // Colori barra hardcoded perché sono semantici universali (Verde/Rosso)
            // Ma usiamo colori brillanti che si vedono su scuro
            deltaBar.setFill(isGood ? Color.web("#22c55e") : Color.web("#ef4444"));
        }

        void clear(){
            mainVal.setText("-");
            subVal.setText("Best: -");
            deltaLbl.setText("");
            deltaBar.setWidth(0);
        }
    }
}