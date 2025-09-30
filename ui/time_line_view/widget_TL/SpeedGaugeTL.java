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

/** Tachimetro semplice: ago su quadrante, scala 0..maxKmH. */
public final class SpeedGaugeTL {
    private static final double CARD_W = 220, CARD_H = 220;
    private static final double DIAL_SIZE = 160;       // diametro quadrante
    private static final double SWEEP_DEG = 240;       // ampiezza (-120..+120)

    private final VBox root = new VBox(6);
    private final Pane dial = new Pane();

    private final Line needle = new Line();
    private final Rotate rot = new Rotate(0);          // pivot impostato dopo buildDial()
    private final Circle hub = new Circle(5, Color.web("#e5e7eb"));

    private final Label title = new Label("Velocità");
    private final Label value = new Label("0 km/h");

    private double maxKmH = 300.0;

    public SpeedGaugeTL(){
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

    public void setMax(double maxKmH){
        if (Double.isFinite(maxKmH) && maxKmH > 1) this.maxKmH = maxKmH;
    }

    public void update(Double kmh){
        double v = (kmh==null || kmh.isNaN()) ? 0.0 : Math.max(0, kmh);
        value.setText(String.format(java.util.Locale.ITALIAN, "%.0f km/h", v));
        setNeedleTo(v / Math.max(1e-6, maxKmH));
    }

    // ---------------------------------------------------------

    private Node buildDial(){
        dial.setPrefSize(DIAL_SIZE, DIAL_SIZE);
        dial.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        dial.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        double cx = DIAL_SIZE / 2.0, cy = DIAL_SIZE / 2.0;

        // sfondo
        Circle bg = new Circle(cx, cy, DIAL_SIZE/2.0);
        bg.setFill(Color.web("#111827"));
        bg.setStroke(Color.web("#1f2937"));
        bg.setStrokeWidth(2);

        // tacche (0, metà, max)
        dial.getChildren().addAll(Ticks.make(cx, cy, DIAL_SIZE, SWEEP_DEG, 3));

        // ago: parte dal centro verso l'alto
        double r = DIAL_SIZE * 0.36;
        needle.setStartX(cx); needle.setStartY(cy);
        needle.setEndX(cx);   needle.setEndY(cy - r);
        needle.setStroke(Color.web("#60a5fa"));
        needle.setStrokeWidth(3);

        // pivot esatto al centro (sul mozzo)
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
        double angle = -SWEEP_DEG/2.0 + SWEEP_DEG * clamped; // -120 .. +120
        rot.setAngle(angle);
    }

    private static void lockFixedSize(Region r, double w, double h){
        r.setMinWidth(w);  r.setPrefWidth(w);  r.setMaxWidth(w);
        r.setMinHeight(h); r.setPrefHeight(h); r.setMaxHeight(h);
    }

    /** Tacche radiali. */
    static final class Ticks {
        static java.util.List<Line> make(double cx, double cy, double size, double sweepDeg, int nMajor){
            java.util.List<Line> out = new java.util.ArrayList<>();
            for (int i=0;i<nMajor;i++){
                double t = (nMajor==1)?0.0:(double)i/(nMajor-1);
                double ang = Math.toRadians(-sweepDeg/2.0 + sweepDeg*t);
                double rOut = size*0.42, rIn = size*0.34;
                double x1 = cx + rIn*Math.sin(ang),  y1 = cy - rIn*Math.cos(ang);
                double x2 = cx + rOut*Math.sin(ang), y2 = cy - rOut*Math.cos(ang);
                Line tick = new Line(x1,y1,x2,y2);
                tick.setStroke(Color.web("#334155"));
                tick.setStrokeWidth(2);
                out.add(tick);
            }
            return out;
        }
    }
}
