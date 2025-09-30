package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Locale;

public final class SeatActuatorsTL{
    private final VBox root = new VBox(6);
    private final HBox row = new HBox(12);
    private final Gauge sx = new Gauge("SX","#fb923c");
    private final Gauge dx = new Gauge("DX","#22d3ee");
    private final Gauge po = new Gauge("POST","#f97316");
    public SeatActuatorsTL(){
        Label title = new Label("Sedile (attuatori)");
        title.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:bold;");
        Region card = card(new VBox(8, title, row));
        row.getChildren().addAll(sx.root, dx.root, po.root);
        root.getChildren().setAll(card);
    }
    public void update(double sx01, double dx01, double post01){
        sx.set01(sx01); dx.set01(dx01); po.set01(post01);
    }
    public Node getRoot(){ return root; }

    private static final class Gauge{
        final VBox root = new VBox(6);
        final StackPane bg = new StackPane();
        final Region fill = new Region();
        final Label labPct = new Label("0 %");
        final Label labN   = new Label("");
        double v01=0;
        Gauge(String name, String color){
            Label nameLbl = new Label(name); nameLbl.setStyle("-fx-text-fill:#e5e7eb;");
            labPct.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.8;");
            labN.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.6;");
            bg.setMinSize(32, 120); bg.setPrefSize(32,120);
            bg.setStyle("-fx-background-color:#111827; -fx-background-radius:8; -fx-padding:2;");
            fill.setStyle("-fx-background-radius:6; -fx-background-color:"+color+";");
            fill.setMinHeight(Region.USE_PREF_SIZE);
            fill.setMaxHeight(Region.USE_PREF_SIZE);
            fill.setPrefHeight(0);
            StackPane inner = new StackPane(fill);
            StackPane.setAlignment(fill, javafx.geometry.Pos.BOTTOM_CENTER);
            inner.setMinSize(28,116);
            inner.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            bg.getChildren().setAll(inner);
            bg.heightProperty().addListener((o,ov,nv)->apply());
            root.setAlignment(javafx.geometry.Pos.CENTER);
            root.getChildren().addAll(bg, nameLbl, labPct);
        }
        void set01(double v){
            v01=Math.max(0,Math.min(1,v));
            labPct.setText(String.format(Locale.ITALIAN, "%d %%", Math.round(v01*100)));
            apply();
        }
        void apply(){ fill.setPrefHeight(Math.max(0, bg.getHeight()-4)*v01); }
    }
    private static Region card(Node c){
        VBox v = (c instanceof VBox) ? (VBox) c : new VBox(c);
        v.setPadding(new Insets(8));
        v.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");
        return v;
    }
}
