package org.simulator.ui.time_line_view.widget_TL;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.simulator.setup.setup_advisor.VehicleTraits;
import org.simulator.widget.oggetti3D.ShockAbsorber3D;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Widget Sospensioni "Aligned & Calibrated".
 * Dimensioni: 460x220 (Fisso).
 * Fix: Allineamento verticale (+3px top) e calibrazione ride height negativa.
 */
public final class SuspensionsTL {

    private static final double CARD_W = 460;
    private static final double CARD_H = 220;

    // Range Default (Fallback)
    private double rideMinMM = 10.0; // Abbassato default per evitare rosso fisso su auto basse
    private double rideMaxMM = 120.0;

    private enum Mode { TRAVEL, RIDE }

    private final VBox root = new VBox(0);
    private final HBox header = new HBox();

    private final HBox toggleSwitch = new HBox();
    private final Label lblTravel = new Label("Corsa %");
    private final Label lblRide = new Label("Ride H.");
    private Mode currentMode = Mode.TRAVEL;

    private final SuspensionUnit fl = new SuspensionUnit("FL");
    private final SuspensionUnit fr = new SuspensionUnit("FR");
    private final SuspensionUnit rl = new SuspensionUnit("RL");
    private final SuspensionUnit rr = new SuspensionUnit("RR");

