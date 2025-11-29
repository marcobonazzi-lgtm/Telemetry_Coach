package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.simulator.setup.setup_advisor.VehicleTraits;
import org.simulator.widget.oggetti3D.BrakeDisc3D;

import java.util.Locale;

public final class TyreWearTL {

    private static final double CARD_W = 460;
    private static final double CARD_H = 220;

    private double tOptMin = 75.0;
    private double tOptMax = 100.0;
    private double tColdLimit = 60.0;
    private double tHotLimit = 115.0;

    private double bOptMin = 250.0;
    private double bOptMax = 600.0;
    private double bColdLimit = 100.0;
    private double bHotLimit = 750.0;

    private final VBox root = new VBox(0);

    private final TyreBox flBox = new TyreBox("FL");
    private final TyreBox frBox = new TyreBox("FR");
    private final TyreBox rlBox = new TyreBox("RL");
    private final TyreBox rrBox = new TyreBox("RR");

    public TyreWearTL(){
        Label title = new Label("Gomme & Freni");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        HBox header = new HBox(title);
        header.setPadding(new Insets(0,0,10,0));
        header.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        grid.add(flBox.root, 0, 0);
        grid.add(frBox.root, 1, 0);
        grid.add(rlBox.root, 0, 1);
        grid.add(rrBox.root, 1, 1);

        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c, c);

        VBox card = new VBox(0, header, grid);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");

