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

public final class FFBBarTL {

    // DIMENSIONI SUPER SLIM
    private static final double CARD_W = 50;
    private static final double CARD_H = 220;

    private final VBox root = new VBox(0);
    private final StackPane track = new StackPane();
    private final Rectangle fill = new Rectangle();
    private final Label valLbl = new Label("0%");

    private Timeline anim;

    public FFBBarTL(){
        Label title = new Label("FFB");
        title.setStyle("-fx-text-fill:#ffffff; -fx-font-weight:800; -fx-font-size:11px;");

        // Track Slim (16px)
        track.setPrefSize(32, 140);
        track.setStyle("-fx-background-color:#334155; -fx-background-radius:4;");
        track.setAlignment(Pos.BOTTOM_CENTER);

        // Fill Slim
        fill.setWidth(16); fill.setHeight(0);
        fill.setArcWidth(4); fill.setArcHeight(4);
        fill.setFill(Color.web("#a855f7"));

        track.getChildren().add(fill);

        valLbl.setStyle("-fx-text-fill:#e5e7eb; -fx-font-family:'Monospaced'; -fx-font-size:10px; -fx-font-weight:bold;");

        VBox card = new VBox(8, title, track, valLbl);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 2, 12, 2));
        card.setStyle("-fx-background-color:#0f172a; -fx-background-radius:12; -fx-border-color: #1e293b; -fx-border-width: 1; -fx-border-radius: 12;");

        UIx.lockFixedSize(card, CARD_W, CARD_H);
        root.getChildren().add(card);
    }

    public Node getRoot(){ return root; }

    public void update(double v01){
        double v = Math.max(0, Math.min(1.5, v01));
        valLbl.setText(String.format(Locale.ROOT, "%.0f", v * 100));

        if (v > 0.98) {
            fill.setFill(Color.web("#ef4444"));
            valLbl.setTextFill(Color.web("#ef4444"));
        } else {
            fill.setFill(Color.web("#a855f7"));
            valLbl.setTextFill(Color.web("#e5e7eb"));
        }

        double targetH = 140 * Math.min(1.0, v);
        if (anim != null) anim.stop();
        anim = new Timeline(new KeyFrame(Duration.millis(80),
                new KeyValue(fill.heightProperty(), targetH, Interpolator.EASE_OUT)));
        anim.play();
    }

    private static class UIx {
        static void lockFixedSize(javafx.scene.layout.Region r, double w, double h) {
            r.setMinSize(w,h); r.setMaxSize(w,h);
        }
    }
}