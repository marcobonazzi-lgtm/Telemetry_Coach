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

/**
 * Widget Pedali Unificato (Input % + Forza N).
 * Barre verticali fluide con indicazione forza numerica.
 */
public final class PedalBarsTL {

    private static final double CARD_W = 240; // Compatto
    private static final double CARD_H = 220;

    private final VBox root = new VBox(0);
    private final PedalBar thr = new PedalBar("Throttle", "#22c55e"); // Verde
    private final PedalBar brk = new PedalBar("Brake",    "#ef4444"); // Rosso
    private final PedalBar clu = new PedalBar("Clutch",   "#3b82f6"); // Blu

    public PedalBarsTL(){
        Label title = new Label("Input Pedali");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        HBox header = new HBox(title);
        header.setPadding(new Insets(0,0,12,0));
        header.setAlignment(Pos.CENTER);

        HBox bars = new HBox(20, clu.root, brk.root, thr.root); // Ordine standard auto (Frizione, Freno, Gas)
        bars.setAlignment(Pos.CENTER);

        VBox card = new VBox(0, header, bars);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");

        lock(card, CARD_W, CARD_H);
        root.getChildren().add(card);
    }

    public Node getRoot(){ return root; }

    public void update(double t01, double b01, double c01, Double tN, Double bN, Double cN){
        thr.update(t01, tN);
        brk.update(b01, bN);
        clu.update(c01, cN);
    }

    private static void lock(Region r, double w, double h) { r.setMinSize(w,h); r.setMaxSize(w,h); }

    // --- Single Bar ---
    private static class PedalBar {
        final VBox root = new VBox(4);
        final StackPane track = new StackPane();
        final Rectangle fill = new Rectangle();
        final Label valLbl = new Label("0");
        final Label forceLbl = new Label("- N");
        private Timeline anim;

        PedalBar(String name, String colorHex){
            // Header (Nome non serve, si capisce dal colore/posizione, usiamo solo valore)
            valLbl.setStyle("-fx-text-fill:#f1f5f9; -fx-font-family:'Monospaced'; -fx-font-weight:bold; -fx-font-size:12px;");

            // Track
            track.setPrefSize(30, 120);
            track.setStyle("-fx-background-color:#334155; -fx-background-radius:4;");
            track.setAlignment(Pos.BOTTOM_CENTER);

            // Fill
            fill.setWidth(30); fill.setHeight(0);
            fill.setArcWidth(4); fill.setArcHeight(4);
            fill.setFill(Color.web(colorHex));

            track.getChildren().add(fill);

            // Force Label
            forceLbl.setStyle("-fx-text-fill:#94a3b8; -fx-font-size:9px;");

            root.getChildren().addAll(valLbl, track, forceLbl);
            root.setAlignment(Pos.CENTER);
        }

        void update(double pct, Double force){
            double v = Math.max(0, Math.min(1, pct));
            valLbl.setText((int)(v*100) + "");

            if (force != null && !Double.isNaN(force)) forceLbl.setText((int)Math.abs(force) + " N");
            else forceLbl.setText("-");

            // Animazione fluida altezza
            double targetH = 120 * v; // 120 è altezza track
            if (anim != null) anim.stop();
            anim = new Timeline(new KeyFrame(Duration.millis(80),
                    new KeyValue(fill.heightProperty(), targetH, Interpolator.EASE_OUT)));
            anim.play();
        }
    }
}