        UIx.lockFixedSize(card, CARD_W, CARD_H);
        root.getChildren().add(card);
    }

    public Node getRoot(){ return root; }

    public void setVehicleTraits(VehicleTraits traits) {
        if (traits == null || traits.targets == null) return;
        VehicleTraits.TargetWindow t = traits.targets;

        this.tOptMin = t.tempCoreMin();
        this.tOptMax = t.tempCoreMax();
        this.tColdLimit = Math.max(20.0, tOptMin - 15.0);
        this.tHotLimit  = tOptMax + 15.0;

        this.bOptMin = t.tempBrakeMin();
        this.bOptMax = t.tempBrakeMax();
        this.bColdLimit = Math.max(50.0, bOptMin - 100.0);
        this.bHotLimit  = bOptMax + 150.0;

        flBox.refreshVisuals(); frBox.refreshVisuals();
        rlBox.refreshVisuals(); rrBox.refreshVisuals();
    }

    public void update(Double flW, Double frW, Double rlW, Double rrW,
                       Double flT, Double frT, Double rlT, Double rrT,
                       Double flP, Double frP, Double rlP, Double rrP,
                       Double flB, Double frB, Double rlB, Double rrB)
    {
        flBox.update(flW, flT, flP, flB);
        frBox.update(frW, frT, frP, frB);
        rlBox.update(rlW, rlT, rlP, rlB);
        rrBox.update(rrW, rrT, rrP, rrB);
    }

    public void updateTempIMO(Double flI, Double flM, Double flO,
                              Double frI, Double frM, Double frO,
                              Double rlI, Double rlM, Double rlO,
                              Double rrI, Double rrM, Double rrO) {
        flBox.setTempsIMO(flI, flM, flO);
        frBox.setTempsIMO(frI, frM, frO);
        rlBox.setTempsIMO(rlI, rlM, rlO);
        rrBox.setTempsIMO(rrI, rrM, rrO);
    }

    public void updateWear(Double wFL, Double wFR, Double wRL, Double wRR) {
        flBox.update(wFL, null, null, null); frBox.update(wFR, null, null, null);
        rlBox.update(wRL, null, null, null); rrBox.update(wRR, null, null, null);
    }
    public void updatePress(Double pFL, Double pFR, Double pRL, Double pRR) {
        flBox.update(null, null, pFL, null); frBox.update(null, null, pFR, null);
        rlBox.update(null, null, pRL, null); rrBox.update(null, null, pRR, null);
    }
    public void updateLoad(Double lFL, Double lFR, Double lRL, Double lRR) {}

    // ================= TYRE BOX CLASS =================

    private class TyreBox {
        final StackPane root = new StackPane();
        final HBox contentBox = new HBox(8);

        final VBox visualCol = new VBox(2);
        final HBox tyreBlock = new HBox(0);
        final Rectangle segI = new Rectangle(14, 36);
        final Rectangle segM = new Rectangle(14, 36);
        final Rectangle segO = new Rectangle(14, 36);

        // [RIMOSSO] brakeBar non c'è più

        final VBox dataCol = new VBox(0);
        final HBox headerRow = new HBox();
        final Label nameLbl = new Label();

        final Label psiLbl = new Label("-- psi");
        final Label tempLbl = new Label("--°C");
        final Label brakeLbl = new Label("Brk: --");
        final Label wearLbl = new Label("Life: --%");

        private final BrakeDisc3D brake3D;

        private Double lastWear, lastPsi, lastBrake;
        private Double lastTI, lastTM, lastTO;

        TyreBox(String name){
            // --- 3D Resize: Ridotto drasticamente a 45x45 ---
            brake3D = new BrakeDisc3D("/Brakes.obj", 45, 45);

            segI.setArcWidth(2); segI.setArcHeight(2);
            segM.setArcWidth(0); segM.setArcHeight(0);
            segO.setArcWidth(2); segO.setArcHeight(2);
            Color baseColor = Color.web("#334155");
            segI.setFill(baseColor); segM.setFill(baseColor); segO.setFill(baseColor);
            tyreBlock.getChildren().addAll(segI, segM, segO);

            // [MODIFICA] Solo tyreBlock in visualCol, niente brakeBar
            visualCol.getChildren().addAll(tyreBlock);
            visualCol.setAlignment(Pos.CENTER);

            nameLbl.setText(name);
            nameLbl.setStyle("-fx-text-fill:#94a3b8; -fx-font-weight:800; -fx-font-size:10px;");

            psiLbl.setStyle("-fx-text-fill:#64748b; -fx-font-size:10px;");
            brakeLbl.setStyle("-fx-text-fill:#38bdf8; -fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:11px;");

            headerRow.getChildren().addAll(nameLbl, UIx.spacer(), brakeLbl);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            tempLbl.setStyle("-fx-text-fill:#f1f5f9; -fx-font-family:'Monospaced'; -fx-font-size:14px; -fx-font-weight:bold;");
            wearLbl.setStyle("-fx-text-fill:#64748b; -fx-font-size:9px;");

            dataCol.getChildren().addAll(headerRow, tempLbl, psiLbl, wearLbl);
            HBox.setHgrow(dataCol, Priority.ALWAYS);

            contentBox.getChildren().addAll(visualCol, dataCol);
            contentBox.setPadding(new Insets(8));
            contentBox.setAlignment(Pos.CENTER_LEFT);

            root.getChildren().addAll(contentBox, brake3D);

            // Posizionamento più stretto all'angolo
            StackPane.setAlignment(brake3D, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(brake3D, new Insets(0, -2, -2, 0));

            root.setStyle("-fx-background-color:#1e293b; -fx-background-radius:6;");
        }

        void setTempsIMO(Double i, Double m, Double o) {
            if (i!=null) lastTI = i; if (m!=null) lastTM = m; if (o!=null) lastTO = o;
            refreshVisuals();
        }

        void update(Double wear, Double tempAvg, Double psi, Double brakeT){
            if (wear != null) lastWear = wear;
            if (psi != null) lastPsi = psi;
            if (brakeT != null) lastBrake = brakeT;
            if (tempAvg != null && (lastTI == null || lastTM == null)) { lastTI = lastTM = lastTO = tempAvg; }
            refreshVisuals();
        }

        private void refreshVisuals() {
            if (isF(lastPsi)) psiLbl.setText(String.format(Locale.ROOT, "%.1f psi", lastPsi));
            else psiLbl.setText("-- psi");

            if (isF(lastTM)) {
                double avg = (isF(lastTI) && isF(lastTO)) ? (lastTI + lastTM + lastTO) / 3.0 : lastTM;
                tempLbl.setText(Math.round(avg) + "°C");
                segI.setFill(interpolateTyreColor(lastTI));
                segM.setFill(interpolateTyreColor(lastTM));
                segO.setFill(interpolateTyreColor(lastTO));
            } else {
                tempLbl.setText("--°C");
            }

            if (isF(lastBrake)) {
                brakeLbl.setText(String.format("Brk: %.0f", lastBrake));
                if (lastBrake > bOptMax + 50)
                    brakeLbl.setStyle("-fx-text-fill:#ef4444; -fx-font-size:11px; -fx-font-weight:bold; -fx-font-family:'Monospaced';");
                else
                    brakeLbl.setStyle("-fx-text-fill:#38bdf8; -fx-font-size:11px; -fx-font-weight:bold; -fx-font-family:'Monospaced';");

                // --- AGGIORNAMENTO 3D PIÙ SENSIBILE ---
                // Passiamo il range ottimale (bOptMin, bOptMax) invece del limite massimo.
                // Il modello 3D ora inizia a scaldarsi già da bOptMin.
                brake3D.updateHeat(lastBrake, bOptMin, bOptMax);

            } else {
                brakeLbl.setText("Brk: --");
            }

            if (isF(lastWear)) {
                double w = (lastWear <= 1.0001) ? lastWear * 100.0 : lastWear;
                wearLbl.setText(String.format("Life: %.0f%%", w));
                if (w < 30) wearLbl.setTextFill(Color.web("#ef4444"));
                else wearLbl.setTextFill(Color.web("#64748b"));
            }
        }
    }

    private static boolean isF(Double d){ return d != null && !d.isNaN() && !d.isInfinite(); }

    private Color interpolateTyreColor(Double t) {
        if (!isF(t)) return Color.web("#334155");
        Color cCold = Color.web("#3b82f6");
        Color cOpt  = Color.web("#22c55e");
        Color cHot  = Color.web("#ef4444");

        if (t <= tColdLimit) return cCold;
        if (t >= tHotLimit) return cHot;
        if (t >= tOptMin && t <= tOptMax) return cOpt;

        if (t < tOptMin) {
            double range = tOptMin - tColdLimit;
            double pos = t - tColdLimit;
            return cCold.interpolate(cOpt, Math.max(0, Math.min(1, pos / range)));
        } else {
            double range = tHotLimit - tOptMax;
            double pos = t - tOptMax;
            return cOpt.interpolate(cHot, Math.max(0, Math.min(1, pos / range)));
        }
    }

    private static class UIx {
        static Region spacer() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
        static void lockFixedSize(Region r, double w, double h) { r.setMinSize(w,h); r.setPrefSize(w,h); r.setMaxSize(w,h); }
    }
}