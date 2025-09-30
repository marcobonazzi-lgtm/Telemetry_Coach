package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Locale;

public final class FFBBarTL{
    private final VBox root = new VBox(6);
    private final Region fill = new Region();
    private final StackPane bg = new StackPane();
    private final Label val = new Label("0 %");
    private double current01 = 0.0;
    public FFBBarTL(){
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");
        Label title = new Label("FFB");
        title.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:bold;");
        bg.setMinSize(28,140);
        bg.setPrefSize(28,140);
        bg.setStyle("-fx-background-color:#111827; -fx-background-radius:8; -fx-padding:2;");
        fill.setStyle("-fx-background-radius:6; -fx-background-color:#a78bfa;");
        fill.setMinHeight(Region.USE_PREF_SIZE);
        fill.setMaxHeight(Region.USE_PREF_SIZE);
        fill.setPrefHeight(0);
        StackPane inner = new StackPane(fill);
        StackPane.setAlignment(fill, Pos.BOTTOM_CENTER);
        inner.setMinSize(24,136);
        inner.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        bg.getChildren().setAll(inner);
        bg.heightProperty().addListener((o,ov,nv)-> apply());
        val.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.8;");
        root.getChildren().setAll(title, bg, val);
    }
    public void update(double v01){
        current01 = Math.max(0, Math.min(1, v01));
        val.setText(String.format(Locale.ITALIAN, "%d %%", Math.round(current01*100)));
        apply();
    }
    private void apply(){
        double h = Math.max(0, bg.getHeight()-4);
        fill.setPrefHeight(h * current01);
    }
    public Node getRoot(){ return root; }
}
