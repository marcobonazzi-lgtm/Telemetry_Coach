package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

/**
 * Forza attuatori pedali (Throttle/Brake/Clutch) basata SOLO sui Newton.
 * Le barre si riempiono in proporzione a |N| / Nmax (Nmax calcolato sul giro).
 */
final class PedalForceTL {
    private final VBox root = new VBox(6);
    private final Bar thr = new Bar("Throttle", "#22c55e");
    private final Bar brk = new Bar("Brake",    "#ef4444");
    private final Bar clu = new Bar("Clutch",   "#3b82f6");

    // massimi per la normalizzazione (per-pedale)
    private double maxThrN = 1.0, maxBrkN = 1.0, maxCluN = 1.0;

    PedalForceTL(){
        Label title = new Label("Forza pedali");
        title.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:bold;");
        HBox row = new HBox(12, thr.root, brk.root, clu.root);
        row.setAlignment(Pos.CENTER);
        VBox box = new VBox(6, title, row);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");
        root.getChildren().setAll(box);
    }

    /** Imposta i massimi in N per la scala delle tre barre. */
    void setMaxes(double thrMaxN, double brkMaxN, double cluMaxN){
        this.maxThrN = safeMax(thrMaxN);
        this.maxBrkN = safeMax(brkMaxN);
        this.maxCluN = safeMax(cluMaxN);
    }
    private static double safeMax(double v){
        if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0) return 1.0;
        return v;
    }

    /** Aggiorna usando SOLO le forze (N). Può passare NaN/null: la barra resta a zero e mostra "—". */
    void updateByForce(Double thrN, Double brkN, Double cluN){
        thr.setForce(thrN, maxThrN);
        brk.setForce(brkN, maxBrkN);
        clu.setForce(cluN, maxCluN);
    }

    Node getRoot(){ return root; }

    // ---------- singola barra ----------
    private static final class Bar {
        final VBox root = new VBox(6);
        final StackPane bg = new StackPane();
        final Region fill = new Region();
        final Label lblName = new Label();
        final Label lblN    = new Label(""); // “~ 120 N”
        double ratio = 0.0;                  // 0..1
        Double curN = Double.NaN;

        Bar(String name, String color){
            lblName.setText(name);
            lblName.setStyle("-fx-text-fill:#e5e7eb;");
            lblN.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.75;");

            bg.setMinSize(28,140);
            bg.setPrefSize(28,140);
            bg.setStyle("-fx-background-color:#111827; -fx-background-radius:8; -fx-padding:2;");
            fill.setStyle("-fx-background-radius:6; -fx-background-color:" + color + ";");
            fill.setMinHeight(Region.USE_PREF_SIZE);
            fill.setMaxHeight(Region.USE_PREF_SIZE);
            fill.setPrefHeight(0);

            StackPane inner = new StackPane(fill);
            StackPane.setAlignment(fill, Pos.BOTTOM_CENTER);
            inner.setMinSize(24,136);
            bg.getChildren().setAll(inner);
            bg.heightProperty().addListener((o,ov,nv)-> apply());

            root.setAlignment(Pos.CENTER);
            root.getChildren().addAll(bg, lblName, lblN);
        }

        void setForce(Double nN, double maxN){
            curN = nN;
            if (nN == null || nN.isNaN() || nN.isInfinite()){
                lblN.setText("—");
                ratio = 0.0;
            } else {
                double val = Math.abs(nN);
                lblN.setText(String.format("~ %.0f N", val));
                ratio = Math.max(0, Math.min(1, val / Math.max(1e-6, maxN)));
            }
            apply();
        }

        void apply(){
            double h = Math.max(0, bg.getHeight()-4);
            fill.setPrefHeight(h * ratio);
        }
    }
}