    public SuspensionsTL(){
        // --- 1. FIX ALLINEAMENTO VISIVO ---
        // Aggiungo 3px di padding sopra per allinearlo al widget "Gomme & Freni"
        root.setPadding(new Insets(3, 0, 0, 0));

        // --- Header ---
        Label title = new Label("Sospensioni");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        lblTravel.setStyle(activeStyle());
        lblRide.setStyle(inactiveStyle());
        toggleSwitch.getChildren().addAll(lblTravel, lblRide);
        toggleSwitch.setAlignment(Pos.CENTER);
        toggleSwitch.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 15; -fx-border-color: #334155; -fx-border-radius: 15;");
        toggleSwitch.setPadding(new Insets(2));

        lblTravel.setOnMouseClicked(e -> setMode(Mode.TRAVEL));
        lblRide.setOnMouseClicked(e -> setMode(Mode.RIDE));

        header.getChildren().addAll(title, UIx.spacer(), toggleSwitch);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 4, 0));

        // --- Grid Layout ---
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);

        grid.add(fl.root, 0, 0);
        grid.add(fr.root, 1, 0);
        grid.add(rl.root, 0, 1);
        grid.add(rr.root, 1, 1);

        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c, c);

        RowConstraints r = new RowConstraints();
        r.setPercentHeight(50);
        grid.getRowConstraints().addAll(r, r);

        VBox card = new VBox(0, header, grid);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");

        UIx.lockFixedSize(card, CARD_W, CARD_H);

        Rectangle clip = new Rectangle(CARD_W, CARD_H);
        clip.setArcWidth(12); clip.setArcHeight(12);
        card.setClip(clip);

        root.getChildren().add(card);
        setMode(Mode.TRAVEL);
    }

    public Node getRoot(){ return root; }

    /**
     * Calibra i range per evitare che la barra diventi rossa troppo presto.
     * Le auto Formula/GT possono girare a 4-5mm da terra, quindi min=0.
     */
    public void setVehicleTraits(VehicleTraits traits) {
        if (traits == null) return;
        switch (traits.category) {
            case FORMULA:
            case PROTOTYPE:
                // Permettiamo valori fino a 0 senza segnare errore critico immediato
                this.rideMinMM = 0.0;
                this.rideMaxMM = 80.0;
                break;
            case GT:
                // Le GT3 girano intorno ai 50-70mm, ma alcune scendono a 40
                this.rideMinMM = 30.0;
                this.rideMaxMM = 150.0;
                break;
            case ROAD:
                this.rideMinMM = 90.0;
                this.rideMaxMM = 280.0;
                break;
            default:
                this.rideMinMM = 20.0;
                this.rideMaxMM = 120.0;
        }
        refreshAllUnits();
    }

    public void update(double pFL, double pFR, double pRL, double pRR,
                       Double rhFL, Double rhFR, Double rhRL, Double rhRR) {
        fl.updateState(s01(pFL), rhFL, currentMode, rideMinMM, rideMaxMM);
        fr.updateState(s01(pFR), rhFR, currentMode, rideMinMM, rideMaxMM);
        rl.updateState(s01(pRL), rhRL, currentMode, rideMinMM, rideMaxMM);
        rr.updateState(s01(pRR), rhRR, currentMode, rideMinMM, rideMaxMM);
    }

    private void setMode(Mode m) {
        this.currentMode = m;
        if (m == Mode.TRAVEL) { lblTravel.setStyle(activeStyle()); lblRide.setStyle(inactiveStyle()); }
        else { lblTravel.setStyle(inactiveStyle()); lblRide.setStyle(activeStyle()); }
        refreshAllUnits();
    }

    private void refreshAllUnits() {
        fl.forceRefresh(currentMode, rideMinMM, rideMaxMM);
        fr.forceRefresh(currentMode, rideMinMM, rideMaxMM);
        rl.forceRefresh(currentMode, rideMinMM, rideMaxMM);
        rr.forceRefresh(currentMode, rideMinMM, rideMaxMM);
    }

    private String activeStyle() { return "-fx-text-fill: #ffffff; -fx-font-weight:bold; -fx-background-color: #3b82f6; -fx-background-radius: 12; -fx-padding: 4 12;"; }
    private String inactiveStyle() { return "-fx-text-fill: #94a3b8; -fx-font-weight:normal; -fx-background-color: transparent; -fx-background-radius: 12; -fx-padding: 4 12; -fx-cursor: hand;"; }
    private static double s01(double v){ return (!Double.isFinite(v)) ? 0 : max(0, min(1, v)); }

    private static class UIx {
        static Region spacer() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
        static void lockFixedSize(Region r, double w, double h) {
            r.setMinWidth(w); r.setPrefWidth(w); r.setMaxWidth(w);
            r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
        }
    }

    // ================= UNITÀ RUOTA =================

    private static final class SuspensionUnit {
        final VBox root = new VBox(0);

        final VBox barSection = new VBox(2);
        final HBox infoBox = new HBox();
        final Label title = new Label();
        final Label valueLabel = new Label();
        final StackPane track = new StackPane();
        final Rectangle fill = new Rectangle();

        final ShockAbsorber3D shock3D;
        final StackPane shockContainer = new StackPane();

        private Timeline anim;
        private double memTravel = 0;
        private Double memRide = 0.0;

        SuspensionUnit(String name){
            title.setText(name);
            title.setStyle("-fx-text-fill:#94a3b8; -fx-font-weight:800; -fx-font-size:10px;");
            valueLabel.setStyle("-fx-text-fill:#f1f5f9; -fx-font-family:'Monospaced'; -fx-font-size:11px; -fx-font-weight:bold;");
            infoBox.getChildren().addAll(title, UIx.spacer(), valueLabel);
            infoBox.setAlignment(Pos.CENTER_LEFT);

            track.setMinHeight(4); track.setMaxHeight(4);
            track.setStyle("-fx-background-color:#334155; -fx-background-radius:2;");
            track.setAlignment(Pos.CENTER_LEFT);
            fill.setHeight(4);
            fill.setArcWidth(2); fill.setArcHeight(2);
            fill.setFill(Color.web("#f59e0b"));
            fill.setWidth(0);
            track.getChildren().add(fill);

            barSection.getChildren().addAll(infoBox, track);
            barSection.setPadding(new Insets(2, 0, 2, 0));

            shock3D = new ShockAbsorber3D("/shock.obj", 190, 46);

            shockContainer.getChildren().add(shock3D);
            shockContainer.setAlignment(Pos.CENTER);
            shockContainer.setMinHeight(46);
            shockContainer.setMaxHeight(46);

            root.getChildren().addAll(barSection, shockContainer);
            root.setPadding(new Insets(4, 8, 2, 8));
            root.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 6;");
        }

        void updateState(double travelNorm, Double rideMm, Mode mode, double rMin, double rMax) {
            this.memTravel = travelNorm; this.memRide = rideMm; refresh(mode, rMin, rMax);
        }
        void forceRefresh(Mode mode, double rMin, double rMax) { refresh(mode, rMin, rMax); }

        private void refresh(Mode mode, double rMin, double rMax) {
            double barPct = 0;
            double compression3D = 0;
            String txt = "--";
            Color c = Color.web("#3b82f6");

            if (mode == Mode.TRAVEL) {
                // Corsa %
                double t = memTravel;
                barPct = t; compression3D = t;
                txt = String.format("%.0f%%", t * 100);
                if (t > 0.95) c = Color.web("#ef4444");
                else if (t > 0.80) c = Color.web("#f59e0b");
                else c = Color.web("#3b82f6");
            } else {
                // Ride Height (mm)
                if (memRide != null && Double.isFinite(memRide)) {
                    txt = String.format("%.0f mm", memRide);

                    // Calcolo Relativo: Più è basso, più la barra è piena.
                    // Se memRide < rMin (es: -2 < 0), rel diventa negativo -> clamped a 0 -> barPct = 1 (Pieno)
                    // Se memRide > rMax, rel > 1 -> clamped a 1 -> barPct = 0 (Vuoto)

                    double range = rMax - rMin;
                    double val = Math.max(rMin, memRide); // Clamp visuale per evitare calcoli errati
                    double rel = (val - rMin) / range;
                    rel = max(0, min(1, rel));

                    barPct = 1.0 - rel;
                    compression3D = 1.0 - rel;

                    // Logica Colori Ride Height:
                    // Se siamo sotto il minimo (es. negativo o < 0), è CRITICO (Rosso)
                    if (memRide <= rMin) c = Color.web("#ef4444");
                    else if (memRide < rMin + 15) c = Color.web("#f59e0b"); // Basso, attenzione
                    else c = Color.web("#22c55e"); // Ok
                }
            }

            valueLabel.setText(txt);
            fill.setFill(c);

            double wAvailable = (root.getWidth() > 0) ? root.getWidth() - 16 : 180;
            double targetW = wAvailable * barPct;

            if (anim != null) anim.stop();
            anim = new Timeline(new KeyFrame(Duration.millis(50),
                    new KeyValue(fill.widthProperty(), targetW, Interpolator.LINEAR)));
            anim.play();

            shock3D.updateState(compression3D, c);
        }
    }
}