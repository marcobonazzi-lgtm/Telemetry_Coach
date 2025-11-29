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

import java.util.Locale;

public final class SeatActuatorsTL {

    private static final double CARD_W = 240;
    private static final double CARD_H = 180; // Più compatto

    private final VBox root = new VBox(0);
    private final ActuatorBar sx = new ActuatorBar("SX", "#22d3ee");   // Ciano
    private final ActuatorBar dx = new ActuatorBar("DX", "#22d3ee");   // Ciano
    private final ActuatorBar po = new ActuatorBar("POST", "#f97316"); // Arancio

    public SeatActuatorsTL(){
        Label title = new Label("Sedile (G-Force)");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:14px;");

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0,0,10,0));

        HBox bars = new HBox(20, sx.root, dx.root, po.root);
        bars.setAlignment(Pos.CENTER);

        VBox card = new VBox(0, header, bars);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");

        UIx.lockFixedSize(card, CARD_W, CARD_H);
        root.getChildren().add(card);
    }

    public Node getRoot(){ return root; }

    public void update(double sx01, double dx01, double post01){
        sx.update(sx01);
        dx.update(dx01);
        po.update(post01);
    }

    private static class ActuatorBar {
        final VBox root = new VBox(4);
        final Label nameLbl = new Label();
        final StackPane track = new StackPane();
        final Rectangle fill = new Rectangle();
        final Label valLbl = new Label("0%");
        private Timeline anim;

        ActuatorBar(String name, String colorHex){
            nameLbl.setText(name);
            nameLbl.setStyle("-fx-text-fill:#94a3b8; -fx-font-weight:700; -fx-font-size:10px;");

            track.setPrefSize(24, 90);
            track.setStyle("-fx-background-color:#334155; -fx-background-radius:4;");
            track.setAlignment(Pos.BOTTOM_CENTER);

            fill.setWidth(24); fill.setHeight(0);
            fill.setArcWidth(4); fill.setArcHeight(4);
            fill.setFill(Color.web(colorHex));

            track.getChildren().add(fill);

            valLbl.setStyle("-fx-text-fill:#f1f5f9; -fx-font-size:10px;");

            root.getChildren().addAll(nameLbl, track, valLbl);
            root.setAlignment(Pos.CENTER);
        }

        void update(double v01){
            double v = Math.max(0, Math.min(1, v01));
            valLbl.setText(String.format(Locale.ROOT, "%.0f", v*100));

            double targetH = 90 * v;
            if (anim != null) anim.stop();
            anim = new Timeline(new KeyFrame(Duration.millis(100),
                    new KeyValue(fill.heightProperty(), targetH, Interpolator.EASE_OUT)));
            anim.play();
        }
    }

    private static class UIx {
        static void lockFixedSize(javafx.scene.layout.Region r, double w, double h) {
            r.setMinSize(w,h); r.setMaxSize(w,h);
        }
    }
}