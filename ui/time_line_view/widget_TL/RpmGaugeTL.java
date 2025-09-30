package org.simulator.ui.time_line_view.widget_TL;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.transform.Rotate;

/** Contagiri: ago su quadrante, scala 0..maxRPM. */
public final class RpmGaugeTL {
    private static final double CARD_W = 220, CARD_H = 220;
    private static final double DIAL_SIZE = 160;
    private static final double SWEEP_DEG = 240;

    private final VBox root = new VBox(6);
    private final Pane dial = new Pane();

    private final Line needle = new Line();
    private final Rotate rot = new Rotate(0); // pivot impostato dopo buildDial()
    private final Circle hub = new Circle(5, Color.web("#e5e7eb"));

    private final Label title = new Label("Contagiri");
    private final Label value = new Label("0 rpm");

    private double maxRpm = 9000;

    public RpmGaugeTL(){
        title.setStyle("-fx-text-fill:#e5e7eb; -fx-font-weight:bold;");
        value.setStyle("-fx-text-fill:#e5e7eb; -fx-opacity:.9;");

        VBox box = new VBox(8, title, buildDial(), value);
        box.setPadding(new Insets(10));
        box.setAlignment(Pos.TOP_CENTER);
        box.setStyle("-fx-background-color:#0f172a; -fx-background-radius:10;");
        lockFixedSize(box, CARD_W, CARD_H);

        root.getChildren().setAll(box);
        lockFixedSize(root, CARD_W, CARD_H);
    }

    public Node getRoot(){ return root; }

    public void setMax(double maxRpm){
        if (Double.isFinite(maxRpm) && maxRpm > 1) this.maxRpm = maxRpm;
    }

    public void update(Double rpm){
        double v = (rpm==null || rpm.isNaN()) ? 0.0 : Math.max(0, rpm);
        value.setText(String.format(java.util.Locale.ITALIAN, "%.0f rpm", v));
        setNeedleTo(v / Math.max(1e-6, maxRpm));
    }

    // ---------------------------------------------------------

    private Node buildDial(){
        dial.setPrefSize(DIAL_SIZE, DIAL_SIZE);
        dial.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        dial.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        double cx = DIAL_SIZE / 2.0, cy = DIAL_SIZE / 2.0;

        Circle bg = new Circle(cx, cy, DIAL_SIZE/2.0);
        bg.setFill(Color.web("#111827"));
        bg.setStroke(Color.web("#1f2937"));
        bg.setStrokeWidth(2);

        dial.getChildren().addAll(SpeedGaugeTL.Ticks.make(cx, cy, DIAL_SIZE, SWEEP_DEG, 4));

        double r = DIAL_SIZE * 0.36;
        needle.setStartX(cx); needle.setStartY(cy);
        needle.setEndX(cx);   needle.setEndY(cy - r);
        needle.setStroke(Color.web("#f59e0b"));
        needle.setStrokeWidth(3);

        rot.setPivotX(cx);
        rot.setPivotY(cy);
        needle.getTransforms().add(rot);

        hub.setCenterX(cx); hub.setCenterY(cy);

        dial.getChildren().add(0, bg);
        dial.getChildren().addAll(needle, hub);
        return dial;
    }

    private void setNeedleTo(double frac01){
        double clamped = Math.max(0, Math.min(1, frac01));
        double angle = -SWEEP_DEG/2.0 + SWEEP_DEG * clamped;
        rot.setAngle(angle);
    }

    private static void lockFixedSize(Region r, double w, double h){
        r.setMinWidth(w);  r.setPrefWidth(w);  r.setMaxWidth(w);
        r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
    }
}
