package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Pedali: barre a % + testo con forza (~ N) integrato sotto ad ogni barra. */
public final class PedalBarsTL {
    private final VBox root = new VBox(8);
    private final FillBar thr = new FillBar("Throttle", "#16a34a");
    private final FillBar brk = new FillBar("Brake",    "#dc2626");
    private final FillBar clu = new FillBar("Clutch",   "#2563eb");

    public PedalBarsTL(){
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");

        Label title = new Label("Pedali");
        title.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:bold;");

        HBox bars = new HBox(12, thr.get(), brk.get(), clu.get());
        bars.setAlignment(Pos.CENTER);

        root.getChildren().setAll(title, bars);
    }

    /** Aggiorna percentuali (0..1) + forze in N (possono essere null/NaN). */
    public void update(double th01, double br01, double cl01, Double thrN, Double brkN, Double cluN){
        thr.set01AndForce(th01, thrN);
        brk.set01AndForce(br01, brkN);
        clu.set01AndForce(cl01, cluN);
    }

    public Node getRoot(){ return root; }

    // ---------- singola barra ----------
    private static final class FillBar{
        private final String name;
        private final VBox box = new VBox(6);
        private final StackPane bg = new StackPane();
        private final Region fill = new Region();
        private final Label labPct;
        private final Label labN;
        private double current01 = 0.0;

        FillBar(String name, String color){
            this.name = name;

            Label labName = new Label(name);
            labName.setStyle("-fx-text-fill:#e5e7eb;");

            labPct = new Label("0 %");
            labPct.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.9;");

            labN = new Label("—");
            labN.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.7;");

            bg.setMinSize(44, 140);
            bg.setPrefSize(44, 140);
            bg.setStyle("-fx-background-color:#111827; -fx-background-radius:8; -fx-padding:2;");

            fill.setStyle("-fx-background-radius:6; -fx-background-color:" + color + ";");
            fill.setMinHeight(Region.USE_PREF_SIZE);
            fill.setMaxHeight(Region.USE_PREF_SIZE);
            fill.setPrefHeight(0);
            fill.setMinWidth(Region.USE_COMPUTED_SIZE);

            StackPane inner = new StackPane(fill);
            StackPane.setAlignment(fill, Pos.BOTTOM_CENTER);
            inner.setMinSize(40,136);
            inner.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            bg.getChildren().setAll(inner);

            bg.heightProperty().addListener((o,ov,nv)-> applyHeight());

            box.setAlignment(Pos.CENTER);
            box.getChildren().addAll(bg, labName, labPct, labN);
        }

        void set01AndForce(double v01, Double nN){
            if (Double.isNaN(v01) || Double.isInfinite(v01)) v01 = 0.0;
            current01 = Math.max(0, Math.min(1, v01));
            labPct.setText(String.format("%s %d%%", name, Math.round(current01*100)));

            if (nN != null && !nN.isNaN() && !nN.isInfinite()){
                labN.setText(String.format("~ %.0f N", Math.abs(nN)));
            } else {
                labN.setText("—");
            }
            applyHeight();
        }

        private void applyHeight(){
            double h = Math.max(0, bg.getHeight()-4);
            fill.setPrefHeight(h * current01);
        }

        Node get(){ return box; }
    }
}
